/*
 * HP9800 Emulator
 * Copyright (C) 2006-2016 Achim Buerger
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

/*
 * 05.07.2016 Rel. 2.00: class created
 * 07.08.2016 Rel. 2.00: beta release
 * 13.08.2016 Rel. 2.01: change ALU operation to use ROM codes 
 * 04.10.2016 Rel. 2.02: optimizing BCD operations
 * 04.10.2016 Rel. 2.02: performance optimization by suppressing useless shift loops
 * 25.10.2016 Rel. 2.03: ALU operations transfered to new class ALU
 */

package emu98;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Vector;

public class CPU
{
  static final int AR1 = 0001744;
  static final int AR2 = 0001754;
  static final int systemProgramCounter = 0001700;
  static final int systemStackPointer = 0001777;
  static final int startupVector = 0000000;
  static final int interruptVector = 0000002;

  Console console;
  Vector<StringBuffer> printBuffer;
  boolean decode = false;

  // ALU
  ALU alu;
  
  // main memory
  Memory memory[];

  // I/O-unit
  IOunit ioUnit;

  // CPU registers
  Register Aregister, Bregister, Eregister; // ALU registers
  Register Mregister, Tregister; // memory adress and transfer registers
  QRegister Qregister; // qualifier register
  Register Pregister; // program counter
  IRegister Iregister; // IOregister is implemented in IOunit
  Register register[];

  // data buses
  Register Rbus, Sbus, Tbus; // sources and destinations for register transfers and ALU operations

  // constant values for shift operations
  Register one, zero;

  int PC; // microprogram counter
  boolean ABselector; // AB register selector
  int BC, DC; // binary and decimal carry
  int primaryModifier, secondaryModifier;

  // micro operations

  // R-bus operations
  public MicroOperation UTR = new UTR(), PTR = new PTR(), TRE = new TRE(), WTM_ZTR = new WTM_ZTR();
  public MicroOperation TQ6_ZTR = new TQ6_ZTR(), QTR = new QTR(), RDM_ZTR = new RDM_ZTR(), ZTR = new ZTR();

  // S-bus operations
  public MicroOperation ZTS = new ZTS(), MTS = new MTS(), TTS = new TTS(), UTS = new UTS();

  // X-code operations
  public MicroOperation TTQ = new TTQ(), QAB = new QAB(), BCD = new BCD(), TBE = new TBE();
  public MicroOperation CAB = new CAB(), TTP = new TTP(), TTX = new TTX(), NOP = new NOP();

  // ALU operations
  public MicroOperation XOR = new XOR(), AND = new AND(), IOR = new IOR(), ZTT = new ZTT();
  public MicroOperation ZTT_CBC = new ZTT_CBC(), IOR_CBC = new IOR_CBC(), IOR_SBC = new IOR_SBC(), ADD = new ADD();

  // derived operations
  public MicroOperation TQR = new TQR(), IOS = new IOS(), ETR = new ETR();

  // I/O operation used in IOunit
  public MicroOperation XTR = new XTR(), ITS = new ITS(), TTO = new TTO();

  // micro encoding
  MicroOperation Rcode[] = {UTR, PTR, TRE, WTM_ZTR, TQ6_ZTR, QTR, RDM_ZTR, ZTR, TQR, IOS, ETR};
  MicroOperation Scode[] = {ZTS, MTS, TTS, UTS};
  MicroOperation Xcode[] = {TTQ, QAB, BCD, TBE, CAB, TTP, TTX, NOP};
  MicroOperation Acode[] = {XOR, AND, IOR, ZTT, ZTT_CBC, IOR_CBC, IOR_SBC, ADD};

  // complete decoded micro-program
  MicroInstruction[] microProgram;

  // micro code storage, each instruction has 28 bits
  int[] microCode_ROM;

  public CPU(Memory memory[])
  {
    MicroInstruction instr;
    int ac;
    int code, i;
    StringBuffer line;

    printBuffer = new Vector<StringBuffer>();
    this.memory = memory;

    Aregister = new Register("A", 16, 0177777);
    Bregister = new Register("B", 16, 0177777);
    Eregister = new Register("E", 4, 017);

    Mregister = new Register("M", 16, 0177777);
    Tregister = new Register("T", 16, 0177777);

    Qregister = new QRegister("Q", 16, 0177777);
    Pregister = new Register("P", 16, 0177777);

    register = new Register[] {Aregister, Bregister, Eregister, Mregister, Tregister, Qregister, Pregister};

    one = new Register("1", 1, 1);
    zero = new Register("0", 1, 0);

    Tbus = new Register("T-Bus", 1, 0);

    microProgram = new MicroInstruction[256];
    microCode_ROM = new int[256];
    loadROMdump("Microcode", microCode_ROM);

    line = new StringBuffer("HP9800 Microcode Decoding:\n");
    printBuffer.add(line);
    line = new StringBuffer("PA__ NA__ BRC IQN C XTR RC_ SC_ XC_ TTM TTT AC_____\n");
    printBuffer.add(line);

    // load and decode micro-program for faster execution
    for(i = 0; i < 256; i++) {
      code = microCode_ROM[i];
      microProgram[i] = instr = new MicroInstruction(code);

      instr.SC = Scode[((code & 0b10) >> 1) | ((code & 0b01) << 1)]; // S-code bits are in reverse order 
      code >>= 2;

      instr.TTT = 1 - (code & 1); // TTT is inverted
      code >>= 1;
      
      instr.TTM = 1 - (code & 1); // TTM is inverted
      code >>= 1;

      ac = (code & 1) << 2; // A-code bit 2
      code >>= 1;

      instr.XC = Xcode[((code & 0b100) >> 2) | ((code &0b011) << 1)]; // X-code bits are rotated by 1
      instr.BCD = instr.XC == BCD? 1 : 0;
      code >>= 3;

      instr.RC = Rcode[((code & 0b100) >> 2) | (code & 0b010) | ((code &0b001) << 2)]; // R-code bits are in reverse order
      code >>= 3;

      instr.BRC = code & 1;
      code >>= 1;

      instr.XTR = 1 - (code & 1); // XTR is inverted
      code >>= 1;

      instr.ALUcode = ac | ((code & 0b10) >> 1) | ((code & 0b01) << 1); // A-code bits are in reverse order
      instr.AC = Acode[instr.ALUcode];
      code >>= 2;

      instr.IQN = code & 1;
      code >>= 1;

      instr.CYCLE = (code & 0b1111);
      code >>= 4;

      instr.SM = ((code & 0b0111) << 1) | ((code & 0b1000) >> 3); // S-code bits are rotated by 1
      code >>= 4;

      instr.PM = code & 0b1111; code >>= 4;

      // build derived operations

      if(instr.XTR == 1) {
        if(instr.RC == UTR) { // XTR*UTR = TQR
          instr.RC = TQR;
          instr.XTR = 0; // don't execute XTR! Otherwise A/B-register would be shifted
        }

        if(instr.RC == PTR) {	// XTR*PTR = IOS
          instr.RC = IOS;
          // execute XTR for use in IOunit OTx and MIx
        } 
      }

      // TRE*TBE = ETR, TTX
      if(instr.RC == TRE)
        if(instr.XC == TBE) {
          instr.RC = ETR;
          instr.XC = TTX;
        }

      // is shift optimization possible?
      instr.shiftOptimize = instr.TTT != 1 && instr.TTM != 1 && instr.XTR != 1;
      instr.shiftOptimize &= instr.SC.shiftOptimize && instr.XC.shiftOptimize && instr.RC.shiftOptimize && instr.AC.shiftOptimize;

      // print decoded instruction
      line = instr.decoded = new StringBuffer("");

      // primary & secondary address
      line.append(intToOctalString(i >> 4, 2));
      line.append(intToOctalString(i & 0b1111, 2));
      line.append(" ");

      // next address
      line.append(intToOctalString((i >> 4) ^ instr.PM, 2));
      line.append(intToOctalString((i & 0b1111) ^ instr.SM, 2));
      line.append(" ");

      if(instr.BRC == 1) {
        qualifierDecode(instr.PM, line);
      } else
        line.append("    ");

      if(instr.IQN == 1) {
        qualifierDecode(instr.PM, line);
      } else
        line.append("    ");

      line.append(intToHexString(instr.CYCLE, 1));
      line.append(instr.shiftOptimize ? "*" : " ");

      line.append(instr.XTR == 1 ? "XTR " : "    ");
      line.append(instr.RC.name + " ");
      line.append(instr.SC.name + " ");
      line.append(instr.XC.name + " ");
      line.append(instr.TTM == 1 ? "TTM " : "    ");
      line.append(instr.TTT == 1 ? "TTT " : "    ");
      line.append(instr.AC.name);
      line.append("\n");

      printBuffer.add(line);
    }
    
    // initialize ALU
    alu = new ALU();

    System.out.println("\nHP9800 CPU loaded.");
  }

  public void setIOunit(IOunit ioUnit)
  {
    this.ioUnit = ioUnit;
    Iregister = ioUnit.Iregister;
  }

  // assign console class
  public void setDisassemblerOutput(Console console)
  {
    this.console = console;
  }

  public void outputDecoderToConsole()
  {
    for(int i = 0; i < printBuffer.size(); i++) {
      console.append(printBuffer.get(i).toString());
    }
  }

  public void setDecode(boolean on)
  {
    decode = on;
  }

  // Dump contents of FP register in hex form
  public String showRegister(int address)
  {
    StringBuffer out = new StringBuffer("\t");

    for(int i = 0; i <= 3; i++) {
      out.append(intToHexString(memory[address+i].getValue(), 4) + " ");
    }

    return(out.toString());
  }

  void loadROMdump(String ROMname, int[] ROM)
  {
    String line;
    int address;
    DataInputStream dumpFile = null;

    System.out.print("HP9800 " + ROMname + " ROM ");

    try{
      dumpFile = new DataInputStream(getClass().getResourceAsStream("/media/HP9800/" + ROMname + "_ROM.dmp"));
    } catch (NullPointerException e) {
      System.out.println("dump not found!");
      System.exit(1);
    }

    // Read ROM file line by line
    try {
      for(address = 0; address < ROM.length; address++) {
        line = dumpFile.readLine();
        if(line == null) {
          System.err.println("dump file is too short.");
          System.exit(1);
        }

        try {
          // read octal value
          int code = Integer.parseInt(line, 2);

          //store in ROM
          ROM[address] = code;
        } catch (NumberFormatException e) {
          // format error
          System.err.println(e.toString());
          System.exit(1);
        }
      }

      dumpFile.close();
      System.out.println("loaded.");

    } catch (NullPointerException e) {
      System.out.println("dump not found!");
      System.exit(1);
    } catch (IOException e) {
      // read error
      System.err.println(e.toString());
      System.exit(1);
    }
  }

  public String intToBinaryString(int value, int digits)
  {
    String bin_value = Integer.toBinaryString(value);
    return("00000000000000000000".substring(20 - digits).substring(bin_value.length()) + bin_value);
  }

  public String intToOctalString(int value, int digits)
  {
    String oct_value = Integer.toOctalString(value);
    return("0000000000".substring(10 - digits).substring(oct_value.length()) + oct_value);
  }

  public String intToHexString(int value, int digits)
  {
    String hex_value = Integer.toHexString(value);
    return("0000000000".substring(10 - digits).substring(hex_value.length()) + hex_value);
  }

  public void exec()
  {
    MicroInstruction instr;
    int clock, i;
    boolean brc = false;
    StringBuffer line = null; 
    Register reg;

    instr = microProgram[PC];

    if(decode) {
      line = new StringBuffer(" ");

      line.append(intToOctalString(Aregister.getValue(), 6));
      line.append(" ");
      line.append(intToOctalString(Bregister.getValue(), 6));
      line.append(" ");
      line.append(intToHexString(Eregister.getValue(), 1));
      line.append(" ");
      line.append(intToOctalString(Mregister.getValue(), 6));
      line.append(" ");
      line.append(intToOctalString(Tregister.getValue(), 6));
      line.append(" ");
      line.append(intToOctalString(Qregister.getValue(), 6));
      line.append(" ");
      line.append(intToOctalString(Pregister.getValue(), 6));
      line.append(" ");
      line.append(intToHexString(Iregister.getValue(), 6));
      line.append("\t");

      // control select codes 2=MLS, 2=MCR, 1=SIH, SSF
      line.append(ioUnit.MLS? "1":".");
      line.append(ioUnit.MCR? "1":".");
      line.append(ioUnit.SIH? "1":".");
      line.append(ioUnit.SSF? "1":".");

      // SSI
      line.append(ioUnit.SSI != 0? "1":".");

      // flag select codes 16=KLS, 8=DEN, 4=PEN, 2=MFL, 1=CEO, 0=STP
      line.append(ioUnit.KLS? "1":".");
      line.append(ioUnit.DEN? "1":".");
      line.append(ioUnit.PEN? "1":".");
      line.append(ioUnit.MFL? "1":".");
      line.append(ioUnit.CEO? "1":".");
      line.append(ioUnit.STP? "1":".");

      line.append(showRegister(AR1));
      line.append(showRegister(AR2));

      line.append("\t");
      line.append(BC == 1? "1":".");
      line.append(DC == 1? "1":".");
      line.append(ABselector? "B" : "A");
      line.append("\t");
      line.append(instr.decoded);

      console.append(line.toString());
    }

    primaryModifier = instr.PM;
    secondaryModifier = instr.SM;

    // set data flow sources
    if(instr.XTR == 1) { // XTR must be executed before RC and XC to override ZTR and TBE (E to R)
      Rbus = ABselector ? Bregister : Aregister; // shift A/B-Register to R-bus
      Rbus.shiftEnable(true);
    } 

    instr.SC.exec(); // execute S-bus operation
    instr.RC.exec(); // execute R-bus operation at last as preparation for IOS

    // set data flow destination
    if(instr.TTM == 1) {
      Mregister.setSource(Tbus);
      Mregister.shiftEnable(true);
    }

    if(instr.TTT == 1) {
      Tregister.setSource(Tbus);
      Tregister.shiftEnable(true);
    }

    instr.XC.exec(); // execute X operation

    // if Rbus or Sbus have no defined source they are 0
    if(Rbus == null)
      Rbus = zero;
    if(Sbus == null)
      Sbus = zero;
    
    // number of shift cycles is CYCLE+1
    clock = instr.CYCLE;

    // inhibit shifting if I/O operation (don't inhibit in SRA)
    if(instr.RC == IOS)
      if((Qregister.getValue() & 0b0000_0100_0000_0000) != 0)
        clock = -1;

    // inhibit shifting if qualifier not met
    if(instr.IQN == 1)
      if(!qualifierTest(primaryModifier))
        clock = -1;

    // prepare ALU parameters
    alu.init(BC, instr.RC == UTR? 1 : 0, instr.BCD);

    // shift loop
    do {
      // execute ALU operation
      alu.exec(instr.ALUcode);
  
      // branch condition must be checked before last shift and executed after shifting (falling edge of ROMCLK loads PC-register)
      if(instr.BRC == 1)
        brc = !qualifierTest(primaryModifier);

      if(clock < 0 || instr.shiftOptimize) {
        if(instr.RC == TQ6_ZTR) {  // TQ6 has to be executed even if shift is inhibited
          Qregister.loadInput();   // load T-bus to input of Q-register
          Qregister.shift();       // load input to Q6
        }

        if(instr.BCD == 0 && clock >= 0) // set binary carry only in case of a performance optimized loop
          BC = alu.bc;
        
        break; // exit loop
      }

      // load all registers with input value from their shift sources
      for(i = 0; i < register.length; i++)
        if((reg = register[i]).shiftEnabled)
          reg.loadInput();
      
      // then shift all registers if enabled
      for(i = 0; i < register.length; i++)
        if((reg = register[i]).shiftEnabled)
          reg.shift();
      
      // binary carry FF is set and cleared only during shift operation (ROMCLK = 1)
      if(instr.BCD == 0)
        BC = alu.bc;
      
    } while(--clock >= 0);

    // finish BCD operations after shift loop, when ROMCLK changes 1->0 
    if(instr.BCD == 1) {
      // transfer BCD result to A only if UTR false
      if(alu.utr == 0)
        Aregister.setValue((Aregister.getValue() & 0xfff0) | alu.z);
      DC = alu.dc; // set decimal carry (SDC is implemented in BCD ROM with UTR=1
    }
    
    if(brc)
      secondaryModifier &= 0b1110; // mask lowest bit in secondary modifier
    PC ^= (primaryModifier << 4) | secondaryModifier; // get next address by XORing current address with PM and SM

    // cleanup shift sources to register rotation
    cleanSources();
  }

  public class ALU
  {
    // ALU operation storage
    int[] ALU_ROM, ALUdecoded;

    // BCD operation storage
    int[] BCD_ROM, BCDdecoded;

    int i, bc, dc, r, s, t, bcd, utr, y, z;
    StringBuffer line;

    public ALU()
    {
      int xy, sum;

      // load and decode ALU ROM for faster execution
      ALU_ROM = new int[256];
      ALUdecoded = new int[256];
      loadROMdump("ALUcode", ALU_ROM);

      line = new StringBuffer("HP9800 ALU ROM Decoding:\n");
      printBuffer.add(line);
      line = new StringBuffer("B AC_ T C R S Y___ ADDR\n");
      printBuffer.add(line);

      for(int b = 0; b < 2; b++) // BCD = 0 or 1
        for(int a = 0; a < 8; a++) // ALU code = 0...7
          for(int t = 0; t < 2; t++) // T1 = 0 or 1
            for(int c = 0; c < 2; c++) // binary carry = 0 or 1
              for(int r = 0; r < 2; r++) // R-bus = 0 or 1
                for(int s = 0; s < 2; s++) { // S-bus = 0 or 1
                  if(b == 0) {
                    i = a & 0b101 | t << 1; // AC2,T1,AC0
                    i = i << 2 | a & 0b10 | (1 - c); // AC1, BC is inverted
                  } else {
                    i = 0b1000 | a & 0b100 | t << 1; // T1
                    i = i << 1 | ~a >> 1 & 1 | ~a << 1 & 0b10; // T2, T3 (in BCD-mode AC0 and AC1 have to be replaced by inverted T2 and T3)  
                    i = i << 1 | (1 - c); // DC is inverted 
                  }

                  i = i << 1 | (1 - r); // Rbus is inverted
                  i = i << 1 | (1 - s); // Sbus is inverted
                  ALUdecoded[a << 5 | b << 4 | r << 3 | s << 2 | t << 1 | c] = ALU_ROM[i];

                  // print decoded operation
                  line = new StringBuffer("");
                  line.append(b);
                  line.append(" ");
                  line.append(intToBinaryString(a, 3));
                  line.append(" ");
                  line.append(t);
                  line.append(" ");
                  line.append(c);
                  line.append(" ");
                  line.append(r);
                  line.append(" ");
                  line.append(s);
                  line.append(" ");
                  line.append(intToBinaryString(ALU_ROM[i], 4));
                  line.append(" ");
                  line.append(i);
                  line.append("\n");

                  printBuffer.add(line);
                }

      // load and decode BCD ROM for faster execution
      BCD_ROM = new int[256];
      BCDdecoded = new int[256];
      loadROMdump("BCDcode", BCD_ROM);

      line = new StringBuffer("HP9800 BCD ROM Decoding:\n");
      printBuffer.add(line);
      line = new StringBuffer("U A__ Y__ T Z___ ADDR\n");
      printBuffer.add(line);

      for(int u = 0; u < 2; u++) // UTR = 0 or 1
        for(int a = 0; a < 8; a++) // A-register A3-A1 = 0...7
          for(int y = 0; y < 8; y++) // ALU-output Y2-Y0 = 0...7
            for(int t = 0; t < 2; t++) { // T1 = 0 or 1
              i = 1 - u; // UTR is inverted
              i = i << 3 | y & 0b100; // Y2
              i = i << 1 | a; // A3,A2,A1
              i = i << 2 | y & 0b11; // Y1,Y0
              i = i << 1 | t; // T1
              BCDdecoded[a << 5 | y << 2 | t << 1 | u] = BCD_ROM[i];

              // print decoded operation
              line = new StringBuffer("");
              line.append(u);
              line.append(" ");
              line.append(intToBinaryString(a, 3));
              line.append(" ");
              line.append(intToBinaryString(y, 3));
              line.append(" ");
              line.append(t);
              line.append(" ");
              line.append(intToBinaryString(BCD_ROM[i], 4));
              line.append(" ");
              line.append(i);
              line.append("\n");

              printBuffer.add(line);
            }

      System.out.print("HP9800 ALU test:");

      for(int c = 0; c < 2; c++) {
        for(int a = 0; a < 10; a++) {
          System.out.print("\n");
          System.out.print(a);
          System.out.print(c == 0? "+":"-");
          for(int t = 0; t < 10; t++) {
            System.out.print(t);
            for(int d = 0; d < 2; d++) {
              r = a & 1; // R-bus
              s = t & 1; // S-bus
              i = c << 2 | t >> 2 & 0b11;  // AC2,T3,T2
              i = i << 5 | 0b10000 | r << 3 | s << 2 | t & 0b10 | d;
              y = ALUdecoded[i];
              
              i = (a & 0b1110) << 4 | (y & 0b111) << 2 | t &0b10 | 0;
              z = BCDdecoded[i];

              xy = z & 1;
              z = z & 0b1110 | y >> 3;

              if(c == 0) { // compare BCD ADD result
                sum = a + t + d;
              } else {// compare BCD SUB result
                sum = a + 9 - t + d;
              }

              if(sum >= 10) {
                sum -= 10;
                DC = 1;
              } else
                DC = 0;

              if(sum != z || DC != xy) {
                System.out.print(":failure ");
              }
            }
          }
        }
      }
    }
    
    public void init(int BC, int utr, int bcd)
    {
      // prepare ALU parameters
      bc = BC;
      this.bcd = bcd;
      this.utr = utr;
      z = 0;
    }
    
    public int exec(int ac)
    {
      // execute ALU operation
      dc = DC;
      r = Rbus.getOutput();
      s = Sbus.getOutput();
      t = Tregister.getValue();

      if(bcd == 0) {
        i = ac << 5 | r << 3 | s << 2 | t & 0b10 | bc;
      } else {
        i = ac & 0b100 | t >> 2 & 0b11;
        i = i << 5 | 0b10000 | r << 3 | s << 2 | t & 0b10 | dc;
      }

      y = ALUdecoded[i];
      Tbus.setValue(y >> 3); // put Y3 into T-bus

      if(bcd == 0) {
        // change binary carry only if A2=1 (carry relevant operations)
        if((ac & 0b100) != 0)
          bc = y >> 2 & 1; // put Y2 into temp. BC
      } else {
        ac = Aregister.getValue();
        i = (ac & 0b1110) << 4 | (y & 0b111) << 2 | t &0b10 | utr;
        z = BCDdecoded[i];

        // transfer results to Aregister and DC only after shift loop (when ROMCLK 1->0)
        dc = z & 1; // decimal carry
        z = z & 0b1110 | Tbus.getValue();
      }

      return(z);
    }
  }

  public void POP()
  {
    PC = 0b1111_1111;
    cleanSources();
    ioUnit.POP();
  }

  // set binary carry (used by /O-Instructions SFS and SFC to increment P-register by 2)
  public void setBC(boolean value)
  {
    BC = value? 1 : 0;
  }

  void cleanSources()
  {
    // set alls shift sources to register rotation and disable shifting
    for(int i = 0; i < register.length; i++) {
      register[i].setSource(register[i]);
    }

    Qregister.setQ6mode(false);
    Iregister.set8Bitmode(false);

    Rbus = Sbus = null; // necessary for ZTR
  }

  boolean qualifierTest(int PM)
  {
    int q = Qregister.getValue();

    if(PM <= 6) {
      return((q & (1 << PM)) != 0);  // test bit PM of Q-register
    }

    switch(PM) {
    case 7: return(BC != 0);  // test binary carry
    case 8: return(Pregister.getOutput() != 0);  // test bit 0 of P-register
    case 9: return((q & 0b1000_0000_0000_0000) != 0);  // test bit 15 of Q-register
    case 10: return((q & 0b0111_0000_0000_0000) != 0b0111_0000_0000_0000);  // test memory reference operation, Q-register bits 14,13,12 <> 111
    case 11: return((q & 0b0000_0100_0000_0000) != 0);  // test bit 10 of Q-register
    case 12: return(!ioUnit.serviceRequested()); // test if no service request
    case 13: return((q & 0b0000_0001_0000_0000) != 0);  // test bit 8 of Q-register
    case 14: return(DC != 0);  // test decimal carry
    case 15: return(ioUnit.QRD); // test I/O active
    }

    return(false);
  }

  void qualifierDecode(int PM, StringBuffer line)
  {
    switch(PM) {
    case 0: line.append("Q0  "); break;
    case 1: line.append("Q1  "); break;
    case 2: line.append("Q2  "); break;
    case 3: line.append("Q3  "); break;
    case 4: line.append("Q4  "); break;
    case 5: line.append("Q5  "); break;
    case 6: line.append("Q6  "); break;
    case 7: line.append("BC  "); break;
    case 8: line.append("P0  "); break;
    case 9: line.append("Q15 "); break;
    case 10: line.append("QMR "); break;
    case 11: line.append("Q10 "); break;
    case 12: line.append("QNR "); break;
    case 13: line.append("Q8  "); break;
    case 14: line.append("DC  "); break;
    case 15: line.append("QRD "); break;
    }
  }

  class MicroInstruction
  {
    int microCode, ALUcode, BCD;
    int PM, SM, CYCLE;
    int BRC, IQN, XTR, TTM, TTT;
    StringBuffer decoded;
    boolean shiftOptimize;

    MicroOperation RC, SC, XC, AC;

    public MicroInstruction(int code)
    {
      microCode = code;
    }
  }

  public class MicroOperation
  {
    String name;
    boolean shiftOptimize;

    public MicroOperation()
    {}

    public void exec()
    {}
  }

  /*
   * R-bus operations
   */

  class UTR extends MicroOperation
  {
    /*
     *  One (1) is shifted into R-bus
     */
    public UTR()
    {
      name = "UTR";
      shiftOptimize = true;
    }

    public void exec()
    {
      Rbus = one;
    }
  }

  class PTR extends MicroOperation
  {
    /*
     *  P-register is shifted into R-bus
     */
    public PTR()
    {
      name = "PTR";
      shiftOptimize = false;
    }

    public void exec()
    {
      Rbus = Pregister;
      Pregister.shiftEnable(true);
    }
  }

  class TRE extends MicroOperation
  {
    /*
     *  T-register is shifted into E-Register and E-Register is shifted into R-bus
     */

    public TRE()
    {
      name = "TRE";
      shiftOptimize = false;
    }

    public void exec()
    {
      Eregister.setSource(Tregister);
      Rbus = Eregister;
      Tregister.shiftEnable(true);
      Eregister.shiftEnable(true);
    }
  }

  class WTM_ZTR extends MicroOperation
  {
    /*
     *  T-register is stored into memory[M] and 0 is right shifted into R-bus
     */

    public WTM_ZTR()
    {
      name = "WTM";
      shiftOptimize = true;
    }

    public void exec()
    {
      memory[Mregister.getValue() & 0077777].setValue(Tregister.getValue()); // indirect bit must be discarded in memory access
      if(Rbus == null) Rbus = zero;
    }
  }

  class TQ6_ZTR extends MicroOperation
  {
    /*
     *  T-bus is shifted into bit 6 of Q-register and 0 is shifted into R-bus
     */

    public TQ6_ZTR()
    {
      name = "TQ6";
      shiftOptimize = false;
    }

    public void exec()
    {
      Qregister.setSource(Tbus);
      Qregister.setQ6mode(true); // shift T-bus into Q-register bit 6
      if(Rbus == null) Rbus = zero;
      Qregister.shiftEnable(true);
    }
  }

  class QTR extends MicroOperation
  {
    /*
     *  Q-register is shifted into R-bus
     */

    public QTR()
    {
      name = "QTR";
      shiftOptimize = false;
    }

    public void exec()
    {
      Rbus = Qregister;
      Qregister.shiftEnable(true);
    }
  }

  class RDM_ZTR extends MicroOperation
  {
    /*
     *  memory[M] is read to T-register and 0 is shifted into R-bus
     */

    public RDM_ZTR()
    {
      name = "RDM";
      shiftOptimize = true;
    }

    public void exec()
    {
      Tregister.setValue(memory[Mregister.getValue() & 0077777].getValue()); // indirect bit must be discarded
      if(Rbus == null) Rbus = zero;
    }
  }

  class ZTR extends MicroOperation
  {
    /*
     *  Zero (0) is shifted into R-bus
     *  Since all inputs of R-bus are in the hardware or-ed, ZTR can be combined with XTR without destroying the data.
     *  To simulate this behaviour, ZTR is only executed if R-bus is not set by XTR (which will be exec'd before ZTR
     */
    public ZTR()
    {
      name = "ZTR";
      shiftOptimize = true;
    }

    public void exec()
    {
      if(Rbus == null) Rbus = zero;
    }
  }

  /*
   * S-bus operations
   */

  class ZTS extends MicroOperation
  {
    /*
     *  Zero (0) is shifted into S-bus
     */
    public ZTS()
    {
      name = "ZTS";
      shiftOptimize = true;
    }

    public void exec()
    {
      Sbus = zero;
    }
  }

  class MTS extends MicroOperation
  {
    /*
     *  M-register is shifted into S-bus
     */

    public MTS()
    {
      name = "MTS";
      shiftOptimize = false;
    }

    public void exec()
    {
      Sbus = Mregister;
      Mregister.shiftEnable(true);
    }
  }

  class TTS extends MicroOperation
  {
    /*
     *  T-register is shifted into S-bus
     */

    public TTS()
    {
      name = "TTS";
      shiftOptimize = false;
    }

    public void exec()
    {
      Sbus = Tregister;
      Tregister.shiftEnable(true);
    }
  }

  class UTS extends MicroOperation
  {
    /*
     *  One (1) is shifted into S-bus
     */
    public UTS()
    {
      name = "UTS";
      shiftOptimize = true;
    }

    public void exec()
    {
      Sbus = one;
    }
  }

  class ITS extends MicroOperation
  {
    /*
     *  IO-register is shifted into S-bus (used only in IOunit)
     */

    public ITS()
    {
      name = "ITS";
      shiftOptimize = false;
    }

    public void exec()
    {
      Sbus = Iregister;
      Iregister.shiftEnable(true);
      Iregister.setSource(zero);  // contents of IO-register is not preserved
    }
  }

  class TTO extends MicroOperation
  {
    /*
     *  T-bus is shifted into IO-register
     */
    public TTO()
    {
      name = "TTO";
      shiftOptimize = false;
    }

    public void exec()
    {
      Iregister.setSource(Tbus);
      Iregister.shiftEnable(true);
    }
  }

  class XTR extends MicroOperation
  {
    /*
     *  A/B-register is shifted into R-bus
     */
    public XTR()
    {
      name = "XTR";
      shiftOptimize = false;
    }

    public void exec()
    {
      Rbus = ABselector ? Bregister : Aregister;
      Rbus.shiftEnable(true);
    }
  }


  /*
   * X-register operations
   */

  class TTQ extends MicroOperation
  {
    /*
     *  T-bus is shifted into Q-register
     */
    public TTQ()
    {
      name = "TTQ";
      shiftOptimize = false;
    }

    public void exec()
    {
      Qregister.setSource(Tbus);
      Qregister.shiftEnable(true);
    }
  }

  class QAB extends MicroOperation
  {
    /*
     *  set A/B-register selector to value of Q-register bit 10
     */
    public QAB()
    {
      name = "QAB";
      shiftOptimize = true;
    }

    public void exec()
    {
      ABselector = (Qregister.getValue() & 0b0000_1000_0000_0000) != 0; // load Q11 to AB-selector
      DC = 0; // clear decimal carry
    }
  }

  class BCD extends MicroOperation
  {
    /*
     *  BCD operations are evaluated in ALU and BCD ROM
     */
    public BCD()
    {
      name = "BCD";
      shiftOptimize = true;
    }

    public void exec()
    {}
  }

  class TBE extends MicroOperation
  {
    /*
     *  T-bus is shifted into E-Register and E-Register is shifted into R-bus
     */

    public TBE()
    {
      name = "TBE";
      shiftOptimize = false;
    }

    public void exec()
    {
      Eregister.setSource(Tbus);
      // shift E to R-bus only if this is not already filled by another source, different from zero.
      // In hardware all sources for R-bus are or-ed
      if(Rbus == null || Rbus == zero)
        Rbus = Eregister;

      Eregister.shiftEnable(true);
    }
  }

  class CAB extends MicroOperation
  {
    /*
     *  complement set A/B-register selector
     */
    public CAB()
    {
      name = "CAB";
      shiftOptimize = true;
    }

    public void exec()
    {
      ABselector = !ABselector;
    }
  }

  class TTP extends MicroOperation
  {
    /*
     *  T-bus is shifted into P-register
     */
    public TTP()
    {
      name = "TTP";
      shiftOptimize = false;
    }

    public void exec()
    {
      Pregister.setSource(Tbus);
      Pregister.shiftEnable(true);
    }
  }

  class TTX extends MicroOperation
  {
    /*
     *  T-bus is shifted into register A or B
     */
    public TTX()
    {
      name = "TTX";
      shiftOptimize = false;
    }

    public void exec()
    {
      if(!ABselector) {
        Aregister.setSource(Tbus);
        Aregister.shiftEnable(true);
      }	else {
        Bregister.setSource(Tbus);
        Bregister.shiftEnable(true);
      }
    }
  }

  class NOP extends MicroOperation
  {
    /*
     *  no operation
     */
    public NOP()
    {
      name = "NOP";
      shiftOptimize = true;
    }

    public void exec()
    {}
  }

  class TQR extends MicroOperation
  {
    /*
     *  primary address modifier comes from Q (Q14,Q13,Q12,Q11*Q14)
     */
    public TQR()
    {
      name = "TQR";
      shiftOptimize = true;
    }

    public void exec()
    {
      primaryModifier = (Qregister.getValue() >> 11) & 0b01111;
      primaryModifier &= ((primaryModifier >> 3) | 0b1110); 
    }
  }

  class IOS extends MicroOperation
  {
    /*
     *  start I/O operation if Q10=1
     *  set single-service FF via nSRA if Q10=0
     */
    public IOS()
    {
      name = "IOS";
      shiftOptimize = true;
    }

    public void exec()
    {
      ioUnit.exec();
    }
  }

  class ETR extends MicroOperation
  {
    /*
     *  0 is shifted into E-Register and E-Register is shifted into R-bus
     */

    public ETR()
    {
      name = "ETR";
      shiftOptimize = false;
    }

    public void exec()
    {
      Eregister.setSource(zero);
      Rbus = Eregister;
      Eregister.shiftEnable(true);
    }
  }

  /*
   * ALU operations, not executed. Operation is performed by ALU ROM
   */

  class XOR extends MicroOperation
  {
    /*
     *  Rbus XOR Sbus -> Tbus
     */
    public XOR()
    {
      name = "XOR";
      shiftOptimize = true;
    }

    public void exec()
    {
    }
  }

  class IOR extends MicroOperation
  {
    /*
     *  Rbus OR Sbus -> Tbus
     */
    public IOR()
    {
      name = "IOR";
      shiftOptimize = true;
    }

    public void exec()
    {
    }
  }

  class AND extends MicroOperation
  {
    /*
     *  Rbus AND Sbus -> Tbus
     */
    public AND()
    {
      name = "AND";
      shiftOptimize = true;
    }

    public void exec()
    {
    }
  }

  class ZTT extends MicroOperation
  {
    /*
     *  0 -> T-register
     */
    public ZTT()
    {
      name = "ZTT";
      shiftOptimize = true;
    }

    public void exec()
    {
    }
  }

  class ZTT_CBC extends MicroOperation
  {
    /*
     *  0 -> T-register, clear binary carry
     */
    public ZTT_CBC()
    {
      name = "ZTT.CBC";
      shiftOptimize = true;
    }

    public void exec()
    {
    }
  }

  class IOR_CBC extends MicroOperation
  {
    /*
     *  Rbus OR Sbus -> Tbus, clear binary carry
     */
    public IOR_CBC()
    {
      name = "IOR.CBC";
      shiftOptimize = true;
    }

    public void exec()
    {
    }
  }

  class IOR_SBC extends MicroOperation
  {
    /*
     *  Rbus OR Sbus -> Tbus, set binary carry
     */
    public IOR_SBC()
    {
      name = "IOR.SBC";
      shiftOptimize = true;
    }

    public void exec()
    {
    }
  }

  class ADD extends MicroOperation
  {
    /*
     * Rbus + Sbus -> Tbus
     */
    public ADD()
    {
      name = "ADD";
      shiftOptimize = true;
    }

    public void exec()
    {
    }
  }
}
