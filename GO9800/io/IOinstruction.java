/*
 * HP9800 Emulator
 * Copyright (C) 2006-2012 Achim Buerger
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
 * 26.05.2006 Moved sleeps after DEN and MLS to device.output() method
 * 31.05.2006 output method revised (ioReg.setValue) 
 * 09.07.2006 Bugfix: getFlag(1) has to return inverse value of CEO
 * 11.07.2006 Added 1ms delay in getFlag(1) for IO thread execution
 * 17.09.2006 Changed CLF 1 for handling of HP9810A keyboard interrupt
 * 18.09.2006 Changed OTx 16 for handling of HP9810A keyboard LEDs
 * 20.09.2006 Changed OTx 4 for handling of internal printer
 * 24.09.2006 Changed CLF 1 for handling of SSF and SSI. Workaround no longer necessary, now correctly done by mainframe.keyReleased())
 * 03.10.2006 Changed CLF 2 and SFS/SFC 02 for handling of MFL 
 * 04.10.2006 Use cardReader.output() with MLS or MCR
 * 25.10.2006 Added 1ms delay in getFlag() for IO thread execution
 * 28.10.2006 Added synchronous input from mag. card in getFlag(2) 
 * 04.11.2006 Changed OTx 1: OR IO-reg with 0x0f00 when no device is selected (for Mass Memory ROM)
 * 05.11.2006 On HP9810A this collides with mag card reader. So do this only on 9820/30
 * 19.11.2006 Changed handling of CEO from asynchronous to synchronous in OTx 0 
 * 21.11.2006 STF 1 now clears CEO (undocumented feature!). This is required for HP9820A together with HP9865A and Cass. ROM file write operations
 * 18.12.2006 Rel. 0.21: In method getFlag() changed Thread.sleep(1) to Thread.yield()
 * 17.01.2007 Rel. 0.23 Bugfix: in CLF 1 only set upper 4 select code bits to 0 
 * 27.07.2007 Rel. 1.20 [tre] Add necessary locking around ioReg access;
 *            set correct status value (0) on bus for nonexistent devices;
 *            no need for yield() since emulator thread at lower priority
 * 29.08.2007 Rel. 1.20 Reworked handling of Service ReQuests. 
 *            The interface SRQ line is now correctly identified by the SSI bit pattern
 *            and only put on the IO-bus if SIH is enabled, SC=0, and line10_20 is true.
 * 12.12.2007 Rel. 1.20 removed direct handling of SRQ from CF 1. This is now done by IOregister.serviceRequestAcknowledge().
 * 28.01.2008 Rel. 1.20 added again Thread.sleep(1) in getFlag() for correct output handling of Typewriter ROM 
 * 09.01.2009 Rel. 1.33 Changed Thread.sleep(1) to ioReg.wait(1) in getFlag()
 * 04.02.2012 Rel. 1.51 Bugfix: set sleep time fixed to 1ms in getFlag() (not ioReg.time_1ms which may be 0)
 */

package io;

public class IOinstruction
{
  IOregister ioReg;

  public IOinstruction(IOregister ioRegister)
  {
    this.ioReg = ioRegister;
  }

  // Set FlipFlop(SC)
  public void setFlag(int selectCode)
  {
    synchronized(ioReg) {
      switch(selectCode) {
      case 1:
        ioReg.SIH = true;

        // CEO is cleared by STF 1 (undocumented!)
        ioReg.CEO = false;

        ioReg.input();
        break;

      case 2:
        ioReg.MCR = true;
        // call MCR-routine
        ioReg.bus.cardReader.output();
        ioReg.MCR = false;
        break;

        // other SC not used
      }
    }
  }

  // Clear FlipFlop(SC)
  public void clearFlag(int selectCode)
  {
    synchronized(ioReg) {
      switch(selectCode) {
      case 1:
        ioReg.SIH = false;    // enable interrupt
        ioReg.CEO = false;
        // set address bits to 0
        ioReg.setSelectCode(0);
        break;

      case 2:
        // reset magn. card FF
        ioReg.MFL = false;
        break;

      case 4:
        ioReg.PEN = false;
        break;

      case 8:
        // disable display
        ioReg.DEN = false;
        break;

      case 16:
        // HP9810A uses CLF 16 to output to keyboard LEDs
        ioReg.KLS = true;
        ioReg.bus.display.output();
        ioReg.KLS = false;
        break;
      }
    }
  }

  public boolean getFlag(int selectCode)
  {
    synchronized(ioReg){
      // wait 1ms for IO execution in other thread
      // absolutely necessary for HP9810 Typewriter-ROM because of fixed SFC-loop
      try {
        Thread.sleep(1 /*ioReg.time_1ms*/); // must not be 0ms!
      } catch(InterruptedException e) { }

      switch(selectCode) {
      case 0:
        return(ioReg.STP);

      case 1:
        // return flag=true if CEO=false!
        return(!ioReg.CEO);

      case 2:
        // synchronous input from magnetic card
        ioReg.bus.cardReader.input();
        return(ioReg.MFL);

      case 4:
        return(ioReg.PEN);

        // other SC NOT USED
      }

      return(false);
    }
  }

  public void setControl(int selectCode)
  {
    synchronized (ioReg) {
      switch(selectCode) {
      case 1:
        // synchronous input/output of IO-register to device
        // device thread has to clear CEO
        ioReg.bus.setCEO();
        break;

      case 2:
        ioReg.MLS = true;
        // call MCR-routine
        // HP9830A + HP9821A uses STC 2 for activation of the beeper
        ioReg.bus.cardReader.output();
        ioReg.MLS = false;
        break;

        // other SC not used
      }
    }
  }

  public void clearControl(int selectCode)
  {
    //NOT USED
  }

  public int output(int selectCode, int regValue)
  {
    synchronized (ioReg) {
      switch(selectCode) {
      case 0:
        // output 8 bits
        ioReg.setData(regValue);
        // synchronous output of IO-register to device
        // device thread has to clear CEO
        ioReg.bus.setCEO();

        // rotate right value 8 bits
        return(((regValue & IOregister.DIO_mask) << 8) | (regValue >> 8));

      case 1:
        // load IO-register with 16 bit value
        ioReg.setValue(regValue);
        // check if device is addressed
        int sc = ioReg.getSelectCode();
        if(sc != 0) {
          IOinterface device = ioReg.bus.selectDevice();
          if(device != null)
            device.input();
          else {
            if(ioReg.line10_20)
              ioReg.bus.din |= 0x0f00;
          }
        }
        break;

      case 2:
        // NOT USED
        // output 1 bit
        // call MCR-routine(regValue & 1)
        break;

      case 4:
        /*
         * during output to internal printer the dots are sent 
         * by 2 consecutive output instructions (OTB 1 and OTA 4).
         * The first out value is shifted serially into a 10 bit
         * printer buffer register when the IO register is loaded
         * with the second out value. This behaviour is simulated below.  
         */

        int printValue = (ioReg.getValue() & 0xffc0) << 10;

        // output 16 bits to IO register
        ioReg.setValue(regValue);
        ioReg.PEN = true;
        // output ioReg.value and printValue to printer
        ioReg.bus.display.output(printValue);
        break;

      case 8:
        // output 16 bits to display
        ioReg.setValue(regValue);
        ioReg.DEN = true;
        ioReg.dispCounter.restart(); // restart display blank counter
        ioReg.bus.display.output();
        break;

      case 16:
        // HP9810A uses OTA 16 to output to keyboard LEDs
        ioReg.setValue(regValue);

        // HP9830A + HP9820A use OTA 16 in interrupt service routine
        // to output device select code and subsequently input of a key code 
        for(IOinterface device = ioReg.bus.selectDevice(); device != null; device = ioReg.bus.nextDevice()) {
          device.input();
        }

        ioReg.KLS = true;
        ioReg.bus.keyboard.enableInterrupt(false);
        ioReg.bus.display.output();
        ioReg.KLS = false;
        ioReg.bus.keyboard.enableInterrupt(true);
        break;
      }

      return(regValue);
    }
  }


  public int input(int selectCode, int regValue)
  {
    synchronized (ioReg) {
      switch(selectCode) {
      case 0:
        // input 8 bits
        int dio = ioReg.getData();
        // asynchronous input of IO-register from device
        // device thread has to clear CEO
        ioReg.CEO = true;

        // shift right value 8 bits
        return((dio << 8) | (regValue >> 8));

      case 1:
        // input 16 bits
        return(ioReg.getValue());

        // other SC not used
      }

      return(0);
    }
  }


  public int merge(int selectCode, int regValue)
  {
    synchronized (ioReg) {
      switch(selectCode) {
      case 0:
        // input 8 bits
        int dio = (regValue | ioReg.getData()) % IOregister.DIO_mask;
        // asynchronous input of IO-register from device
        // device thread has to clear CEO
        ioReg.CEO = true;

        // shift right value 8 bits
        return((dio << 8) | (regValue >> 8));

      case 1:
        // input 16 bits
        return(regValue | ioReg.getValue());

        // other SC not used
      }

      return(0);
    }
  }
}
