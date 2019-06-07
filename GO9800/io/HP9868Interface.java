/*
 * HP9800 Emulator
 * Copyright (C) 2006-2019 Achim Buerger
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
 * 27.05.2019 Class created 
*/

package io;

import emu98.IOunit;

public class HP9868Interface extends IOinterface
{
  public HP9868Interface(Integer selectCode, HP9800Mainframe hp9800Mainframe)
  {
    // create named thread
    super(selectCode, "HP9868Interface", hp9800Mainframe);
  }
  
  public void run()  {
    while(true) {
      // sleep until interrupted by IO-instruction
      try {
        Thread.sleep(timerValue);
      } catch(InterruptedException e) {
      }
      
      // do nothing
    }
  }
  
  protected void requestInterrupt()
  {
  }
  
  public boolean input()
  {
    return(true); // nothing to input, hold CEO
  }
  
  public boolean output()
  {
    return(true); // nothing to output, hold CEO
  }
}
