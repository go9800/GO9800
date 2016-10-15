/*
 * HP9800 Emulator
 * Copyright (C) 2006 Achim Buerger
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

package emu98;


public class ProgramCounter extends Register
{
  boolean incr;  // for P-register only

  ProgramCounter(int bitMask, int initValue)
  {
    super("P", bitMask, initValue);
    incr = true;
  }

  int setValue(int value)
  {
    // no increment after opcode execution
    incr = false;
    return((this.value = value & mask));
  }
  
  int getValue()
  {
    return(value);
  }
  
  void setIncrement(boolean increment)
  {
    incr = increment;
  }
  
  int increment()
  {
    if(incr)
      value = (value + 1) & mask;
    
    return(value);
  }
}
