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
 * 26.05.2006 Added 100ms delay in keyReleased() if STOP key is pressed 
 * 27.05.2006 Added output of fan sound
 * 28.05.2006 Added bitmap of keyboard and mouse input
 * 13.05.2006 Added tapeDevice, key column 20 for cassette OPEN, keycodes for REWIND+OPEN
 * 31.07.2006 Changed Mainframe parameter to Emulator
 * 31.07.2006 Added switching of disassembler mode (Ctrl+D, Ctrl+N)
 * 13.08.2006 Added LED matrix display
 * 29.08.2006 Changed data directory structure
 * 05.09.2006 Moved instantiation of system RWM from emulator class
 * 07.09.2006 Implementing interface Mainframe
 * 14.09.2006 Added display() method for single character output
 * 20.09.2006 Optimized LED matrix output (character spacing, background color)
 * 24.09.2006 Bugfix: Clear SSI if key released
 * 04.10.2006 Moved beeper to new class HP9830Beeper inherited from MagneticCardReader
 * 29.10.2006 Changed sound output to Sound class 
 * 05.11.2006 Changed Ctrl+D: opens disasm dialog instead starting the disassembler  
 * 09.11.2006 Bugfix: fillRect of ledMatrix with backGround color was too small by 1 
 * 24.11.2006 Changed loading of images from file to JAR
 * 15.12.2006 Rel. 0.21 New: window setLocation
 * 05.01.2007 Rel. 0.23 Changed setTapeDevice() to draw tape status indicators
 * 03.02.2007 Rel. 0.30 New: Ctrl+T to start/stop opcode timing measurement
 * 03.02.2007 Rel. 0.30 Changed Ctrl+D: now toggles disasmOutput visibility
 * 06.03.2007 Rel. 0.30 Added use of configuration file and removed memory initialization 
 * 10.03.2007 Rel. 0.30 Added ROMselector 
 * 10.03.2007 Rel. 0.30 Added machine reset (Ctrl+R) 
 * 09.04.2007 Rel. 1.00 Code reorganized for usage of class HP9800Mainframe
 * 17.07.2007 Rel. 1.20 Added use of keyCode Hashtable
 * 24.09.2007 Rel. 1.20 Changed display blanking control from fixed timer to instruction counter
 * 13.12.2007 Rel. 1.20 Don't release keyboard by mouseReleased() or keyReleased(). This is now done after 5ms by KeyboardInterface.run()   
 */

package io.HP9830A;

import io.*;

import java.awt.*;
import java.awt.event.*;

import emu98.*;

public class HP9830AMainframe extends HP9800Mainframe
{
  private static final long serialVersionUID = 1L;

  static int ledMatrixValues[][] = {
    {0x3e, 0x41, 0x5d, 0x55, 0x3c}, // @ 
    {0x3f, 0x48, 0x48, 0x48, 0x3f}, // A 
    {0x7f, 0x49, 0x49, 0x49, 0x36}, // B
    {0x3e, 0x41, 0x41, 0x41, 0x22}, // C
    {0x41, 0x7f, 0x41, 0x41, 0x3e}, // D
    {0x7f, 0x49, 0x49, 0x49, 0x41}, // E
    {0x7f, 0x48, 0x48, 0x48, 0x40}, // F
    {0x3e, 0x41, 0x41, 0x45, 0x47}, // G
    {0x7f, 0x08, 0x08, 0x08, 0x7f}, // H
    {0x00, 0x41, 0x7f, 0x41, 0x00}, // I
    {0x02, 0x01, 0x01, 0x01, 0x7e}, // J
    {0x7f, 0x08, 0x14, 0x22, 0x41}, // K
    {0x7f, 0x01, 0x01, 0x01, 0x01}, // L
    {0x7f, 0x20, 0x18, 0x20, 0x7f}, // M
    {0x7f, 0x10, 0x08, 0x04, 0x7f}, // N
    {0x3e, 0x41, 0x41, 0x41, 0x3e}, // O
    {0x7f, 0x48, 0x48, 0x48, 0x30}, // P
    {0x3e, 0x41, 0x45, 0x42, 0x3d}, // Q
    {0x7f, 0x48, 0x4c, 0x4a, 0x31}, // R
    {0x32, 0x49, 0x49, 0x49, 0x26}, // S
    {0x40, 0x40, 0x7f, 0x40, 0x40}, // T
    {0x7e, 0x01, 0x01, 0x01, 0x7e}, // U
    {0x70, 0x0c, 0x03, 0x0c, 0x70}, // V
    {0x7f, 0x02, 0x0c, 0x02, 0x7f}, // W
    {0x63, 0x14, 0x08, 0x14, 0x63}, // X
    {0x60, 0x10, 0x0f, 0x10, 0x60}, // Y
    {0x43, 0x45, 0x49, 0x51, 0x61}, // Z
    {0x00, 0x7f, 0x41, 0x41, 0x00}, // [
    {0x7f, 0x7f, 0x7f, 0x7f, 0x7f}, // cursor
    {0x00, 0x41, 0x41, 0x7f, 0x00}, // ]
    {0x10, 0x20, 0x7f, 0x20, 0x10}, // up
    {0x7f, 0x08, 0x08, 0x08, 0x08}, // lazy-T
    {0x00, 0x00, 0x00, 0x00, 0x00}, // space
    {0x00, 0x00, 0x7d, 0x00, 0x00}, // !
    {0x00, 0x60, 0x00, 0x60, 0x00}, // "
    {0x14, 0x7f, 0x14, 0x7f, 0x14}, // #
    {0x12, 0x2a, 0x7f, 0x2a, 0x24}, // $
    {0x62, 0x64, 0x08, 0x13, 0x23}, // %
    {0x36, 0x49, 0x35, 0x02, 0x05}, // &
    {0x00, 0x68, 0x70, 0x00, 0x00}, // '
    {0x00, 0x1c, 0x22, 0x41, 0x00}, // (
    {0x00, 0x41, 0x22, 0x1c, 0x00}, // )
    {0x08, 0x2a, 0x1c, 0x2a, 0x08}, // *
    {0x08, 0x08, 0x3e, 0x08, 0x08}, // +
    {0x00, 0x0d, 0x0e, 0x00, 0x00}, // ,
    {0x08, 0x08, 0x08, 0x08, 0x08}, // -
    {0x00, 0x03, 0x03, 0x00, 0x00}, // .
    {0x02, 0x04, 0x08, 0x10, 0x20}, // /
    {0x3e, 0x45, 0x49, 0x51, 0x3e}, // 0
    {0x00, 0x21, 0x7f, 0x01, 0x00}, // 1
    {0x23, 0x45, 0x49, 0x49, 0x31}, // 2
    {0x22, 0x41, 0x49, 0x49, 0x36}, // 3
    {0x0c, 0x14, 0x24, 0x7f, 0x04}, // 4
    {0x72, 0x51, 0x51, 0x51, 0x4e}, // 5
    {0x1e, 0x29, 0x49, 0x49, 0x46}, // 6
    {0x40, 0x47, 0x48, 0x50, 0x60}, // 7
    {0x36, 0x49, 0x49, 0x49, 0x36}, // 8
    {0x30, 0x49, 0x49, 0x4a, 0x3c}, // 9
    {0x00, 0x36, 0x36, 0x00, 0x00}, // :
    {0x00, 0x6d, 0x6e, 0x00, 0x00}, // ,
    {0x00, 0x08, 0x14, 0x22, 0x41}, // <
    {0x14, 0x14, 0x14, 0x14, 0x14}, // =
    {0x41, 0x22, 0x14, 0x08, 0x00}, // >
    {0x30, 0x40, 0x45, 0x48, 0x30} // ?
  };

  static int[] HP9830keyOffsetX = {90, 90, 70, 60, 80, 80, 80, 80}; // offset of leftmost key in row
  static int[][] HP9830keyCodes = {
    {-1, -1, ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', -1, -1, -1, 013, -1, '0', '.', ',', -1, '+', -1},
    {-1, 'Z', 'X', 'C', 'V', 'B', 'N', 'M', ',', '.', '/', -1, -1, 013, -1, '1', '2', '3', -1, '-', -1},
    {034, 'A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L', ';', ':',  -1, 0153, -1, '4', '5', '6', -1, '*', -1},
    {0163, 'Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P', 0162, -1, '=', -1, '7', '8', '9', -1, '/', -1},
    {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0', 017, 0177, -1, 0152, -1, '(', ')', 0157, -1, 0136, -1},
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1},
    {005, 006, 007, 010, 011, -1, 022, 023, 037, 020, 021, -1, 026, 027, -1, 0164, 0165, 035, 0166, 032, -1},
    {000, 001, 002, 003, 004, -1, 036, 016, 0160, 014, 015, -1, 024, 025, -1, 0167, 012, 030, 031, 0776, 0777}
  };

  HP9830ROMslots romSlots;
  HP9865A tapeDevice;
  HP9865Interface hp9865Interface;

  Image[] ledMatrix;
  boolean tapeLoaded = false;

  public HP9830AMainframe(Emulator emu)
  {
    this(emu, "HP9830A");
  }
  
  public HP9830AMainframe(Emulator emu, String machine)
  {
    super(emu, machine);

    keyWidth = (920 - 80) / 20;  // with of one key area in pix
    keyOffsetY = 504;            // offset of lowest key row from image boarder 
    keyOffsetX = HP9830keyOffsetX;
    keyCodes = HP9830keyCodes;

    DISPLAY_X = 40;
    DISPLAY_Y = 25;
    DISPLAY_W = 635;
    DISPLAY_H = 30;
    
    STOP_KEYCODE = 0177; // code of STOP key used by super.keyPressed()

    addWindowListener(new windowListener());
    addMouseListener(new mouseListener());
    ioUnit.line10_20 = true;  // set 10/20 flag

    // create beeper (card reader)
    ioUnit.bus.cardReader = new HP9800BeeperInterface(this);

    // create display
    ioUnit.bus.display = new HP9830DisplayInterface(this);

    // create keyboard
    ioUnit.bus.keyboard = new HP9830KeyboardInterface(12);
    ioUnit.bus.interfaces.add((IOinterface)ioUnit.bus.keyboard);

    // create internal tape drive
    hp9865Interface = new HP9865Interface(Integer.valueOf(10));
    tapeDevice = new HP9865A(10);
    hp9865Interface.setDevice(tapeDevice); 
    tapeDevice.setInterface(hp9865Interface);
    tapeDevice.setStatusFrame(this, 820, 70);
    tapeDevice.hpName = "HP9865A";
    hp9865Interface.start();


    keyboardImage = new ImageMedia("media/HP9830A/HP9830A_Keyboard.jpg").getImage();
    romSlots = new HP9830ROMslots(this);

    setSize();
    System.out.println("HP9830 Mainframe loaded.");
  }

  class windowListener extends WindowAdapter
  {
    public void windowOpened(WindowEvent event)
    {
      // create LED matrix images after window is set visible
      ledMatrix = new Image[64];

      Graphics ledGraphics;

      for(int i = 0; i < 64; i++) {
        ledMatrix[i] = createImage(5 * LED_DOT_SIZE, 7 * LED_DOT_SIZE);
        ledGraphics = ledMatrix[i].getGraphics();
        ledGraphics.setColor(ledBack);
        ledGraphics.fillRect(0, 0, 5 * LED_DOT_SIZE, 7 * LED_DOT_SIZE);

        ledGraphics.setColor(ledRed);
        for(int x = 0; x < 5; x++) {
          int ledColumn = ledMatrixValues[i][x];

          for(int y = 6; y >= 0; y--) {
            if((ledColumn & 1) != 0) {
              ledGraphics.fillRect(x * LED_DOT_SIZE, y * LED_DOT_SIZE, LED_DOT_SIZE - 1, LED_DOT_SIZE - 1);
            }

            ledColumn >>= 1;
          }
        }
      }
    }

    public void windowClosing(WindowEvent event)
    {
      setVisible(false);
      dispose();
      System.out.println("HP9800 Emulator terminated.");
      System.exit(0);
    }
  }

  class mouseListener extends MouseAdapter
  {
    public void mousePressed(MouseEvent event)
    {
      int x = event.getX() - getInsets().left;
      int y = event.getY() - getInsets().top;

      if(y < 80 && x <= 250) {
        if(event.getButton() == MouseEvent.BUTTON1) {
          romSlots.setVisible(true);
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

      // not in main keypad?
      if(col > 11) {
        // use offset of upper row to get column# of right keypads
        col = (x - keyOffsetX[7]) / keyWidth;
      }
      if(col > 20)
        return; // right of key row?

      int keyCode = keyCodes[row][col];

      if(keyCode == 0776) {
        // cassette REWIND
        if(tapeDevice != null) {
          // send READ|REVERSE|FAST to tape device
          tapeDevice.output(0x0300);
        }

        return;
      }

      if(keyCode == 0777) {
        // cassette OPEN
        keyboardImage = new ImageMedia("media/HP9830A/HP9830A_Keyboard+Tape.jpg").getImage();
        repaint();
        tapeLoaded = tapeDevice.openTapeFile();
        if(tapeLoaded) {
          keyboardImage = new ImageMedia("media/HP9830A/HP9830A_Keyboard+Cassette.jpg").getImage();
          repaint();
        } else {
          keyboardImage = new ImageMedia("media/HP9830A/HP9830A_Keyboard.jpg").getImage();
          repaint();
          return;
        }
        return;
      }

      if(keyCode != -1) {
        if(event.isShiftDown())
          keyCode |= 0x80;

        if(emu.keyLogMode) {
          emu.console.append(Integer.toOctalString(keyCode) + " ");
          return;
        }

        if(keyCode == 0177) {
          // set STP flag and request service
          ioUnit.STP = true; // STOP
        }

        ioUnit.bus.keyboard.setKeyCode(keyCode);
        ioUnit.bus.keyboard.requestInterrupt();
      }
    }

    public void mouseReleased(MouseEvent event)
    {
    }
  }

  public void paint(Graphics g)
  {
    int[][] displayBuffer = ioUnit.bus.display.getDisplayBuffer();
    int x = getInsets().left;
    int y = getInsets().top;
    int charCode;

    backgroundImage = g.drawImage(keyboardImage, x, y, keyboardImage.getWidth(this), keyboardImage.getHeight(this), this);

    if(backgroundImage) {
      // draw display only not blanked 
      if(ioUnit.dispCounter.running()) {
        for(int i = 0; i < 32; i++) {
          charCode = displayBuffer[0][i];
          g.drawImage(ledMatrix[charCode], x + DISPLAY_X + i * (6 * LED_DOT_SIZE + 2), y + DISPLAY_Y, 5 * LED_DOT_SIZE, 7 * LED_DOT_SIZE, this);
        }
      }
      
      displayKeyMatrix();
    }
  }

  public void display(int line, int i)
  {
    Graphics g = this.getGraphics();

    if(ioUnit.DEN && backgroundImage && g != null) {
      int[][] displayBuffer = ioUnit.bus.display.getDisplayBuffer();
      int x = getInsets().left;
      int y = getInsets().top;
      int charCode = displayBuffer[0][i];

      g.drawImage(ledMatrix[charCode], x + DISPLAY_X + i * (6 * LED_DOT_SIZE + 2), y + DISPLAY_Y, 5 * LED_DOT_SIZE, 7 * LED_DOT_SIZE, this);
    }
  }

  public void printOutput(int dotGroup)
  {}
}