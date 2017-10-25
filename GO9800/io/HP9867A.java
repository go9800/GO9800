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
 * 26.05.2007 Class created 
 * 05.06.2007 Added drive lights  
 * 16.06.2007 Added disc status indicator and load switch
 * 16.10.2007 Rel. 1.20 Changed frame size control
 * 22.01.2009 Rel. 1.40 Added high-speed mode and keyPressed()
 * 20.03.2009 Rel. 1.40 Added synchronized(keyboardImage) before visualizing main window to avoid flickering
*/

package io;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

public class HP9867A extends IOdevice //Frame implements KeyListener
{
  private static final long serialVersionUID = 1L;

  public static int LOADSW_X = 700;
  public static int LOADSW_Y = 236;
  public static int DRIVE_POWER_X = 596;
  public static int DRIVE_POWER_Y = 216;
  public static int DRIVE_POWER_W = 38;
  public static int DRIVE_POWER_H = 40;
  public static int DOOR_UNLOCKED_X = 494;
  public static int DOOR_UNLOCKED_Y = 216;
  public static int DOOR_UNLOCKED_W = 37;
  public static int DOOR_UNLOCKED_H = 40;
  public static int SWITCH_LOAD_X = 693;
  public static int SWITCH_LOAD_Y = 225;
  public static int SWITCH_LOAD_W = 15;
  public static int SWITCH_LOAD_H = 19;
  public static int DRIVE_READY_X = 546;
  public static int DRIVE_READY_Y = 217;
  public static int DRIVE_READY_W = 37;
  public static int DRIVE_READY_H = 40;
  public static int PROTECT_LD_X = 443;
  public static int PROTECT_LD_Y = 238;
  public static int PROTECT_LD_W = 38;
  public static int PROTECT_LD_H = 19;
  public static int PROTECT_UD_X = 443;
  public static int PROTECT_UD_Y = 217;
  public static int PROTECT_UD_W = 38;
  public static int PROTECT_UD_H = 20;

  public static int STATUS_X = 300;
  public static int STATUS_Y = 250;
  
  Image hp9867Image;
  Image drivePowerImage, doorUnlockedImage, driveReadyImage, protectLdImage, protectUdImage, loadSwitchImage;
  RandomAccessFile diskFile;
  HP9867A statusFrame;
  String statusString = "", prevStatus = "";
  private String windowTitle;
  int accessMode = -1;
  int unit;
  int numDiscs;
  boolean powerOn = true;
  boolean doorUnlocked = false;
  boolean driveReady = true;
  boolean driveProtectL = false;
  boolean driveProtectU = false;
  private boolean highSpeed;
  static int time_10ms = 10;

  public HP9867A(int unit, int numDiscs, HP9867A dispFrame)
  {
    super("HP9867A Unit " + unit);
    windowTitle = "HP9867A Unit " + unit;

    this.unit = unit;
    this.numDiscs = numDiscs;

    if(dispFrame == null) {
      statusFrame = this;
      addKeyListener(this);
      addWindowListener(new windowListener());
      addMouseListener(new mouseListener());

      //hp9867Image = new ImageMedia("media/HP9880A/HP9867" + (numDiscs == 1? "A" : "B") + ".jpg").getImage();
      hp9867Image = new ImageMedia("media/HP9880A/HP9867B.jpg").getImage();
      drivePowerImage = new ImageMedia("media/HP9880A/HP9867B_DRIVE_POWER.jpg").getImage();
      doorUnlockedImage = new ImageMedia("media/HP9880A/HP9867B_DOOR_UNLOCKED.jpg").getImage();
      driveReadyImage = new ImageMedia("media/HP9880A/HP9867B_DRIVE_READY.jpg").getImage();
      protectLdImage = new ImageMedia("media/HP9880A/HP9867B_PROTECT_LD.jpg").getImage();
      protectUdImage = new ImageMedia("media/HP9880A/HP9867B_PROTECT_UD.jpg").getImage();
      loadSwitchImage = new ImageMedia("media/HP9880A/HP9867B_LOAD_SWITCH.jpg").getImage();

      setBackground(Color.BLACK);

      setLocation(220 + unit * 20, unit * 20);
      setResizable(false);
      setVisible(true);
      // wait until background image has been loaded
      synchronized(hp9867Image) {
      	while(hp9867Image.getWidth(this) <= 0) {
      		try
      		{
      			hp9867Image.wait(100);
      		} catch (InterruptedException e)
      		{ }
      	}
      }
      setSize(hp9867Image.getWidth(this) + getInsets().left + getInsets().right, hp9867Image.getHeight(this) + getInsets().top + getInsets().bottom);
    } else {
        statusFrame = dispFrame;
        statusFrame.windowTitle = "HP9867B Unit " + (unit-1) + "+" + unit;
        statusFrame.setTitle(statusFrame.windowTitle);
      }

      // load standard disc file
      try{
        diskFile = new RandomAccessFile("applications/HP9830/HP9880-UNIT" + unit + ".disc", "rw");
      } catch (FileNotFoundException e) {
      }

      System.out.println("HP9867" + (numDiscs == 1? "A" : "B") + " Mass Memory Storage Unit " + unit + " loaded.");
    }
  
  class windowListener extends WindowAdapter
  {
    public void windowClosing(WindowEvent event)
    {
    	close();
   }
  }
 
  public boolean openDiskFile()
  {
    int l = getInsets().left;
    int t = getInsets().top;
    
    closeDiskFile();

    FileDialog fileDialog = new FileDialog(this, "Load Cartridge for Disk Unit " + unit);
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

      statusFrame.driveReady = true;
      doorUnlocked = false;
      repaint(DOOR_UNLOCKED_X + l, DOOR_UNLOCKED_Y + t, DOOR_UNLOCKED_W + DRIVE_READY_W + 20, DOOR_UNLOCKED_H);
      repaint(SWITCH_LOAD_X + l , SWITCH_LOAD_Y + t, SWITCH_LOAD_W, SWITCH_LOAD_H);

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
  
  class mouseListener extends MouseAdapter
  {
    public void mousePressed(MouseEvent event)
    {
      int l = getInsets().left;
      int t = getInsets().top;
      int x = event.getX() - l;
      int y = event.getY() - t;
      
      if(x > LOADSW_X - 10 && x < LOADSW_X + 10)
        if(y > LOADSW_Y - 10 && y < LOADSW_Y + 10) {
          statusFrame.driveReady = false;
          doorUnlocked = true;
          repaint(DOOR_UNLOCKED_X + l, DOOR_UNLOCKED_Y + t, DOOR_UNLOCKED_W + DRIVE_READY_W + 20, DOOR_UNLOCKED_H);
          openDiskFile();
          return;
        }
      
      if(x > PROTECT_UD_X && x < PROTECT_UD_X + PROTECT_UD_W)
        if(y > PROTECT_UD_Y - 10 && y < PROTECT_UD_Y + PROTECT_UD_H) {
          driveProtectU = !driveProtectU;
          repaint(PROTECT_UD_X + l, PROTECT_UD_Y + t, PROTECT_UD_W, PROTECT_UD_H);
          return;
        }
      
      if(numDiscs > 1) {
        if(x > PROTECT_LD_X && x < PROTECT_LD_X + PROTECT_LD_W)
          if(y > PROTECT_LD_Y - 10 && y < PROTECT_LD_Y + PROTECT_LD_H) {
            driveProtectL = !driveProtectL;
            repaint(PROTECT_LD_X + l, PROTECT_LD_Y + t, PROTECT_LD_W, PROTECT_LD_H);
            return;
          }
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
    case 'S':
      statusFrame.highSpeed = !statusFrame.highSpeed;
      this.setTitle(statusFrame.windowTitle + (statusFrame.highSpeed? " High Speed" : ""));
      break;

    default:
      return;
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
    int x = getInsets().left;
    int y = getInsets().top;

    boolean backgroundImage = g.drawImage(hp9867Image, x, y, hp9867Image.getWidth(this), hp9867Image.getHeight(this), this);
    if(backgroundImage) {
      if(powerOn)
        g.drawImage(drivePowerImage, x + DRIVE_POWER_X, y + DRIVE_POWER_Y, DRIVE_POWER_W, DRIVE_POWER_H, this);
      
      if(statusFrame.driveReady)
        g.drawImage(driveReadyImage, x + DRIVE_READY_X, y + DRIVE_READY_Y, DRIVE_READY_W, DRIVE_READY_H, this);
      
      if(doorUnlocked)
        g.drawImage(doorUnlockedImage, x + DOOR_UNLOCKED_X, y + DOOR_UNLOCKED_Y, DOOR_UNLOCKED_W, DOOR_UNLOCKED_H, this);
      else
        g.drawImage(loadSwitchImage, x + SWITCH_LOAD_X, y + SWITCH_LOAD_Y, SWITCH_LOAD_W, SWITCH_LOAD_H, this);
      
      if(driveProtectL)
        g.drawImage(protectLdImage, x + PROTECT_LD_X, y + PROTECT_LD_Y, PROTECT_LD_W, PROTECT_LD_H, this);
      
      if(driveProtectU)
        g.drawImage(protectUdImage, x + PROTECT_UD_X, y + PROTECT_UD_Y, PROTECT_UD_W, PROTECT_UD_H, this);
    }
  }

  void drawStatus()
  {
    int x = statusFrame.getInsets().left;
    int y = statusFrame.getInsets().top;
    Graphics g = statusFrame.getGraphics();
    Font font = new Font("Monospaced", Font.BOLD, 20);
    g.setFont(font);

    // overpaint previous status
    g.setColor(new Color(180, 170, 170));
    g.drawString(statusFrame.prevStatus, x + STATUS_X, y + STATUS_Y);
    
    switch(accessMode) {
    case 0:
      // write mode
      g.setColor(Color.RED);
      break;

    case 1:
      // read mode
      g.setColor(Color.GREEN);
      break;
      
    case 2:
    case 3:
      // inititalize
      g.setColor(Color.YELLOW);
    }
    
    g.drawString(statusFrame.statusString, x + STATUS_X, y + STATUS_Y);
  }

  public int output(int head, int cylinder, int sector, int mode)
  {
    int address;
    int record;
    
    if(diskFile == null || doorUnlocked || !statusFrame.driveReady)
      return(HP11305A.POWER_ON | HP11305A.DRIVE_UNSAFE_ERROR);

    if((head > 1) || (cylinder > 202) || (sector > 22) || ((sector & 1) != 0))
      return(HP11305A.POWER_ON | HP11305A.ADDRESS_ERROR);

    record = (head * 203  + cylinder) * 24 + sector;
    
    statusFrame.prevStatus = statusFrame.statusString;
    statusFrame.statusString = (numDiscs == 1 ? "" : ((unit & 1) == 0 ? "U" : "L")) + Integer.toString(record);
    accessMode = mode;
    drawStatus();
    
    try {
      diskFile.seek(256 * record);
      
      // sleep 10ms for approx. realistic timing
      if(!statusFrame.highSpeed) {
        try {
          Thread.sleep(time_10ms);
        } catch (InterruptedException e) { }
      }
      
      // HP9867A is connected to extended memory in IOinterface (HP11273A) via HP11305A
      for(address = 0; address < 0400; address++) {
        if((mode & 1) != 0) {
          // read mode
          IOinterface.memory[077000 + address].setValue(diskFile.readShort());
        } else {
          // write mode
          // is drive write protected?
          if((unit & 1) == 0 || (numDiscs == 1)) {
            if(driveProtectU)
              return(HP11305A.POWER_ON | HP11305A.ADDRESS_ERROR);
          } else {
            if(driveProtectL)
              return(HP11305A.POWER_ON);
          }
          
          diskFile.writeShort(IOinterface.memory[077000 + address].getValue());
        }
      }
    } catch (IOException e) {
      return(HP11305A.POWER_ON | HP11305A.ADDRESS_ERROR);
    }

    return(HP11305A.POWER_ON);
  }
  
  public void close()
  {
    setVisible(false);  // close window
    dispose();  // and remove it
    IOinterface.ioUnit.bus.devices.removeElement(this);  // remove device object from devices list

    System.out.println("HP9867 Unit " + unit + " unloaded.");
  }
}
