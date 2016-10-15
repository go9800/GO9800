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
 * 18.03.2008 Rel. 1.30 Class created based on HP9866Interface
 * 26.03.2010 Rel. 1.42 Changed to asynchronous output mode (method run())
 * 03.04.2010 Rel. 1.50 Inheritance from IOinterface and initialization completely reworked
*/

package io;

import emu98.IOunit;

public class HP11201A extends IOinterface
{
  protected HP9861A hp9861a;
  protected boolean delay = false;
  protected boolean debug = false;

  public HP11201A(Integer selectCode)
  {
    // create named thread
    super(selectCode, "HP11201A");
  }

  public void setDevice(IOdevice ioDev)
  {
    super.setDevice(ioDev);
    hp9861a = (HP9861A)ioDev;
  }

  public void run()
  {
    while(true) {
      // sleep until interrupted by IO-instruction
      try {
        Thread.sleep(timerValue);
      } catch(InterruptedException e) {
        // restart with new timerValue
        continue;
      }

      // put printer in ready status after delay
      if(delay) {
        synchronized(ioReg) {
          status |= IOunit.devStatusReady;
          ioReg.CEO = false;
          delay = false;
          hp9861a.soundStop();
          // set timer to idle
          timerValue = 1000;
        }
      }
    }
  }
  
  public boolean input()
  {
    synchronized(ioReg) {
      // put status on IO bus (8=EOL)
      ioReg.bus.setStatus(status);
      return(ioReg.CEO); // hold CEO
    }
  }

  public boolean output()
  {
    synchronized(ioReg) {
      status = hp9861a.output(ioReg.getStatus(), ioReg.getData());

      // restart timer for printer status
      timerValue = highSpeed? 0 : timerValue;
      devThread.interrupt();

      ioReg.setStatus(status); // return printer status (CFI loads IO-Register and clears CEO)

      if((status & IOunit.devStatusReady) == 0) {
        delay = true;
        return(ioReg.CEO); // during line output set busy status and hold CEO 
      }
      else
        return(false); // character output ok, reset CEO
    }
  }
}
