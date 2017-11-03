/*
 * HP9800 Emulator
 * Copyright (C) 2006-2018 Achim Buerger
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
 * 06.06.2006 Class created 
 * 07.06.2006 Vector for output lines added
 * 08.06.2006 Sound added, delay for line print
 * 15.06.2006 Moved output buffering and handling to device class HP9866A
 * 26.02.2007 Rel. 0.30 changed class name to HP9866Interface
 * 27.01.2008 Rel. 1.20 Bugfix: return only status bits on IO-bus to avoid interference with HP9810A mag. card reader
 * 21.01.2008 Rel. 1.20 Delay CEO on line feed to comply with HP9810A Typewriter ROM 
 * 10.01.2009 Rel. 1.33 Added speed toggle
 * 03.04.2010 Rel. 1.50 Inheritance from IOinterface and initialization completely reworked
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
*/

package io;

import emu98.IOunit;

public class HP9866Interface extends IOinterface
{
  protected IOdevice hp9866;
  protected boolean delay = false;
  protected boolean debug = false;
  
  public HP9866Interface(Integer selectCode, HP9800Mainframe hp9800Mainframe)
  {
    // create named thread
    super(selectCode, "HP9866AB", hp9800Mainframe);
  }
  
  public void setDevice(IOdevice ioDev)
  {
    super.setDevice(ioDev);
    hp9866 = ioDev;
  }
  
  public void run()
  {
    while(true) {
      // sleep until interrupted by IO-instruction
      try {
        Thread.sleep(timerValue);
      } catch(InterruptedException e) {
        continue;
      }
      
      // put printer in ready status after delay of 270ms
      synchronized(ioUnit) {
        if(delay) {
          status |= IOunit.devStatusReady;
          ioUnit.CEO = false; // reset CEO for HP9810A Typewriter ROM
          delay = false;
        }
      }
      
      timerValue = 1000;
    }
  }
  
  public boolean input()
  {
    synchronized(ioUnit) {
      // put status on IO bus (1=ready)
      ioUnit.bus.setStatus(status);
      return(ioUnit.CEO); // hold CEO
    }
  }
  
  public boolean output()
  {
    synchronized(ioUnit) {
      status = hp9866.output(ioUnit.getStatus(), ioUnit.getData());
      // restart timer for printer status
      timerValue = highSpeed? 0 : timerValue;
      devThread.interrupt();

      if(status == 0) {
        delay = true;
        return(ioUnit.CEO); // during line output set busy status and hold CEO 
      }
      else
        return(false); // character output ok, reset CEO
    }
  }
}
