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
 */

package emu98;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;

import io.IOinstruction;
import io.IOregister;
import io.IOinterface;
import io.IOdevice;

public class Emulator implements Runnable 
{
  public String model, version;
  public IOregister ioRegister;
  public Memory[] memory;
  public Hashtable<String, MemoryBlock> memoryBlocks;
  public Hashtable<String, String> keyCodes, keyStrings;
  
  Thread emuThread;
  public Console console;
  DataInputStream asmFile;
  Register register, registerA, registerB, registerE;
  ProgramCounter registerP;
  IOinstruction ioInstruction;
  private Memory unusedMemory;
  private HP2116Panel panel;

  public long minExecTime, maxExecTime, sumExecTime, numOps, t;
  public boolean measure = false;
  
  int timerValue = 0;
  boolean emulate, disassemble;
  boolean dumpRegisters, dumpFPregisters;
  boolean FPop;
  public boolean keyLogMode = false;
  private String instr;

  static final int AR1 = 0001744;
  static final int AR2 = 0001754;
  static final int systemProgramCounter = 0001700;
  static final int systemStackPointer = 0001777;
  static final int startupVector = 0000000;
  static final int interruptVector = 0000002;

  public Emulator(String machine)
  {
    registerA = new Register("A", 0177777, 0);
    registerB = new Register("B", 0177777, 0);
    registerE = new Register("E", 0xF, 0);
    // only 32k words addressable memory 
    registerP = new ProgramCounter(0077777, 0);
    ioRegister = new IOregister(0177777,0);
    ioInstruction = new IOinstruction(ioRegister);
    //panel = new HP2116Panel(this);
    
    // initialize complete memory to 'unused'  
    memory = new Memory[0100000];
    //unusedMemory = new Memory(false, -1, 0);
    for(int i = 0; i <= 077777; i++)
      //memory[i] = unusedMemory;
      memory[i] = new Memory(false, i, 0); // 23.05.2016 AB

    // set object variable for trace outputs
    Memory.emu = this;

    disassemble = dumpRegisters = dumpFPregisters = false;
    emulate = true;
    emuThread = new Thread(this, "HP9800 CPU");
    // Set emulator priority lower to guarantee that events such as keypresses
    // and device thread timer expirations get service immediately
    emuThread.setPriority(Thread.NORM_PRIORITY - 1);

    System.out.println("HP9800 CPU loaded.");

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
  }

  public void setEmulatorMode(boolean emuMode)
  {
    emulate = emuMode;
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
  
  
  public void pushStack(int address)
  {
    int sp = memory[systemStackPointer].getValue() & 077777;
    
    memory[sp].setValue(address);
    memory[systemStackPointer].setValue(sp + 1);
  }
  
  
  public int popStack()
  {
    int sp = memory[systemStackPointer].getValue() - 1;
    
    memory[systemStackPointer].setValue(sp);
    return(memory[sp].getValue());
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
      //if(emulate) {
        // nested indirect addressing if bit15 = 1
        do {
          memory_address = memory[memory_address & 0077777].getValue();
        } while((memory_address & 0100000) != 0);
      //}
    }
    
    if(disassemble) {
      indStr = indirect? ",I [" + intToOctalString(memory_address, 7) + "]" : "";
    }

    Memory m = memory[memory_address];
 
    switch(opcode & 0074000) {
    case 0000000:
    case 0004000:
      if(disassemble) {
        instr = "AD" + register.name;
      }
      
      if(emulate) {
        int regVal = register.getValue() + m.getValue();
        // set carry in E
        if(regVal > register.mask)
          registerE.setValue(1);
        register.setValue(regVal);
      }  
      break;
      
    case 0010000:
    case 0014000:
      if(disassemble) {
        instr = "CP" + register.name;
      }
      
      if(emulate) {
        if(register.getValue() != m.getValue()) {
          registerP.setValue(address + 2);
        }
      }
      break;

    case 0020000:
    case 0024000:
      if(disassemble) {
        instr= "LD" + register.name;
      }
      
      if(emulate) {
        register.setValue(m.getValue());
      }
      break;
      
    case 0030000:
    case 0034000:
      if(disassemble) {
        instr = "ST" + register.name;
      }
      
      if(emulate) {
        m.setValue(register.getValue());
      }
      break;
      
    case 0040000:
      if(disassemble) {
        instr = "IOR";
      }

      if(emulate) {
        registerA.setValue(registerA.getValue() | m.getValue());
      }
      break;
        
    case 0044000:
      if(disassemble) {
        instr = "ISZ";
      }
      
      if(emulate) {
        if(m.setValue(m.getValue() + 1) == 0) {
          registerP.setValue(address + 2);
        }
      }  
      break;
      
    case 0050000:
      if(disassemble) {
        instr = "AND";
      }

      if(emulate) {
        registerA.setValue(registerA.getValue() & m.getValue());
      }
      break;
        
      case 0054000:
      if(disassemble) {
        instr = "DSZ";
      }
      
      if(emulate) {
        if(m.setValue(m.getValue() - 1) == 0) {
          registerP.setValue(address + 2);
        }
      }  
      break;
      
    case 0060000:
      if(disassemble) {
        instr = "JSM";
      }
      
      if(emulate) {
        pushStack(registerP.getValue());
        registerP.setValue(memory_address);
      }  
      break;
        
    case 0064000:
      if(disassemble) {
        instr = "JMP";
      }
      
      if(emulate) {
        registerP.setValue(memory_address);
      }  
      break;
      
    default:
      return(instr);
    }

    if(disassemble) {
       instr = instr + " " + intToOctalString(direct_address, 6) + indStr;
    }
    
    return(instr);
  }
  
  
  public String shiftRotateGroup(int opcode)
  {
    int shift = ((opcode & 0000740) >> 5) + 1;
    
    switch(opcode & 0000007) {
    case 0000000:
      if(disassemble) {
        instr = "A" + register.name + "R";
      }
      
      if(emulate) {
        int regVal = register.getValue();
        // value negative (Bit15 != 0) ?
        if((regVal & 0x8000) != 0)
          regVal = regVal | 0xffff0000;
        register.setValue(regVal >> shift);
      }
     break;
      
    case 0000002:
      if(disassemble) {
        instr = "S" + register.name + "R";
      }

      if(emulate) {
        register.setValue(register.getValue() >> shift);
      }
      break;
      
    case 0000004:
      shift = 17 - shift;
      if(disassemble) {
        instr = "S" + register.name + "L";
      }

      if(emulate) {
        register.setValue(register.getValue() << shift);
      }
      break;
      
    case 0000006:
      if(disassemble) {
        instr = "R" + register.name + "R";
      }

      if(emulate) {
        int regVal = register.getValue();
        regVal = (regVal << 16) | regVal; 
        register.setValue(regVal >> shift);
      }
      break;
      
    default:
      return(instr);
    }
    
    if(disassemble)
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
    
    if(disassemble) {
      skipcode = "*" + ((skip >= 0)? "+" : "") + Integer.toString(skip);

      if(set)
        testbit_SC = ",S";
      else {
        if(clear)
          testbit_SC = ",C";
        else
          testbit_SC = "";
      }
    }
   
    switch(opcode & 0000007) {
    case 0000000:
      switch(opcode & 0002020) {
      case 0000000:
        if(disassemble) {
          instr = "SZ";
        }
        
        if(emulate) {
          if(register.getValue() == 0) {
            registerP.setValue(address);
          }
        }
        break;
        
      case 0002000:
        if(disassemble) {
          instr = "RZ";
        }
        
        if(emulate) {
          if(register.getValue() != 0) {
            registerP.setValue(address);
          }
        }
        break;
        
      case 0000020:
        if(disassemble) {
          instr = "SI";
        }
        
        if(emulate) {
          int regVal = register.getValue();
          if(regVal == 0) {
            registerP.setValue(address);
          }
          register.setValue(regVal + 1);
        }
        break;
        
      case 0002020:
        if(disassemble) {
          instr = "RI";
        }
        
        if(emulate) {
          int regVal = register.getValue();
          if(regVal != 0) {
            registerP.setValue(address);
          }
          register.setValue(regVal + 1);
        }
        break;
      }
      
      if(disassemble)
        instr = instr + register.name + " " + skipcode;
      return(instr);
      
    case 0000001:
      if(disassemble) {
        instr = "SL" + register.name;
      }
      
      if(emulate) {
        int regVal = register.getValue();
        if((regVal & 1) == 0) {
          registerP.setValue(address);
        }
        
        if(clear)
          register.setValue(regVal & 0xfffe);
        if(set)
          register.setValue(regVal | 1);
      }
      break;
        
    case 0000002:
      if(disassemble) {
        instr = "S" + register.name + "M";
      }
      
      if(emulate) {
        int regVal = register.getValue();
        if((regVal & 0x8000) != 0) {
          registerP.setValue(address);
        }

        if(clear)
          register.setValue(regVal & 0x7fff);
        if(set)
          register.setValue(regVal | 0x8000);
      }
      break;
        
    case 0000003:
      if(disassemble) {
        instr = "S" + register.name + "P";
      }
      
      if(emulate) {
        int regVal = register.getValue();
        if((regVal & 0x8000) == 0) {
          registerP.setValue(address);
        }
        
        if(clear)
          register.setValue(regVal & 0x7fff);
        if(set)
          register.setValue(regVal | 0x8000);
      }
      break;
        
    case 0000004:
      if(disassemble) {
        instr = "SES";
      }
      
      if(emulate) {
        if((registerE.getValue() & 1) != 0) {
          registerP.setValue(address);
        }

        if(clear)
          registerE.setValue(0);
        if(set)
          registerE.setValue(0xf);
      }
      break;
        
    case 0000005:
      if(disassemble) {
        instr = "SEC";
      }
      
      if(emulate) {
        if((registerE.getValue() & 1) == 0) {
          registerP.setValue(address);
        }

        if(clear)
          registerE.setValue(0);
        if(set)
          registerE.setValue(0xf);
      }
      break;
      
    default:
      return(instr);
    }

    if(disassemble)
      instr = instr + " " + skipcode + testbit_SC;
    
    return(instr);
  }
  
  
  public String registerReferenceGroup(int opcode, int address)
  {
    String indStr = "";
    int regValue, memory_address;
    boolean indirect;
    Register opRegister;
    Memory m = null;

    regValue = register.getValue();
    memory_address = regValue;
    
    // direct or indirect?
    indirect = (opcode & 0000400) != 0;
    
    if(indirect)
    {
      //if(emulate) {
        // nested indirect addressing if address-bit15 = 1
        while((memory_address & 0100000) != 0) {
          memory_address = memory[memory_address & 0077777].getValue();
        }
        
        m = memory[memory_address];
        regValue = m.getValue();     // this is also correct for STx, although existing memory value will not be used
      //}
        
      if(disassemble) {
        indStr = ",I [" + intToOctalString(memory_address, 7) + "]";
      }
    }

    if((opcode & 000020) == 0)
      opRegister = registerA;
    else
      opRegister = registerB;
      
    switch(opcode & 0000360) {
    case 0000000:
    case 0000020:
      if(disassemble) {
        instr = "AD" + opRegister.name;
      }
      
      if(emulate) {
        int regVal = opRegister.getValue() + regValue;
        if(regVal > register.mask)
          registerE.setValue(1);
        opRegister.setValue(regVal);
      }
      break;
      
    case 0000040:
    case 0000060:
      if(disassemble) {
        instr = "CP" + opRegister.name;
      }
      
      if(emulate) {
        if(opRegister.getValue() != regValue) {
          registerP.setValue(address + 2);
        }
      }
      break;
      
    case 0000100:
    case 0000120:
      if(disassemble) {
        instr = "LD" + opRegister.name;
      }
      
      if(emulate) {
        opRegister.setValue(regValue);
      }
      break;
      
    case 0000140:
    case 0000160:
      if(disassemble) {
        instr = "ST" + opRegister.name;
      }
      
      if(emulate) {
        if(indirect)
          m.setValue(opRegister.getValue());
        else
          register.setValue(opRegister.getValue());
      }
      break;
      
    case 0000200:
      if(disassemble) {
        instr = "IOR";
      }

      if(emulate) {
        registerA.setValue(registerA.getValue() | regValue);
      }
      break;
      
    case 0000220:
      if(disassemble) {
        instr = "ISZ";
      }
      
      if(emulate) {
        if(++regValue == 0) {
          registerP.setValue(address + 2);
        }

        if(indirect)
          m.setValue(regValue);
        else
          register.setValue(regValue);
      }  
      break;
      
    case 0000240:
      if(disassemble) {
        instr = "AND";
      }

      if(emulate) {
        registerA.setValue(registerA.getValue() & regValue);
      }
      break;
      
    case 0000260:
      if(disassemble) {
        instr = "DSZ";
      }
      
      if(emulate) {
        if(--regValue == 0) {
          registerP.setValue(address + 2);
        }

        if(indirect)
          m.setValue(regValue);
        else
          register.setValue(regValue);
      }  
      break;
      
    case 0000300:
      if(disassemble) {
        instr = "JSM";
      }
      
      if(emulate) {
        pushStack(registerP.getValue());
        registerP.setValue(memory_address);
      }  
      break;
      
    case 0000320:
      if(disassemble) {
        instr = "JMP";
      }
      
      if(emulate) {
        registerP.setValue(memory_address);
      }  
      break;
      
    default:
      return(instr);
    }
    
    if(disassemble) {
      instr += " " + register.name + indStr;
    }
    
    return(instr);
  }
  
  
  public String compExecuteDmaGroup(int opcode, int address)
  {
    switch(opcode & 0000070) {
    case 0000030:
      if(disassemble) {
        instr = "DMA";
      }
      
      if(emulate) {
        System.err.println("DMA instruction not implemented.");
       /*  
        // not implemented yet
        address |= 0100000;  // activate DMA, set Indirect bit
        dma.setValue(registerA.getValue()); // output A
        dma.setValue(registerB.getValue()); // output B
        dma.wait(); // wait for DMA to finish
      */
      }
      return(instr);
      
    case 0000010:
      if(disassemble) {
        instr = "EX";
      }
      
      if(emulate) {
        // execute opcode in register.value
        decode(register.getValue(), address);
      }
      break;
      
    case 0000050:
      if(disassemble) {
        instr = "CM";
      }
      
      if(emulate) {
        // one's complement of register.value
        register.setValue(~register.getValue());
      }
      break;
      
    case 0000070:
      if(disassemble) {
        instr = "TC";
      }
      
      if(emulate) {
        // two's complement of register.value
        register.setValue(~register.getValue() + 1);
      }
      break;
   
    }
    
    if(disassemble)
      instr += register.name;
    
    return(instr);
  }
  
  public String inputOutputGroup(int opcode, int address)
  {
    String testbit_HC = null;
    boolean clear;
    
    int selectCode = opcode & 0000037;

    clear = ((opcode & 0001000) != 0);
    
    if(disassemble) {
      testbit_HC = clear? ",C" : "";
    }
    
    switch(opcode & 0000740) {
    case 0000740:
      if(!clear) {
        if(disassemble) {
          instr = "STF";
        }
    
        if(emulate) {
          ioInstruction.setFlag(selectCode);
        }
      } else {
        if(disassemble) {
          instr = "CLF";
        }
        
        if(emulate) {
          ioInstruction.clearFlag(selectCode);
        }
      }
      
      if(disassemble)
        instr += " " + Integer.toString(selectCode);
      return(instr);
      
    case 0000700:
      if(disassemble) {
        instr = "SFC";
      }
      
      if(emulate) {
        if(!ioInstruction.getFlag(selectCode)) {
          registerP.setValue(address + 2);
        }
      }
      break;
      
    case 0000500:
      if(disassemble) {
        instr = "SFS";
      }
      
      if(emulate) {
        if(ioInstruction.getFlag(selectCode)) {
          registerP.setValue(address + 2);
        }
      }
      break;
      
    case 0000540:
      if(disassemble) {
        instr = "CLC";
      }
      
      if(emulate) {
        ioInstruction.clearControl(selectCode);
      }
      break;
      
    case 0000600:
      if(disassemble) {
        instr = "STC";
      }
      
      if(emulate) {
        ioInstruction.setControl(selectCode);
      }
      break;
      
    case 0000140:
      if(disassemble) {
        instr = "OT" + register.name;
      }
      
      if(emulate) {
        register.setValue(ioInstruction.output(selectCode, register.getValue()));
      }
      break;
      
    case 0000240:
      if(disassemble) {
        instr = "LI" + register.name;
      }
      
      if(emulate) {
        register.setValue(ioInstruction.input(selectCode, register.getValue()));
      }
      break;
      
    case 0000040:
      if(disassemble) {
        instr = "MI" + register.name;
      }
      
      if(emulate) {
        register.setValue(ioInstruction.merge(selectCode, register.getValue()));
      }
      break;
      
    default:
      return(instr);
    }

    if(disassemble)
      instr += " " + Integer.toString(selectCode) + testbit_HC;

    if(emulate && clear) {
        ioInstruction.clearFlag(selectCode);
    }

    return(instr);
  }
  
 
  public String macGroup(int opcode)
  {
    switch(opcode) {
    case 0170402:
      FPop = false;
      if(disassemble) {
        instr = "RET";
      }
      
      if(emulate) {
        registerP.setValue(popStack());
        // increment program counter after execution
        registerP.setIncrement(true);
      }  
      break;
      
    case 0170002:
      FPop = false;
      if(disassemble) {
        instr = "MOV";
      }
      
      if(emulate) {
        // move registerE.value to registerA.value
        registerA.setValue(registerE.getValue());
        registerE.setValue(0);
      }  
      break;
      
    case 0170000:
      FPop = false;
      if(disassemble) {
        instr = "CLR";
      }
      
      if(emulate) {
        // clear FP register referenced by registerA.value
        int address = registerA.getValue();
        for(int i = 0; i <= 3; i++) {
          memory[address+i].setValue(0);
        }
      }  
      break;
      
    case 0170004:
      FPop = false;
      if(disassemble) {
        instr = "XFR";
      }
      
      if(emulate) {
        // transfer FP register ref. by registerA.value to register ref. by registerB.value 
        int addressA = registerA.getValue();
        int addressB = registerB.getValue();
        for(int i = 0; i <= 3; i++) {
          memory[addressB].setValue(memory[addressA].getValue());
          addressA++;
          addressB++;
        }
        
        // set A and B to last address of FP registers (undocumented!)
        registerA.setValue(addressA-1);
        registerB.setValue(addressB-1);
      }  
      break;
      
    case 0174430:
      if(disassemble) {
        instr = "MRX";
      }
      
      if(emulate) {
        // shift AR1 to right by registerB.value
        registerA.setValue(mrx(AR1));
      }  
      break;
      
    case 0174470:
      if(disassemble) {
        instr = "MRY";
      }
      
      if(emulate) {
        // shift AR2 to right by registerB.value
        registerA.setValue(mrx(AR2));
      }  
      break;
      
    case 0171400:
      if(disassemble) {
        instr = "MLS";
      }
      
      if(emulate) {
        // shift AR2 left and set D12=0, D1->A
        registerA.setValue(dls(AR2, 0));
      }  
      break;
      
    case 0170410:
      if(disassemble) {
        instr = "DRS";
      }
      
      if(emulate) {
        // shift AR1 right and set D1 to 0, D12->A
        registerA.setValue(drs(AR1, 0));
      }  
      break;
      
    case 0175400:
      if(disassemble) {
        instr = "DLS";
      }
      
      if(emulate) {
        // shift AR1 left and set D12 to A
        registerA.setValue(dls(AR1, registerA.getValue()));
      }  
      break;
      
    case 0170560:
      if(disassemble) {
        instr = "FXA";
      }
      
      if(emulate) {
        //int carry = (registerE.getValue() == 0xf) ? 1 : 0;
        int carry = registerE.getValue() & 1;
        registerE.setValue(fadd(carry));
      }  
      break;
      
    case 0171460:
      if(disassemble) {
        instr = "FMP";
      }
      
      if(emulate) {
        int B = registerB.getValue();
        int carry_sum = 0;
        int carry = registerE.getValue() & 1;

        // add m times (multiply by m)
        for(int m = B & 0xf; m >= 1; m--) {
          carry_sum += fadd(carry);
          carry = 0;
        }

        registerA.setValue(carry_sum);
        registerE.setValue(0);
        // set last 4 bits in B to 1111 (undocumented!)
        registerB.setValue(B | 0xf);
      }  
      break;
      
    case 0170420:
      if(disassemble) {
        instr = "FDV";
      }
      
      if(emulate) {
        // sum to value in register B
        int B = registerB.getValue();
        //int carry = (registerE.getValue() == 0xf) ? 1 : 0;
        int carry = registerE.getValue() & 1;

        // add AR1 and AR2 until carry occurs
        while(fadd(carry) == 0) {
          B++;
          carry = 0;
        }

        registerB.setValue(B);
        registerE.setValue(0);
      }  
      break;
      
    case 0174400:
      if(disassemble) {
        instr = "CMX";
      }
      
      if(emulate) {
        cmpl(AR1);
      }  
      break;
      
    case 0170400:
      if(disassemble) {
        instr = "CMY";
      }
      
      if(emulate) {
        cmpl(AR2);
      }  
      break;
      
    case 0170540:
      if(disassemble) {
        instr = "MDI";
      }
      
      if(emulate) {
        mdi(registerA.getValue(), true);
      }  
      break;
      
    case 0171450:
      if(disassemble) {
        instr = "NRM";
      }
      
      if(emulate) {
        int B;
        int E = 1; // set E-register=1 if all digits are zero
        
        for(B = 0; B < 12; B++) {
          // test if D1=0
          if((memory[AR2+1].getValue() & 0xf000) != 0) {
            E = 0; // no overflow
            break;
          }
          // shift AR2 left
          dls(AR2, 0);
        }
        
        registerB.setValue(B);
        registerE.setValue(E);
      }  
      break;
      
    }
    
    return(instr);
  }
  
  
  // decimal right shift by B
  private int mrx(int address)
  {
   // shift ARx to right by registerB.value  
    int B = registerB.getValue() & 0xf;
    // use lower 4 bits of A for first shift ->D1
    int A = registerA.getValue();
    int E = 0;

    for( ; B > 0; B--) {
      E = drs(address, A);
      // start next shift with 0->D1
      A = 0;
    }

    registerB.setValue(registerB.getValue() & 0xfff0);
    registerE.setValue(0);

    //return D12
    return(E);
  }
  
  // decimal left shift once  
  private int dls(int address, int A)
  {
    // mask lower 4 bits of A in E for first shift ->D12
    int E = A & 0xf;
    
    for(int i = 3; i >= 1; i--) {
      
      // shift memory word 4 bits (1 digit) left and merge E
      A = (memory[address+i].getValue() << 4) | E;

      // put upper 4 bits of memory word to E-register for next cycle
      E = A >> 16;
      memory[address+i].setValue(A);
    }
    
    registerE.setValue(0);

    // return D1
    return(E);
  }
  
  // decimal right shift once
  private int drs(int address, int A)
  {
    // mask lower 4 bits of A in E for first shift ->D1
    int E = A & 0xf;

    for(int i = 1; i <= 3; i++) {
      address++;
      // put value of E above memory word
      A = (E << 16) | memory[address].getValue();
      // save lower 4 bits of memory word in E for next cycle
      E = A & 0xf;
      // and shift memory word 4 bits (1 digit) right
      memory[address].setValue(A >> 4);
    }

    registerE.setValue(0);
    // return D12
    return(E);
  }
  
  
  // 10's complement
  private void cmpl(int address)
  {
    for(int i = 1; i <= 3; i++) {
      // 9's complement of 4 digits
      memory[address+i].setValue(0x9999 - memory[address+i].getValue());
    }

    // add 1 to mantissa w/o overflow -> 10's complement
    mdi(address, false);
    
    registerE.setValue(0);
  }
  
  
  // mantissa decimal increment
  private void mdi(int address, boolean ovf)
  {
    int i, sum = 0;
    
  all_digits:
    for(i = 3; i >= 1; i--) {
      // get 4 decimal digits
      sum = memory[address+i].getValue();
      // constants for increment and compare
      int one = 1, ten = 0xA, mask = 0xF;
      
      // increment lowest of 4 digits
      sum += one;
      for(int j = 1; j <= 4; j++) {
        // if less or equal 9: ready
        if((sum & mask) < ten) {
          memory[address+i].setValue(sum);
          break all_digits;
        }
        
        // else set digit to 0 and increment next digit
        sum += 6 * one;
        // shift constants to next digit
        one <<= 4;
        ten <<= 4;
        mask <<= 4;
      }
      
      // overflow to next 4 digits
      // store 4 zero digits in memory
      memory[address+i].setValue(0);
    }
    
    // overflow in last digit (i.e. loop over all digits completed)?
    if((i == 0) && ovf) {
      // set mantissa to 1.00000000000 
      memory[address+1].setValue(0x1000);
      registerE.setValue(1);
    }
    else
      registerE.setValue(0);
  }  

  
  private int fadd(int carry)
  {
    // loop over all 12 digits (3 groups of 4 digits)
    for(int i = 3; i >= 1; i--) {
      // constants for increment and compare
      int one = 0x10, ten = 0xA, mask = 0xF;
      int sum = 0;  // sum word of 4 digits
      
      int d1 = memory[AR1+i].getValue(); // 4-digit word 1
      int d2 = memory[AR2+i].getValue(); // 4-digit word 2

      for(int j = 0; j < 4; j++) {
        // add 1 digit
        int dsum = (d1 & mask) + (d2 & mask) + carry;

        // carry to next digit
        if(dsum >= ten) {
          dsum -= ten;
          carry = one;
        } else
          carry = 0;

        // and place in result
        sum |= dsum;
        
        // shift constants to next digit
        one <<= 4;
        ten <<= 4;
        mask <<= 4;
      }

      // put sum of 4 digits back in AR2
      memory[AR2+i].setValue(sum);

      // set carry to lowest 1-bit
      if(carry != 0)
        carry = 1;
    }
    
    return(carry);
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
      register = registerA;
    } else { 
      register = registerB;
    }

    if(dumpRegisters | disassemble | dumpFPregisters) {
      instr = "???"; // default string: undefined instruction
      line = new StringBuffer(" ");
    }
    
    if(dumpRegisters && line != null) {
      // dump register and flag contents BEFORE execution 
      line.append(intToOctalString(registerA.getValue(), 7));
      line.append(" ");
      line.append(intToOctalString(registerB.getValue(), 7));
      line.append(" ");
      line.append(intToHexString(registerE.getValue(), 1));
      line.append(" ");
      line.append(intToHexString(ioRegister.getValue(), 4));
      line.append("\t");
      
      // control select codes 2=MLS, 2=MCR, 1=SIH, SSF
      line.append(ioRegister.MLS? "1":".");
      line.append(ioRegister.MCR? "1":".");
      line.append(ioRegister.SIH? "1":".");
      line.append(ioRegister.SSF? "1":".");
      
      // flag select codes 16=KLS, 8=DEN, 4=PEN, 2=MFL, 1=CEO, 0=STP
      line.append(ioRegister.KLS? "1":".");   
      line.append(ioRegister.DEN? "1":".");   
      line.append(ioRegister.PEN? "1":".");   
      line.append(ioRegister.MFL? "1":".");   
      line.append(ioRegister.CEO? "1":".");   
      line.append(ioRegister.STP? "1":".");   
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
      line.append(intToOctalString(address, 5)); 
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
    // startup address
    int address = startupVector;
        
    // wait for all peripheral devices to be initialized asynchronously
    try {
      Thread.sleep(500);
    } catch (InterruptedException e1) {
    }

    System.out.println("HP9800 Emulator started.");
    
    while(true) {
      // reset machine
      synchronized(ioRegister) {
        if(ioRegister.reset) {
          ioRegister.reset = false;
          address = startupVector;
          registerP.setValue(address);
          memory[systemProgramCounter].setValue(address);
          registerA.setValue(0);
          registerB.setValue(0);
        }
      }

      // Service Request?
      if(ioRegister.serviceRequested()) {
        ioRegister.serviceRequestAcknowledge();

        // decrement program counter, so that SRQ RET returns to this address 
       registerP.setValue(address - 1);

        // execute opcode in address 000002 (SRQ address)
        address = interruptVector;
      }

      if(memory[address].breakPoint) {
        console.append("> Breakpoint\n");
        console.breakpoint();
      }

      // next opcode
      int opcode = memory[address].fetchOpcode();

      // disassemble opcode

      if(Memory.trace) {
        emulate = false; dumpFPregisters = dumpRegisters = disassemble = true;
        decode(opcode, address);
        emulate = true; dumpFPregisters = dumpRegisters = disassemble = false;

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

      // increment P-Register after opcode execution
      registerP.setIncrement(true);

      // measure opcode execution time
      if(measure)
        t = System.nanoTime();

      // execute opcode
      decode(opcode, address);

      // increment P only if not set by previous opcode
      address = registerP.increment();
      memory[systemProgramCounter].setValue(address);

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

      // dump FP registers after FP operation
      if(Memory.trace && FPop) {
        console.append("\t\t\t\t\t\t\t\t");
        console.append(showRegister(AR1));
        console.append(showRegister(AR2));
        console.append("\n");
      }

      // decrement instruction counter for display blanking and key release
      ioRegister.instructionCounter();
    }
  }
}