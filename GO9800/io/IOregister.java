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
 * 31.05.2006 method setValue revised 
 * 03.06.2006 IObus incorporated as local class
 * 12.06.2006 Store devices in Vector instead of an array to enable multiple us of selectCode 12
 * 25.07.2006 Added method setStatus
 * 07.09.2006 Added prevDEN variable and removed this from DisplayInterface
 * 24.09.2006 Added flag10_20 for proper HP9810 IO handling
 * 02.10.2006 Added MFL for mag. card reader, added bus.cardReader
 * 23.10.2006 Changed MagneticCardReader to MagneticCardReaderInterface
 * 18.11.2006 Added disasmOutput
 * 04.01.2007 Rel. 0.22 Added dispSRQ flag for monitoring SRQ occurences during display output. This is necessary for HP9860A SRQs.
 * 17.01.2007 Rel. 0.23 Added method setSelectCode()
 * 10.03.2007 Rel. 0.30 Added reset flag
 * 23.03.2007 Rel. 0.31 Added volatile attribute to various IO flags
 * 24.09.2007 Rel. 1.20 Changed display blanking control from fixed timer to instruction counter
 * 12.12.2007 Rel. 1.20 Changed serviceRequestAcknowledge(): input IO-bus value to IO-register here instead of each IOinterface.requestService()
 * 27.01.2008 Rel. 1.20 Added Bus.setData() and Bus.setStatus() for HP9866 status return 
 * 27.02.2008 Rel. 1.21 Added keyCounter, Class Counter and method instructionCounter()
 * 17.04.2008 Rel. 1.41 Added checkTiming() and various timing values for use by Thread.sleep() in I/O devices
 * 12.03.2011 Rel. 1.50 Changed to static setting of IOregister in IOinterface
 */

package io;

import java.util.*;

import emu98.Console;
import io.DisplayInterface;
import io.KeyboardInterface;

public class IOregister
{
  // IO-Register = CO | SIO | DIO
  static final int DIO_mask = 0x00ff; // 8 bit IO-Data
  static final int SIO_mask = 0x0f00; // 4 bit IO-Status
  static final int  CO_mask = 0xf000; // 4 bit IO-SelectCode
  static final int  SD_mask = 0x0fff; // 12 bit IO-Status+Data combined
  
  public static final int devStatusReady = 0x0100; // device status ready: S0=1
  
  public int SSI; // Service Strobe Input (15 device party line)
  public boolean SSF; // Single Service FF (Service Request acknowledged

  public boolean STP; // SC=00 Stop Switch
  public boolean SIH; // SC=01 Service Inhibit
  public boolean CEO; // SC=01 Device Ready
  public boolean MCR; // SC=02 Magnetic Card Reader Output (pulse)
  public boolean MLS; // SC=02 Magnetic Card Reader Control (pulse)
  public boolean MFL; // SC=02 Magnetic Card Reader Input Flag
  public boolean PEN; // SC=04 Printer Enable
  public boolean DEN; // SC=08 Display Enable
  public boolean KLS; // SC=16 Keyboard LEDs (pulse)
  
  public boolean dispSRQ; // flag for SRQ occured during display refresh phase
  public Counter dispCounter; // instruction counter for display blank control
  public Counter keyCounter; // instruction counter for key release control
  public final int DISP_INSTR = 1000; // number of instructions until display is blanked
  public final int KEYB_INSTR = 1000; // number of instructions until keyboard is released
  
  private int value;   // value of IO-register
  public boolean line10_20; // false for HP9810A, true for HP9820A+HP9830A
  public boolean reset = true; // true for machine restart
  public Bus bus;     // IO bus
  int mask;
  
  public int time_1ms = 1;
  public int time_3ms = 3;
  public int time_7ms = 7;
  public int time_10ms = 10;
  public int time_12ms = 12;
  public int time_16ms = 16;
  public int time_30ms = 30;
  public int time_32ms = 32;
  public int time_100ms = 100;

  public Console console;

  public IOregister(int bitMask, int initValue)
  {
    bus = new Bus(0);
    mask = bitMask; 
    value = initValue;
    SSI = 0x00;
    SSF = STP = CEO = MCR = PEN = DEN = KLS = MFL = false;
    SIH = true;
    line10_20 = false;
    
    dispCounter = new Counter(DISP_INSTR);
    keyCounter = new Counter(KEYB_INSTR);

    System.out.print("Timing calibration in progress ");
    time_1ms = checkTiming(1);
    time_3ms = checkTiming(3);
    time_7ms = checkTiming(7);
    time_10ms = checkTiming(10);
    time_12ms = checkTiming(12);
    time_16ms = checkTiming(16);
    time_30ms = checkTiming(30);
    time_32ms = checkTiming(32);
    time_100ms = checkTiming(100);
    System.out.print(" done.\n");
    
    // connect IOregister to all IOinterfaces
    IOinterface.setIOregister(this);

    System.out.println("HP9800 I/O register loaded.");
  }
  
  private int checkTiming(int time)
  {
    long t = 0;
    int num = 100 / time;
    
    System.out.print(" " + Integer.toString(time));
    
    synchronized(this) {
      for(int i = 1; i <= num; i++) {
        t -= System.nanoTime();
        try {
          Thread.sleep(time);
        } catch (InterruptedException e) { }
        t += System.nanoTime();
      }
    }
    
    time = (int)((1000000L * num * time * time) / t);
    System.out.print("/" + Integer.toString(time));

    return(time);
  }
  
  public void setDisassemblerOutput(Console console)
  {
    this.console = console;
  }

  public void instructionCounter()
  {
    // decrement instruction counter for display blanking
    if(dispCounter.count()) {
      bus.display.blank();
    }
    
    // decrement instruction counter for key release
    if(keyCounter.count())
      bus.keyboard.release();
  }
  
  public synchronized int getSelectCode()
  {
    return((value & CO_mask) >> 12);
  }
  
  synchronized void setValue(int value)
  {
    // set all 16 bits
    this.value = value;
  }
  
  synchronized void setStatusData(int value)
  {
    // or register data- and status-input to CO-bits
    this.value = (this.value & CO_mask) | (value & SD_mask);
  }
  
  synchronized void setSelectCode(int value)
  {
    // or register select code to data- and status-bits 
    this.value = ((value << 12) & CO_mask) | (this.value & SD_mask);
  }
  
  public synchronized int getValue()
  {
    // return all 16 bits
    return(value);
  }
  
  synchronized int getStatus()
  {
    // return value of register status bits
    return(value & SIO_mask);
  }
  
  synchronized int getData()
  {
    // return value of register data bits
    return(value & DIO_mask);
  }
  
  synchronized void setData(int dio)
  {
    // set register data bits 
    dio &= DIO_mask;
    value = (value & (IOregister.CO_mask | IOregister.SIO_mask)) | dio;
  }
  
  synchronized void setStatus(int sio)
  {
    // set register status bits 
    sio &= SIO_mask;
    value = (value & (IOregister.CO_mask | IOregister.DIO_mask)) | sio;
  }
  
  public synchronized void input()
  {
    value = (value & CO_mask) | (bus.din & SD_mask);
  }
  
  // QNR
  public synchronized boolean serviceRequested()
  {
    // accept Service Request only if not in service routine and (not inhibited or STOP key pressed)  
    return(!SSF && ((!SIH && SSI != 0) | STP ));
  }
  
  // SRA
  public synchronized void serviceRequestAcknowledge()
  {
    // SRA (pulse) sets Single Service FF and Service Inhibit FF
    SSF = true; // set Single Service FF
    SIH = true; // set Service Inhibit flag
    dispSRQ = true; // memorize SRQ event for control of display refresh

    /*
    * if line10_20 is true (HP9820/30), SC=0 enables the SRQ-bit of an interface to be put on the IO-bus.
    * this is simulated by input of the SSI bit pattern
    * if line10_20 is false (HP9810), the requesting device puts the input value directly on the IO-bus.
    */

    setSelectCode(0);
    if(line10_20) {
      if(SSI == (1 << 11)) // SRQ from keyboard device (SC=12)?
        bus.din = 0;     // keyboard has no SRQ bit
      else
        bus.din = SSI & 0xffff;
    }
    
    input(); // input IO-bus to IO-register
  }
  
  public class Bus
  {
    public Vector<IOinterface> interfaces;
    IOinterface device;
    public KeyboardInterface keyboard;
    public DisplayInterface display;
    public HP9800MagneticCardReaderInterface cardReader;
    Enumeration<IOinterface> devEnum = null;
    
    public int din; // value of input bus lines from all devices
    public int dout; // value of output bus lines to all devices

    public Bus(int value)
    {
      din = value;
      interfaces = new Vector<IOinterface>();
     
      System.out.println("HP9800 I/O bus loaded.");
    }

    // get device with matching selectCode on bus
    public IOinterface selectDevice()
    {
      IOinterface device;
      int selectCode = getSelectCode();
      
      if(selectCode != 0) {
        for(devEnum = interfaces.elements(); devEnum.hasMoreElements(); ) {
          device = (IOinterface)devEnum.nextElement();
          if(device.selectCode == selectCode) {
            return(device);
          }
        }
      }
      
      devEnum = null;
      return(null);  // no matching device found
    }
    
    // get next device with matching selectCode on bus
    public IOinterface nextDevice()
    {
      IOinterface device;
      int selectCode = getSelectCode();
      
      if(devEnum != null) {
        for( ; devEnum.hasMoreElements(); ) {
          device = (IOinterface)devEnum.nextElement();
          if(device.selectCode == selectCode) {
            return(device);
          }
        }
        
        devEnum = null;
      }
      return(null);  // no matching device found
    }
    
    // set CEO line and call device IO-method
    public synchronized void setCEO()
    {
      CEO = true;
      
      device = selectDevice();
      if(device != null) {
        CEO = device.output();
        if(CEO)
          CEO = device.input();
      }
    }
    
    synchronized void setData(int dio)
    {
      // set register data bits 
      dio &= DIO_mask;
      din = (din & (IOregister.CO_mask | IOregister.SIO_mask)) | dio;
    }
    
    synchronized void setStatus(int sio)
    {
      // set register status bits 
      sio &= SIO_mask;
      din = (din & (IOregister.CO_mask | IOregister.DIO_mask)) | sio;
    }
  }
  
  public class Counter
  {
    private int value; // instruction counter for device control
    private int initialValue; // number of instructions until counter is 0 

    public Counter(int init)
    {
      initialValue = init;
      value = 0;
    }
    
    public synchronized void restart()
    {
      value = initialValue;
    }
    
    public synchronized boolean count()
    {
      // decrement instruction counter
      // return true only if reaching zero
      return(value > 0 && (--value == 0));
    }
    
    public synchronized boolean running()
    {
      return(value > 0);
    }
  }
}
