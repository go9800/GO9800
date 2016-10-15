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
 * 31.07.2006 Don't check serviceRequested before issueing new Service Request
 * 22.09.2006 Bugfix: Don't call super.input() when no SRQ otherwise HP9810A printer output maybe corrupted
 * 24.09.2006 Added handling of 10/20 flag for HP9810A
 * 24.09.2006 Changed handling of SSI and SSF for HP9810A
 * 03.01.2007 Rel. 0.22 handling of SRQ reworked 
 * 03.01.2007 Rel. 0.22 added different WAIT constants 
 * 05.09.2007 Rel. 1.20 requestInterrupt() reworked
 * 12.12.2007 Rel. 1.20 don't input IO-bus value to IO-register in requestInterrupt(). This is now done by IOregister.serviceRequestAcknowledge() 
 * 03.04.2010 Rel. 1.50 Inheritance from IOinterface and initialization completely reworked
*/

package io;

public class HP11200A extends IOinterface
{
  protected HP9860A hp9860a;
  protected boolean reading = false;
  protected int keyCode = -1;
  
  public HP11200A(Integer selectCode)
  {
    // create named thread
    super(selectCode, "HP11200A");
    timerValue = HP9860A.WAIT_IDLE;  // value for idle loop
  }

  public void setDevice(IOdevice ioDev)
  {
    super.setDevice(ioDev);
    hp9860a = (HP9860A)ioDev;
  }
    
  public void run()
  {
    while(true) {
      // sleep 30ms before returning next character 
      try {
        Thread.sleep(timerValue);
      } catch(InterruptedException e) {
        // restart timer with (changed) timerValue
        continue;
      }

      synchronized(ioReg) {
        if(serviceRequested && !ioReg.line10_20) {
          // on HP9860 SRQ is held only for one card clock
          // clear Service Request line 12
          ioReg.SSI &= ~srqBits;

          // this in turn clears Single Service FF
          ioReg.SSF = false;
          //serviceRequested = false;
        }
      }
      
      if(reading) {
        reading = hp9860a.readInputFile();
        if(reading && keyCode != -1) {
          requestInterrupt();
        }
      }
    }
  }

  public void requestInterrupt()
  {
    serviceRequested = true;

    synchronized(ioReg) {
      // set Service Request
      ioReg.SSI |= srqBits;
      ioReg.notifyAll(); // Notify waiting threads esp. HP98xxDisplayInterface
      
      // if SRQ not inhibited and SC=0 and HP9820/30
      if(!ioReg.SIH && !ioReg.SSF) {
        if(!ioReg.line10_20) {
          // on HP9810 deliver keyCode directly
          ioReg.bus.din = keyCode;
        }
      }
    }
  }
  
  public boolean input()
  {
    synchronized(ioReg) {
      if(serviceRequested && (keyCode != -1)) {
        ioReg.bus.din = keyCode;
        keyCode = -1;

        // clear Service Request
        ioReg.SSI &= ~srqBits;
        ioReg.SSF = false;
        serviceRequested = false;
      }
      
      return(true);
    }
  }
}