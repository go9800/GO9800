/*
 * HP9800 Emulator
 * Copyright (C) 2006-2011 Achim Buerger
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
 * Bugfix: memory.setValue has to return masked value (malfunction in ISZ)
 * Bugfix: Simple indirect jump has to use register.value (malfunction in JMP A,I)
 * Bugfix: Program counter has to incremented after opcode exec (malfunction in asm-subroutine parameter fetch)
 * Bugfix: nested indirect memory adressing was missing (malfunction in JMP A,I)
 * Bugfix: DRS has to set D1 to 0 instead of A
 * Bugfix: Error in fadd carry handling
 * Bugfix: Error in cmpl digit-word increment 
 * Bugfix: Error in MRX,MRY: E was not set to 0
 * Bugfix: Error in FDV: result no summed to existing value in B register
 * 05.05.2006 Bugfix: Error in FMP: last 4 bits in B have to be set to 1111 (undocumented!)
 * 09.05.2006 Bugfix: Error in XFR: set A and B to last transfer addresses (undocumented!) 
 * 11.05.2006 Bugfix: In CMX,CMY cmpl must *NOT* use MDI with overflow handling (malfunction in TAN())
 * 14.05.2006 Implemented 1ms sleep after output to display (OTx 8)
 * 15.05.2006 Bugfix: Service Request routine has to return to P not P+1
 * 25.05.2006 Limit addressable memory to 32k words (wrap around at 0100000) 
 * 05.09.2006 Move instantiation of system RWM to mainframe class 
 * 18.10.2006 Use DisasmOutput instead of console 
 * 19.10.2006 Run emulator in interruptable thread (for trace purpose)
 * 04.11.2006 Changed use of disasmOutput: append to line buffer instead 
 * 19.11.2006 Changed register dump: allways show contents before instruction execution
 * 24.11.2006 Changed reading of ROM dump from plain file to JAR 
 * 18.12.2006 Rel. 0.21: Changed Thread.sleep(0) to Thread.yield();
 * 10.02.2007 Rel. 0.30: Added opcode timing measurement
 * 25.02.2007 Rel. 0.30: Added loadConfig(), setROM()
 * 02.04.2007 Rel. 1.00 Bugfix: set A and B register to 0 after machine reset.
 * 03.05.2007 Rel. 1.01 Bugfix: pushStack now limited to 32k SP address range (for system test programs)
 * 24.05.2007 Rel. 1.01 Added Breakpoints and Watchpoints to configuration file and execution control.
 * 12.07.2007 Rel. 1.20 Changed JAR-file access
 * 18.07.2007 Rel. 1.20 Added keyCode configuration file
 * 02.10.2007 Rel. 1.20 Added increment of ioRegister.displayCounter for display blanking
 * 02.10.2007 Rel. 1.20 moved setVisible() to contructor of external devices
 * 27.07.2007 Rel. 1.20 decrement display off counter in instruction loop
 * 27.07.2007 Rel. 1.20 emulator thread at lower priority
 * 18.10.2007 Rel. 1.20 optimized String allocation for instruction disassembler
 * 28.10.2007 Rel. 1.20 Added infinite waiting in disassembler single step mode
 * 27.02.2007 Rel. 1.21 Moved instruction counters for display and keyboard to IOregister.instructionCounter()
 * 30.09.2008 Rel. 1.31 Extended loadKeyConfig() for display of keyboard mapping
 * 06.10.2008 Rel. 1.31 Added file IO mode to HP11202A interface
 * 06.12.2008 Rel. 1.32 Bugfix: loadKeyConfig() now handles missing keyname file correctly
 * 29.12.2008 Rel. 1.32 Added openConfigFile() for using default (in JAR file) and customized configurations
 * 07.01.2009 Rel. 1.33 openConfigFile() now looks for config-files in current working directory, then in the ClassPath, then in the JAR file
 * 18.03.2009 Rel. 1.40 Added check of ROM validity and reload of previous ROM in case of error
 * 01.04.2010 Rel. 1.50 Added method loadDevice with dynamically creation of device class using Reflection API
 * 23.05.2016 Rel. 1.61 Changed disassembly of indirect addressing instructions: output effective memory address 
 * 23.05.2016 Rel. 1.61 Bugfix: initialize unused memory with legal address (not -1) to avoid crash in Watchpoint
 * 24.05.2016 Rel. 1.61 Change: value of program counter is now also stored in systemProgramCounter (memory address 0001700)
 * 02.08.2016 Rel. 2.00 Change: use class CPU for micro-code emulation and class IOunit instead of IOregister 
 * 15.10.2016 Rel. 2.03 Bugfix: Values of A, B, and P register in disassembly output now 6 octal digits wide 
 */

package emu98;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;

import io.IOinterface;
import io.IOdevice;

public class Emulator implements Runnable 
{
  public String model, version;
  public CPU cpu;
  public IOunit ioUnit;
  public Memory[] memory;
  public Hashtable<String, MemoryBlock> memoryBlocks;
  public Hashtable<String, String> keyCodes, keyStrings;

  Thread emuThread;
  public Console console;
  DataInputStream asmFile;
  Register register;

  public long minExecTime, maxExecTime, sumExecTime, numOps, t;
  public boolean measure = false;

  int timerValue = 0;
  boolean disassemble;
  boolean dumpRegisters, dumpFPregisters;
  boolean dumpMicroCode;
  boolean FPop;
  public boolean keyLogMode = false;
  private String instr;

  static final int AR1 = 0001744;
  static final int AR2 = 0001754;

  public Emulator(String machine)
  {
    // initialize complete memory to 'unused'  
    memory = new Memory[0100000];
    //unusedMemory = new Memory(false, -1, 0);
    for(int i = 0; i <= 077777; i++)
      memory[i] = new Memory(false, i, 0);

    // set object variable for trace outputs
    Memory.emu = this;

    // initialize CPU
    cpu = new CPU(memory);

    // initialize IO-unit
    ioUnit = new IOunit(cpu);
    cpu.setIOunit(ioUnit);

    disassemble = dumpRegisters = dumpFPregisters = dumpMicroCode = false;
    emuThread = new Thread(this, "HP9800 CPU");

    // Set emulator priority lower to guarantee that events such as keypresses
    // and device thread timer expirations get service immediately
    emuThread.setPriority(Thread.NORM_PRIORITY - 1);

    memoryBlocks = new Hashtable<String, MemoryBlock>();
    loadConfig(machine);

    keyCodes = new Hashtable<String, String>();
    keyStrings = new Hashtable<String, String>();
    loadKeyConfig(machine);

    setDisassemblerMode(false);
  }

  public void start()
  {
    emuThread.start();
  }

  public void setDisassemblerMode(boolean disasmMode)
  {
    disassemble = dumpRegisters = dumpFPregisters = disasmMode;
  }

  public void setConsole(Console console)
  {
    this.console = console;
    cpu.setDisassemblerOutput(console);
  }

  public void startMeasure()
  {
    minExecTime = 100000000000L; 
    maxExecTime = sumExecTime = numOps = t = 0;
    measure = true;
  }

  public void stopMeasure()
  {
    measure = false;
  }

  public char asciiChar(char c)
  {
    if(c < 0x20 || c > 0x5f)
      c = ' ';
    return(c);
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

  public String memoryReferenceGroup(int opcode, int address)
  {
    String indStr = null;
    boolean indirect;

    int memory_address = opcode & 0001777;

    // current or zero page?
    if((opcode & 0002000) != 0)
      memory_address |= address & 01776000; // current page

    int direct_address = memory_address;

    // direct or indirect?
    indirect = (opcode & 0100000) != 0;

    if(indirect) {
      // nested indirect addressing if bit15 = 1
      do {
        memory_address = memory[memory_address & 0077777].getValue();
      } while((memory_address & 0100000) != 0);
    }

    indStr = indirect? ",I [" + intToOctalString(memory_address, 6) + "]" : "";

    Memory m = memory[memory_address];

    switch(opcode & 0074000) {
    case 0000000:
    case 0004000:
      instr = "AD" + register.name;
      break;

    case 0010000:
    case 0014000:
      instr = "CP" + register.name;
      break;

    case 0020000:
    case 0024000:
      instr= "LD" + register.name;
      break;

    case 0030000:
    case 0034000:
      instr = "ST" + register.name;
      break;

    case 0040000:
      instr = "IOR";
      break;

    case 0044000:
      instr = "ISZ";
      break;

    case 0050000:
      instr = "AND";
      break;

    case 0054000:
      instr = "DSZ";
      break;

    case 0060000:
      instr = "JSM";
      break;

    case 0064000:
      instr = "JMP";
      break;

    default:
      return(instr);
    }

    instr = instr + " " + intToOctalString(direct_address, 6) + indStr;

    return(instr);
  }


  public String shiftRotateGroup(int opcode)
  {
    int shift = ((opcode & 0000740) >> 5) + 1;

    switch(opcode & 0000007) {
    case 0000000:
      instr = "A" + register.name + "R";
      break;

    case 0000002:
      instr = "S" + register.name + "R";
      break;

    case 0000004:
      shift = 17 - shift;
      instr = "S" + register.name + "L";
      break;

    case 0000006:
      instr = "R" + register.name + "R";
      break;

    default:
      return(instr);
    }

    instr = instr + " " + Integer.toString(shift);

    return(instr);
  }


  public String alterSkipGroup(int opcode, int address)
  {
    String skipcode = null;
    String testbit_SC = null;
    boolean set, clear;

    int skip = (opcode & 0001740) >> 5;
    if(skip >= 16)
      skip -= 32;

    // add skip-value for new address
    address += skip;

    clear = (opcode & 0000020) != 0;
    set = (opcode & 0002000) != 0;

    skipcode = "*" + ((skip >= 0)? "+" : "") + Integer.toString(skip);

    if(set)
      testbit_SC = ",S";
    else {
      if(clear)
        testbit_SC = ",C";
      else
        testbit_SC = "";
    }

    switch(opcode & 0000007) {
    case 0000000:
      switch(opcode & 0002020) {
      case 0000000:
        instr = "SZ";
        break;

      case 0002000:
        instr = "RZ";
        break;

      case 0000020:
        instr = "SI";
        break;

      case 0002020:
        instr = "RI";
        break;
      }

      instr = instr + register.name + " " + skipcode;
      return(instr);

    case 0000001:
      instr = "SL" + register.name;
      break;

    case 0000002:
      instr = "S" + register.name + "M";
      break;

    case 0000003:
      instr = "S" + register.name + "P";
      break;

    case 0000004:
      instr = "SES";
      break;

    case 0000005:
      instr = "SEC";
      break;

    default:
      return(instr);
    }

    instr = instr + " " + skipcode + testbit_SC;

    return(instr);
  }


  public String registerReferenceGroup(int opcode, int address)
  {
    String indStr = "";
    int regValue, memory_address;
    boolean indirect;
    String opRegister;
    Memory m = null;

    regValue = register.getValue();
    memory_address = regValue;

    // direct or indirect?
    indirect = (opcode & 0000400) != 0;

    if(indirect)
    {
      // nested indirect addressing if address-bit15 = 1
      while((memory_address & 0100000) != 0) {
        memory_address = memory[memory_address & 0077777].getValue();
      }

      m = memory[memory_address];
      regValue = m.getValue();     // this is also correct for STx, although existing memory value will not be used

      indStr = ",I [" + intToOctalString(memory_address, 6) + "]";
    }

    if((opcode & 000020) == 0)
      opRegister = "A";
    else
      opRegister = "B";

    switch(opcode & 0000360) {
    case 0000000:
    case 0000020:
      instr = "AD" + opRegister;
      break;

    case 0000040:
    case 0000060:
      instr = "CP" + opRegister;
      break;

    case 0000100:
    case 0000120:
      instr = "LD" + opRegister;
      break;

    case 0000140:
    case 0000160:
      instr = "ST" + opRegister;
      break;

    case 0000200:
      instr = "IOR";
      break;

    case 0000220:
      instr = "ISZ";
      break;

    case 0000240:
      instr = "AND";
      break;

    case 0000260:
      instr = "DSZ";
      break;

    case 0000300:
      instr = "JSM";
      break;

    case 0000320:
      instr = "JMP";
      break;

    default:
      return(instr);
    }

    instr += " " + register.name + indStr;
    return(instr);
  }


  public String compExecuteDmaGroup(int opcode, int address)
  {
    switch(opcode & 0000070) {
    case 0000030:
      instr = "DMA";
      return(instr);

    case 0000010:
      instr = "EX";
      break;

    case 0000050:
      instr = "CM";
      break;

    case 0000070:
      instr = "TC";
      break;
    }

    instr += register.name;
    return(instr);
  }

  public String inputOutputGroup(int opcode, int address)
  {
    String testbit_HC = null;
    boolean clear;

    int selectCode = opcode & 0000037;

    clear = ((opcode & 0001000) != 0); // Q9

    testbit_HC = clear? ",C" : "";

    switch(opcode & 0000740) {
    case 0000740:
      if(!clear) {
        instr = "STF";
      } else {
        instr = "CLF";
      }

      instr += " " + Integer.toString(selectCode);
      return(instr);

    case 0000700:
      instr = "SFC";
      break;

    case 0000500:
      instr = "SFS";
      break;

    case 0000540:
      instr = "CLC";
      break;

    case 0000600:
      instr = "STC";
      break;

    case 0000140:
      instr = "OT" + register.name;
      break;

    case 0000240:
      instr = "LI" + register.name;
      break;

    case 0000040:
      instr = "MI" + register.name;
      break;

    default:
      return(instr);
    }

    instr += " " + Integer.toString(selectCode) + testbit_HC;
    return(instr);
  }


  public String macGroup(int opcode)
  {
    switch(opcode) {
    case 0170402:
      FPop = false;
      instr = "RET";
      break;

    case 0170002:
      FPop = false;
      instr = "MOV";
      break;

    case 0170000:
      FPop = false;
      instr = "CLR";
      break;

    case 0170004:
      FPop = false;
      instr = "XFR";
      break;

    case 0174430:
      instr = "MRX";
      break;

    case 0174470:
      instr = "MRY";
      break;

    case 0171400:
      instr = "MLS";
      break;

    case 0170410:
      instr = "DRS";
      break;

    case 0175400:
      instr = "DLS";
      break;

    case 0170560:
      instr = "FXA";
      break;

    case 0171460:
      instr = "FMP";
      break;

    case 0170420:
      instr = "FDV";
      break;

    case 0174400:
      instr = "CMX";
      break;

    case 0170400:
      instr = "CMY";
      break;

    case 0170540:
      instr = "MDI";
      break;

    case 0171450:
      instr = "NRM";
      break;
    }

    return(instr);
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


  public void decode(int opcode, int address)
  {
    String instr = null;
    String fpReg = null;
    StringBuffer line = null; 

    FPop = false;
    if((opcode & 0004000) == 0) {
      register = cpu.Aregister;
    } else { 
      register = cpu.Bregister;
    }

    if(dumpRegisters | disassemble | dumpFPregisters) {
      instr = "???"; // default string: undefined instruction
      line = new StringBuffer(" ");
    }

    if(dumpMicroCode && line != null) {
      line.append("\n>");
    }

    if(dumpRegisters && line != null) {
      // dump register and flag contents BEFORE execution 
      line.append(intToOctalString(cpu.Aregister.getValue(), 6));
      line.append(" ");
      line.append(intToOctalString(cpu.Bregister.getValue(), 6));
      line.append(" ");
      line.append(intToHexString(cpu.Eregister.getValue(), 1));
      line.append(" ");
      line.append(intToHexString(cpu.Iregister.getValue(), 4));
      line.append("\t");

      // control select codes 2=MLS, 2=MCR, 1=SIH, SSF
      line.append(ioUnit.MLS? "1":".");
      line.append(ioUnit.MCR? "1":".");
      line.append(ioUnit.SIH? "1":".");
      line.append(ioUnit.SSF? "1":".");

      // flag select codes 16=KLS, 8=DEN, 4=PEN, 2=MFL, 1=CEO, 0=STP
      line.append(ioUnit.KLS? "1":".");   
      line.append(ioUnit.DEN? "1":".");   
      line.append(ioUnit.PEN? "1":".");   
      line.append(ioUnit.MFL? "1":".");   
      line.append(ioUnit.CEO? "1":".");   
      line.append(ioUnit.STP? "1":".");   
      line.append("\t");
    }

    if((opcode & 0070000) != 0070000)
      instr = memoryReferenceGroup(opcode, address);
    else if((opcode & 0100000) != 0) {
      if((opcode & 0002000) != 0) {
        instr = inputOutputGroup(opcode, address);
      } else {
        FPop = true;
        // dump FP register contents BEFORE execution 
        if(dumpFPregisters)
          fpReg = showRegister(AR1) + showRegister(AR2);
        instr = macGroup(opcode);
      }
    } else {
      if((opcode & 0000007) == 0000007)
        instr = registerReferenceGroup(opcode, address);
      else if((opcode & 0000010) == 0)
        instr = shiftRotateGroup(opcode);
      else if((opcode & 0000007) == 0000006)
        instr = compExecuteDmaGroup(opcode, address);
      else
        instr = alterSkipGroup(opcode, address);
    }

    if(disassemble && line != null) {
      line.append(intToOctalString(address, 6)); 
      line.append("\t");
      line.append(intToOctalString(opcode, 6)); 
      line.append("\t");
      line.append(instr);
    }

    if(dumpFPregisters && FPop && line != null) {
      line.append("\t");
      line.append(fpReg);
    }

    if((dumpRegisters | disassemble | dumpFPregisters) && line != null) {
      line.append("\n");
      console.append(line.toString());
    }
  }

  private DataInputStream openConfigFile(String fileName, boolean logging)
  {
    DataInputStream cfgFile = null;
    String filePath = null;
    String jarPath = System.getProperty("java.class.path");
    int pos;

    pos = jarPath.lastIndexOf('/');
    if(pos < 0)
      pos = jarPath.lastIndexOf('\\');

    // try to open customized config file
    try{

      cfgFile = new DataInputStream(new BufferedInputStream(new FileInputStream(fileName)));
      if(logging)
        System.out.println("Custom configuration file " + fileName + " loaded.");

    } catch (FileNotFoundException e) {

      // try to open standard config file
      try{

        filePath = jarPath.substring(0, pos + 1) + fileName;
        cfgFile = new DataInputStream(new BufferedInputStream(new FileInputStream(filePath)));
        if(logging)
          System.out.println("Standard configuration file " + filePath + " loaded.");

      } catch (FileNotFoundException e1) {

        // open default config file from JAR-file
        filePath = "/config/" + fileName;
        if(getClass().getResourceAsStream(filePath) == null) {
          System.out.println("Configuration file " + fileName + " not found.");
          System.out.println("HP9800 Emulator terminated.");
          System.exit(1);
        }

        cfgFile = new DataInputStream(new BufferedInputStream(getClass().getResourceAsStream(filePath)));
        if(logging)
          System.out.println("Default configuration file " + jarPath + filePath + " loaded.");

      }
    }

    return(cfgFile);
  }

  @SuppressWarnings({ "deprecation" })
  private void loadDevice(String deviceName, int selectCode)
  {
    DataInputStream cfgFile = null;
    Class<?>[] formpara;
    Object[] actpara;
    Class<?> ioDev, ioInt;
    Constructor<?> constr;
    IOdevice ioDevice = null;
    IOinterface ioInterface = null;
    String line, keyWord, keyValue;
    String hpDevice = "", title = "", hpInterface = "", sc;
    int address = 0, length = 0;
    String[] parameters = null;

    sc = Integer.toString(selectCode);

    // Read config file line by line
    try {
      cfgFile = openConfigFile(deviceName + ".cfg", false);

      while ((line = cfgFile.readLine()) != null && line.length() != 0) {

        // ignore comment lines
        if(line.charAt(0) == ';')
          continue;

        // and tokenize
        StringTokenizer tokenline = new StringTokenizer(line, " \t");

        lineLoop:
          while(tokenline.hasMoreTokens()) {
            try {
              // read key word
              keyWord = tokenline.nextToken();

              // is it a model definition?
              if(keyWord.equals("Model")) {
                // check if current machine appears in model list 
                while(tokenline.hasMoreTokens()) {
                  keyValue = tokenline.nextToken();
                  if(keyValue.equals(model))
                    continue lineLoop; // read next line
                }
                System.out.println("Illegal peripheral device for this model!");
                return;
              }

              // HP-number of physical device
              if(keyWord.equals("Name")) {
                hpDevice = tokenline.nextToken();
                System.out.print(hpDevice + " ");
                continue; // read next line
              }

              // Descriptive name of physical device
              if(keyWord.equals("Title")) {
                title = tokenline.nextToken();
                System.out.print(title + " ");
                continue; // read next line
              }

              // HP-number of calculator interface
              if(keyWord.equals("Interface")) {
                hpInterface = tokenline.nextToken();
                System.out.print(hpInterface);
                continue; // read next line
              }

              // allowed select codes
              if(keyWord.equals("Selectcode")) {
                // check if current SC appears in SC list 
                while(tokenline.hasMoreTokens()) {
                  keyValue = tokenline.nextToken();
                  // if no select code is given, use first (default) value of SC list
                  if(selectCode == 0) {
                    sc = keyValue;
                    selectCode = Integer.parseInt(sc);
                  }

                  if(keyValue.equals(sc)) {
                    System.out.print(", select code " + sc + " ");

                    continue lineLoop; // read next line
                  }
                }
                System.out.println("- Illegal select code " + sc + "!");
                return;
              }

              // connection of main memory to extended memory (used by HP11273A)
              if(keyWord.equals("RWM")) {
                address = Integer.parseInt(tokenline.nextToken(), 8);
                length = Integer.parseInt(tokenline.nextToken(), 8);
                MemoryBlock memoryBlock = new MemoryBlock(model, "RWM", address, length, "", hpInterface);
                memoryBlocks.put(hpInterface, memoryBlock);
                if(memoryBlock.initialize(memory) != 0)
                  System.exit(1);
                continue; // read next line
              }

              // more parameters for device or interface
              if(keyWord.equals("Parameters")) {
                int n = tokenline.countTokens();
                parameters = new String[n];
                for(int i = 0; i < n; i++) {
                  parameters[i] = tokenline.nextToken();
                }
                continue; // read next line
              }

            } catch (NumberFormatException e) {
              // format error
              System.err.println(e.toString());
            }
          }
      }

      cfgFile.close();
    } catch (IOException e) {
      // read error
      System.out.println(e.toString());
      System.exit(1);
    }

    // create object for device interface dynamically
    formpara = new Class[]{Integer.class};
    actpara = new Object[]{Integer.valueOf(selectCode)};
    try {
      // find Class for device interface by name
      ioInt = Class.forName("io." + hpInterface);
      // find constructor for formal parameters
      constr = ioInt.getConstructor(formpara);
      // create new object instance of device interface
      ioInterface = (IOinterface)constr.newInstance(actpara);
    } catch(Exception e) {
      System.err.println("\nClass for interface " + hpInterface + " not found.");
      System.exit(1);      
    }

    // is a peripheral device configured? 
    if(hpDevice != "") {
      // create object for peripheral device dynamically
      if(parameters != null) { // additional parameters?
        formpara = new Class[]{String[].class}; // parameters for HP11202A, HP11305A etc.
        actpara = new Object[]{parameters};
      } else { // no parameters
        formpara = new Class[]{}; // no parameters for other devices
        actpara = new Object[]{};
      }

      try {
        // find Class for device by name
        ioDev = Class.forName("io." + hpDevice);
        // find constructor for formal parameters
        constr = ioDev.getConstructor(formpara);
        // create new object instance of device
        ioDevice = (IOdevice)constr.newInstance(actpara);
      } catch(Exception e) {
        System.err.println("\nClass for device " + hpDevice + " not found.");
        System.exit(1);      
      }
    }

    // set link from device to interface
    if(ioDevice != null) {
      ioDevice.setInterface(ioInterface);
      ioDevice.hpName = hpDevice;
    }

    // set link from interface to device
    if(ioInterface != null) {
      ioInterface.setDevice(ioDevice);
      // start IOinterface thread at last 
      ioInterface.start();
    }

    if(length == 0)
      System.out.println("loaded.");
  }

  @SuppressWarnings("deprecation")
  private void loadConfig(String machineName)
  {
    DataInputStream cfgFile = null;
    String line;
    String blockName, slot, blockType;
    int address, length;

    model = machineName;
    version = "";

    // Read config file line by line
    try {
      cfgFile = openConfigFile(machineName + ".cfg", true);

      while ((line = cfgFile.readLine()) != null && line.length() != 0) {

        // ignore comment lines
        if(line.charAt(0) == ';')
          continue;

        // and tokenize
        StringTokenizer tokenline = new StringTokenizer(line, " \t");

        while(tokenline.hasMoreTokens()) {
          try {

            // read memory type
            blockType = tokenline.nextToken();

            // is it a model definition?
            if(blockType.equals("Model")) {
              model = tokenline.nextToken();
              if(tokenline.hasMoreTokens())
                version = tokenline.nextToken();
              continue; // read next line
            }

            // is it a IO-device definition?
            if(blockType.equals("DEV")) {
              // read device name
              blockName = tokenline.nextToken();

              if(tokenline.hasMoreTokens()) {
                // read select code
                address = Integer.parseInt(tokenline.nextToken());
              } else {
                address = 0; // no select code given, use interface default value
              }

              // load IO device and interface
              loadDevice(blockName, address);

              continue; // read next line
            }

            // read octal start address
            address = Integer.parseInt(tokenline.nextToken(), 8);

            // is it a breakpoint definition?
            if(blockType.startsWith("Break")) {
              if(memory[address] != null)
                memory[address].breakPoint = true;
              continue; // read next line
            }

            // is it a watchpoint definition?
            if(blockType.startsWith("Watch")) {
              if(memory[address] != null) {
                memory[address].watchPoint = true;
                if(tokenline.hasMoreTokens()) {
                  memory[address].watchValue = Integer.parseInt(tokenline.nextToken(), 8);
                  memory[address].watchCondition = tokenline.nextToken().charAt(0);
                }
              }
              continue; // read next line
            }

            // read octal block length
            length = Integer.parseInt(tokenline.nextToken(), 8);
            // read ROM name
            blockName = tokenline.nextToken();

            // read ROM slot number if applicable
            if(tokenline.hasMoreTokens()) {
              slot = tokenline.nextToken();
            } else {
              slot = "";
            }

            MemoryBlock memoryBlock = new MemoryBlock(model, blockType, address, length, blockName, slot);
            memoryBlocks.put(slot, memoryBlock);
            if(memoryBlock.initialize(memory) != 0)
              System.exit(1);

          } catch (Exception e) {
            // format error
            System.err.println(e.toString());
          }
        }

      }

      cfgFile.close();

    } catch (IOException e) {
      // read error
      System.err.println(e.toString());
      System.exit(1);
    }
  }

  public void setROM(String slot, String romName)
  {
    MemoryBlock memoryBlock = (MemoryBlock)memoryBlocks.get(slot);

    String prevName = memoryBlock.getName();
    memoryBlock.unload();
    memoryBlock.setName(romName);
    if(memoryBlock.initialize(memory) != 0) {
      // on error reload previous block
      memoryBlock.setName(prevName);
      memoryBlock.initialize(memory);
    }
  }

  @SuppressWarnings("deprecation")
  private void loadKeyConfig(String machineName)
  {
    Hashtable<String, String> keyNames;
    DataInputStream cfgFile = null;
    String line;
    int keyCode, scanCode;
    String modifier, keyName, keyString;

    keyNames = new Hashtable<String, String>();

    // Read key name file line by line
    try {
      cfgFile = openConfigFile("keynames.cfg", true);
      while ((line = cfgFile.readLine()) != null) {

        // ignore empty and comment lines
        if(!line.equals(""))
          if(line.charAt(0) == ';')
            continue;

        // and tokenize
        StringTokenizer tokenline = new StringTokenizer(line, " \t");

        if(tokenline.hasMoreTokens()) {
          try {

            // read host PC key code
            keyCode = Integer.parseInt(tokenline.nextToken());

            // read host key name
            keyName = tokenline.nextToken();

            keyNames.put(Integer.toString(keyCode), keyName);

          } catch (NumberFormatException e) {
            // format error
            System.err.println(e.toString());
          }
        }

      }

      cfgFile.close();

    } catch (IOException e) {
      // read error
      System.err.println(e.toString());
      System.exit(1);
    }

    // Read config file line by line
    try {
      cfgFile = openConfigFile(machineName + "-keyb.cfg", true);
      while ((line = cfgFile.readLine()) != null) {

        // ignore empty and comment lines
        if(!line.equals(""))
          if(line.charAt(0) == ';')
            continue;

        // and tokenize
        StringTokenizer tokenline = new StringTokenizer(line, " \t");

        if(tokenline.hasMoreTokens()) {
          try {

            // read calculator key code
            keyCode = Integer.parseInt(tokenline.nextToken(), 8);

            // read host key code
            scanCode = Integer.parseInt(tokenline.nextToken());

            // read modifier keys
            if(tokenline.hasMoreTokens()) {
              modifier = tokenline.nextToken().toUpperCase();
              if(modifier.charAt(0) == ';')
                modifier = "";
            }
            else
              modifier = "";

            keyCodes.put(Integer.toString(scanCode) + modifier, Integer.toOctalString(keyCode));

            keyString = Integer.toString(scanCode);
            keyName = (String)keyNames.get(keyString);
            if(keyName == null)
              keyName = String.valueOf((char)scanCode);
            keyString = (modifier != "")? modifier + "+" + keyName : keyName;
            keyStrings.put(Integer.toString(keyCode), keyString);

          } catch (NumberFormatException e) {
            // format error
            System.err.println(e.toString());
          }
        }
      }

      cfgFile.close();

    } catch (IOException e) {
      // read error
      System.err.println(e.toString());
      System.exit(1);
    }
  }

  public void run()
  {
    int address;

    // wait for all peripheral devices to be initialized asynchronously
    try {
      Thread.sleep(500);
    } catch (InterruptedException e1) {
    }

    System.out.println("HP9800 Emulator started.");
    console.append("HP9800 CPU Initialization\n");

    while(true) {
      // reset machine
      synchronized(ioUnit) {
        if(ioUnit.reset) {
          ioUnit.reset = false;
          cpu.setDecode(true);
          cpu.POP(); // Power On Preset
        }
      }

      // execute micro instruction
      cpu.exec();

      // micro-address counter at position 0616 (next instruction)? 
      if(cpu.PC != 0x6e)
        continue; // no, execute next micro-instruction

      if(measure && t != 0) {
        // opcode execution time
        t = System.nanoTime() - t;

        if(t < 1000000) {
          if(t < minExecTime)
            minExecTime = t;

          if(t > maxExecTime)
            maxExecTime = t;

          sumExecTime += t;
          numOps++;
        }
      }

      // yes, execute ASM-level disassembly etc.

      // dump FP registers from previous FP operation
      if(Memory.trace && FPop) {
        console.append("\t\t\t\t\t\t\t\t");
        console.append(showRegister(AR1));
        console.append(showRegister(AR2));
        console.append("\n");
      }

      // check for ASM-level breakpoints
      address = cpu.Pregister.getValue();

      if(memory[address].breakPoint) {
        console.append("> Breakpoint\n");
        console.breakpoint();
      }

      // disassemble opcode
      if(disassemble) {
        dumpFPregisters = dumpRegisters = !dumpMicroCode;
        decode(memory[address].fetchOpcode(), address);
      }

      if(Memory.trace) {
        disassemble = true;
        dumpFPregisters = dumpRegisters = !dumpMicroCode;

        decode(memory[address].fetchOpcode(), address);

        dumpFPregisters = dumpRegisters = disassemble = false;

        // wait for interrupt by user action
        while(timerValue == 0) {
          // wait infinite until interrupted
          try {
            Thread.sleep(1000);
          } catch(InterruptedException e) {
            break;
          }
        }

        try {
          // wait for <timerValue> milliseconds or until interrupted
          Thread.sleep(timerValue);
        } catch(InterruptedException e) {
          // nothing
        }
      } else {
        Thread.yield();
      }

      // decrement instruction counter for display blanking and key release
      ioUnit.instructionCounter();

      // measure opcode execution time
      if(measure)
        t = System.nanoTime();

      cpu.setDecode((disassemble || Memory.trace) && dumpMicroCode);
    }
  }
}