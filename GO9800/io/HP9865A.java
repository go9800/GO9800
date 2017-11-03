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
     * 05.07.2006 Class created 
     * 12.07.2006 Cassette command handling complete reworked 
     * 25.07.2006 Added sound output
     * 31.07.2006 Set WRITE-PROTECT bit when cassette out
     * 05.08.2006 Removed automatic stopTape at end of tape, set status only 
     * 15.08.2006 Bugfix: Stop tape immediately when REWIND is issued with CASSETTE_OUT
     * 29.08.2006 Changed data directory structure
     * 29.10.2006 Changed sound output to Sound class 
     * 19.11.2006 Added debug output to disassembler frame
     * 15.12.2006 Rel. 0.21 New: window setLocation
     * 22.12.2006 Rel. 0.21 Bugfix: return CLEAR_LEADER after REWIND
     * 22.12.2006 Rel. 0.21 Bugfix: service request only in control mode, also for EOT/BOT.
     * 26.12.2006 Rel. 0.22 Bugfix: discriminate between calculator models for different handling of BOT
     * 27.12.2006 Rel. 0.22 Extended transscription of asc-files
     * 05.01.2007 Rel. 0.23 New: draw tape status indicators drawStatus(), setStatusFrame()
     * 18.01.2007 Rel. 0.23 Bugfix: All images are now correctly loaded from JAR file
     * 04.02.2007 Rel. 0.30 Bugfix: tape status indicator behaves now correctly at BOT 
     * 17.03.2007 Rel. 0.30 Bugfix: broken service request at EOT/BOT fixed
     * 24.03.2007 Rel. 1.00 Bugfix: closeTapeFile() when door is opened
     * 15.05.2007 Rel. 1.01 Extended transscription of asc-files to support complete tape dumps
     * 16.10.2007 Rel. 1.20 Changed frame size control
     * 20.03.2009 Rel. 1.40 Added synchronized(keyboardImage) before visualizing main window to avoid flickering
     * 09.02.2010 Rel. 1.42 Added stopping of HP9865Interface thread before unloading
     * 04.04.2010 Rel. 1.50 Class now inherited from IOdevice and completely reworked
     */

    package io;

    //import emu98.Emulator;
    import java.awt.*;
    import java.awt.event.*;
    import java.io.*;
    import java.util.StringTokenizer;

    public class HP9865A extends IOdevice
    {
      private static final long serialVersionUID = 1L;
      
      // calculator output status bits 
      static final int CONTROL = 0x0800;
      static final int READ = 0x0000;
      static final int WRITE = 0x0400;
      static final int FORWARD = 0x0000;
      static final int REVERSE = 0x0200;
      static final int SLOW = 0x0000;
      static final int FAST = 0x0100;
      static final int STOP = 0x0500;
      static final int CONTINUE = 0x0700;

      // calculator input status bits 
      static final int CASSETTE_OUT = 0x0800;
      static final int CLEAR_LEADER = 0x0400;
      static final int WRITE_PROTECT = 0x0200;
      static final int POWER_ON = 0x0100;
      
      HP9865Interface hp9865Interface;
      private Image hp9865aImage;
      private SoundMedia doorOpenSound, doorCloseSound;
      private SoundMedia motorStartSound, motorStopSound, motorSound;
      private SoundMedia motorSlowSound, motorFastSound, motorRewindSound;
      private RandomAccessFile tapeFile;
      private int tapeCommand = STOP;  // last tape command from calculator
      private int prevCommand = STOP;
      private int driveStatus = POWER_ON | CASSETTE_OUT | WRITE_PROTECT;
      private boolean runFlag = false;
      private boolean rewindFlag = false;
      protected boolean inByteReady = false;  // true if new read byte present 
      protected boolean outByteReady = false;  // true if byte to write present
      protected boolean debug;

      private Frame statusFrame;
      private String statusString = "";
      private int xStatus = 210;
      private int yStatus = 160;

      public HP9865A(IOinterface ioInterface)
      {
        super("HP9865A", ioInterface); // set window title
        hp9865Interface = (HP9865Interface)ioInterface;

        loadSound();
        statusFrame = this;
        
        addKeyListener(this);
        addMouseListener(new mouseListener());

        hp9865aImage = new ImageMedia("media/HP9865A/HP9865A.jpg").getImage();
        setResizable(false);
        setLocation(280, 0);

        setBackground(Color.BLACK);
        setForeground(Color.WHITE);

        setVisible(true);
        // wait until background image has been loaded
        synchronized(hp9865aImage) {
        	while(hp9865aImage.getWidth(this) <= 0) {
        		try
        		{
        			hp9865aImage.wait(100);
        		} catch (InterruptedException e)
        		{ }
        	}
        }

        setState(ICONIFIED);
        setSize(hp9865aImage.getWidth(this) + getInsets().left + getInsets().right, hp9865aImage.getHeight(this) + getInsets().top + getInsets().bottom);
      }
      
      public HP9865A(int selectCode, IOinterface ioInterface)
      {
        super("HP9865A", ioInterface); // set window title
        hp9865Interface = (HP9865Interface)ioInterface;
        loadSound();
        
        /*
        don't visualize window if it is a build-in 9865 (SC=10)
        */
        
        System.out.println("HP9800 Internal Tape Drive, select code " + selectCode + " loaded.");
      }
      
      private void loadSound()
      {
        // generate door sound
        doorOpenSound = new SoundMedia("media/HP9865A/HP9865_DOOR_OPEN.wav", true);
        doorCloseSound = new SoundMedia("media/HP9865A/HP9865_DOOR_CLOSE.wav", true);

        // generate motor sound
        motorStartSound = new SoundMedia("media/HP9865A/HP9865_MOTOR_START.wav", true);
        motorStopSound = new SoundMedia("media/HP9865A/HP9865_MOTOR_STOP.wav", true);
        motorSlowSound = new SoundMedia("media/HP9865A/HP9865_MOTOR_SLOW.wav", true);
        motorFastSound = new SoundMedia("media/HP9865A/HP9865_MOTOR_FAST.wav", true);
        motorRewindSound = new SoundMedia("media/HP9865A/HP9865_MOTOR_REWIND.wav", true);
      }
      
      public boolean openTapeFile()
      {
        closeTapeFile();
        doorOpenSound.start();
        hp9865aImage = new ImageMedia("media/HP9865A/HP9865A_Open.jpg").getImage();
        repaint();

        FileDialog fileDialog = new FileDialog(this, "Load Cassette");
        fileDialog.setBackground(Color.WHITE);
        fileDialog.setVisible(true);
        
        String fileName = fileDialog.getFile();
        String dirName = fileDialog.getDirectory();
        
        if(fileName != null) {
          fileName = dirName + fileName;
          
          String mode = "rw";
          driveStatus = POWER_ON;
          while(true) {
            try{
              tapeFile = new RandomAccessFile(fileName, mode);
              break;
            } catch (FileNotFoundException e) {
              if(mode.equals("r")) {
                System.err.println(e.toString());
                return(false);
              }
              
              mode = "r";
              driveStatus |= WRITE_PROTECT;
            }
          }

          // copy ascii coded input file to binary output file
          // input values are 16bit words 
          if(fileName.endsWith(".asc")) {
            RandomAccessFile outFile;
            String line;
            int value;

            String outFileName = fileName + ".tape";
            try {
              outFile = new RandomAccessFile(outFileName, "rw");
            } catch (FileNotFoundException e) {
              System.err.println(e.toString());
              return(false);
            }

            try {
              outFile.writeShort(0);
              outFile.writeShort(0);
              outFile.writeShort(0);
              outFile.writeShort(0);
              outFile.writeShort(0);
              outFile.writeShort(0);
              outFile.writeShort(0);
              outFile.writeShort(0);
            } catch (IOException e) {
              // nothing  
            }

            try {
              try {
                StringTokenizer tokenline;
                int i;
                int absFileSize = 0, fileSize = 0;
                
                // read multiple files, separated by one empty line
                do {
                  // write control byte
                  outFile.writeShort(0x13c);

                  // write file header
                  int checkSum = 0;
                  for(i = 1; i <= 7; i++) {
                    line = tapeFile.readLine();
                    tokenline = new StringTokenizer(line, " \t");
                    value = 0;
                    if(line != null)
                      value = Integer.parseInt(tokenline.nextToken());
                    checkSum += value;
                    outFile.writeShort(value & 0xff); // output low byte
                    outFile.writeShort(value >> 8); // output high byte
                    
                    switch(i) {
                    case 1:
                      System.out.println(value);
                      break;

                    case 2:
                      fileSize = value;

                    case 4:
                      absFileSize = value;
                    }
                  }

                  // 5 empty header words
                  for(i = 1; i <= 10; i++) {
                    outFile.writeShort(0);
                  }

                  // write check sum
                  outFile.writeShort(checkSum & 0xff);
                  outFile.writeShort(checkSum >> 8);

                  // write file body
                  checkSum = 0;
                  for(i = 0; (line = tapeFile.readLine()) != null && i < fileSize; i++) {
                    tokenline = new StringTokenizer(line, " \t");
                    if(tokenline.hasMoreTokens()) {
                      value = Integer.parseInt(tokenline.nextToken());
                      checkSum += value;
                      outFile.writeShort(value & 0xff); // output low byte
                      outFile.writeShort(value >> 8); // output high byte
                    }
                  }

                  // write check sum
                  outFile.writeShort(checkSum & 0xff);
                  outFile.writeShort(checkSum >> 8);

                  // fill up file
                  for( ; i <= absFileSize; i++) {
                    outFile.writeShort(0);
                    outFile.writeShort(0);
                  }
                  
                  // read next file
                } while(line != null && line.equals(""));
                
                // write control byte for next empty file
                outFile.writeShort(0x13c);
                
              } catch (EOFException e) {
                // nothing  
              }

              tapeFile.close();
              outFile.close();
              //return(false);
              
              try {
                tapeFile = new RandomAccessFile(outFileName, mode);
              } catch (FileNotFoundException e) {
                if(mode.equals("r")) {
                  System.err.println(e.toString());
                  return(false);
                }

                mode = "r";
                driveStatus |= WRITE_PROTECT;
              }
            } catch (IOException e) {
              // nothing  
            }
          }
            
          doorCloseSound.start();
          hp9865aImage = new ImageMedia("media/HP9865A/HP9865A+Cassette.jpg").getImage();
          repaint();

          hp9865Interface.status = driveStatus;
          return(true);
        } else {
          doorCloseSound.start();
        }
        
        closeTapeFile();

        return(false);
      }
      
      public boolean closeTapeFile()
      {
        if(tapeFile != null) {
          try {
            tapeFile.close();
            tapeFile = null;
          } catch (IOException e) { }
        }
        
        stopTape();
        hp9865Interface.status = driveStatus = POWER_ON | CASSETTE_OUT | WRITE_PROTECT;
        hp9865aImage = new ImageMedia("media/HP9865A/HP9865A.jpg").getImage();
        repaint();

        return(false);
      }
      
      class mouseListener extends MouseAdapter
      {
        public void mousePressed(MouseEvent event)
        {
          int x = event.getX() - getInsets().left;
          int y = event.getY() - getInsets().top;
          
          if(x >= 380 && x <= 410) {
            if(y >= 250 && y <= 300)
              openTapeFile();
          }
            
          if(x >= 100 && x <= 350) {
            if(y >= 70 && y <= 280)
              closeTapeFile();
          }
          
          if(x >= 390 && x <= 405) {
            if(y >= 80 && y <= 95)
              output(READ|REVERSE|FAST);
          }
        }

        public void mouseReleased(MouseEvent event)
        {
        }
      }
      
      public void keyPressed(KeyEvent event)
      {
        int keyCode = event.getKeyCode();
        
        switch(keyCode) {
        case KeyEvent.VK_ENTER:
          openTapeFile();
          break;
          
        case KeyEvent.VK_PAUSE:
          closeTapeFile();
          
        default:
          return;
        }
        
        repaint();
      }

      public void keyReleased(KeyEvent event)
      {
      }

      public void keyTyped(KeyEvent event)
      {
      }
      
      public void paint(Graphics g)
      {
        int x = getInsets().left;
        int y = getInsets().top;

        g.drawImage(hp9865aImage, x, y, hp9865aImage.getWidth(this), hp9865aImage.getHeight(this), this);
      }

      void drawStatus()
      {
        if(tapeCommand == prevCommand)
          return;

        Graphics g = statusFrame.getGraphics();
        Font font = new Font("Monospaced", Font.BOLD, 20);
        g.setFont(font);

        // overpaint previous status
        g.setColor(new Color(67, 66, 55));
        g.drawString(statusString, xStatus, yStatus);
        
        if(tapeCommand == STOP)
          return;

        if((tapeCommand & CONTROL ) != 0)
          // search for control word
          g.setColor(Color.YELLOW);
        else {
          if((tapeCommand & WRITE ) != 0)  
            g.setColor(Color.RED);
          else
            g.setColor(Color.GREEN);
        }
        
        if((tapeCommand & FAST) != 0) {
          if((tapeCommand & REVERSE) != 0)
            statusString = "<<";
          else
            statusString = ">>";
        } else {
          if((tapeCommand & REVERSE) != 0)
            statusString = "<";
          else 
            statusString = ">";
        }
        
        g.drawString(statusString, xStatus, yStatus);
      }
      
      public void setStatusFrame(Frame frame, int x, int y)
      {
        statusFrame = frame;
        xStatus = x;
        yStatus = y;
      }
      
      /*
       * asynchronous execution of tape command
       */
      public int executeCommand()
      {
        long pos;
        int ioByte;

        if(!runFlag)
          // do nothing
          return(driveStatus);

        if(tapeFile == null) {
          driveStatus = CASSETTE_OUT | WRITE_PROTECT | POWER_ON;
          return(driveStatus);
        }

        debug = ioInterface.mainframe.console.getDebugMode();

        // write mode?
        if((tapeCommand & WRITE) != 0) {
          // anything to do?
          if(!outByteReady)
            return(driveStatus);  // no
        } else {
          // previous read input fetched?
          if(inByteReady && !rewindFlag)
            return(driveStatus);  // no
        }

        try {
          pos = tapeFile.getFilePointer();

          if((tapeCommand & REVERSE) != 0) {
            pos -= 2;

            if(pos < 0) {
              if(debug)
              	ioInterface.mainframe.console.append("HP9865A Begin of tape\n");

              // generate SRQ only in control mode
              if((tapeCommand & CONTROL) != 0) {
                hp9865Interface.requestInterrupt(); 
              }
              
              stopTape();
              
              // return CLEAR_LEADER only once
              driveStatus |= CLEAR_LEADER;
              return(driveStatus);
            } else
            
            tapeFile.seek(pos);
          }

          if((tapeCommand & WRITE) != 0) {
            ioByte = hp9865Interface.tapeValue;

            // set control bit
            if((tapeCommand & CONTROL) != 0)
              ioByte |= 0x100;

            tapeFile.writeShort(ioByte);
            outByteReady = false;
            // clear CEO when byte is written
            ioInterface.ioUnit.CEO = false;
            
            if(debug)
            	ioInterface.mainframe.console.append("HP9865A write " + pos + ": " + Integer.toHexString(ioByte) + "\n");
            
          } else { // READ

            ioByte = tapeFile.readShort();
            if(debug)
            	ioInterface.mainframe.console.append("HP9865A  read " + pos + ": " + Integer.toHexString(ioByte) + "\n");

            // in control mode read until control byte found
            if(((tapeCommand & CONTROL) != 0) && ((ioByte & 0x100) == 0)) {
              inByteReady = false;
            } else {
              hp9865Interface.tapeValue = ioByte & 0xff;
              inByteReady = true;
              // generate SRQ only in control mode and when control char found
              if(((tapeCommand & CONTROL) != 0) && ((ioByte & 0x100) != 0)) {
                hp9865Interface.requestInterrupt();
              }
            }
          }

          if((tapeCommand & REVERSE) != 0) {
            tapeFile.seek(pos);
          }

          return(driveStatus);

        } catch (EOFException e) {
          // at EOF set status to end-of-tape, tape will be stopped by mainframe command
          if(debug)
          	ioInterface.mainframe.console.append("HP9865A End of tape\n");
          
          // generate SRQ only in control mode
          if((tapeCommand & CONTROL) != 0) {
            hp9865Interface.requestInterrupt();
          }
          
          // clear CEO required
          ioInterface.mainframe.ioUnit.CEO = false;

          // return CLEAR_LEADER only once
          driveStatus |= CLEAR_LEADER;
          return(driveStatus);
          
        } catch (IOException e) {
          // read error, close file and "eject" tape
          System.err.println(e.toString());
        }

        closeTapeFile();

        driveStatus = CASSETTE_OUT | WRITE_PROTECT | POWER_ON;
        return(driveStatus);
      }

      public void stopTape()
      {
        // stop motor and set timer to idle value
        if(motorSound != null) {
          motorSound.stop();
          motorSound = null;
          motorStopSound.start();
        }
        
        debug = ioInterface.mainframe.console.getDebugMode();
        if(debug)
        	ioInterface.mainframe.console.append("HP9865A Stop\n");
        
        hp9865Interface.timerValue = hp9865Interface.IDLE_TIMER;
        hp9865Interface.tapeValue = 0; // input 0
        prevCommand = tapeCommand;
        tapeCommand = STOP;
        runFlag = rewindFlag = inByteReady = outByteReady = false;

        // draw status indicator string 
        drawStatus();
      }
      
      
      public void output(int status)
      {
        debug = ioInterface.mainframe.console.getDebugMode();
        if(debug)
        	ioInterface.mainframe.console.append("HP9865A Commmand: " + Integer.toHexString(status >> 8) + "\n");
        
        rewindFlag = false;
        driveStatus &= ~CLEAR_LEADER;

        switch(status & ~CONTROL) {
        case STOP:
          stopTape();
          break;
          
        case CONTINUE:
          // continue with last command
          break;
          
        default:
          prevCommand = tapeCommand;
          tapeCommand = status;
          
          if(!runFlag) {
            motorStartSound.start();
            runFlag = true;
          }
        
          if(((tapeCommand & WRITE ) != 0) && ((prevCommand & WRITE) == 0)) {  
            inByteReady = false;
            outByteReady = false;
          }
          
          // set new time value and rewindFlag
          if((tapeCommand & FAST) != 0) {
            hp9865Interface.timerValue = hp9865Interface.FAST_TIMER;
            
            if((tapeCommand & REVERSE) != 0) {
              rewindFlag = true;
              
              if(motorSound != motorRewindSound) {
                if(motorSound != null)
                  motorSound.stop();
                motorSound = motorRewindSound;
                motorSound.loop();
              }
            } else {
              if(motorSound != motorFastSound) {
                if(motorSound != null)
                  motorSound.stop();
                motorSound = motorFastSound;
                motorSound.loop();
              }
            }
          } else {
            hp9865Interface.timerValue = hp9865Interface.SLOW_TIMER;
            
            if(motorSound != motorSlowSound) {
              if(motorSound != null)
                motorSound.stop();
              motorSound = motorSlowSound;
              motorSound.loop();
            }
          }

          if((driveStatus & CASSETTE_OUT) != 0)
            stopTape();
          else
            // draw status indicator string 
            drawStatus();
        }
      }
      
      public void close()
      {
      	// stop all sound and image threads
        doorOpenSound.close();
        doorCloseSound.close();
        motorStartSound.close();
        motorStopSound.close();
        motorSlowSound.close();
        motorFastSound.close();
        motorRewindSound.close();
        if(hp9865aImage != null) hp9865aImage.flush();

      	super.close();
      }
    }
