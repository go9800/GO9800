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
 * 06.04.2007 Rel. 1.00 Changed: isRW now public (for 9820 MAW handling)
 * 24.05.2007 Rel. 1.01 Added disasmOutput for handling of watchpoints
 * 02.08.2016 Rel. 2.00 Removed registerM, registerT (no longer necessary)
 */

package emu98;

public class Memory
{
  static final int MASK = 0177777;
//  static int registerM, registerT; // for HP2116Panel
  public static Emulator emu;
  public static boolean trace = false;
  public boolean isRW;
  public boolean breakPoint;
  public boolean watchPoint;
  private int address;
  int value;
  int watchValue;
  char watchCondition;
  
  public Memory(boolean rwMemory, int address, int initValue)
  {
    isRW = rwMemory;
    this.address = address;
    value = initValue;
    breakPoint = watchPoint = false;
    watchCondition = ' ';
  }

  void doWatchPoint()
  {
    if(watchPoint) {
      switch(watchCondition) {
        case ' ':
          break;
          
        case '>':
          if(value > watchValue)
            break;
          else
            return;
        
        case '<':
          if(value < watchValue)
            break;
          else
            return;
        
        case '=':
          if(value == watchValue)
            break;
          else
            return;
        
        default:
          return;
      }
      
      emu.console.append("> Watchpoint at " + emu.intToOctalString(address, 7) + " Value=" + emu.intToOctalString(value, 7) + "\n");
      emu.console.breakpoint();
    }
  }
  
  public int setValue(int value)
  {
    if(isRW)
      this.value = value & MASK;
    
//    registerT = value;
//    registerM = address;
    
    doWatchPoint();
    return(this.value);  // return masked value
  }
  
  public int getValue()
  {
//    registerT = value;
//    registerM = address;
    doWatchPoint();
    return(value);
  }
  
  public int fetchOpcode()
  {
//    registerT = value;
//    registerM = address;
    doWatchPoint();
    return(value);
  }  
}
