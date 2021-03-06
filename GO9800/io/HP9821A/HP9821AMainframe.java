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
 * 04.04.2006 Class derived from HP9820A and HP9830A mainframe 
 * 06.04.2007 Rel. 1.00 Added handling of MAW (memory[01377])
 * 09.04.2007 Rel. 1.00 Code reorganized for usage of class HP9800Mainframe
 * 13.12.2007 Rel. 1.20 Don't release keyboard by mouseReleased() or keyReleased(). This is now done after 5ms by KeyboardInterface.run()   
 * 18.01.2009 Rel. 1.40 Bugfix: Close tape drive door when no tape file selected
 * 18.03.2009 Rel. 1.40 Added display of InstructionsWindow with right mouse click on ROM block
 * 30.10.2011 Rel. 1.50 Added original HP9821A system ROMs (finally!)
 * 30.10.2011 Rel. 1.50 Added beeper, changed internal tape select code to 10
 * 25.02.2012 Rel. 1.60 Added display of keyboard overlay in InstructionsWindow
 * 21.10.2017 Rel. 2.10 Added Graphics scaling using class Graphics2D
 * 24.10.2017 Rel. 2.10 Added display of click areas, changed size and behaviour (left-click) of ROM template and instructions click areas
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 */

package io.HP9821A;

import java.awt.Graphics;
import java.awt.event.*;

import javax.swing.JFrame;

import io.*;
import io.HP9820A.HP9820AMainframe;
import emu98.*;

public class HP9821AMainframe extends HP9820AMainframe
{
  private static final long serialVersionUID = 1L;

  static final int[] HP9821keyOffsetX = {110, 110, 110, 110, 110, 110, 110, 110, 110}; // offset of leftmost key in row
  static final int[][] HP9821keyCodes = {
      {077, 040, -1, 0110, 0115, -1, 0122, 0127, -1, 060, 060, 056, 0135, 0135, -1, 0133, 0133, 041, 002, 002},
      {047, 017, -1, 0107, 0114, -1, 0121, 0126, -1, 053, 061, 062, 063, 073, -1, 0137, 072, 043, 037, 0100},
      {046, 016, -1, 0106, 0113, -1, 0120, 0125, -1, 055, 064, 065, 066, 054, -1, 0103, 0132, 075, 036, 033},
      {045, 011, -1, 0105, 0112, -1, 0117, 0124, -1, 052, 067, 070, 071, 042, -1, 0102, 0131, 074, 034, 035},
      {044, 010, -1, 0104, 0111, -1, 0116, 0123, -1, 057, 0134, 0136, 050, 051, -1, 0101, 0130, 076, 031, 032},
      {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1},
      {020, 021, -1, 0141, 0142, 0140, -1, 0144, 0143, -1, 003, -1, 024, 025, 027, 026, -1, 004, 005, 030},
      {0145, -1, -1, -1, -1, -1, -1, 022, 023, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0776},
      {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0777, -1, -1, -1, -1, -1, -1},
  };

  int xTapeStatus = 865;
  int yTapeStatus = 154;

  HP9865Interface hp9865Interface; 
  boolean tapeLoaded = false;
  
  public HP9821AMainframe(Emulator emu)
  {
    super(emu, "HP9821A");

    // override constants for HP9821A
    keyWidth = (950 - 110) / 20;  // with of one key area in pix
    keyOffsetY = 615;            // offset of lowest key row from image boarder 
    keyOffsetX = HP9821keyOffsetX;
    keyCodes = HP9821keyCodes;

    NORMAL_W = 1065;
    NORMAL_H = 650;
    
    DRIVE_X = 745;
    DRIVE_Y = 125;
    DRIVE_W = 295;
    DRIVE_H = 109;
    
    DISPLAY_X = 74;
    DISPLAY_Y = 133;
    DISPLAY_W = 75;
    DISPLAY_H = 12;
    LED_X = +45;
    LED_Y = +8;
  
    PAPER_HEIGHT = 208;
    PAPER_LEFT = 557;
    PAPER_EDGE = 123;

    BLOCK1_X = 38;
    BLOCK1_S = -0.15; 
    BLOCK2_X = 187;
    BLOCK2_S = -0.10; 
    BLOCK3_X = 338;
    BLOCK3_S = -0.05;
    BLOCK_W = 151;
    BLOCK_H = 50;

    
    // ROM template sizes
    TEMPLATE1_X = 106;
    TEMPLATE1_Y = 391;
    TEMPLATE1_S = -0.030;
    TEMPLATE2_X = 230;
    TEMPLATE2_Y = 391;
    TEMPLATE2_S = -0.025;
    TEMPLATE3_X = 350;
    TEMPLATE3_Y = 391;
    TEMPLATE3_S = -0.010;
    TEMPLATE_W = 111;
    TEMPLATE_H = 224;

    // click area for ROM block exchange
    ROM_X = 30;
    ROM_Y = 5;
    ROM_W = 150;
    ROM_H = 45;
    // click area for keyboard overlay
    OVERLAY_X = 90;
    OVERLAY_Y = 370;
    OVERLAY_W = 123;
    OVERLAY_H = 30;
    // click area for instructions window
    INSTRUCTIONS_X = 515;
    INSTRUCTIONS_Y = 40;
    INSTRUCTIONS_W = 230;
    INSTRUCTIONS_H = 30;

    addMouseListener(new mouseListener());
    
    // create beeper (card reader)
    ioUnit.bus.cardReader = new HP9800BeeperInterface(this);
    
    // create internal tape drive
    hp9865Interface = new HP9865Interface(Integer.valueOf(10), this);
    tapeDevice = new HP9865A(5, hp9865Interface);
    tapeDevice.createWindow = false; // this device doesn't need a separate window 
    tapeDevice.setDeviceWindow(hp9800Window); // set parent Frame for dialogs
  	tapeDevice.setStatusPanel(this, xTapeStatus, yTapeStatus); // set panel for tape status output

    
    hp9865Interface.setDevice(tapeDevice); 
    hp9865Interface.start();

    romSelector.addRomButton("media/HP9821A/HP11XXXX_Slot.png", "HP11XXXX");
    romSelector.addRomButton("media/HP9821A/HP11220A_Module.png", "HP11220A");
    romSelector.addRomButton("media/HP9821A/HP11221A_Module.png", "HP11221A");
    romSelector.addRomButton("media/HP9821A/HP11222A_Module.png", "HP11222A");
    //romSelector.addRomButton("media/HP9821A/HP11223A_Block.jpg", "HP11223A");
    
    keyboardImageMedia = new ImageMedia("media/HP9821A/HP9821A_Keyboard.png", imageController);
 		driveopenImageMedia = new ImageMedia("media/HP9821A/HP9821A_Drive_Open.png", imageController);
 		driveloadedImageMedia = new ImageMedia("media/HP9821A/HP9821A_Drive_Loaded.png", imageController);
    blockImageMedia = new ImageMedia("media/HP9820A/HP9820A_Module.png", imageController);

    setNormalSize();
    System.out.println("HP9821 Mainframe loaded.");
  }
  
  public void setHP9800Window(HP9800Window hp9800Window)
  {
  	this.hp9800Window = hp9800Window;
  }
  
  class mouseListener extends MouseAdapter
  {
    public void mousePressed(MouseEvent event)
    {
    	// get unscaled coordinates of mouse position
      int x = (int)((event.getX() - getInsets().left) / widthScale); 
      int y = (int)((event.getY() - getInsets().top) / heightScale);

      // ROM block click area
      if((y >= ROM_Y && y <= ROM_Y + ROM_H) && (x >= ROM_X && x <= ROM_X + 3 * ROM_W)) {
        int block = (x - ROM_X) / ROM_W + 1;
        if(event.getButton() == MouseEvent.BUTTON1) {
          romSelector.setRomSlot("Slot" + Integer.toString(block));
          romSelector.setTitle("HP9821A ROM Blocks Slot " + Integer.toString(block));
          romSelector.setVisible(true);
        } else {
          MemoryBlock romBlock = (MemoryBlock)config.memoryBlocks.get("Slot" + Integer.toString(block));
          if(romBlock != null) {
            instructionsWindow.setROMblock(romBlock);
            instructionsWindow.showInstructions();
          }
        }

        return;
      }

      // Overlay block click area
      if((y >= OVERLAY_Y && y <= OVERLAY_Y + OVERLAY_H) && (x >= OVERLAY_X && x <= OVERLAY_X + 3 * OVERLAY_W)) {
        int block = (x - OVERLAY_X) / OVERLAY_W + 1;
        if(event.getButton() == MouseEvent.BUTTON1) {
          MemoryBlock romBlock = (MemoryBlock)config.memoryBlocks.get("Slot" + Integer.toString(block));
          if(romBlock != null) {
            instructionsWindow.setROMblock(romBlock);
            instructionsWindow.showInstructions();
          }

          return;
        }
      }

      // instructions window click area
      if((y >= INSTRUCTIONS_Y && y <= INSTRUCTIONS_Y + INSTRUCTIONS_H) && (x >= INSTRUCTIONS_X && x <= INSTRUCTIONS_X + INSTRUCTIONS_W)) {
        if(event.getButton() == MouseEvent.BUTTON1) {
        } else {
          MemoryBlock romBlock = (MemoryBlock)config.memoryBlocks.get("Block0");
          if(romBlock != null) {
            instructionsWindow.setROMblock(romBlock);
            instructionsWindow.showInstructions();
          }

        }

        return;
      }

      // calculate key row#
      int row = keyOffsetY - y;
      if(row < 0) return; // below key area?
      row /= keyWidth;
      if(row > 8) return; // above key area?

      // calculate key column# from row-individual offset
      int col = x - keyOffsetX[row];
      if(col < 0) return; // left of key row?
      col /= keyWidth;

      if(col > 19)
        return; // right of key row?

      int keyCode = keyCodes[row][col];

      if(keyCode == 0776) {
        // cassette OPEN
      	tapedriveImage = driveopenImageMedia.getScaledImage((int)(DRIVE_W * widthScale), (int)(DRIVE_H * heightScale));
        repaint();
        
        tapeLoaded = tapeDevice.openTapeFile();
        if(tapeLoaded)
        	tapedriveImage = driveloadedImageMedia.getScaledImage((int)(DRIVE_W * widthScale), (int)(DRIVE_H * heightScale));
        else
        	tapedriveImage = null;
        
        repaint();
        return;
      }

      // PAPER
      if(keyCode == 0777) {
        paper(1);
        return;
      }

      if(keyCode != -1) {
        if(emu.keyLogMode) {
          emu.console.append(Integer.toOctalString(keyCode) + " ");
          return;
        }

        if(keyCode == 041) {
          // set STP flag and request service
        }

        ioUnit.bus.keyboard.setKeyCode(keyCode);
        ioUnit.bus.keyboard.requestInterrupt();
      }
    }

    public void mouseReleased(MouseEvent event)
    {
      if(advancing) {
        paper(0);
      }
    }
  }
}
