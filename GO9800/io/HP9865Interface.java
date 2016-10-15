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
 * 17.06.2006 Class created 
 * 12.06.2006 IO handshake complete reworked
 * 25.07.2006 Bugfix: command (in-status) was not preserved on input
 * 25.07.2006 Added handling of STP
 * 31.07.2006 Don't clear SSF in input method. This is now done by CLF 1 instruction  
 * 31.07.2006 Set error status after command execution 
 * 01.08.2006 Disabled handling of STP flag, STOP is accepted as command only
 * 13.11.2006 Bugfix: Clear SSF after handling of SRQ in input()
 * 14.11.2006 Bugfix: SRQ input() has also to return tape status 
 * 19.11.2006 Asynchronous IO completely reworked for HP9820A
 * 03.04.2010 Rel. 1.50 Inheritance from IOinterface and initialization completely reworked
 * 08.11.2014 Rel. 1.61 Timing problem on some hosts fixed (too small SLOW_TIMER may result in ERROR 59)
 */

package io;

public class HP9865Interface extends IOinterface
{
  protected HP9865A hp9865a;
  protected int tapeValue = 0;
  public int IDLE_TIMER;
  public int SLOW_TIMER;
  public int FAST_TIMER;


  public HP9865Interface(Integer selectCode)
  {
    // create named thread
    super(selectCode, "HP9865Interface");
    status = HP9865A.POWER_ON | HP9865A.CASSETTE_OUT | HP9865A.WRITE_PROTECT;

    IDLE_TIMER = 5 * ioReg.time_100ms;
    SLOW_TIMER = ioReg.time_3ms >= 3 ? ioReg.time_3ms : 4; // timing critical on some hosts -  may result in ERROR 59 if too small
    FAST_TIMER = ioReg.time_1ms >= 1 ? ioReg.time_1ms : 1;

    timerValue = IDLE_TIMER;  // value for idle loop
  }

  public void setDevice(IOdevice ioDev)
  {
    super.setDevice(ioDev);
    hp9865a = (HP9865A)ioDev;
  }

  public void run()
  {
    while(true) {
      // sleep until interrupted by IO-instruction
      try {
        Thread.sleep(timerValue);
      } catch(InterruptedException e) {
        // restart timer with (changed) timerValue
        continue;
      }

      // asynchronous execution of tape command
      status = hp9865a.executeCommand();

      // don't put driveStatus on bus here!
      // this may destroy actual tape command

      synchronized(ioReg) {
        if(ioReg.getSelectCode() == selectCode) {
          if(ioReg.CEO) {
            // CFI set?
            if(hp9865a.inByteReady) {
              // load input value to IO-register and keep status!!
              ioReg.setData(tapeValue);
              hp9865a.inByteReady = false;

              // clear CEO by CFI
              ioReg.CEO = false;
            } else {
              // get IO data for write command
              tapeValue = ioReg.getData();
              hp9865a.outByteReady = true;
            }
          }
        }
      }
    }
  }

  public boolean input()
  {
    synchronized(ioReg) {
      if(serviceRequested) {
        ioReg.bus.din = tapeValue | status;
        hp9865a.inByteReady = false;

        // clear Service Request
        ioReg.SSI &= ~srqBits;

        // this in turn clears Single Service FF
        ioReg.SSF = false;
        serviceRequested = false;
        if(hp9865a.debug)
          ioReg.console.append("HP9865A  SRQ Input: " + Integer.toHexString(ioReg.bus.din) + "\n");
      } else { 
        if(ioReg.CEO) {
          // put status on IO bus
          ioReg.bus.din = status;
          // newly read byte present?
          if(hp9865a.inByteReady) {
            ioReg.bus.din |= tapeValue;
            hp9865a.inByteReady = false;
            if(hp9865a.debug)
              ioReg.console.append("HP9865A  Input: " + Integer.toHexString(ioReg.bus.din) + "\n");
          }
        } else {
          // put status on IO bus
          ioReg.bus.din = status;
        }
      }

      return(ioReg.CEO); // hold CEO
    }
  }

  public boolean output()
  {
    synchronized(ioReg) {
      if(ioReg.CEO) {
        // prepare asynchronous execution
        hp9865a.output(ioReg.getStatus());

        //ioReg.bus.value = status; this is done by input above
      }

      // return immediately, but hold CEO
      // CEO will be cleared by CFI when a new word is read
      return(ioReg.CEO);
    }
  }
}
