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
 * 26.05.2007 Class created
 * 06.08.2008 Rel. 1.20: Asynchronous execution disabled because of timing problems on some plattforms
 * 22.10.2009 Rel. 1.40 Moved output sleep() to HP9867.java
 * 04.04.2010 Rel. 1.50 Inheritance from IOinterface and initialization completely reworked
 * 25.10.2017 Rel. 2.03 Changed static access to ioUnit, removed deprecated use of ioRegister
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 */

package io;

public class HP11273A extends IOinterface
{
  public HP11305A hp11305a;
  private int command = 0;
  private boolean busy = false;
  private boolean debug = false;
  
  public HP11273A(Integer selectCode, HP9800Mainframe hp9800Mainframe)
  {
    // create thread
    super(selectCode, hp9800Mainframe);
    status = HP11305A.POWER_ON;
    timerValue = HP11305A.IDLE_TIMER;
  }

  public void setDevice(IOdevice ioDev)
  {
    super.setDevice(ioDev);
    hp11305a = (HP11305A)ioDev;
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

      // execute disc command asynchronously
      if(busy) {
        busy = false;
        timerValue = HP11305A.IDLE_TIMER;

        status = hp11305a.output(command);
        ioUnit.setStatus(status); // return error status
        ioUnit.CEO = false; // clear CEO
      }
    }
  }

  public boolean output()
  {
    debug = ioUnit.console.getDebugMode();
    
    synchronized(ioUnit) {
      // is it an output operation (SO3=0)?
      if(ioUnit.CEO) {
        if(debug)
          ioUnit.console.append("HP11273A out: " + Integer.toHexString(ioUnit.getValue()) + "\n");

      // synchronous execution of disc command
      status = hp11305a.output(ioUnit.getValue());
      ioUnit.setStatus(status); // return error status

      return(false); // clear CEO

      /*
        // asynchronous execution of disc command
        command = ioReg.getValue();
        busy = true;
        timerValue = HP11305A.BUSY_TIMER;
        devThread.interrupt();
      */
      }

      return(ioUnit.CEO); // hold CEO
    }
  }

  public boolean input()
  {
    debug = ioUnit.console.getDebugMode();

    synchronized(ioUnit) {
      if(ioUnit.CEO) {
        // put status on IO bus
        ioUnit.bus.din = status;

        if(debug)
          ioUnit.console.append("HP11273A in: " + Integer.toHexString(ioUnit.bus.din) + "\n");
      } else {
        // put status on IO bus
        ioUnit.bus.din = status;
      }

      return(ioUnit.CEO); // hold CEO
    }
  }
}