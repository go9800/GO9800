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
 * 08.11.2006 Class derived from HP9810A and HP9830A mainframe 
 * 14.11.2006 Bugfix: Remove use of pseudo SRQ line 16 and reset of SSF
 * 22.11.2006 Removed check of DEN in display() method
 * 24.11.2006 Changed loading of images from file to JAR
 * 10.12.2006 Rel. 0.21 Bugfix: Start print sound only if one line completely printed.
 * 15.12.2006 Rel. 0.21 Bugfix: display position 15 is now correctly repainted.
 * 15.12.2006 Rel. 0.21 New: window setLocation
 * 27.01.2007 Rel. 0.30 Bugfix: STOP key now clears SSF
 * 03.02.2007 Rel. 0.30 New: Ctrl+T to start/stop opcode timing measurement
 * 03.02.2007 Rel. 0.30 Changed Ctrl+D: now toggles disasmOutput visibility
 * 26.02.2007 Rel. 0.30 Added ROMselector
 * 26.02.2007 Rel. 0.30 Added use of configuration file and removed memory initialization
 * 10.03.2007 Rel. 0.30 Added machine reset (Ctrl+R) 
 * 06.04.2007 Rel. 1.00 Added handling of MAW (memory[01377])
 * 09.04.2007 Rel. 1.00 Code reorganized for usage of class HP9800Mainframe
 * 24.08.2007 Rel. 1.20 Use keyPressed() from HP9800Mainframe
 * 05.09.2007 Rel. 1.20 Bugfix: DEN is now longer evaluated in display() method
 * 24.09.2007 Rel. 1.20 Changed display blanking control from fixed timer to instruction counter
 * 13.12.2007 Rel. 1.20 Don't release keyboard by mouseReleased() or keyReleased(). This is now done after 5ms by KeyboardInterface.run()   
 * 18.01.2009 Rel. 1.40 Added display of InstructionsWindow with right mouse click on ROM block
 * 18.03.2009 Rel. 1.40 Added display of InstructionsWindow with right mouse click on handle of top cover
 * 25.02.2012 Rel. 1.60 Added display of keyboard overlay in InstructionsWindow
 * 21.10.2017 Rel. 2.10 Added Graphics scaling using class Graphics2D
 * 24.10.2017 Rel. 2.10 Added display of click areas, changed size and behaviour (left-click) of ROM template and instructions click areas
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 * 10.11.2017 Tel. 2.10 Added dynamic image scaling and processing
 */

package io.HP9820A;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.ImageObserver;

import io.*;
import emu98.*;

public class HP9820AMainframe extends HP9800Mainframe
{
  private static final long serialVersionUID = 1L;

  static final int[] HP9820keyOffsetX = {80, 80, 80, 80, 80, 80, 80, 80}; // offset of leftmost key in row
  static final int[][] HP9820keyCodes = {
      {077, 040, -1, 0110, 0115, -1, 0122, 0127, -1, 060, 060, 056, 0135, 0135, -1, 0133, 0133, 041, 002, 002},
      {047, 017, -1, 0107, 0114, -1, 0121, 0126, -1, 053, 061, 062, 063, 073, -1, 0137, 072, 043, 037, 0100},
      {046, 016, -1, 0106, 0113, -1, 0120, 0125, -1, 055, 064, 065, 066, 054, -1, 0103, 0132, 075, 036, 033},
      {045, 011, -1, 0105, 0112, -1, 0117, 0124, -1, 052, 067, 070, 071, 042, -1, 0102, 0131, 074, 034, 035},
      {044, 010, -1, 0104, 0111, -1, 0116, 0123, -1, 057, 0134, 0136, 050, 051, -1, 0101, 0130, 076, 031, 032},
      {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1},
      {020, 021, -1, 0141, 0142, 0140, -1, 0144, 0143, -1, 003, -1, 024, 025, 027, 026, -1, 004, 005, 030},
      {0145, -1, -1, -1, -1, -1, -1, 022, 023, -1, -1, -1, -1, -1, -1, 0777, -1, -1, -1, -1},
  };

  // the following constants are used in HP9820A only
  
  // ROM template sizes
  public int TEMPLATE1_X = 73;
  public int TEMPLATE1_Y = 301;
  public double TEMPLATE1_S = -0.025;
  public int TEMPLATE2_X = 197;
  public int TEMPLATE2_Y = 301;
  public double TEMPLATE2_S = -0.020;
  public int TEMPLATE3_X = 320;
  public int TEMPLATE3_Y = 301;
  public double TEMPLATE3_S = -0.010;
  public int TEMPLATE_W = 111;
  public int TEMPLATE_H = 224;
  
  // click area for ROM block exchange
  public int ROM_X = 25;
  public int ROM_Y = 5;
  public int ROM_W = 150;
  public int ROM_H = 45;
  // click area for keyboard overlay
  public int OVERLAY_X = 60;
  public int OVERLAY_Y = 280;
  public int OVERLAY_W = 123;
  public int OVERLAY_H = 30;
  // click area for instructions window
  public int INSTRUCTIONS_X = 765;
  public int INSTRUCTIONS_Y = 40;
  public int INSTRUCTIONS_W = 230;
  public int INSTRUCTIONS_H = 30;
  

  public HP9820AMainframe(Emulator emu, String machine)
  {
    super(emu, machine);

    keyWidth = (920 - 80) / 20;  // with of one key area in pix
    keyOffsetY = 523;            // offset of lowest key row from image boarder 
    keyOffsetX = HP9820keyOffsetX;
    keyCodes = HP9820keyCodes;
    
    DISPLAY_X = 50;
    DISPLAY_Y = 109;
    DISPLAY_W = 85;
    DISPLAY_H = 6;
    LED_X = +45;
    LED_Y = +4;

    ioUnit.line10_20 = true;  // set 10/20 flag

    // create display
    ioUnit.bus.display = new HP9820DisplayInterface(this);

    // create keyboard
    ioUnit.bus.keyboard = new HP9820KeyboardInterface(12, this);
  }
  
  public HP9820AMainframe(Emulator emu)
  {
    this(emu, "HP9820A");
    addMouseListener(new mouseListener());

    // check if RW memory extension is installed
    int MAW;
    // check every block of 02000 words
    for(MAW = 032000; MAW > 020000; MAW -= 002000)
    {
      if(memory[MAW-1].isRW)
        break;
    }

    // set MAW
    memory[01377] = new Memory(false, 01377, MAW);

    // create card reader
    ioUnit.bus.cardReader = new HP9800MagneticCardReaderInterface(this);

    romSelector.addRomButton("media/HP9820A/HP11XXXX_Block.jpg", "HP11XXXX");
    romSelector.addRomButton("media/HP9820A/HP11220A_Block.jpg", "HP11220A");
    romSelector.addRomButton("media/HP9820A/HP11221A_Block.jpg", "HP11221A");
    romSelector.addRomButton("media/HP9820A/HP11222A_Block.jpg", "HP11222A");
    romSelector.addRomButton("media/HP9820A/HP11223A_Block.jpg", "HP11223A");

    keyboardImageMedia = new ImageMedia("media/HP9820A/HP9820A_Keyboard.png");
    blockImageMedia = new ImageMedia("media/HP9820A/HP9820A_Module.png");

    setSize();
    System.out.println("HP9820 Mainframe loaded.");
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
          romSelector.setTitle("HP9820A ROM Blocks Slot " + Integer.toString(block));
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
      if(row > 7) return; // above key area?

      // calculate key column# from row-individual offset
      int col = x - keyOffsetX[row];
      if(col < 0) return; // left of key row?
      col /= keyWidth;

      if(col > 19)
        return; // right of key row?

      int keyCode = keyCodes[row][col];

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

        if(keyCode == STOP_KEYCODE) {
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
  	AffineTransform g2dSaveTransform;
  	int i, j;
  	int x = 0, y = 0; // positioning is done by g2d.translate()

  	// normalize frame and get scaling parameters
  	super.paint(g);
  	
  	// scale keyboard image to normal size
  	keyboardImage = keyboardImageMedia.getScaledImage((int)(KEYB_W * widthScale), (int)(KEYB_H * heightScale));
  	backgroundImage = g2d.drawImage(keyboardImage, x, y, KEYB_W, KEYB_H, this);
  	
    if(!backgroundImage)  // dont draw modules and templates before keyboard is ready
    	return;
    
    blockImage = blockImageMedia.getScaledImage((int)(BLOCK_W * widthScale), (int)(BLOCK_H * heightScale));
    g2dSaveTransform = g2d.getTransform();  // save current transformation, changed by ROM blocks

  	// get images of ROM modules and template
  	MemoryBlock block = (MemoryBlock)config.memoryBlocks.get("Slot1");

  	if(block != null) {
  		// draw universal ROM module
  		moduleImage = block.getUniModule((int)(MODULE_W * widthScale), (int)(MODULE_H * heightScale));  // get scaled image

  		if(moduleImage != null) {
  			g2d.shear(BLOCK1_S, 0.);  // negative horizontal shear for correct perspective
  			g2d.drawImage(blockImage, x + BLOCK1_X, y + BLOCK1_Y, BLOCK_W, BLOCK_H, this);  // draw dummy module

  			// draw ROM module label with transparence, scaled and processed for brightness and contrast
  			moduleImage = block.getUniModule(1f, 50f);  // get processed image, based on scaled image
  			g2d.drawImage(moduleImage, x + BLOCK1_X + 1, y + BLOCK1_Y + 1, MODULE_W, MODULE_H, this);  // draw module label
  			g2d.setTransform(g2dSaveTransform);  // restore original transformation

  			// draw ROM template with transparence
  			g2d.shear(TEMPLATE1_S, 0.);  // negative horizontal shear for correct perspective
  			templateImage = block.getUniTemplate((int)(TEMPLATE_W * widthScale), (int)(TEMPLATE_H * heightScale));  // first get scaled image
  			templateImage = block.getUniTemplate(1f, 80f);  // then get processed image, based on scaled image
  			g2d.drawImage(templateImage, x + TEMPLATE1_X, y + TEMPLATE1_Y, TEMPLATE_W, TEMPLATE_H, this);

  			g2d.setTransform(g2dSaveTransform);  // restore original transformation
  		}
  	}

  	block = (MemoryBlock)config.memoryBlocks.get("Slot2");
  	if(block != null) {
  		// draw universal ROM module
  		moduleImage = block.getUniModule((int)(MODULE_W * widthScale), (int)(MODULE_H * heightScale));  // get scaled image

  		if(moduleImage != null) {
  			g2d.shear(BLOCK2_S, 0.);  // negative horizontal shear for correct perspective
  			g2d.drawImage(blockImage, x + BLOCK2_X, y + BLOCK2_Y, BLOCK_W, BLOCK_H, this);  // draw dummy module

  			// draw ROM module label with transparence, scaled and processed for brightness and contrast
  			moduleImage = block.getUniModule(1f, 50f);  // get processed image, based on scaled image
  			g2d.drawImage(moduleImage, x + BLOCK2_X + 1, y + BLOCK2_Y + 1, MODULE_W, MODULE_H, this);
  			g2d.setTransform(g2dSaveTransform);  // restore original transformation

  			// draw ROM template with transparence
  			g2d.shear(TEMPLATE2_S, 0.);  // negative horizontal shear for correct perspective
  			templateImage = block.getUniTemplate((int)(TEMPLATE_W * widthScale), (int)(TEMPLATE_H * heightScale));  // get scaled image
  			templateImage = block.getUniTemplate(1f, 80f);  // get processed image, based on scaled image
  			g2d.drawImage(templateImage, x + TEMPLATE2_X, y + TEMPLATE2_Y, TEMPLATE_W, TEMPLATE_H, this);

  			g2d.setTransform(g2dSaveTransform);  // restore original transformation
  		}
  	}

  	block = (MemoryBlock)config.memoryBlocks.get("Slot3");
  	if(block != null) {
  		// draw universal ROM module
  		moduleImage = block.getUniModule((int)(MODULE_W * widthScale), (int)(MODULE_H * heightScale));

  		if(moduleImage != null) {
  			g2d.shear(BLOCK3_S, 0.);  // negative horizontal shear for correct perspective
  			g2d.drawImage(blockImage, x + BLOCK3_X, y + BLOCK3_Y, BLOCK_W, BLOCK_H, this);  // draw dummy module

  			// draw ROM module label with transparence, scaled and processed for brightness and contrast
  			moduleImage = block.getUniModule(1f, 50f);  // get processed image, based on scaled image
  			g2d.drawImage(moduleImage, x + BLOCK3_X + 1, y + BLOCK3_Y + 1, MODULE_W, MODULE_H, this);
  			g2d.setTransform(g2dSaveTransform);  // restore original transformation

  			// draw ROM template with transparence
  			g2d.shear(TEMPLATE3_S, 0.);  // negative horizontal shear for correct perspective
  			templateImage = block.getUniTemplate((int)(TEMPLATE_W * widthScale), (int)(TEMPLATE_H * heightScale));  // get scaled image
  			templateImage = block.getUniTemplate(1f, 80f);  // get processed image, based on scaled image
  			g2d.drawImage(templateImage, x + TEMPLATE3_X, y + TEMPLATE3_Y, TEMPLATE_W, TEMPLATE_H, this);

  			g2d.setTransform(g2dSaveTransform);  // restore original transformation
  		}
  	}

  	// draw display background area
  	g2d.setColor(ledBack);
  	g2d.fillRect(x + DISPLAY_X, y + DISPLAY_Y, DISPLAY_W + 16 * (6 * LED_DOT_SIZE + 2), DISPLAY_H + 7 * LED_DOT_SIZE);

  	// draw display only not blanked 
  	if(ioUnit.dispCounter.running()) {
  		for(i = 0; i < 5; i++) {
  			for(j = 0; j < 16; j++) {
  				display(i, j);
  			}
  		}
  	}

  	displayPrintOutput();
  	displayKeyMatrix();
  }

  public void displayClickAreas()
  {
    float[] dashArray = {4f, 4f};
    int i;
    
    BasicStroke stroke = new BasicStroke(1, 0, 0, 1f, dashArray, 0f);
    g2d.setStroke(stroke);
    g2d.setColor(Color.white);

    for(i = 0; i < 3; i++) {
    	g2d.drawRect(ROM_X + i * ROM_W, ROM_Y, ROM_W, ROM_H);
    }
    
    for(i = 0; i < 3; i++) {
    	g2d.drawRect(OVERLAY_X + i * OVERLAY_W, OVERLAY_Y, OVERLAY_W, OVERLAY_H);
    }
    
    g2d.drawRect(INSTRUCTIONS_X, INSTRUCTIONS_Y, INSTRUCTIONS_W, INSTRUCTIONS_H);
  }

  public void display(int col, int chr)
  {
    if(backgroundImage && this.getGraphics() != null) {
      int[][] displayBuffer = ioUnit.bus.display.getDisplayBuffer();
      int x = DISPLAY_X + LED_X + chr * (6 * LED_DOT_SIZE + 2)  + col * LED_DOT_SIZE;
      int y = DISPLAY_Y + LED_Y;
      int ledColumn = displayBuffer[col][chr];

      g2d.setColor(ledBack);
      g2d.fillRect(x - 1 , y - 1, LED_DOT_SIZE + 2, 7 * LED_DOT_SIZE + 2); // draw character background slightly greater
      g2d.setColor(ledRed);

      for(int j = 6; j >= 0; j--) {
        if((ledColumn & 1) != 0)
        	g2d.fillRect(x, y + j * LED_DOT_SIZE, LED_DOT_SIZE - 1, LED_DOT_SIZE - 1);
        ledColumn >>= 1;
      }
    }
  }
}
