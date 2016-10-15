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
 * 31.05.2006 Return of status bit when device is addressed, but CEO=false 
 * 31.07.2006 Don't check serviceRequested before issuing new Service Request
 * 31.07.2006 Don't clear SSF in input method. This is now done by CLF 1 instruction   
 * 14.11.2006 Do it anyway. CLF 1 instruction doesn't.
 * 27.07.2007 [tre] constructor sets thread name to assist in debugging;
 *            add necessary locking around ioReg access;
 *            requestInterrupt() notifies emulator thread, likely waiting in
 *            display refresh loop; set correct default status value (0)
 * 29.08.2007 Rel. 1.20 Reworked handling of Service ReQuests (see class IOinstruction)
 * 12.12.2007 Rel. 1.20 don't input IO-bus value to IO-register in requestInterrupt(). This is now done by IOregister.serviceRequestAcknowledge()
 * 09.02.2010 Rel. 1.42 Added method stop() for stopping device threads before unloading 
 * 12.03.2011 Rel. 1.50 Changed to static setting of IOregister
 */

package io;

import emu98.Memory;

public class IOinterface implements Runnable
{
  static protected IOregister ioReg; // connection to IOregister
  static protected Memory[] memory; // connection to HP9830A memory extension (e.g. HP11273A)
  protected int selectCode;
  public int srqBits; // bit pattern for service request
  public boolean serviceRequested;
  public boolean highSpeed = false;
  
  protected IOdevice ioDevice;
  protected Thread devThread;
  
  protected int status = IOregister.devStatusReady;
  protected int timerValue = 1000;
  
  public IOinterface()
  {

  }
  
  // constructor for internal interfaces (display, keyboard, printer, card reader)  
  public IOinterface(int selectCode, String name)
  {
    devThread = (name == null) ? new Thread(this) : new Thread(this, name);

    this.selectCode = selectCode;
    srqBits = 1 << (selectCode - 1);
    serviceRequested = false;
    
    // start only named threads
    if(name != null)
      devThread.start();
  }
  
  public IOinterface(int selectCode)
  {
    this(selectCode, null);
  }
  
  // constructor for external interfaces (loaded by Emulator.loadDevice())
  public IOinterface(Integer selectCode, String name)
  {
    devThread = (name == null) ? new Thread(this) : new Thread(this, name);

    this.selectCode = selectCode.intValue();
    srqBits = 1 << (this.selectCode - 1);
    serviceRequested = false;
  }

  public IOinterface(Integer selectCode)
  {
    this(selectCode, null);
  }
  
  // connect to IOregister for all IOinterfaces
  public static void setIOregister(IOregister ioReg)
  {
    IOinterface.ioReg = ioReg;
  }
  
  public void setDevice(IOdevice ioDev)
  {
    ioDevice = ioDev;
  }
  
  public void start()
  {
    // start only explicid named threads
    if(!devThread.getName().startsWith("Thread"))
      devThread.start();
  }
  
  public void run()
  {
    while(true) {
      // sleep until interrupted by IO-instruction
      try {
        Thread.sleep(timerValue);
      } catch(InterruptedException e) {
      }

      synchronized(ioReg) {
        // selectCode of this device on ioBus?
        if(ioReg.getSelectCode() == selectCode) {
          // set or get ioReg.value;
          if(ioReg.CEO) {
            ioReg.CEO = output();
          }

          if(ioReg.CEO) {
            ioReg.CEO = input();
          } else {
            // put status on IO bus (S0=1)
            ioReg.bus.din = IOregister.devStatusReady;
          }
        }
      }
    }
  }
  
  public void stop()
  {
    devThread.stop();
  }
  
  protected void requestInterrupt()
  {
    synchronized(ioReg) {
      serviceRequested = true;
      
      // set Service ReQuest bit (selectCode-1) to signal device which requested interrupt
      if(ioReg.line10_20)
        ioReg.SSI |= srqBits;

      ioReg.notifyAll(); // Notify waiting threads esp. HP98xxDisplayInterface
    }
  }
  
  public boolean input()
  {
    synchronized(ioReg) {
      if(serviceRequested) {
        ioReg.setStatusData(0);

        // clear Service Request
        ioReg.SSI &= ~srqBits;

        // this in turn clears Single Service FF
        ioReg.SSF = false;
        serviceRequested = false;
      } else {
        if(ioReg.CEO) {
          // put status on IO bus
          ioReg.bus.din = 0;
        } else {
          // put status on IO bus
          ioReg.bus.din = 0;
        }
      }

      return(false); // input ok, reset CEO
    }
  }
  
  public boolean output()
  {
    //ioReg.getData();

    return(true); // nothing to output, hold CEO
  }
}
