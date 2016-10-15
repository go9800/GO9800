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
 * 14.06.2006 Class created 
 * 26.09.2006 Changed status + CEO handshake for HP9810A
 * 10.01.2009 Rel. 1.33 Added speed toggle
 * 22.12.2009 Rel. 1.42 Changed plotter movement delay and sound output
 * 03.04.2010 Rel. 1.50 Inheritance from IOinterface and initialization completely reworked
 */

package io;

import emu98.IOunit;

public class HP9862Interface extends IOinterface
{
  protected HP9862A hp9862a;
  protected boolean delay = false;
  protected boolean debug = false;
  
  public HP9862Interface(Integer selectCode)
  {
    // create named thread
    super(selectCode, "HP9862Interface");
    status = IOunit.devStatusReady | HP9862A.POWER;
  }

  public void setDevice(IOdevice ioDev)
  {
    super.setDevice(ioDev);
    hp9862a = (HP9862A)ioDev;
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

      // put plotter in ready status after delay
      if(delay) {
        synchronized(ioReg) {
          status |= IOunit.devStatusReady | HP9862A.POWER;
          ioReg.CEO = false;
          delay = false;
          hp9862a.soundStop();
          // set timer to idle
          timerValue = 1000;
        }
      }
    }
  }
  
  public boolean input()
  {
    synchronized(ioReg) {
      if(debug)
        ioReg.console.append("HP9862A status input: " + Integer.toHexString(status) + "\n");
      
      if(ioReg.CEO)
        // put status on IO bus
        ioReg.bus.din = status;
      else
        // put status on IO bus
        ioReg.bus.din = status;

      return(true); // hold CEO
    }
  }
  
  public boolean output()
  {
    synchronized(ioReg) {
      if(debug)
        ioReg.console.append("HP9862A output: " + Integer.toHexString(ioReg.getValue()) + "\n");

      // returns pen status and not ready -> delay for plotter output 
      status = hp9862a.output(ioReg.getStatus(), ioReg.getData());

      // on HP9820/30 CEO is held and handshake is done 
      // by status=IOregister.devStatusReady.
      // on HP9810 handshake is done by CFI/CEO
      if((status & IOunit.devStatusReady) != 0) {
        return(false); // clear CEO when ready
      }

      delay = true;
      // restart timer
      timerValue = highSpeed? 0 : timerValue;
      devThread.interrupt();
    }
    
    return(true);
  }
}
