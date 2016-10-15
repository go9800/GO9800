/*
 * HP9800 Emulator
 * Copyright (C) 2006-2012 Achim Buerger
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
 * 05.09.2006 Class derived from HP9830A mainframe 
 * 07.09.2006 Implementing interface Mainframe
 * 17.09.2006 Added drawing of LED display lines
 * 18.09.2006 Added drawing of keyboard LEDs, use pseudo select code 16 for keyboard
 * 20.09.2006 Optimized LED display output (segment length)
 * 21.09.2006 Added internal printer output
 * 24.09.2006 Added paging in printer output
 * 24.09.2006 Clear SSI if key released
 * 02.10.2006 Added second dislay version, added magn. card reader
 * 04.10.2006 Removed method setWindow and changed constructor
 * 29.10.2006 Changed sound output to Sound class 
 * 05.11.2006 Changed Ctrl+D: opens disasm dialog instead starting the disassembler 
 * 24.11.2006 Changed loading of images from file to JAR
 * 10.12.2006 Rel. 0.21 Bugfix: Start print sound only if one line completely printed.
 * 15.12.2006 Rel. 0.21 New: window setLocation
 * 03.02.2007 Rel. 0.30 New: Ctrl+T to start/stop opcode timing measurement
 * 03.02.2007 Rel. 0.30 Changed Ctrl+D: now toggles disasmOutput visibility
 * 11.02.2007 Rel. 0.30 Changed ROM modules to HP11261A, HP11262A, HP11252A
 * 23.02.2007 Rel. 0.30 Added ROMselector 
 * 25.02.2007 Rel. 0.30 Added use of configuration file and removed memory initialization 
 * 10.03.2007 Rel. 0.30 Added machine reset (Ctrl+R) 
 * 09.04.2007 Rel. 1.00 Code reorganized for usage of class HP9800Mainframe
 * 24.08.2007 Rel. 1.20 Use keyPressed() from HP9800Mainframe
 * 05.09.2007 Rel. 1.20 Bugfix: DEN is now longer evaluated in display() method
 * 24.09.2007 Rel. 1.20 Changed display blanking control from fixed timer to instruction counter
 * 13.12.2007 Rel. 1.20 Don't release keyboard by mouseReleased() or keyReleased(). This is now done after 5ms by KeyboardInterface.run()   
 * 27.01.2008 Rel. 1.20 Added ROM modules HP11213A, HP11267A
 * 18.01.2009 Rel. 1.40 Added display of InstructionsWindow with right mouse click on ROM block
 * 18.03.2009 Rel. 1.40 Added display of InstructionsWindow with right mouse click on handle of top cover
 * 25.02.2012 Rel. 1.60 Added display of keyboard overlay in InstructionsWindow
 */

package io.HP9810A;

import java.awt.*;
import java.awt.event.*;

import io.*;
import emu98.*;

public class HP9810AMainframe extends HP9800Mainframe
{
  private static final long serialVersionUID = 1L;

  static final int[] HP9810keyOffsetX = {120, 120, 120, 120, 120, 120, 120, 120}; // offset of leftmost key in row
  static final int[][] HP9810keyCodes = {
      {060, 075, 071, -1, 023, 067, 027, -1, 033, 000, 000, 021, -1, 047, -1, 077, 044, 0100, -1},
      {063, 065, 073, -1, 040, 024, 030, -1, 034, 001, 002, 003, -1, 047, -1, 045, 053, 0101, -1},
      {061, 074, 070, -1, 013, 031, 025, -1, 036, 004, 005, 006, -1, -1, -1, 042, 050, 041, -1},
      {066, 015, 072, -1, 014, 064, 022, -1, 035, 007, 010, 011, -1, -1, -1, 051, 052, 057, -1},
      {062, 016, 055, -1, 056, 017, 012, -1, 076, 032, 026, 037, -1, 020, -1, 054, 043, 046, -1},
      {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1},
      {-1, -1, -1, -1, -1, -1, -1, 0111, 0110, -1, 0107, 0106, -1, 0105, 0104, -1, 0103, -1, 0102},
      {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0777, -1, -1, -1, -1},
  };

  // the following constants are used in HP9810A only
  static int TEMPLATE_X = 100;
  static int TEMPLATE_Y = 269;
  static int TEMPLATE_W = 162;
  static int TEMPLATE_H = 260;
  
  static int LED1_X = 136;
  static int LED2_X = 178;
  static int LED3_X = 219;
  static int LED_SMALL_Y = 279;
  static int LED_SMALL_WH = 11;

  static int STATUS_X = 342;
  static int FLOAT_X = 424;
  static int FIX_X = 467;
  static int RUN_X = 552;
  static int PRGM_X = 595;
  static int KEYLOG_X = 679;
  static int CARD_X = 847;
  static int LED_LARGE_Y = 274;
  static int LED_LARGE_WH = 18;
  
  static int LED_SEGMENT_SIZE = 4;

  Image ledLargeOn, ledLargeOff;
  Image ledSmallOn, ledSmallOff;
  

  public HP9810AMainframe(Emulator emu)
  {
    super(emu, "HP9810A");
    addMouseListener(new mouseListener());

    keyOffsetX = HP9810keyOffsetX;
    keyCodes = HP9810keyCodes;
    
    // create card reader
    ioUnit.bus.cardReader = new HP9800MagneticCardReaderInterface(this);

    // create display
    ioUnit.bus.display = new HP9810DisplayInterface(this);

    // create keyboard
    // use pseudo select code 16 for SSI interrupt line
    // HP9810A keyboard has in fact no select code
    ioUnit.bus.keyboard = new HP9810KeyboardInterface(16);
    ioUnit.bus.interfaces.add((IOinterface)ioUnit.bus.keyboard);

    romSelector.addRomButton("media/HP9810A/HP11XXXX_Block.jpg", "HP11XXXX");
    romSelector.addRomButton("media/HP9810A/HP11210A_Block.jpg", "HP11210A");
    romSelector.addRomButton("media/HP9810A/HP11211A_Block.jpg", "HP11211A");
    romSelector.addRomButton("media/HP9810A/HP11213A_Block.jpg", "HP11213A");
    romSelector.addRomButton("media/HP9810A/HP11214A_Block.jpg", "HP11214A");
    romSelector.addRomButton("media/HP9810A/HP11215A_Block.jpg", "HP11215A");
    romSelector.addRomButton("media/HP9810A/HP11252A_Block.jpg", "HP11252A");
    romSelector.addRomButton("media/HP9810A/HP11261A_Block.jpg", "HP11261A");
    romSelector.addRomButton("media/HP9810A/HP11262A_Block.jpg", "HP11262A");
    romSelector.addRomButton("media/HP9810A/HP11266A_Block.jpg", "HP11266A");
    romSelector.addRomButton("media/HP9810A/HP11267A_Block.jpg", "HP11267A");

    keyboardImage = new ImageMedia("media/HP9810A/HP9810A_Keyboard.jpg").getImage();
    displayImage = new ImageMedia("media/HP9810A/HP9810A_Display.jpg").getImage();

    ledLargeOn = new ImageMedia("media/HP9810A/HP9810A_LED_Large_On.jpg").getImage();
    ledLargeOff = new ImageMedia("media/HP9810A/HP9810A_LED_Large_Off.jpg").getImage();
    ledSmallOn = new ImageMedia("media/HP9810A/HP9810A_LED_Small_On.jpg").getImage();
    ledSmallOff = new ImageMedia("media/HP9810A/HP9810A_LED_Small_Off.jpg").getImage();

    setSize();
    System.out.println("HP9810 Mainframe loaded.");
  }
  
  public class mouseListener extends MouseAdapter
  {
    public void mousePressed(MouseEvent event)
    {
      int x = event.getX() - getInsets().left;
      int y = event.getY() - getInsets().top;
      
      // ROM block area
      if((y > 10 && y < 50) && (x >= 25 && x <= 475)) {
        int block = (x - 25) / 150 + 1;
        if(event.getButton() == MouseEvent.BUTTON1) {
          romSelector.setRomSlot("Slot" + Integer.toString(block));
          romSelector.setTitle("HP9810A ROM Blocks Slot " + Integer.toString(block));
          romSelector.setVisible(true);
        } else {
          MemoryBlock romBlock = (MemoryBlock)emu.memoryBlocks.get("Slot" + Integer.toString(block));
          if(romBlock != null) {
            instructionsWindow.setROMblock(romBlock);
            instructionsWindow.showInstructions();
          }
        }

        return;
      }

      // Overlay block area
      if((y > 275 && y < 520) && (x >= 100 && x <= 250)) {
        if(event.getButton() != MouseEvent.BUTTON1) {
          MemoryBlock romBlock = (MemoryBlock)emu.memoryBlocks.get("Slot" + Integer.toString(1));
          if(romBlock != null) {
            instructionsWindow.setROMblock(romBlock);
            instructionsWindow.showInstructions();
          }

          return;
        }
      }

      // instructions area
      if((y >= 40 && y <= 70) && (x >= 760 && x <= 990)) {
        if(event.getButton() == MouseEvent.BUTTON1) {
        } else {
          MemoryBlock romBlock = (MemoryBlock)emu.memoryBlocks.get("Block0");
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
      if(row > 7) return; // above key area?

      // calculate key column# from row-individual offset
      int col = x - keyOffsetX[row];
      if(col < 0) return; // left of key row?
      col /= keyWidth;

      if(col > 18)
        return; // right of key row?

      int keyCode = keyCodes[row][col];

      // PAPER
      if(keyCode == 0777) {
        paper(1);
        return;
      }

      if(keyCode != -1) {
        if(emu.keyLogMode) {
          console.append(Integer.toOctalString(keyCode) + " ");
          return;
        }

        if(keyCode == 041) {
          // set STP flag and request service
          ioUnit.STP = true; // STOP
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
    
  public void paint(Graphics g)
  {
    int i, j;
    int x = getInsets().left;
    int y = getInsets().top;

    backgroundImage = g.drawImage(keyboardImage, x, y, keyboardImage.getWidth(this), keyboardImage.getHeight(this), this);

    if(backgroundImage) {
      // get images of ROM modules and template
      MemoryBlock block = (MemoryBlock)emu.memoryBlocks.get("Slot1");

      if(block != null) {
        g.drawImage(block.getModule(), x + BLOCK1_X, y + BLOCK1_Y, BLOCK_W, BLOCK_H, this);
        // draw ROM template
        g.drawImage(block.getTemplate(), x + TEMPLATE_X, y + TEMPLATE_Y, TEMPLATE_W, TEMPLATE_H, this);
      }
        
      block = (MemoryBlock)emu.memoryBlocks.get("Slot2");
      if(block != null) {
        g.drawImage(block.getModule(), x + BLOCK2_X, y + BLOCK1_Y, BLOCK_W, BLOCK_H, this);
      }
        
      block = (MemoryBlock)emu.memoryBlocks.get("Slot3");
      if(block != null) {
        g.drawImage(block.getModule(), x + BLOCK3_X, y + BLOCK3_Y, BLOCK_W, BLOCK_H, this);
      }

      // draw display area
      g.drawImage(displayImage, x + DISPLAY_X, y + DISPLAY_Y, DISPLAY_W, DISPLAY_H, this);

      // draw keyboard LEDs
      displayLEDs(ioUnit.bus.display.getKeyLEDs());

      // draw display only not blanked 
      if(ioUnit.dispCounter.running()) {
        for(j = 0; j < 3; j++) {
          for(i = 0; i < 15; i++) {
            display(j, i);
          }
        }
      }

      displayPrintOutput();
      displayKeyMatrix();
    }
  }

  public void displayLEDs(int keyLEDs)
  {
    int x = getInsets().left;
    int y = getInsets().top;

    Graphics g = this.getGraphics();
    
    // STATUS
    if((keyLEDs & 0x10) != 0) {
      g.drawImage(ledLargeOn, x + STATUS_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
    } else {
      g.drawImage(ledLargeOff, x + STATUS_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
    }

    // FLOAT / FIX
    if((keyLEDs & 0x80) != 0) {
      g.drawImage(ledLargeOn, x + FLOAT_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
      g.drawImage(ledLargeOff, x + FIX_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
    } else {
      g.drawImage(ledLargeOff, x + FLOAT_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
      g.drawImage(ledLargeOn, x + FIX_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
    }

    // RUN / PRGM
    if((keyLEDs & 0x02) != 0) {
      g.drawImage(ledLargeOn, x + RUN_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
      g.drawImage(ledLargeOff, x + PRGM_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
    } else {
      g.drawImage(ledLargeOff, x + RUN_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
      g.drawImage(ledLargeOn, x + PRGM_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
    }
    
    // KEY LOG
    if((keyLEDs & 0x04) != 0) {
      g.drawImage(ledLargeOn, x + KEYLOG_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
    } else {
      g.drawImage(ledLargeOff, x + KEYLOG_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
    }
    
    // INSERT CARD
    if((keyLEDs & 0x01) != 0) {
      g.drawImage(ledLargeOn, x + CARD_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
    } else {
      g.drawImage(ledLargeOff, x + CARD_X, y + LED_LARGE_Y, LED_LARGE_WH, LED_LARGE_WH, this);
    }
    
    // check if ROM template is present
     if(((MemoryBlock)emu.memoryBlocks.get("Slot1")).getTemplate() == null) {
      if((keyLEDs & 0x20) != 0) {
        g.drawImage(ledLargeOn, x + LED1_X - 3, y + LED_LARGE_Y + 2, LED_LARGE_WH, LED_LARGE_WH, this);
      }

      if((keyLEDs & 0x40) != 0) {
        g.drawImage(ledLargeOn, x + LED2_X - 3, y + LED_LARGE_Y + 2, LED_LARGE_WH, LED_LARGE_WH, this);
      }

      if((keyLEDs & 0x08) != 0) {
        g.drawImage(ledLargeOn, x + LED3_X - 3, y + LED_LARGE_Y + 2, LED_LARGE_WH, LED_LARGE_WH, this);
      }
    } else {
      // LED 1
      g.drawImage((keyLEDs & 0x20) != 0 ? ledSmallOn : ledSmallOff, x + LED1_X, y + LED_SMALL_Y, LED_SMALL_WH, LED_SMALL_WH, this);

      // LED 2
      g.drawImage((keyLEDs & 0x40) != 0 ? ledSmallOn : ledSmallOff, x + LED2_X, y + LED_SMALL_Y, LED_SMALL_WH, LED_SMALL_WH, this);

      // LED 3
      g.drawImage((keyLEDs & 0x08) != 0 ? ledSmallOn : ledSmallOff, x + LED3_X, y + LED_SMALL_Y, LED_SMALL_WH, LED_SMALL_WH, this);
    }
  }
  
  public void display(int reg, int i)
  {
    int[][] displayBuffer = ioUnit.bus.display.getDisplayBuffer();
    int x = getInsets().left;
    int y = getInsets().top;
    int x1, y1, y2, segments;
    Graphics g = this.getGraphics();

    if(backgroundImage && g != null) {
      segments = displayBuffer[reg][i];

      // graphic digit position
      x += DISPLAY_X + LED_X + i * (LED_SEGMENT_SIZE + 10);
      y += DISPLAY_Y + LED_Y + reg * (2 * LED_SEGMENT_SIZE + 30);

      // segment end points
      x1 = x + LED_SEGMENT_SIZE + 2;
      y1 = y + LED_SEGMENT_SIZE;
      y2 = y1 + LED_SEGMENT_SIZE;


      g.setColor(ledBack);
      g.fillRect(x, y, LED_SEGMENT_SIZE + 5, 2 * LED_SEGMENT_SIZE+1);
      g.setColor(ledRed);

      if(segments == 0)
        return;

      // segment a
      if((segments & 0x40) != 0) {
        g.drawLine(x+3, y, x1+1, y);
      }

      // segment b
      if((segments & 0x20) != 0) {
        g.drawLine(x1+2, y, x1+1, y1);
      }

      // segment c
      if((segments & 0x02) != 0) {
        g.drawLine(x1+1, y1, x1, y2);
      }

      // segment d
      if((segments & 0x04) != 0) {
        g.drawLine(x+1, y2, x1-1, y2);
      }

      // segment e
      if((segments & 0x08) != 0) {
        g.drawLine(x+1, y1, x, y2);
      }

      // segment f
      if((segments & 0x80) != 0) {
        g.drawLine(x+2, y, x+1, y1);
      }

      // segment g
      if((segments & 0x10) != 0) {
        g.drawLine(x+2, y1, x1, y1);
      }

      // segment p
      if((segments & 0x01) != 0) {
        g.drawLine(x1+2, y2, x1+2, y2);
      }
    }
  }
}