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
 * 09.06.2006 Class created 
 * 10.06.2006 File reader added
 * 14.06.2006 KeyListener added, mouse click areas added
 * 29.08.2006 Changed data directory structure
 * 24.08.2006 Added handling of NumberFormatException for HP9810A
 * 29.10.2006 Changed sound output to Sound class 
 * 15.12.2006 Rel. 0.21 New: window setLocation
 * 03.01.2007 Rel. 0.22 New: Added dummy key code 200 for SKIP
 * 03.01.2007 Rel. 0.22 New: Added different timer values
 * 16.10.2007 Rel. 1.20 Changed frame size control
 * 09.02.2010 Rel. 1.42 Added stopping of HP11200 thread before unloading
 * 03.04.2010 Rel. 1.50 Class now inherited from IOdevice and completely reworked
 */

package io;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

public class HP9860A extends IOdevice
{
  private static final long serialVersionUID = 1L;

  HP11200A hp11200a;
  Image hp9860aImage;
  SoundMedia cardReaderSound;
  DataInputStream inFile;
  String cardType;

  static final int WAIT_CARD = 300;  // wait for 1st character on card
  static final int WAIT_IDLE = 5000; // nothing to do

  public HP9860A(IOinterface ioInterface)
  {
    super("HP9860A", ioInterface);
    hp11200a = (HP11200A)ioInterface;

    // generate motor sound
    cardReaderSound = new SoundMedia("media/HP9860A/HP9860_CARD.wav", false);
    hp9860aImage = new ImageMedia("media/HP9860A/HP9860A.jpg").getImage();
    setResizable(false);
    setLocation(740,0);
    setBackground(Color.BLACK);

    setVisible(true);
    // wait until background image has been loaded
    synchronized(hp9860aImage) {
    	while(hp9860aImage.getWidth(this) <= 0) {
    		try
    		{
    			hp9860aImage.wait(100);
    		} catch (InterruptedException e)
    		{ }
    	}
    }
    
    setState(ICONIFIED);
    setSize(hp9860aImage.getWidth(this) + getInsets().left + getInsets().right, hp9860aImage.getHeight(this) + getInsets().top + getInsets().bottom);
  }

  boolean openInputFile()
  {
    FileDialog fileDialog = new FileDialog(this, "Load Card");
    fileDialog.setBackground(Color.WHITE);
    fileDialog.setVisible(true);

    String fileName = fileDialog.getFile();
    String dirName = fileDialog.getDirectory();

    if(fileName != null) {
      fileName = dirName + fileName;

      try{
        inFile = new DataInputStream(
            new BufferedInputStream(
                new FileInputStream(fileName)));

      } catch (FileNotFoundException e) {
        System.err.println(e.toString());
        return(false);
      }

      try {
        cardType = inFile.readLine();
      } catch (IOException e) {
        // read error
        System.err.println(e.toString());
      }

      cardReaderSound.loop();
      hp9860aImage = new ImageMedia("media/HP9860A/HP9860A+Card.jpg").getImage();
      repaint();
      // restart timer
      hp11200a.timerValue = WAIT_CARD;
      hp11200a.devThread.interrupt();

      return(true);
    }

    return(false);
  }

  boolean readInputFile()
  {
    int inByte = -1;

    try {
      if(cardType.equals("OCT")) {
        // read one octal value per line
        inByte = Integer.parseInt(inFile.readLine(), 8);
      }

      // set timer value for next character
      hp11200a.timerValue = ioInterface.ioUnit.time_30ms;

      if(cardType.equals("BAS")) {
        inByte = inFile.readUnsignedByte();

        // convert to upper case
        // (not all lower case characters supported by 9830/9860)
        if((inByte >= 'a') && (inByte <= 'z'))
          inByte &= 0xDF;

        switch(inByte) {
        case 0x0A:
          // pause for simulated new card
          hp11200a.timerValue = WAIT_CARD;
          inByte = 0162; break;  // END OF LINE
          //inByte = 013; break;  // EXECUTE

        case 0x0D:
          inByte = -1; break;  // ignore return
        }
      }        

      // dummy code 200 for skip
      if(inByte > 0177) {
        if(inByte == 0377) {
          // SKIP 177 ends reading
          closeInputFile();
          return(false);
        } else {
          // other SKIP codes pauses for simulated new card
          inByte = -1;
          hp11200a.timerValue = WAIT_CARD;
        }
      }

      hp11200a.keyCode = inByte;
      return(true);
    } catch (NumberFormatException e) {
      // nothing
    } catch (EOFException e) {
      // nothing
    } catch (IOException e) {
      // read error
      System.err.println(e.toString());
    }

    closeInputFile();

    return(false);
  }

  boolean closeInputFile()
  {
    if(hp11200a.reading) {
      hp11200a.reading = false;

      synchronized(ioInterface.ioUnit) {
        // cancel pending service request
        ioInterface.ioUnit.SSI &= ~(1 << 11);
        ioInterface.ioUnit.SIH = ioInterface.ioUnit.SSF = false;
      }

      try {
        inFile.close();
      } catch (IOException e) { }
    }

    hp9860aImage = new ImageMedia("media/HP9860A/HP9860A.jpg").getImage();
    repaint();
    cardReaderSound.loop(0);
    hp11200a.timerValue = WAIT_IDLE;

    return(false);
  }

  public void mousePressed(MouseEvent event)
  {
    int x = event.getX() - getInsets().left;
    int y = event.getY() - getInsets().top;

    if(x >= 55 && x <= 210) {
      if(y >= 500)
        hp11200a.reading = openInputFile();

      if(y <= 330)
        closeInputFile();
    }
  }

  public void mouseReleased(MouseEvent event)
  {
  }

  public void keyPressed(KeyEvent event)
  {
    int keyCode = event.getKeyCode();

    switch(keyCode) {
    case KeyEvent.VK_ENTER:
      hp11200a.reading = openInputFile();
      break;

    case KeyEvent.VK_PAUSE:
      closeInputFile();
    }
  }

  public void paint(Graphics g)
  {
    int x = getInsets().left;
    int y = getInsets().top;

    g.drawImage(hp9860aImage, x, y, hp9860aImage.getWidth(this), hp9860aImage.getHeight(this), this);
  }
  
  public void close()
  {
  	// stop all sound and image threads
  	cardReaderSound.close();
  	hp9860aImage.flush();

  	super.close();
  }
}
