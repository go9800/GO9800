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
 * 30.06.2016 Rel. 2.00: class created 
 * 07.08.2016 Rel. 2.00: beta release
 * 25.10.2016 Rel. 2.03: fixed ALU operation in shift cycle
 * 24.10.2017 Rel. 2.04: Added method closeAllDevices() for forced closing of IOdevices
 */

package emu98;

import java.util.Enumeration;
import java.util.Vector;

import io.DisplayInterface;
import io.HP9800MagneticCardReaderInterface;
import io.IOdevice;
import io.IOinterface;
import io.KeyboardInterface;

public class IOunit
{
  CPU cpu;
  public Bus bus;     // IO bus

  IRegister Iregister;
  Register printBuffer;

  // IO-Register = CO | SIO | DIO
  public static final int DIO_mask = 0x00ff; // 8 bit IO-Data
  public static final int SIO_mask = 0x0f00; // 4 bit IO-Status
  public static final int  CO_mask = 0xf000; // 4 bit IO-SelectCode
  public static final int  SD_mask = 0x0fff; // 12 bit IO-Status+Data combined

  public static final int devStatusReady = 0x0100; // device status ready: S0=1

  public int SSI; // Service Strobe Input (15 device party line)
  public boolean SSF; // Single Service FF (Service Request acknowledged)
  public boolean QRD; // Qualifier ROM-disable (if I/O active)

  public boolean STP; // SC=00 Stop Switch
  public boolean SIH; // SC=01 Service Inhibit
  public boolean CEO; // SC=01 Device Ready
  public boolean MCR; // SC=02 Magnetic Card Reader Output (pulse)
  public boolean MLS; // SC=02 Magnetic Card Reader Control (pulse)
  public boolean MFL; // SC=02 Magnetic Card Reader Input Flag
  public boolean PEN; // SC=04 Printer Enable
  public boolean DEN; // SC=08 Display Enable
  public boolean KLS; // SC=16 Keyboard LEDs (pulse)

  public boolean line10_20; // false for HP9810A, true for HP9820A+HP9830A
  public boolean reset = true; // true for machine restart

  public boolean dispSRQ; // flag for SRQ occured during display refresh phase
  public Counter dispCounter; // instruction counter for display blank control
  public Counter keyCounter; // instruction counter for key release control
  public final int DISP_INSTR = 200; // number of instructions until display is blanked
  public final int KEYB_INSTR = 100; // number of instructions until keyboard is released

  public int time_1ms = 1;
  public int time_3ms = 3;
  public int time_5ms = 5;
  public int time_10ms = 10;
  public int time_20ms = 20;
  public int time_30ms = 30;
  public int time_32ms = 32;
  public int time_100ms = 100;

  public Console console; // for debug-output of devices

  public IOunit(CPU cpu)
  {
    this.cpu = cpu; // for access to IO-register and setBC()

    Iregister = new IRegister("IO", 16, 0177777);
    
    printBuffer = new Register("PB", 10, 0); // 10 bit buffer for internal printer
    printBuffer.setSource(Iregister); // print buffer is filled directly by IO-register

    bus = new Bus(0);

    SSI = 0x00;
    QRD = SIH = SSF = STP = CEO = MCR = PEN = DEN = KLS = MFL = false;
    SIH = true;
    line10_20 = false;

    dispCounter = new Counter(DISP_INSTR);
    keyCounter = new Counter(KEYB_INSTR);

    System.out.print("Timing calibration");
    time_1ms = checkTiming(1);
    time_3ms = checkTiming(3);
    time_5ms = checkTiming(5);
    time_10ms = checkTiming(10);
    time_20ms = checkTiming(20);
    time_30ms = checkTiming(30);
    time_32ms = checkTiming(32);
    time_100ms = checkTiming(100);
    System.out.print(" done.\n");

    // connect IOunit to all IOinterfaces
    IOinterface.setIOunit(this);

    System.out.println("HP9800 I/O unit loaded.");
  }

  // generate miscellaneous timing constants
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

  // assign console class
  public void setDisassemblerOutput(Console console)
  {
    this.console = console;
  }

  // Power On Preset
  public void POP()
  {
  	QRD = SIH = PEN = CEO = false;
  	SSI = 0;
  }

  // hardware emulation of the HP9800 I/O-unit

  public void exec()
  {
    int q, hcFlag, ioInstr, sc;

    q = cpu.Qregister.getValue();

    // I/O-operation opcode Q10
    if((q & 0b0000_0100_0000_0000) == 0) {
      if(serviceRequested()) {
        // IOS*(Q10=0)*SRQ = Service Request Acknowledge (SRA)
        serviceRequestAcknowledge();
      }
      
      // IOS*(Q10=0) = no I/O operation
      return;
    }

    QRD = true; // must not be set before SRA!

    // internal select code lines Q4-Q0
    sc = q & 0b0000_0000_0001_1111;

    // IO micro code Q8-Q5, inverted inputs to 1-of-16 selector 74154
    ioInstr = ((~q & 0b0000_0001_1110_0000) >> 5);
    // hold / clear Q9
    hcFlag = q & 0b0000_0010_0000_0000;

    // IO micro-instruction decoder
    switch(ioInstr) {
    case 0: // 1111=Q8765
      if(hcFlag == 0) {  // instruction STF
        switch(sc) { // select code
        case 1:
          SIH = true; // set service inhibit FF

          // CEO is cleared by STF 1, 600ns after input
          input();
          CEO = false;
          break;

        case 2:
          MCR = true;
          // call MCR-routine
          bus.cardReader.output();
          MCR = false;
        }
      }
      break;

    case 1: // 1110 (instruction SFC)
      // skip next instruction (increment PC by 2 using BC), if flag clear
      switch(sc) { // select code
      case 0:
        cpu.setBC(!STP);  // skip if STOP not pressed
        break;

      case 1:
        cpu.setBC(CEO);  // skip if CEO set
        break;

      case 2:
        // synchronous input from magnetic card
        bus.cardReader.input();
        cpu.setBC(!MFL);
        break;

      case 4:
        cpu.setBC(!PEN);  // skip if PEN clear
      }
      break;

    case 3: // 1100 (instruction STC)
      switch(sc) { // select code
      case 1:
        // synchronous input/output of IO-register to device
        // device thread has to clear CEO
        bus.setCEO();
        break;

      case 2:
        MLS = true;
        // call MCR-routine
        // HP9830A + HP9821A uses STC 2 for activation of the beeper
        bus.cardReader.output();
        MLS = false;
      }
      break;

    case 4: // 1011 (instruction CLC)
      // not used in HP9800
      break;

    case 5: // 1010 (instruction SFS)

      // skip next instruction (increment PC by 2 using carry BC), if flag set
      switch(sc) { // select code
      case 0:
        cpu.setBC(STP);  // skip if STOP pressed
        break;

      case 1:
        cpu.setBC(!CEO);  // skip if CEO clear
        break;

      case 2:
        // synchronous input from magnetic card
        bus.cardReader.input();
        cpu.setBC(MFL);
        break;

      case 4:
        cpu.setBC(PEN);  // skip if PEN set
      }
      break;

    case 10: // 0101 (instruction LIx)
      cpu.Rbus = null; // override XTR from CPU
      cpu.ZTR.exec(); // shift 0 to R-bus

    case 14: // 0001 (instruction MIx)
      // cpu.XTR.exec(); // shift A/B-register to R-bus, already exec'd in CPU
      cpu.ITS.exec(); // shift IO-register to S-bus
      cpu.TTX.exec(); // shift T-bus to A/B-register

      switch(sc) {
      case 0:
        // input 8 bits
        Iregister.set8Bitmode(true);  // shift only 8 bits if select code = 0
        shift(8);
        // asynchronous input of IO-register from device
        // device thread has to clear CEO
        bus.setCEO();
        break;

      case 1:
        Iregister.set8Bitmode(false);  // shift 16 bits
        shift(16);
      }
      break;

    case 12: // 0011 (instruction OTx)
      cpu.TTO.exec(); // shift T-bus to IO-register
      // cpu.XTR.exec(); // shift A/B-register to R-bus, already exec'd in CPU
      cpu.ZTS.exec(); // shift 0 to S-bus
      Iregister.set8Bitmode(false);  // shift 16 bits

      switch(sc) {
      case 0:
        Iregister.set8Bitmode(true);  // shift only 8 bits if select code = 0
        shift(8);
        // asynchronous input of IO-register from device
        // device thread has to clear CEO
        bus.setCEO(); // don't use CEO=true, as this doesn't execute the output
        break;

      case 1:
        // load IO-register with 16 bit value
        shift(16);

        // check if device is addressed
        if(getSelectCode() != 0) {
          IOinterface device = bus.selectDevice();
          if(device != null)
            device.input();
          else {
            if(line10_20)
              bus.din |= 0x0f00;
          }
        }

        break;

      case 4:
        /*
         * during output to internal printer the dots are sent 
         * by 2 consecutive output instructions (OTB 1 and OTA 4).
         * The first out value is shifted serially into a 10 bit
         * printer buffer register when the IO register is loaded
         * with the second out value. 
         */

        // output 16 bits to IO register
        printBuffer.shiftEnabled = true;
        shift(16);
        printBuffer.shiftEnabled = false; // disable shifting only for performance reason, in real HW the buffer will be always filled

        PEN = true; // PEN is cleared by printer output. The absence of a printer is detected by PEN staying set.

        // output ioReg.value and printValue to printer
        bus.display.output(printBuffer.getValue());
        break;

      case 8:
        // output 16 bits to display
        shift(16);
        DEN = true;

        dispCounter.restart(); // restart display blank counter
        bus.display.output();
        break;

      case 16:
        // HP9810A uses OTA 16 to output to keyboard LEDs
        shift(16);

        // HP9830A + HP9820A use OTA 16 in interrupt service routine
        // to output device select code and subsequently input of a key code 
        for(IOinterface device = bus.selectDevice(); device != null; device = bus.nextDevice()) {
          device.input();
        }

        KLS = true;
        bus.keyboard.enableInterrupt(false);
        bus.display.output();
        KLS = false;
        bus.keyboard.enableInterrupt(true);
      }
      break;

    case 13: // 0010
      // EOW is implicit created
      break;

    case 15: // 0000
      break;
    }

    // instruction CLF is executed after each I/O operation if Q9 != 0
    if(hcFlag != 0) {
      switch(sc) { // select code
      case 1:
        SIH = false; // clear service inhibit FF
        CEO = false; // clear device ready FF
        Iregister.setValue(Iregister.getValue() & SD_mask); // clear adress bits (select code) in IO-register
        break;

      case 2:
        MFL = false; // clear magn. card FF
        break;

      case 4:
        PEN = false; // clear printer enable FF
        break;

      case 8:
        DEN = false; // clear display enable FF
        break;

      case 16:
        // HP9810A uses CLF 16 to output to keyboard LEDs
        KLS = true;
        bus.display.output();
        KLS = false;
      }
    } 

    QRD = false;
  }

  private void shift(int count)
  {
    int i;

    for(i = 0; i < count; i++) {
      cpu.alu.exec(2); // execute ALU IOR operation, defined in micro-instructions 0512+1207 (IO wait loop)
      // load registers with input value from their shift sources
      Iregister.loadInput();
      cpu.Aregister.loadInput();
      cpu.Bregister.loadInput();
      printBuffer.loadInput(); // buffer for internal printer only used with OTx 4

      // then shift registers if enabled
      Iregister.shift();
      cpu.Aregister.shift();
      cpu.Bregister.shift();
      printBuffer.shift();
    }
  }

  // high-level methods for I/O-devices

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
    return((Iregister.getValue() & CO_mask) >> 12);
  }

  synchronized void setValue(int value)
  {
    // set all 16 bits
    Iregister.setValue(value);
  }

  public synchronized void setStatusData(int value)
  {
    // or register data- and status-input to CO-bits
    Iregister.setValue((Iregister.getValue() & CO_mask) | (value & SD_mask));
  }

  public synchronized void setSelectCode(int value)
  {
    // or register select code to data- and status-bits 
    Iregister.setValue(((value << 12) & CO_mask) | (Iregister.getValue() & SD_mask));
  }

  public synchronized int getValue()
  {
    // return all 16 bits
    return(Iregister.getValue());
  }

  public synchronized int getStatus()
  {
    // return value of register status bits
    return(Iregister.getValue() & SIO_mask);
  }

  public synchronized int getData()
  {
    // return value of register data bits
    return(Iregister.getValue() & DIO_mask);
  }

  public synchronized void setData(int dio)
  {
    // set register data bits 
    dio &= DIO_mask;
    Iregister.setValue((Iregister.getValue() & (CO_mask | SIO_mask)) | dio);
  }

  public synchronized void setStatus(int sio)
  {
    // set register status bits 
    sio &= SIO_mask;
    Iregister.setValue((Iregister.getValue() & (CO_mask | DIO_mask)) | sio);
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

  // I/O-bus is the connector for all I/O-interfaces

  public class Bus
  {
    public Vector<IOinterface> interfaces;
    public Vector<IOdevice> devices;
    IOinterface device;
    public KeyboardInterface keyboard;
    public DisplayInterface display;
    public HP9800MagneticCardReaderInterface cardReader;
    Enumeration<IOinterface> interfaceEnum = null;

    public int din; // value of input bus lines from all devices
    public int dout; // value of output bus lines to all devices

    public Bus(int value)
    {
      din = value;
      // List of all loaded IOinterfaces
      interfaces = new Vector<IOinterface>();
      
      // List of loaded IOdevices
      devices = new Vector<IOdevice>();

      //System.out.println("HP9800 I/O bus loaded.");
    }

    // get device with matching selectCode on bus
    public IOinterface selectDevice()
    {
      IOinterface device;
      int selectCode = getSelectCode();

      if(selectCode != 0) {
        for(interfaceEnum = interfaces.elements(); interfaceEnum.hasMoreElements(); ) {
          device = (IOinterface)interfaceEnum.nextElement();
          if(device.selectCode == selectCode) {
            return(device);
          }
        }
      }

      interfaceEnum = null;
      return(null);  // no matching device found
    }
    
    public void closeAllDevices()
    {
    	// close all open devices one by one
    	while(!devices.isEmpty())
    	{
  			devices.lastElement().close(); // close() also removes the device from the list
    	}
    }

    // get next device with matching selectCode on bus
    public IOinterface nextDevice()
    {
      IOinterface device;
      int selectCode = getSelectCode();

      if(interfaceEnum != null) {
        for( ; interfaceEnum.hasMoreElements(); ) {
          device = (IOinterface)interfaceEnum.nextElement();
          if(device.selectCode == selectCode) {
            return(device);
          }
        }

        interfaceEnum = null;
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

    public synchronized void setData(int dio)
    {
      // set register data bits 
      dio &= DIO_mask;
      din = (din & (CO_mask | SIO_mask)) | dio;
    }

    public synchronized void setStatus(int sio)
    {
      // set register status bits 
      sio &= SIO_mask;
      din = (din & (CO_mask | DIO_mask)) | sio;
    }
  }

  public synchronized void input()
  {
    Iregister.setValue((Iregister.getValue() & CO_mask) | (bus.din & SD_mask));
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

