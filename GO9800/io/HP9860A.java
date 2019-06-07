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
 * 02.01.2018 Rel. 2.10 Added use of class DeviceWindow
 * 25.05.2019 Rel. 2.30 Changed to bigger, resizable images. Card image overlay.
 */

package io;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.*;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;


public class HP9860A extends IOdevice implements ActionListener
{
  private static final long serialVersionUID = 1L;

  // HP9860A reals size in inches
  double REAL_W = 5.31, REAL_H = 11.19;
  int CARD_W = 174, CARD_H = 336, CARD_X = 44, CARD_Y = 0;
  
  HP11200A hp11200a;
  ImageMedia cardImageMedia;
  Image hp9860Image, cardImage;
  SoundMedia cardReaderSound;
  DataInputStream inFile;
  String cardType;
  public boolean backgroundImage = false;
  Boolean loading = false;

  static final int WAIT_CARD = 300;  // wait for 1st character on card
  static final int WAIT_IDLE = 5000; // nothing to do

  public HP9860A(IOinterface ioInterface)
  {
    super("HP9860A", ioInterface);
    hp11200a = (HP11200A)ioInterface;

    NORMAL_W = 266;
    NORMAL_H = 560;

    // create motor sound
    cardReaderSound = new SoundMedia("media/HP9860A/HP9860_Card.wav", ioInterface.mainframe.soundController, false);
    deviceImageMedia = new ImageMedia("media/HP9860A/HP9860A.png", ioInterface.mainframe.imageController);
    cardImageMedia = new ImageMedia("media/HP9860A/HP9860A_Card.png", ioInterface.mainframe.imageController);
  }
  
  public void setDeviceWindow(JFrame window)
  {
  	super.setDeviceWindow(window);
  	
  	if(createWindow) {
  		deviceWindow.setResizable(true);
  		deviceWindow.setLocation(740, 0);
  		
      menuBar.removeAll();  // remove dummy menu
      
      JMenu runMenu = new JMenu("Run");
      runMenu.add(makeMenuItem("Exit"));
      menuBar.add(runMenu);

      JMenu viewMenu = new JMenu("View");
      viewMenu.add(makeMenuItem("Normal Size", KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK));
      viewMenu.add(makeMenuItem("Real Size", KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK));
      viewMenu.add(makeMenuItem("Hide Menu", KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK));
      menuBar.add(viewMenu);

      JMenu mediaMenu = new JMenu("Media");
      mediaMenu.add(makeMenuItem("Load Card", KeyEvent.VK_ENTER, 0));
      mediaMenu.add(makeMenuItem("Stop Loading", KeyEvent.VK_DELETE, 0));
      menuBar.add(mediaMenu);
  	}

		// set size of surrounding JFrame only after loading all window components 
		addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				setScale(true, false);
			}
		});

		setNormalSize();
  }

  public JMenuItem makeMenuItem(String menuText)
  {
  	return(makeMenuItem(menuText, 0, 0, null));
  }
  
  public JMenuItem makeMenuItem(String menuText, int key, int accelerator)
  {
  	return(makeMenuItem(menuText, key, accelerator, null));
  }
  
  public JMenuItem makeMenuItem(String menuText, int key, int accelerator, String cmd)
  {
  	JMenuItem menuItem = new JMenuItem(menuText);
    menuItem.addActionListener(this);
    if(cmd != null)
    	menuItem.setActionCommand(cmd);

    if(key != 0) {
    	KeyStroke ks = KeyStroke.getKeyStroke(key, accelerator);
    	menuItem.setAccelerator(ks);
    }
    
    return(menuItem);
  }

  public void actionPerformed(ActionEvent event)
  {
    String cmd = event.getActionCommand();

    if(cmd.startsWith("Exit")) {
      close();
    } else if(cmd.startsWith("Normal Size")) {
      setNormalSize();
    } else if(cmd.startsWith("Real Size")) {
      setRealSize(REAL_W, REAL_H);
    } else if(cmd.startsWith("Hide Menu")) {
      if(extDeviceWindow != null)
        extDeviceWindow.setFrameSize(!menuBar.isVisible());
    } else if(cmd.startsWith("Load Card")) {
      hp11200a.reading = openInputFile();
    } else if(cmd.startsWith("Stop Loading")) {
      closeInputFile();
    }

    repaint();
  }

  public void mousePressed(MouseEvent event)
  {
  	// get unscaled coordinates of mouse position
    int x = (int)((event.getX() - getInsets().left) / widthScale); 
    int y = (int)((event.getY() - getInsets().top) / heightScale);

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

    event.consume(); // do not pass key event to other levels (e.g. menuBar)

    switch(keyCode) {
      case 'M':
        if(event.isControlDown())
          if(extDeviceWindow != null)
            extDeviceWindow.setFrameSize(!menuBar.isVisible());
        break;

      case 'N':
        if(event.isControlDown())
          setNormalSize();
        break;

      case 'R':
        if(event.isControlDown())
          setRealSize(REAL_W, REAL_H);
        break;
        
      case KeyEvent.VK_ENTER:
      hp11200a.reading = openInputFile();
      break;

    case KeyEvent.VK_DELETE:
      closeInputFile();
    }
  }

  boolean openInputFile()
  {
    FileDialog fileDialog = new FileDialog(deviceWindow, "Load Card");
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
   		loading = true;
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
      hp11200a.timerValue = 60; //ioInterface.ioUnit.time_30ms;

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

    loading = false;
    repaint();
 		cardReaderSound.loop(0);
  	hp11200a.timerValue = WAIT_IDLE;

    return(false);
  }

  public void paint(Graphics g)
  {
  	int x = 0, y = 0; // positioning is done by g2d.translate()
  	
    super.paint(g);
    setScale(true, false);

  	// scale device image to normal size
  	hp9860Image = deviceImageMedia.getScaledImage((int)(NORMAL_W * widthScale), (int)(NORMAL_H * heightScale));
  	backgroundImage = g2d.drawImage(hp9860Image, x, y, NORMAL_W, NORMAL_H, this);
  	
  	if(!backgroundImage)  // don't draw modules and templates before keyboard is ready
  		return;

    if(loading) {
    	// draw card
    	cardImage = cardImageMedia.getScaledImage((int)(CARD_W * widthScale), (int)(CARD_H * heightScale));
    	g2d.drawImage(cardImage, x + CARD_X, y + CARD_Y, CARD_W, CARD_H, this);
    }
  }
  
  public void close()
  {
  	// stop all sound and image threads
 		cardReaderSound.close();

 		if(cardImageMedia != null)
 			cardImageMedia.close();

  	super.close();
  }
}
