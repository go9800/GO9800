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
 * 21.10.2017 Rel. 2.10 Added Graphics scaling using class Graphics2D
 * 24.10.2017 Rel. 2.10 Added display of click areas, changed size and behaviour (left-click) of ROM template and instructions click areas
 * 28.10.2017 Rel. 2.10 Added new linking between Mainframe and other components
 * 10.11.2017 Rel. 2.10 Added dynamic image scaling and processing
 * 13.11.2017 Rel. 2.10 Added HP11265A Cassette Memory keyboard overlay
 * 18.11.2017 Rel. 2.10 Bugfix: display(), displayLEDs(), displayClickAreas() now get actual Graphics2D to avoid problems during update()
 */

package io.HP9810A;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;

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
  
  // ROM template size
  int TEMPLATE1_X = 117;
  int TEMPLATE1_Y = 271;
  int TEMPLATE_W = 147;
  int TEMPLATE_H = 254;
  public double TEMPLATE_S = -0.025;
  int TEMPLATE2_X = 262;
  int TEMPLATE2_Y = 291;
  int TEMPLATE2_W = 645;
  int TEMPLATE2_H = 252;
  
  // click area for ROM block exchange
  int ROM_X = 25;
  int ROM_Y = 5;
  int ROM_W = 150;
  int ROM_H = 45;
  // click area for keyboard overlay
  int OVERLAY_X = 105;
  int OVERLAY_Y = 255;
  int OVERLAY_W = 150;
  int OVERLAY_H = 60;
  // click area for instructions window
  int INSTRUCTIONS_X = 765;
  int INSTRUCTIONS_Y = 40;
  int INSTRUCTIONS_W = 230;
  int INSTRUCTIONS_H = 30;

  // Indicator LEDs
  int LED1_X = 134;
  int LED2_X = 175;
  int LED3_X = 217;

  int STATUS_X = 342;
  int FLOAT_X = 426;
  int FIX_X = 468;
  int RUN_X = 552;
  int PRGM_X = 595;
  int KEYLOG_X = 679;
  int CARD_X = 848;
  int LED_LAMP_Y = 274;
  int LED_WH = 18;
  
  public int LED_SEGMENT_SIZE = 4;


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
    ioUnit.bus.keyboard = new HP9810KeyboardInterface(16, this);

    romSelector.addRomButton("media/HP9810A/HP11XXXX_Slot.png", "HP11XXXX");
    romSelector.addRomButton("media/HP9810A/HP11210A_Module.png", "HP11210A");
    romSelector.addRomButton("media/HP9810A/HP11211A_Module.png", "HP11211A");
    romSelector.addRomButton("media/HP9810A/HP11213A_Module.png", "HP11213A");
    romSelector.addRomButton("media/HP9810A/HP11214A_Module.png", "HP11214A");
    romSelector.addRomButton("media/HP9810A/HP11215A_Module.png", "HP11215A");
    romSelector.addRomButton("media/HP9810A/HP11252A_Module.png", "HP11252A");
    romSelector.addRomButton("media/HP9810A/HP11261A_Module.png", "HP11261A");
    romSelector.addRomButton("media/HP9810A/HP11262A_Module.png", "HP11262A");
    romSelector.addRomButton("media/HP9810A/HP11266A_Module.png", "HP11266A");
    romSelector.addRomButton("media/HP9810A/HP11267A_Module.png", "HP11267A");

    keyboardImageMedia = new ImageMedia("media/HP9810A/HP9810A_Keyboard.png", imageController);
    displayImageMedia = new ImageMedia("media/HP9810A/HP9810A_Display.png", imageController);
    blockImageMedia = new ImageMedia("media/HP9810A/HP9810A_Module.png", imageController);

    ledOnImageMedia = new ImageMedia("media/HP9810A/HP9810A_LED_On.png", imageController);
    ledOffImageMedia = new ImageMedia("media/HP9810A/HP9810A_LED_Off.png", imageController);
    ledSmallOnImageMedia = new ImageMedia("media/HP9810A/HP9810A_LED_Small_On.png", imageController);
    ledSmallOffImageMedia = new ImageMedia("media/HP9810A/HP9810A_LED_Small_Off.png", imageController);
    
    setSize();
    System.out.println("HP9810 Mainframe loaded.");
  }
  
  public class mouseListener extends MouseAdapter
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
          romSelector.setTitle("HP9810A ROM Blocks Slot " + Integer.toString(block));
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
      if((y >= OVERLAY_Y && y <= OVERLAY_Y + OVERLAY_H) && (x >= OVERLAY_X && x <= OVERLAY_X + OVERLAY_W)) {
        if(event.getButton() == MouseEvent.BUTTON1) {
          MemoryBlock romBlock = (MemoryBlock)config.memoryBlocks.get("Slot" + Integer.toString(1));
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
  	AffineTransform g2dSaveTransform;
  	Image moduleImage;
  	int i, j;
  	int x = 0, y = 0; // positioning is done by g2d.translate()

  	// normalize frame and get scaling parameters
  	super.paint(g);
  	
  	// scale keyboard image to normal size
  	keyboardImage = keyboardImageMedia.getScaledImage((int)(KEYB_W * widthScale), (int)(KEYB_H * heightScale));
  	backgroundImage = g2d.drawImage(keyboardImage, x, y, KEYB_W, KEYB_H, this);
  	displayImage = displayImageMedia.getScaledImage((int)(DISPLAY_W * widthScale), (int)(DISPLAY_H * heightScale));

  	if(!backgroundImage)  // don't draw modules and templates before keyboard is ready
  		return;
  	
  	// draw display area
  	backgroundImage = g2d.drawImage(displayImage, x + DISPLAY_X, y + DISPLAY_Y, DISPLAY_W, DISPLAY_H, this);

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
  			moduleImage = block.getUniModule(1.1f, 25f);  // get processed image, based on scaled image
  			g2d.drawImage(moduleImage, x + BLOCK1_X + 1, y + BLOCK1_Y + 1, MODULE_W, MODULE_H, this);  // draw module label
  			g2d.setTransform(g2dSaveTransform);  // restore original transformation

  			// draw ROM template with transparence
  			g2d.shear(TEMPLATE_S, 0.);  // negative horizontal shear for correct perspective
  			templateImage = block.getUniTemplate((int)(TEMPLATE_W * widthScale), (int)(TEMPLATE_H * heightScale));  // first get scaled image
  			templateImage = block.getUniTemplate(1f, 50f);  // then get processed image, based on scaled image
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
  			moduleImage = block.getUniModule(1.1f, 25f);  // get processed image, based on scaled image
  			g2d.drawImage(moduleImage, x + BLOCK2_X + 1, y + BLOCK2_Y + 1, MODULE_W, MODULE_H, this);
  			g2d.setTransform(g2dSaveTransform);  // restore original transformation
  			
  			if(block.getTitle().indexOf("CASSETTE_MEMORY") >= 0) {
    			// draw ROM template with transparence
    			templateImage = block.getUniTemplate((int)(TEMPLATE2_W * widthScale), (int)(TEMPLATE2_H * heightScale));  // first get scaled image
    			templateImage = block.getUniTemplate(1f, 50f);  // then get processed image, based on scaled image
    			g2d.drawImage(templateImage, x + TEMPLATE2_X, y + TEMPLATE2_Y, TEMPLATE2_W, TEMPLATE2_H, this);

    			g2d.setTransform(g2dSaveTransform);  // restore original transformation
  			}
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
  			moduleImage = block.getUniModule(1.1f, 25f);  // get processed image, based on scaled image
  			g2d.drawImage(moduleImage, x + BLOCK3_X + 1, y + BLOCK3_Y + 1, MODULE_W, MODULE_H, this);
  			g2d.setTransform(g2dSaveTransform);  // restore original transformation
  			
  			if(block.getTitle().indexOf("CASSETTE_MEMORY") >= 0) {
    			// draw ROM template with transparence
    			templateImage = block.getUniTemplate((int)(TEMPLATE2_W * widthScale), (int)(TEMPLATE2_H * heightScale));  // first get scaled image
    			templateImage = block.getUniTemplate(1f, 50f);  // then get processed image, based on scaled image
    			g2d.drawImage(templateImage, x + TEMPLATE2_X, y + TEMPLATE2_Y, TEMPLATE2_W, TEMPLATE2_H, this);

    			g2d.setTransform(g2dSaveTransform);  // restore original transformation
  			}
  		}
  	}
  	
  	// draw keyboard LEDs
  	displayLEDs(g2d, ioUnit.bus.display.getKeyLEDs());
 	
  	// draw display only not blanked 
  	if(ioUnit.dispCounter.running()) {
  		for(j = 0; j < 3; j++) {
  			for(i = 0; i < 15; i++) {
  				display(g2d, j, i);
  			}
  		}
  	}

  	displayPrintOutput(g2d);
  	displayKeyMatrix(g2d);
  }
  
  public void displayClickAreas(Graphics2D g2d)
  {
    float[] dashArray = {4f, 4f};
    int i;
    
    if(g2d == null)
    	g2d = getG2D(getGraphics());  // get current graphics if not given by paint()

    BasicStroke stroke = new BasicStroke(1, 0, 0, 1f, dashArray, 0f);
    g2d.setStroke(stroke);
    g2d.setColor(Color.white);

    for(i = 0; i < 3; i++) {
    	g2d.drawRect(ROM_X + i * ROM_W, ROM_Y, ROM_W, ROM_H);
    }

    g2d.drawRect(OVERLAY_X, OVERLAY_Y, OVERLAY_W, OVERLAY_H);
    g2d.drawRect(INSTRUCTIONS_X, INSTRUCTIONS_Y, INSTRUCTIONS_W, INSTRUCTIONS_H);
  }

  public void displayLEDs(Graphics2D g2d, int keyLEDs)
  {
    int x = 0, y = 0; // positioning is done by g2d.translate()
    
    if(g2d == null)
    	g2d = getG2D(getGraphics());  // get current graphics if not given by paint()

    ledOn = ledOnImageMedia.getScaledImage((int)(LED_WH * widthScale), (int)(LED_WH * heightScale));
    ledOff = ledOffImageMedia.getScaledImage((int)(LED_WH * widthScale), (int)(LED_WH * heightScale));
    
    // STATUS
    if((keyLEDs & 0x10) != 0) {
      g2d.drawImage(ledOn, x + STATUS_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
    } else {
      g2d.drawImage(ledOff, x + STATUS_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
    }

    // FLOAT / FIX
    if((keyLEDs & 0x80) != 0) {
      g2d.drawImage(ledOn, x + FLOAT_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
      g2d.drawImage(ledOff, x + FIX_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
    } else {
      g2d.drawImage(ledOff, x + FLOAT_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
      g2d.drawImage(ledOn, x + FIX_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
    }

    // RUN / PRGM
    if((keyLEDs & 0x02) != 0) {
      g2d.drawImage(ledOn, x + RUN_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
      g2d.drawImage(ledOff, x + PRGM_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
    } else {
      g2d.drawImage(ledOff, x + RUN_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
      g2d.drawImage(ledOn, x + PRGM_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
    }
    
    // KEY LOG
    if((keyLEDs & 0x04) != 0) {
      g2d.drawImage(ledOn, x + KEYLOG_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
    } else {
      g2d.drawImage(ledOff, x + KEYLOG_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
    }
    
    // INSERT CARD
    if((keyLEDs & 0x01) != 0) {
      g2d.drawImage(ledOn, x + CARD_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
    } else {
      g2d.drawImage(ledOff, x + CARD_X, y + LED_LAMP_Y, LED_WH, LED_WH, this);
    }
    
    // check if ROM template is present
     if(((MemoryBlock)config.memoryBlocks.get("Slot1")).getUniTemplate() == null) {
      if((keyLEDs & 0x20) != 0) {
        g2d.drawImage(ledOn, x + LED1_X - 1, y + LED_LAMP_Y, LED_WH - 1, LED_WH - 1, this);
      }

      if((keyLEDs & 0x40) != 0) {
        g2d.drawImage(ledOn, x + LED2_X - 1, y + LED_LAMP_Y, LED_WH - 1, LED_WH - 1, this);
      }

      if((keyLEDs & 0x08) != 0) {
        g2d.drawImage(ledOn, x + LED3_X - 1, y + LED_LAMP_Y, LED_WH - 1, LED_WH - 1, this);
      }
    } else {
      ledOn = ledSmallOnImageMedia.getScaledImage((int)(LED_WH * widthScale), (int)(LED_WH * heightScale));
      ledOff = ledSmallOffImageMedia.getScaledImage((int)(LED_WH * widthScale), (int)(LED_WH * heightScale));

      // LED 1
      //g2d.drawImage((keyLEDs & 0x20) != 0 ? ledOn : ledOff, x + LED1_X, y + LED_LAMP_Y + 1, LED_WH - 1, LED_WH - 1, this);
      g2d.drawImage((keyLEDs & 0x20) != 0 ? ledOn : ledOff, x + LED1_X + 2, y + LED_LAMP_Y + 4, LED_WH - 7, LED_WH - 7, this);

      // LED 2
      g2d.drawImage((keyLEDs & 0x40) != 0 ? ledOn : ledOff, x + LED2_X + 2, y + LED_LAMP_Y + 4, LED_WH - 7, LED_WH - 7, this);

      // LED 3
      g2d.drawImage((keyLEDs & 0x08) != 0 ? ledOn : ledOff, x + LED3_X + 2, y + LED_LAMP_Y + 4, LED_WH - 7, LED_WH - 7, this);
    }
  }
  
  public void display(Graphics2D g2d, int reg, int i)
  {
    int x = 0, y = 0; // positioning is done by g2d.translate()
    
    if(backgroundImage && this.getGraphics() != null) {
      if(g2d == null)
      	g2d = getG2D(getGraphics());  // get current graphics if not given by paint()

      int[][] displayBuffer = ioUnit.bus.display.getDisplayBuffer();
      int x1, y1, y2, segments;

      segments = displayBuffer[reg][i];

      // graphic digit position
      x += DISPLAY_X + LED_X + i * (LED_SEGMENT_SIZE + 10);
      y += DISPLAY_Y + LED_Y + reg * (2 * LED_SEGMENT_SIZE + 30);

      // segment end points
      x1 = x + LED_SEGMENT_SIZE + 2;
      y1 = y + LED_SEGMENT_SIZE;
      y2 = y1 + LED_SEGMENT_SIZE;


      g2d.setColor(ledBack);
      g2d.fillRect(x - 1, y - 1, LED_SEGMENT_SIZE + 7, 2 * LED_SEGMENT_SIZE + 3); // draw digit background slightly greater than segment area
      g2d.setColor(ledRed);

      if(segments == 0)
        return;
      
  		// enable antialiasing for higher quality of line graphics
  		g2d.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));

      /* Stroking to simulate single LED dots is not really useful
      float[] dashArray = {0.9f, 0.1f};
      BasicStroke stroke = new BasicStroke(1, 0, 0, 1, dashArray, 0);
      g2d.setStroke(stroke);
      */
      
      // segment a
      if((segments & 0x40) != 0) {
        g2d.drawLine(x+3, y, x1+1, y);
      }

      // segment b
      if((segments & 0x20) != 0) {
        g2d.drawLine(x1+2, y, x1+1, y1);
      }

      // segment c
      if((segments & 0x02) != 0) {
        g2d.drawLine(x1+1, y1, x1, y2);
      }

      // segment d
      if((segments & 0x04) != 0) {
        g2d.drawLine(x+1, y2, x1-1, y2);
      }

      // segment e
      if((segments & 0x08) != 0) {
        g2d.drawLine(x+1, y1, x, y2);
      }

      // segment f
      if((segments & 0x80) != 0) {
        g2d.drawLine(x+2, y, x+1, y1);
      }

      // segment g
      if((segments & 0x10) != 0) {
        g2d.drawLine(x+2, y1, x1, y1);
      }

      // segment p
      if((segments & 0x01) != 0) {
        g2d.drawLine(x1+2, y2, x1+2, y2);
      }

  		// disable antialiasing for higher speed
  		g2d.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF));
    }
  }
}
