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
 * 06.10.2006 Class created, based on HP11200Interface
 * 15.01.2007 Rel. 0.23: Added file i/o
 * 17.01.2007 Rel. 0.23: input() reworked for CEO controlled input buffering
 * 28.01.2007 Rel. 0.30: Split into pure HP11202 interface and HostIO class
 * 07.04.2010 Rel. 1.50 Inheritance from IOinterface and initialization completely reworked
 * 09.05.2012 Rel. 1.60 Added SO status output to device
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 */

package io;

import emu98.IOunit;

public class HP11202A extends IOinterface
{
  public HostIO ioDevice;
  private int inByte = 0;
  private boolean debug = false;
  
  static final int INPUT_MODE = 0x0800;
  
  
  public HP11202A(Integer selectCode, HP9800Mainframe hp9800Mainframe)
  {
    // create named thread
    super(selectCode, "HP11202A", hp9800Mainframe);
  }

  public void setDevice(IOdevice ioDev)
  {
    super.setDevice(ioDev);
    ioDevice = (HostIO)ioDev;
  }
  
  public void run()
  {
    while(true) {
      try {
        Thread.sleep(timerValue);
      } catch(InterruptedException e) {
        // restart timer
        continue;
      }

      ioDevice.timerCallback();
      status = IOunit.devStatusReady;
    }
  }

  public boolean output()
  {
    debug = ioUnit.console.getDebugMode();

    synchronized(ioUnit) {
      // is it an output operation (SO3=0)?
      if(ioUnit.CEO && (ioUnit.getStatus() & INPUT_MODE) == 0) {
        status = 0;

        if(debug)
          ioUnit.console.append("HP11202A out: " + Integer.toHexString(ioUnit.getValue()) + "\n");

        if(ioDevice.output(ioUnit.getStatus(), ioUnit.getData()) == 0) {
          // set status to ready
          status = IOunit.devStatusReady;
        }
      }

      return(ioUnit.CEO); // hold CEO
    }
  }

  public boolean input()
  {
    debug = ioUnit.console.getDebugMode();

    synchronized(ioUnit) {
      // input operation (SO3=1)?
      // CEO triggers reading of file (peripheral) and buffering in inByte
      if(ioUnit.CEO && (ioUnit.getStatus() & INPUT_MODE) != 0) {
        ioUnit.bus.din = status;

        inByte = ioDevice.input(ioUnit.getStatus());
        if(inByte != -1) {
          ioUnit.bus.din |= inByte;

          if(debug)
            ioUnit.console.append("HP11202A in: " + Integer.toHexString(inByte) + "\n");
        }
      } else {
        // put status and last buffered value on IO bus (1=ready)
        ioUnit.bus.din = status | inByte;
      }

      return(ioUnit.CEO); // hold CEO
    }
  }
}