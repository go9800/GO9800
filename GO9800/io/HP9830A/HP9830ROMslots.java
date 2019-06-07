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
 * 10.03.2007 Rel. 0.30 Class created 
 * 26.09.2007 Rel. 1.20 Added ROM blocks HP11272B (Extended I/O), HP11277B (Terminal I), HP11278B (Batch Basic)
 * 03.11.2008 Rel. 1.31 Bugfix: avoid null-pointer exception in paint for empty ROM slot 
 * 18.03.2009 Rel. 1.40 Added display of InstructionsWindow with right mouse click on ROM block
 * 22.03.2009 Rel. 1.40 Bugfix: call blockImage.getHeight(this) not blockImage.getHeight(null) to avoid incomplete drawing
 * 31.10.2011 Rel. 1.50 Added ROM block HP11296B (DATA COMM I), Infotek FAST BASIC IV
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 */

package io.HP9830A;

import io.*;
import emu98.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.JFrame;

public class HP9830ROMslots extends JFrame
{
  private static final long serialVersionUID = 1L;

  static int MODULE_W = 200;
  static int MODULE_H = 44;
  static int SLOT_W = 265;
  static int SLOT_H = 318;

  Image romSlotImage;
  HP9830AMainframe hp9830;
  ROMselector romSelector;
  boolean backgroundImage = false;

  public HP9830ROMslots(HP9830AMainframe hp9830)
  {
    super("HP9830A ROMs");

    addWindowListener(new windowListener());
    addMouseListener(new mouseListener());
    
    this.hp9830 = hp9830;

    setSize(264, 345);
    setLocation(20, 120);
    setBackground(Color.BLACK);
    setAlwaysOnTop(true);
    
    romSlotImage = new ImageMedia("media/HP9830A/HP9830A_ROMslot.png", hp9830.imageController).getScaledImage(SLOT_W, SLOT_H);

    romSelector = new ROMselector(this, hp9830, MODULE_W, MODULE_H);
    romSelector.addRomButton("media/HP9830A/HP11XXXX_Module.png", "HP11XXXX");
    romSelector.addRomButton("media/HP9830A/HP11270B_Module.png", "HP11270B");
    romSelector.addRomButton("media/HP9830A/HP11271B_Module.png", "HP11271B");
    romSelector.addRomButton("media/HP9830A/HP11272B_Module.png", "HP11272B");
    romSelector.addRomButton("media/HP9830A/HP11273B_Module.png", "HP11273B");
    romSelector.addRomButton("media/HP9830A/HP11274B_Module.png", "HP11274B");
    romSelector.addRomButton("media/HP9830A/HP11277B_Module.png", "HP11277B");
    romSelector.addRomButton("media/HP9830A/HP11278B_Module.png", "HP11278B");
    romSelector.addRomButton("media/HP9830A/HP11279B_Module.png", "HP11279B");
    romSelector.addRomButton("media/HP9830A/HP11283B_Module.png", "HP11283B");
    romSelector.addRomButton("media/HP9830A/HP11289B_Module.png", "HP11289B");
    romSelector.addRomButton("media/HP9830A/HP11296B_Module.png", "HP11296B");
    romSelector.addRomButton("media/HP9830A/INFOTEK_FB1_Module.png", "INFOTEK_FB1");
    //romSelector.addRomButton("media/HP9830A/INFOTEK_FB2_Module.png", "INFOTEK_FB2"); // FB2 is only allowed/functional in internal slot 0
    romSelector.addRomButton("media/HP9830A/INFOTEK_FB3_Module.png", "INFOTEK_FB3");
    romSelector.addRomButton("media/HP9830A/INFOTEK_FB4_Module.png", "INFOTEK_FB4");
  }
  
   
  class windowListener extends WindowAdapter
  {
    public void windowOpened(WindowEvent event)
    {
    }
    
    public void windowClosing(WindowEvent event)
    {
      setVisible(false);
    }
  }

  class mouseListener extends MouseAdapter
  {
    public void mousePressed(MouseEvent event)
    {
      int x = event.getX() - getInsets().left;
      int y = event.getY() - getInsets().top;

      if((y > 20 && y < 250) && (x > 35 && x < 235)) {
        int block = (y - 5) / 50 + 1;
        if(event.getButton() == MouseEvent.BUTTON1) {
          romSelector.setRomSlot("Slot" + Integer.toString(block));
          romSelector.setTitle("HP9830A ROM Blocks Slot " + Integer.toString(block));
          romSelector.setVisible(true);
        } else {
          MemoryBlock romBlock = (MemoryBlock)hp9830.config.memoryBlocks.get("Slot" + Integer.toString(block));
          if(romBlock != null) {
            hp9830.instructionsWindow.setROMblock(romBlock);
            hp9830.instructionsWindow.showInstructions();
          }
        }
      }
    }

    public void mouseReleased(MouseEvent event)
    {
      
    }
  }

  public void update(Graphics g)
  {
    // avoid flickering by not drawing background color
    paint(g);
  }
  
  public void paint(Graphics g)
  {
    int x = getInsets().left;
    int y = getInsets().top;
    MemoryBlock block;
    Image blockImage;

    backgroundImage = g.drawImage(romSlotImage, x, y, 256, 318, this);
    
    if(backgroundImage) {
      for(int i = 1; i <= 5; i++) {
        // get images of ROM modules
        block = (MemoryBlock)hp9830.config.memoryBlocks.get("Slot" + i);

        if(block != null) {
          blockImage = block.getUniModule(200, 44);
          if(blockImage != null) {
            g.drawImage(blockImage, x+32, y + 50*i - 32, 200, blockImage.getHeight(this), this);
          }
        }
      }
    }
  }
  
  public void dispose()
  {
  	romSelector.dispose();
  	super.dispose();
  }
 }
