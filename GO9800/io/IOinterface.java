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
 * 31.05.2006 Return of status bit when device is addressed, but CEO=false 
 * 31.07.2006 Don't check serviceRequested before issuing new Service Request
 * 31.07.2006 Don't clear SSF in input method. This is now done by CLF 1 instruction   
 * 14.11.2006 Do it anyway. CLF 1 instruction doesn't.
 * 27.07.2007 [tre] constructor sets thread name to assist in debugging;
 *            add necessary locking around ioUnit access;
 *            requestInterrupt() notifies emulator thread, likely waiting in
 *            display refresh loop; set correct default status value (0)
 * 29.08.2007 Rel. 1.20 Reworked handling of Service ReQuests (see class IOinstruction)
 * 12.12.2007 Rel. 1.20 don't input IO-bus value to IO-register in requestInterrupt(). This is now done by IOregister.serviceRequestAcknowledge()
 * 09.02.2010 Rel. 1.42 Added method stop() for stopping device threads before unloading 
 * 12.03.2011 Rel. 1.50 Changed to static setting of IOregister
 * 01.08.2016 Rel. 2.00 Changed to reference ioUnit instead of ioRegister
 * 25.10.2017 Rel. 2.03 Changed static access to ioUnit, removed deprecated use of ioRegister
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 */

package io;

import emu98.IOunit;

public class IOinterface implements Runnable
{
	public HP9800Mainframe mainframe; // connection to HP9800 mainframe
  protected IOunit ioUnit; // connection to IOunit
  public ImageMedia labelImageMedia;
  public boolean internalInterface;
  public int selectCode;
  public int srqBits; // bit pattern for service request
  public boolean serviceRequested;
  public boolean highSpeed = false;
  
  protected IOdevice ioDevice;
  protected Thread devThread;
  
  protected int status = IOunit.devStatusReady;
  protected int timerValue = 1000;
  
  public IOinterface()
  {
  	
  }
  
  // constructor for internal interfaces (display, keyboard, printer, card reader)  
  public IOinterface(int selectCode, String name, HP9800Mainframe hp9800Mainframe)
  {
  	mainframe = hp9800Mainframe; // connect to HP9800Mainframe
  	ioUnit = mainframe.ioUnit; // connect to IOunit
    mainframe.ioInterfaces.add(this); // connect to ioBus
    
    devThread = (name == null) ? new Thread(this) : new Thread(this, name);

    this.selectCode = selectCode;
    srqBits = 1 << (selectCode - 1);
    serviceRequested = false;
    
    // start only named threads
    if(name != null)
      devThread.start();
  }
  
  public IOinterface(int selectCode, HP9800Mainframe hp9800Mainframe)
  {
    this(selectCode, null, hp9800Mainframe);
  }
  
  // constructor for external interfaces (loaded by loadDevice())
  public IOinterface(Integer selectCode, String name, HP9800Mainframe hp9800Mainframe)
  {
  	mainframe = hp9800Mainframe; // connect to HP9800Mainframe
  	ioUnit = mainframe.ioUnit; // connect to IOunit
    mainframe.ioInterfaces.add(this); // connect to ioBus
    labelImageMedia = new ImageMedia("media/HP9800/" + name + "_Label.png", mainframe.imageController); // load interface label
  	

    devThread = (name == null) ? new Thread(this) : new Thread(this, name);

    this.selectCode = selectCode.intValue();
    if(selectCode == 0) { // device without select code (HP9868A)
    	srqBits = 0;
    }
    else
    	srqBits = 1 << (this.selectCode - 1);
    serviceRequested = false;
  }

  public IOinterface(Integer selectCode, HP9800Mainframe hp9800Mainframe)
  {
    this(selectCode, null, hp9800Mainframe);
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
  
  public void run()  {
    while(true) {
      // sleep until interrupted by IO-instruction
      try {
        Thread.sleep(timerValue);
      } catch(InterruptedException e) {
      }

      synchronized(ioUnit) {
        // selectCode of this device on ioBus?
        if(ioUnit.getSelectCode() == selectCode) {
          // set or get ioUnit.value;
          if(ioUnit.CEO) {
            ioUnit.CEO = output();
          }

          if(ioUnit.CEO) {
            ioUnit.CEO = input();
          } else {
            // put status on IO bus (S0=1)
            ioUnit.bus.din = IOunit.devStatusReady;
          }
        }
      }
    }
  }
  
  public void stop()
  {
  	if(devThread != null)
  	{
  		devThread.stop();
  		devThread = null;
  	}
  	
    mainframe.ioInterfaces.removeElement(this);  // remove device interface object from IObus
    
    if(labelImageMedia != null) // dispose interface label
    	labelImageMedia.close();
  }
  
  protected void requestInterrupt()
  {
    synchronized(ioUnit) {
      serviceRequested = true;
      
      // set Service ReQuest bit (selectCode-1) to signal device which requested interrupt
      if(ioUnit.line10_20)
        ioUnit.SSI |= srqBits;

      ioUnit.notifyAll(); // Notify waiting threads esp. HP98xxDisplayInterface
    }
  }
  
  public boolean input()
  {
    synchronized(ioUnit) {
      if(serviceRequested) {
        ioUnit.setStatusData(0);

        // clear Service Request
        ioUnit.SSI &= ~srqBits;

        // this in turn clears Single Service FF
        ioUnit.SSF = false;
        serviceRequested = false;
      } else {
        if(ioUnit.CEO) {
          // put status on IO bus
          ioUnit.bus.din = 0;
        } else {
          // put status on IO bus
          ioUnit.bus.din = 0;
        }
      }

      return(false); // input ok, reset CEO
    }
  }
  
  public boolean output()
  {
    //ioUnit.getData();

    return(true); // nothing to output, hold CEO
  }
}
