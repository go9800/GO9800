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
 * 26.05.2007 Class created 
 * 05.06.2007 Added drive lights  
 * 16.06.2007 Added disc status indicator and load switch
 * 16.10.2007 Rel. 1.20 Changed frame size control
 * 22.01.2009 Rel. 1.40 Added high-speed mode and keyPressed()
 * 20.03.2009 Rel. 1.40 Added synchronized(keyboardImage) before visualizing main window to avoid flickering
 * 28.10.2017 Rel. 2.10 Added new linking between Mainframe and other components
 * 02.01.2018 Rel. 2.10 Added use of class DeviceWindow
 * 04.06.2019 Rel. 2.30 Changed to support of HP9867B only 
 */

package io;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

public class HP9867B extends IOdevice implements ActionListener
{
  private static final long serialVersionUID = 1L;

  // HP9867A reals size in inches
  double REAL_W = 19.0, REAL_H = 10.35;

  public int LOADSW_X = 701; // mid position of LOAD switch
  public int LOADSW_Y = 236;

  public int SWITCH_LOAD_X = 685;
  public int SWITCH_LOAD_Y = 221;
  public int SWITCH_LOAD_W = 31;
  public int SWITCH_LOAD_H = 31;
  public int DRIVE_POWER_X = 594;
  public int DRIVE_POWER_Y = 213;
  public int DRIVE_POWER_W = 43;
  public int DRIVE_POWER_H = 46;
  public int DRIVE_READY_X = 543;
  public int DRIVE_READY_Y = 214;
  public int DRIVE_READY_W = 43;
  public int DRIVE_READY_H = 46;
  public int DOOR_UNLOCKED_X = 492;
  public int DOOR_UNLOCKED_Y = 213;
  public int DOOR_UNLOCKED_W = 43;
  public int DOOR_UNLOCKED_H = 46;
  public int PROTECT_UD_X = 441;
  public int PROTECT_UD_Y = 214;
  public int PROTECT_UD_W = 43;
  public int PROTECT_UD_H = 23;
  public int PROTECT_LD_X = 440;
  public int PROTECT_LD_Y = 237;
  public int PROTECT_LD_W = 44;
  public int PROTECT_LD_H = 22;

  public int STATUS_X = 300;
  public int STATUS_Y = 245;

  ImageMedia drivePowerImageMedia, doorUnlockedImageMedia, driveReadyImageMedia, protectLdImageMedia, protectUdImageMedia, loadSwitchImageMedia;
  Image hp9867Image, drivePowerImage, doorUnlockedImage, driveReadyImage, protectLdImage, protectUdImage, loadSwitchImage;
  
  HP9800Mainframe mainframe;
  public Disk[] disks;
  String statusString = "";
  private String windowTitle;
  int drive;
  int accessMode = -1;
  boolean powerOn = true;
  boolean doorUnlocked = false;
  boolean driveReady = true;
  boolean driveProtectL = false;
  boolean driveProtectU = false;
  private boolean highSpeed;
  boolean backgroundImage = false;
  static int time_10ms = 10;

  public HP9867B(int drive, IOinterface ioInterface)
  {
  	super("HP9867B Drive " + drive, null);  // HP9867B has no own IOinterface
  	this.drive = drive;
  	this.mainframe = ioInterface.mainframe;
  	windowTitle = "HP9867B Unit " + Integer.toString(2 * drive) + "+" + Integer.toString(2 * drive + 1);
  	
  	disks = new Disk[2];
  	
  	for(int i = 0; i < 2; i++) {
  		disks[i] = new Disk(2 * drive + i); // unit numbers are 0+1 for drive 0 and 2+3 for drive 1
  	}

  	addKeyListener(this);

  	deviceImageMedia = new ImageMedia("media/HP9880A/HP9867B.png", mainframe.imageController);
  	drivePowerImageMedia = new ImageMedia("media/HP9880A/HP9867B_POWER.png", mainframe.imageController);
  	doorUnlockedImageMedia = new ImageMedia("media/HP9880A/HP9867B_UNLOCKED.png", mainframe.imageController);
  	driveReadyImageMedia = new ImageMedia("media/HP9880A/HP9867B_READY.png", mainframe.imageController);
  	protectLdImageMedia = new ImageMedia("media/HP9880A/HP9867B_LD.png", mainframe.imageController);
  	protectUdImageMedia = new ImageMedia("media/HP9880A/HP9867B_UD.png", mainframe.imageController);
  	loadSwitchImageMedia = new ImageMedia("media/HP9880A/HP9867B_SWITCH.png", mainframe.imageController);

  	NORMAL_W = 800;  // 900;
  	NORMAL_H = 436;  //490;

  	System.out.println("HP9867B Mass Memory Storage Unit " + Integer.toString(2 * drive) + "+" + Integer.toString(2 * drive + 1) + " loaded.");
  }

  public void setDeviceWindow(JFrame window)
  {
  	super.setDeviceWindow(window);
    deviceWindow.setTitle(windowTitle);
  	
  	if(createWindow) {
  		deviceWindow.setResizable(true);
      deviceWindow.setLocation(220 + drive * 20, drive * 20);
  		
      menuBar.removeAll();  // remove dummy menu
      
      JMenu runMenu = new JMenu("Run");
      runMenu.add(makeMenuItem("Exit"));
      menuBar.add(runMenu);

      JMenu viewMenu = new JMenu("View");
      viewMenu.add(makeMenuItem("Normal Size", KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK));
      viewMenu.add(makeMenuItem("Double Size", KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
      viewMenu.add(makeMenuItem("Real Size", KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK));
      viewMenu.add(makeMenuItem("Hide Menu", KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK));
      menuBar.add(viewMenu);

      JMenu deviceMenu = new JMenu("Cartridge");
      deviceMenu.add(makeMenuItem("Load", KeyEvent.VK_ENTER, 0));
      deviceMenu.add(makeMenuItem("Unload", KeyEvent.VK_DELETE, 0));
      menuBar.add(deviceMenu);
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

  	switch(1) { // just to use break for ending cmd compare
  		case 1:
  			if(cmd.startsWith("Exit")) {
  				close();
  				break;
  			}
  			
  			if(cmd.startsWith("Normal Size")) {
  				setNormalSize();
  				break;
  			}

  			if(cmd.startsWith("Double Size")) {
  				setDoubleSize();
  				break;
  			}

  			if(cmd.startsWith("Real Size")) {
  				setRealSize(REAL_W, REAL_H);
  				break;
  			}
  			
  			if(cmd.startsWith("Hide Menu")) {
  				if(extDeviceWindow != null)
  					extDeviceWindow.setFrameSize(!menuBar.isVisible());
  				break;
  			}
  			
  			if(cmd.startsWith("Load")) {
    			driveReady = false;
    			doorUnlocked = true;
    			repaint();
    			disks[0].openDiskFile();
  				break;
  			}
  			
  			if(cmd.startsWith("Unload")) {
    			driveReady = false;
    			doorUnlocked = true;
    			repaint();
    			disks[0].closeDiskFile();
  				break;
  			}
  	}
  	
  	repaint();
  }


  class windowListener extends WindowAdapter
  {
    public void windowClosing(WindowEvent event)
    {
      close();
    }
  }
  
  public class Disk
  {
  	RandomAccessFile diskFile;
  	int unit;

  	public Disk(int unit)
  	{
  		this.unit = unit;
  		// load standard disc file
  		try{
  			diskFile = new RandomAccessFile("applications/HP9830/HP9880-UNIT" + Integer.toString(unit) + ".disc", "rw");
  		} catch (FileNotFoundException e) {
  		}  		
  	}

  	public boolean openDiskFile()
  	{
  		int l = getInsets().left;
  		int t = getInsets().top;

  		closeDiskFile();

  		FileDialog fileDialog = new FileDialog(deviceWindow, "Load Cartridge for Disk Unit " + unit);
  		fileDialog.setBackground(Color.WHITE);
  		fileDialog.setVisible(true);

  		String fileName = fileDialog.getFile();
  		String dirName = fileDialog.getDirectory();

  		if(fileName != null) {
  			fileName = dirName + fileName;

  			try{
  				diskFile = new RandomAccessFile(fileName, "rw");
  			} catch (FileNotFoundException e) {
  				System.err.println(e.toString());
  				return(false);
  			}

  			driveReady = true;
  			doorUnlocked = false;
  			repaint();
  			//repaint(DOOR_UNLOCKED_X + l, DOOR_UNLOCKED_Y + t, DOOR_UNLOCKED_W + DRIVE_READY_W + 20, DOOR_UNLOCKED_H);
  			//repaint(SWITCH_LOAD_X + l , SWITCH_LOAD_Y + t, SWITCH_LOAD_W, SWITCH_LOAD_H);

  			return(true);
  		}

  		closeDiskFile();

  		return(false);
  	}

  	public boolean closeDiskFile()
  	{
  		if(diskFile != null) {
  			try {
  				diskFile.close();
  				diskFile = null;
  			} catch (IOException e) { }
  		}

  		return(false);
  	}
  	
    public int output(int head, int cylinder, int sector, int mode)
    {
      int address;
      int record;

      if(diskFile == null || doorUnlocked || !driveReady)
        return(HP11305A.POWER_ON | HP11305A.DRIVE_UNSAFE_ERROR);

      if((head > 1) || (cylinder > 202) || (sector > 22) || ((sector & 1) != 0))
        return(HP11305A.POWER_ON | HP11305A.ADDRESS_ERROR);

      record = (head * 203  + cylinder) * 24 + sector;

      statusString = ((unit & 1) == 0 ? "U" : "L") + Integer.toString(record);
      accessMode = mode;
      repaint();
      //repaint(STATUS_X, STATUS_Y - 15, 50, 20);

      try {
        diskFile.seek(256 * record);

        // sleep 10ms for approx. realistic timing
        if(!highSpeed) {
          try {
            Thread.sleep(time_10ms);
          } catch (InterruptedException e) { }
        }

        // HP9867A is connected to extended memory in IOinterface (HP11273A) via HP11305A
        for(address = 0; address < 0400; address++) {
          if((mode & 1) != 0) {
            // read mode
            mainframe.memory[077000 + address].setValue(diskFile.readShort());
          } else {
            // write mode
            // is drive write protected?
            if((unit & 1) == 0) {
              if(driveProtectU)
                return(HP11305A.POWER_ON | HP11305A.ADDRESS_ERROR);
            } else {
              if(driveProtectL)
                return(HP11305A.POWER_ON | HP11305A.ADDRESS_ERROR);
            }

            diskFile.writeShort(mainframe.memory[077000 + address].getValue());
          }
        }
      } catch (IOException e) {
        return(HP11305A.POWER_ON | HP11305A.ADDRESS_ERROR);
      }

      return(HP11305A.POWER_ON);
    }
  }

  public void mousePressed(MouseEvent event)
  {
  	// get unscaled coordinates of mouse position
  	int x = (int)((event.getX() - getInsets().left) / widthScale); 
  	int y = (int)((event.getY() - getInsets().top) / heightScale);

  	if(x > LOADSW_X - 10 && x < LOADSW_X + 10)
  		if(y > LOADSW_Y - 10 && y < LOADSW_Y + 10) {
  			driveReady = false;
  			doorUnlocked = true;
  			repaint();
  			disks[0].openDiskFile();
  			return;
  		}

  	if(x > PROTECT_UD_X && x < PROTECT_UD_X + PROTECT_UD_W)
  		if(y > PROTECT_UD_Y && y < PROTECT_UD_Y + PROTECT_UD_H) {
  			driveProtectU = !driveProtectU;
  	  	repaint();
  			return;
  		}

  	if(x > PROTECT_LD_X && x < PROTECT_LD_X + PROTECT_LD_W)
  		if(y > PROTECT_LD_Y && y < PROTECT_LD_Y + PROTECT_LD_H) {
  			driveProtectL = !driveProtectL;
  	  	repaint();
  			return;
  		}
  }

  public void mouseReleased(MouseEvent event)
  {
  }

  public void keyPressed(KeyEvent event)
  {
  	ActionEvent actionEvent;
    int keyCode = event.getKeyCode();

    event.consume(); // do not pass key event to other levels (e.g. menuBar)

    switch(keyCode) {
    case 'S':
      highSpeed = !highSpeed;
      deviceWindow.setTitle(windowTitle + (highSpeed? " High Speed" : ""));
      break;
      
    case 'M':
      if(event.isControlDown())
        if(extDeviceWindow != null)
          extDeviceWindow.setFrameSize(!menuBar.isVisible());
      break;

    case 'N':
      if(event.isControlDown())
        setNormalSize();
      break;

    case 'O':
      if(event.isControlDown())
        setDoubleSize();
      break;

    case 'R':
      if(event.isControlDown())
        setRealSize(REAL_W, REAL_H);
      break;
      
    case KeyEvent.VK_DELETE:
    	actionEvent = new ActionEvent(this, ActionEvent.ACTION_FIRST, "Unload");
    	actionPerformed(actionEvent);
      break;
    }
  }

  public void keyReleased(KeyEvent event)
  {
  }

  public void keyTyped(KeyEvent event)
  {
  }

  public void paint(Graphics g)
  {
  	int x = 0, y = 0; // positioning is done by g2d.translate()

    super.paint(g);
    setScale(true, false);

  	hp9867Image = deviceImageMedia.getScaledImage((int)(NORMAL_W * widthScale), (int)(NORMAL_H * heightScale));
  	backgroundImage = g2d.drawImage(hp9867Image, x, y, NORMAL_W, NORMAL_H, this);

  	if(!backgroundImage)  // don't overlays before background is ready
  		return;

  	drivePowerImage = drivePowerImageMedia.getScaledImage((int)(DRIVE_POWER_W * widthScale), (int)(DRIVE_POWER_H * heightScale));
  	driveReadyImage = driveReadyImageMedia.getScaledImage((int)(DRIVE_READY_W * widthScale), (int)(DRIVE_READY_H * heightScale));
  	doorUnlockedImage = doorUnlockedImageMedia.getScaledImage((int)(DOOR_UNLOCKED_W * widthScale), (int)(DOOR_UNLOCKED_H * heightScale));
  	loadSwitchImage = loadSwitchImageMedia.getScaledImage((int)(SWITCH_LOAD_W * widthScale), (int)(SWITCH_LOAD_H * heightScale));
  	protectLdImage = protectLdImageMedia.getScaledImage((int)(PROTECT_LD_W * widthScale), (int)(PROTECT_LD_H * heightScale));
  	protectUdImage = protectUdImageMedia.getScaledImage((int)(PROTECT_UD_W * widthScale), (int)(PROTECT_UD_H * heightScale));

    if(backgroundImage) {
      if(powerOn)
        g2d.drawImage(drivePowerImage, x + DRIVE_POWER_X, y + DRIVE_POWER_Y, DRIVE_POWER_W, DRIVE_POWER_H, this);

      if(driveReady)
        g2d.drawImage(driveReadyImage, x + DRIVE_READY_X, y + DRIVE_READY_Y, DRIVE_READY_W, DRIVE_READY_H, this);

      if(doorUnlocked)
        g2d.drawImage(doorUnlockedImage, x + DOOR_UNLOCKED_X, y + DOOR_UNLOCKED_Y, DOOR_UNLOCKED_W, DOOR_UNLOCKED_H, this);
      else
        g2d.drawImage(loadSwitchImage, x + SWITCH_LOAD_X, y + SWITCH_LOAD_Y, SWITCH_LOAD_W, SWITCH_LOAD_H, this);

      if(driveProtectL)
        g2d.drawImage(protectLdImage, x + PROTECT_LD_X, y + PROTECT_LD_Y, PROTECT_LD_W, PROTECT_LD_H, this);

      if(driveProtectU)
        g2d.drawImage(protectUdImage, x + PROTECT_UD_X, y + PROTECT_UD_Y, PROTECT_UD_W, PROTECT_UD_H, this);
    }

    drawStatus(g2d);
  }

  void drawStatus(Graphics2D g2d)
  {
    Font font = new Font("Monospaced", Font.BOLD, 20);
    g2d.setFont(font); 

    switch(accessMode) {
    case 0:
      // write mode
      g2d.setColor(Color.RED);
      break;

    case 1:
      // read mode
      g2d.setColor(Color.GREEN);
      break;

    case 2:
    case 3:
      // inititalize
      g2d.setColor(Color.YELLOW);
    }

    g2d.drawString(statusString, STATUS_X, STATUS_Y);
  }
  
  public void close()
  {
 		if(drivePowerImageMedia != null)
 			drivePowerImageMedia.close();

 		if(doorUnlockedImageMedia != null)
 			doorUnlockedImageMedia.close();

 		if(driveReadyImageMedia != null)
 			driveReadyImageMedia.close();
 		
 		if(protectLdImageMedia != null)
 			protectLdImageMedia.close();
 	 
 		if(protectUdImageMedia != null)
 			protectUdImageMedia.close();
 	 
 		if(loadSwitchImageMedia != null)
 			loadSwitchImageMedia.close();
 	 
  	super.close();
  }
}
