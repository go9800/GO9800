/*
 * HP9800 Emulator
 * Copyright (C) 2006 - 2018 Achim Buerger
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
 * 06.07.2016 Rel. 2.00: Added method rshift()
 * 10.07.2016 Rel. 2.00: Added method setBits()
 * 01.08.2016 Rel. 2.00: Added classes Qregister and Iregister
 * 04.10.2016 Rel. 2.02: Performance optimization in shift
 */

package emu98;


public class Register
{
  int width, mask, value, inputBit;
  String name;
  Register src; // source and destination registers for shift operations
  boolean shiftEnabled;
  
  Register(String registerName, int registerWidth, int initValue)
  {
    name = registerName;
    width = registerWidth;
    mask = (1 << width) - 1; 
    value = initValue;
    src = this; // shift rotate value in register
    shiftEnabled = false; // don't shift this register
  }
    
  int setValue(int value)
  {
    return((this.value = value & mask));
  }
  
  int getValue()
  {
    return(value);
  }
  
  // source Register or Bus for shift operation
  void setSource(Register src)
  {
  	this.src = src;
  	src.shiftEnabled = false; // shift must be enabled separately
  }
  
  // load input bit (LSB) from source
  void loadInput()
  {
  	// load only if shift enabled
  	if(shiftEnabled)
  	  //inputBit = src.getOutput();
  	  inputBit = src.value & 1; // variable access is much faster than method call
  }
  
  // return output bit (LSB) of register
  int getOutput()
  {
  	return(value & 1);
  }
  
  // enable and disable shifting
  void shiftEnable(boolean enable) {
  	shiftEnabled = enable;
  }
  
  void shift()
  {
  	// shift only if enabled
  	if(shiftEnabled) {
  	  // shift register 1 bit right and set input bit (MSB)
  	  value = (inputBit << width | value) >> 1;
  	}
  }
}

class QRegister extends Register
{
  private boolean q6mode;

	public QRegister(String registerName, int registerWidth, int initValue)
	{
		super(registerName, registerWidth, initValue);
	}

  // set special mode for Q-register to load only bit 6
  void setQ6mode(boolean mode)
  {
  	q6mode = mode;
  }
  
  void shift()
  {
  	// shift only if enabled
  	if(shiftEnabled) {
  		if(q6mode) {
  			// set bit 6 in register to input bit
  			value &= 0b1111_1111_1011_1111;
  			value |= inputBit << 6;
  		}	else {
  			super.shift();
  		}
  	}
  }
}

class IRegister extends Register
{
  private boolean b8mode;

	public IRegister(String registerName, int registerWidth, int initValue)
	{
		super(registerName, registerWidth, initValue);
	}

  // set special mode for IO-register to shift only least significant 8 bits
  void set8Bitmode(boolean mode)
  {
  	b8mode = mode;
  }
  
  void shift()
  {
  	// shift only if enabled
  	if(shiftEnabled) {
  		if(b8mode) {
  			// shift lower 8 bits of register 1 bit right and set input bit
  			value = ((value & 0b0000_0000_1111_1111) >> 1) | (inputBit << 7) | (value & 0b1111_1111_0000_0000);
  		}	else {
  			super.shift();
  		}
  	}
  }
}
