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
 * 04.10.2006 Class created
 * 22.10.2006 Changed to MagneticCardReaderInterface and thread logic
 * 24.10.2006 Finished read/write logic and timing
 * 28.10.2006 Logic completely reworked for loading of card and synchronous reading
 * 26.11.2006 Changed sound output to Sound class
 * 10.12.2006 Rel. 0.21 Bugfix: Don't stop reading after fixed card time (for cards >900 bytes)
 *            Instead call of closeCardFile() the card-out sensors are set.
 *            The closeCardFile() is now called at EOF. 
 * 09.04.2007 Rel. 1.00 removed dispWindow
*/

package io;

import java.awt.Color;
import java.awt.FileDialog;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.StringTokenizer;

public class HP9800MagneticCardReaderInterface extends IOinterface
{
  // MCR input bits
  static final int CARD_DATA = 0x07;  // 3 data channels
  static final int CARD_END_RECORD = 0x08; // =1 when sensor is blocked
  static final int CARD_IN = 0x10;
  static final int CARD_UNPROTECTED = 0x20;
  static final int CARD_END_LOAD = 0x40;

  // MCR output bits
  static final int STROBE_DATA = 0x08;  // clock channel
  static final int INHIBIT_SOURCE = 0x10; // =1 when reading from MCR 
  static final int MOTOR_CONTROL = 0x20; // =1 when motor is on

  // values for thread timer
  static int WAIT_BYTE = 7;     // timer value for reading one byte
  static int WAIT_INSERT = 200; // time value for insert card (matches length of motor start sound)
  static final int WAIT_CARD = 3000;  // time value for one complete card
  static final int WAIT_IDLE = 10000; // nothing to do

  // asynchronous reading
  static final boolean asyncMode = true;
  
  public HP9800Mainframe mainframe;
  int outBuffer = 0;
  int dataStrobe = 0;
  int sensors = 0;
  
  RandomAccessFile cardFile;
  SoundMedia motorSound, startSound, loopSound, cardSound;

  int timerValue = WAIT_IDLE;  // value for idle loop
  boolean readMode = false;
  boolean debug = false;

  public HP9800MagneticCardReaderInterface()
  {
    
  }
  
  public HP9800MagneticCardReaderInterface(HP9800Mainframe mainframe)
  {
    super(0, "MCR");

    // store references and start thread
    this.mainframe = mainframe;
    WAIT_BYTE = ioUnit.time_3ms;
    WAIT_INSERT = 2 * ioUnit.time_100ms;

    // generate motor sound
    startSound = new SoundMedia("media/HP9800/HP9800_CARD_START.wav");
    loopSound = new SoundMedia("media/HP9800/HP9800_CARD_MOTOR.wav");
    cardSound = new SoundMedia("media/HP9800/HP9800_CARD_IN.wav");

    System.out.println("HP9800 Magnetic Card Reader loaded.");
  }
  
  public void run()
  {
    debug = IOinterface.ioUnit.console.getDebugMode();
    
    while(true) {
      // sleep until interrupted by IO-instruction
      try {
        Thread.sleep(timerValue);
      } catch(InterruptedException e) {
        // restart timer with (changed) timerValue
        continue;
      }

      // check if card is in progress
      synchronized(ioUnit) {
        switch(sensors) {
        case 0:
          break;

        case CARD_END_RECORD:
          if(cardFile == null) {
            motorSound = loopSound;
            motorSound.loop();

            if(openCardFile()) {
              sensors |= CARD_END_LOAD | CARD_IN;
              ioUnit.bus.din = sensors;
              motorSound.stop();
              motorSound = cardSound;
              motorSound.loop();

              readMode = (outBuffer & INHIBIT_SOURCE) != 0;
              if(readMode) {
                // set timer for reading of card bytes
                if(asyncMode)
                  timerValue = WAIT_BYTE;
                else
                  timerValue = WAIT_CARD;
              } else {
                // set time value for max. card "length" for writing
                timerValue = WAIT_CARD;
              }
            } else {
              sensors = 0;
              ioUnit.bus.din = sensors;
            }
          }
          break;

        case CARD_END_RECORD | CARD_END_LOAD | CARD_IN:
        case CARD_END_RECORD | CARD_END_LOAD | CARD_IN | CARD_UNPROTECTED:
          if(readMode && asyncMode) {
            // read from magn. card
            try {
              // read byte from card and put on bus together with sensor status
              ioUnit.bus.din = cardFile.readByte() | sensors;

              // set card reader flag
              ioUnit.MFL = true;

              if(debug)
                ioUnit.console.append("HP9800 MC read async " + Integer.toHexString(ioUnit.bus.din & CARD_DATA) + "\n");
            } catch (IOException e) {
              // when card reading is complete stop read mode
              readMode = false;
            }
          } else {
            // card is out of reader
            ioUnit.MFL = false;
            sensors = 0;
            ioUnit.bus.din = sensors;
            // don't stop reading here
            //closeCardFile();
          }
        }
      }
    }
  }
  
  boolean openCardFile()
  {
    FileDialog fileDialog = new FileDialog(mainframe, "Load Magnetic Card");
    fileDialog.setBackground(Color.WHITE);
    fileDialog.setVisible(true);

    String fileName = fileDialog.getFile();
    String dirName = fileDialog.getDirectory();

    if(fileName == null) {
      return(false);
    }

    fileName = dirName + fileName;
    String mode = "rw";
    sensors |= CARD_UNPROTECTED;  // assume card is unprotected

    while(true) {
      try{
        cardFile = new RandomAccessFile(fileName, mode);
        break;
      } catch (FileNotFoundException e) {
        if(mode.equals("r")) {
          System.err.println(e.toString());
          sensors = 0;
          return(false);
        }

        sensors &= ~CARD_UNPROTECTED;  // card is protected
        mode = "r";
      }
    }

    // copy ascii coded input file to binary output file
    // input values are 3bit words 
    if(fileName.endsWith(".asc")) {
      RandomAccessFile outFile;
      String line;
      int value;

      String outFileName = fileName + ".mcard";
      try {
        outFile = new RandomAccessFile(outFileName, "rw");
      } catch (FileNotFoundException e) {
        System.err.println(e.toString());
        return(false);
      }

      try {
        try {
          StringTokenizer tokenline;

          // write file body
          while((line = cardFile.readLine()) != null) {
            tokenline = new StringTokenizer(line, " \t");
            if(tokenline.hasMoreTokens()) {
              value = Integer.parseInt(tokenline.nextToken());
              outFile.writeByte(value & 0x7);
            }
          }
        } catch (EOFException e) {
          // nothing  
        }

        cardFile.close();
        outFile.close();
        
        try{
          cardFile = new RandomAccessFile(outFileName, mode);
        } catch (FileNotFoundException e) {
          if(mode.equals("r")) {
            System.err.println(e.toString());
            return(false);
          }

          mode = "r";
        }
      } catch (IOException e) {
        // nothing  
      }
    }
    
    // copy hex coded input file to binary output file
    // input values are 6bit words 
    if(fileName.endsWith(".hex")) {
      RandomAccessFile outFile;
      String line;
      int value;

      String outFileName = fileName + ".mcard";
      try {
        outFile = new RandomAccessFile(outFileName, "rw");
      } catch (FileNotFoundException e) {
        System.err.println(e.toString());
        return(false);
      }

      try {
        try {
          StringTokenizer tokenline;

          // write file body
          while((line = cardFile.readLine()) != null) {
            tokenline = new StringTokenizer(line, " \t");
            if(tokenline.hasMoreTokens()) {
              value = Integer.parseInt(tokenline.nextToken(), 16);
              outFile.writeByte(value & 0x7);
              outFile.writeByte((value >> 3) & 0x7);
            }
          }
        } catch (EOFException e) {
          // nothing  
        }

        cardFile.close();
        outFile.close();
        
        try{
          cardFile = new RandomAccessFile(outFileName, mode);
        } catch (FileNotFoundException e) {
          if(mode.equals("r")) {
            System.err.println(e.toString());
            return(false);
          }

          mode = "r";
        }
      } catch (IOException e) {
        // nothing  
      }
    }

    return(true);
  }
  
  boolean closeCardFile()
  {
    if(cardFile != null) {
      try {
        cardFile.close();
        cardFile = null;
      } catch (IOException e) { }
    } else {
      // stop empty card reader
      motorSound.stop();
    }
    
    synchronized(ioUnit) {
      readMode = false;
      ioUnit.MFL = false;
      sensors = 0;
      ioUnit.bus.din = sensors;
      timerValue = WAIT_IDLE;
    }
    
    return(false);
  }

  public boolean output()
  {
    debug = IOinterface.ioUnit.console.getDebugMode();
    
    synchronized(ioUnit) {
      int value = ioUnit.getValue();

      // store output data into buffer FFs
      if(ioUnit.MLS) {
        outBuffer = value;

        if((outBuffer & MOTOR_CONTROL) != 0) {
          switch(sensors) {
          case 0:
            startSound.start();

            // card inserted and first sensor blocked
            sensors = CARD_END_RECORD;
            ioUnit.bus.din = sensors;
            timerValue = WAIT_INSERT;
            devThread.interrupt();
            break;

          case CARD_END_RECORD | CARD_END_LOAD | CARD_IN | CARD_UNPROTECTED:
            try {
              // write to magn. card
              cardFile.writeByte((~outBuffer) & CARD_DATA);

              try {
                Thread.sleep(0);
              } catch (InterruptedException e) {
              }

              if(debug)
                ioUnit.console.append("HP9800 MC write " + Integer.toHexString((~outBuffer) & CARD_DATA) + "\n");
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        }

        if(debug)
          ioUnit.console.append("HP9800 MLS " + Integer.toHexString(value) + "\n");
        // reset MLS pulse
        ioUnit.MLS = false;
      }

      // store STROBE CHANNEL DATA into buffer FF 
      if(ioUnit.MCR) {
        // output value alternates between 0x5555 and 0xAAAA
        dataStrobe = value & STROBE_DATA;
        if(debug)
          ioUnit.console.append("HP9800 MCR "  + Integer.toHexString(value) + "\n");
      }

      // if MOTOR_CONTROL is on, put sensor bits on IO-bus
      // these are later read by get-sensor ASM routine
      if((outBuffer & MOTOR_CONTROL) != 0) {
        ioUnit.bus.din = sensors;
      } else {
        closeCardFile();
      }

      return(false);
    }
  }
  
  public boolean input()
  {
    debug = IOinterface.ioUnit.console.getDebugMode();
    
    if(!asyncMode && readMode && cardFile != null) {
      // read from magn. card
      try {
        synchronized(ioUnit) {
          // read byte from card and put on bus together with sensor status
          ioUnit.bus.din = cardFile.readByte() | sensors;
          
          try {
            Thread.sleep(2);
          } catch(InterruptedException e) {
          }

          // set card reader flag
          ioUnit.MFL = true;
        }

        if(debug)
          ioUnit.console.append("HP9800 MC read " + Integer.toHexString(ioUnit.bus.din & CARD_DATA) + "\n");
      } catch (IOException e) {
        // when card reading is complete stop read mode
        readMode = false;
      }
    }

    return(true);
  }
}
