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
 * 07.04.2007 Rel. 1.00 Superclass derived from HP9810Mainframe 
 * 24.08.2007 Rel. 1.20 Added default keyPressed() using keyCode Hashtable
 * 24.08.2007 Rel. 1.20 Added keyLog support
 * 05.09.2007 Rel. 1.20 Internal printer no longer initialized for HP9830
 * 30.09.2007 Rel. 1.20 Added method setSize()
 * 16.10.2007 Rel. 1.20 Changed frame size control
 * 24.03.2008 Rel. 1.30 Changed keyPressed() to use keyChar in case that the corresponding keyCode is undefined in keyboard configuration
 * 16.04.2008 Rel. 1.30 Changed keyPressed() to handle lower an upper case characters correctly
 * 30.04.2008 Rel. 1.30 Added event.consume() in keyPressed() to hide key event from host OS
 * 30.09.2008 Rel. 1.31 Added displayKeyMatrix()
 * 30.09.2008 Rel. 1.31 Added case 'K' in keyPressed() 
 * 10.01.2009 Rel. 1.33 Added high speed output for printer when sound is disabled. 
 * 18.01.2009 Rel. 1.40 Added use of InstructionsWindow
 * 18.01.2009 Rel. 1.40 Added synchronized(keyboardImage) in setSize() before visualizing main window to avoid flickering
 * 28.04.2009 Rel. 1.41 Added methods paper() and update() for paper advance 
 * 18.04.2016 Rel. 1.61 Fixed displacement of keyMatrix for HP9830B
 * 25.06.2016 Rek. 1.61 Changed wait time in printOutput() and paper() to consider run-time of painting the output 
 */

package io;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.util.*;
import javax.sound.sampled.*;

import emu98.*;

public class HP9800Mainframe extends Frame implements KeyListener, LineListener, Printable
{
  private static final long serialVersionUID = 1L;

  public static int keyWidth = (920 - 80) / 20;  // with of one key area in pix
  public static int keyOffsetY = 525;            // offset of lowest key row from image boarder 
  public static int[] keyOffsetX; // offset of leftmost key in row
  public static int[][] keyCodes;

  public static int KEYB_W = 1000;
  public static int KEYB_H = 578;
  
  public static int DISPLAY_X = 100;
  public static int DISPLAY_Y = 105;
  public static int DISPLAY_W = 320;
  public static int DISPLAY_H = 118;
  public static int LED_X = +100;
  public static int LED_Y = +25;
  public static int LED_DOT_SIZE = 3; // used in HP9820/21/30 only


  public static int PAPER_HEIGHT = 168;
  public static int PAPER_WIDTH = 124;
  public static int PAPER_LEFT = 548;
  public static int PAPER_EDGE = 126;

  public static int BLOCK1_X = 23;
  public static int BLOCK1_Y = 3;
  public static int BLOCK2_X = 174;
  public static int BLOCK2_Y = 3;
  public static int BLOCK3_X = 324;
  public static int BLOCK3_Y = 3;
  public static int BLOCK_W = 152;
  public static int BLOCK_H = 54;
  
  public static int STOP_KEYCODE = 041; // code of STOP key
  
  public Emulator emu;
  protected Memory[] memory;
  protected IOregister ioReg;
  protected Console console;
  //protected HP2116Panel hp2116panel; 
  protected ROMselector romSelector;
  public InstructionsWindow instructionsWindow;
  
  protected Image keyboardImage, displayImage;
  protected Color ledRed, ledBack, paperWhite, paperGray;
  private SoundMedia fanSound, printSound, paperSound;
  Vector<byte[]> printBuffer;
  byte[] lineBuffer;
  public int numLines;
  public int page;
  int dotLine = 0;
  public boolean backgroundImage = false;
  boolean printing = false;
  public boolean advancing = false;
  boolean showKeycode = false;
  private PrinterJob printJob;
  private PageFormat pageFormat;


  public HP9800Mainframe(Emulator emu, String machine) 
  {
    super(machine);

    this.emu = emu;

    console = new Console(this, emu);
    //hp2116panel = new HP2116Panel(emu);
    emu.setConsole(console); // for console output of emulator (disassembler)

    // connect memory to Mainframe
    memory = emu.memory;
    // connect IOregister to Mainframe
    ioReg = emu.ioRegister;
    // connect extended memory to Mainframe via IOinterface (e.g. HP11273A)
    IOinterface.memory = memory;
    
    ioReg.setDisassemblerOutput(console);

    addKeyListener(this);
    addWindowListener(new windowListener());

    fanSound = new SoundMedia("media/HP9800/HP9800_FAN.wav");
  
    fanSound.loop();

    romSelector = new ROMselector(this, emu);
    instructionsWindow = new InstructionsWindow(this);
    instructionsWindow.setSize(860, 800);
    ledRed = new Color(255, 125, 25);
    ledBack = new Color(31, 10, 9);
    
    setBackground(Color.BLACK);
    setLocation(0, 100);
    //hp2116panel.setVisible(false);
    if(!machine.startsWith("HP9830")) {
      printSound = new SoundMedia("media/HP9810A/HP9810A_PRINT_LINE.wav");
      paperSound = new SoundMedia("media/HP9810A/HP9810A_PAPER.wav");
      paperWhite = new Color(230, 230, 230);
      paperGray = new Color(100, 100, 85);
      initializeBuffer();

      // set Printable
      printJob = PrinterJob.getPrinterJob();
      printJob.setPrintable(this);
      pageFormat = printJob.defaultPage();

      System.out.println("HP9800 Printer loaded.");
    }
  }
 
  public void setSize()
  {
    setResizable(false);
    setVisible(true);
    // wait until background image has been loaded
    synchronized(keyboardImage) {
      try
      {
        keyboardImage.wait(500);
      } catch (InterruptedException e)
      { }
    }
    
    setSize(keyboardImage.getWidth(this) + getInsets().left + getInsets().right, keyboardImage.getHeight(this) + getInsets().top + getInsets().bottom);
  }
  
  public void setTapeDevice(HP9865A tapeDevice)
  {}
  
  class windowListener extends WindowAdapter
  {
    public void windowClosing(WindowEvent event)
    {
      setVisible(false);
      dispose();
      System.out.println("HP9800 Emulator terminated.");
      System.exit(0);
   }
  }

  public class mouseListener extends MouseAdapter
  {}
  
  public void update(Graphics g)
  {
    // avoid flickering by not drawing background color
    paint(g);
  }
  
  public void paint(Graphics g)
  {}

  public void displayLEDs(int keyLEDs)
  {}
  
  public void display(int reg, int i)
  {}

  public void keyPressed(KeyEvent event)
  {
    int numPages = numLines / PAPER_HEIGHT;
    int keyChar = event.getKeyChar();
    int keyCode = event.getKeyCode();
    String strCode = "";
    String modifier = "";

    switch(keyCode) {
    case KeyEvent.VK_UNDEFINED:
      if(keyChar != KeyEvent.CHAR_UNDEFINED)
        strCode = Integer.toString(keyChar);
      else
        return;
      break;

    case KeyEvent.VK_SHIFT:     // don't send modifier keys
    case KeyEvent.VK_CONTROL:
    case KeyEvent.VK_ALT:
    case KeyEvent.VK_NUM_LOCK:
      return;


    default:
      if(event.isControlDown()) {
        switch(keyCode) {
        /*
        case 'Q':
          for(int i = 01000; i <= 01777; i++) {
            emu.console.append(Integer.toOctalString(emu.memory[i].getValue()) + "\n");
          }
          break;
        */ 
        /*
        case 'C':
          hp2116panel.setVisible(!hp2116panel.isVisible());
          break;
        */
        case 'D':
          console.setVisible(!console.isVisible());
          break;

        case 'F':
          fanSound.toggle();
          break;
        
        case 'K':
          showKeycode = ! showKeycode;
          repaint();
          break;

        case 'P':
          if(event.isShiftDown())
            pageFormat = printJob.pageDialog(pageFormat);
          else {
            printJob.printDialog();
            try {
              printJob.print();
            } catch (PrinterException e) { }
          }
          break;

        case 'R':
          if(event.isAltDown())
            synchronized(ioReg) {
              ioReg.reset = true;
            }
          break;

        case 'S':
          if(SoundMedia.isEnabled()) {
            fanSound.stop();
            SoundMedia.enable(false);
          } else {
            SoundMedia.enable(true);
            fanSound.loop();
          }
          break;

        case 'T':
          if(emu.measure) {
            emu.stopMeasure();
            console.append("NumOps=" + emu.numOps);
            console.append(" Min=" + emu.minExecTime);
            console.append(" Max=" + emu.maxExecTime);
            console.append(" Mean=" + emu.sumExecTime / emu.numOps + "[ns]\n");
            if(!console.isVisible())
              console.setVisible(true);
          } else
            emu.startMeasure();
          break;

        case KeyEvent.VK_PAGE_UP:
          // paper page up
          if(--page < 0) page = 0;
          displayPrintOutput();
          break;

        case KeyEvent.VK_PAGE_DOWN:
          // paper page down
          page++;
          if(page >= numPages) {
            page = numPages;
            repaint();
          }
          else
            displayPrintOutput();
          break;

        case KeyEvent.VK_DELETE:
          // clear print buffer
          initializeBuffer();
          page = 0;
          repaint();
          break;

        case KeyEvent.VK_HOME:
          // PAPER
          paper(2);
          keyCode = -1;
          break;

        }

        return;
      }

    strCode = Integer.toString(keyCode);
    }

    if(event.isAltDown())
      modifier = "A";

    if(event.isControlDown())
      modifier += "C";

    if(event.isShiftDown())
      modifier += "S";

    if(emu.keyLogMode) {
      emu.console.append(strCode);
      if(modifier != "")
        emu.console.append(" " + modifier);
      emu.console.append(" ; " + (char)keyChar);
      return;
    }

    strCode = (String)emu.keyCodes.get(strCode + modifier);
    if(strCode != null)
      keyCode = Integer.parseInt(strCode, 8);
    else if(keyChar >= 040 && keyChar <= 0172)
      if(Character.isLowerCase(keyChar))
        keyCode = Character.toUpperCase(keyChar);
      else if(Character.isUpperCase(keyChar ))
        keyCode = keyChar + 0200;
      else
        keyCode = keyChar;
    else
      return;

    if(keyCode == STOP_KEYCODE) {
      // set STP flag
      ioReg.STP = true;
    }

    event.consume(); // do not pass key event to host system 
    ioReg.bus.keyboard.setKeyCode(keyCode);
    ioReg.bus.keyboard.requestInterrupt();
  }

  public void keyReleased(KeyEvent event)
  {}

  public void keyTyped(KeyEvent event)
  {}

  public void initializeBuffer()
  {
    numLines = 0;
    lineBuffer = new byte[16];
    printBuffer = new Vector<byte[]>();
  }

  public void printOutput(int dotGroup)
  {
    // dot group number
    int i = (dotGroup & 0x03) * 4;
    long time;

    if(dotLine == 0 && !printing) {
      printSound.start();
      printing = true;
    }

    // store character 1 in line buffer
    lineBuffer[i++] = (byte)((dotGroup >> 16) & 0x1f);
    // store character 2 in line buffer
    lineBuffer[i++] = (byte)((dotGroup >> 21) & 0x1f);
    // store character 3 in line buffer
    lineBuffer[i++] = (byte)((dotGroup >> 6) & 0x1f);
    // store character 4 in line buffer
    lineBuffer[i++] = (byte)((dotGroup >> 11) & 0x1f);

    // last dot group?
    if(i == 16) {
      time = System.nanoTime();
      numLines++;
      printBuffer.addElement(lineBuffer);
      lineBuffer = new byte[16];

      displayPrintOutput();

      if(++dotLine == 10) {
        dotLine = 0;
        printing = false;
      }

      // wait 4*8ms for exact printer timing
      // considering run-rime for painting the output
      time = ioReg.time_32ms - (System.nanoTime() - time) / 1000000;
      if(time < 0) time = 0;
      
      try {
        Thread.sleep(SoundMedia.isEnabled()? time : 0);
      } catch(InterruptedException e) { }
    }
  }
  
  public void update(LineEvent event)
  {
    if(advancing && event.getType() == LineEvent.Type.STOP) {
      paper(2);
    }
  }
  
  public void paper(int advance)
  {
    long time;

    switch(advance) {
    case 0: // mouse release
      advancing = false;
      paperSound.getClip().removeLineListener(this);
      return;
      
    case 1: // mouse click and hold on PAPER
      paperSound.getClip().addLineListener(this);
      
    case 2: // Ctrl+Home key
      advancing = true;
      paperSound.start();
    }
    
    for(int i = 1; i <= 10; i++) {
      time = System.nanoTime();
      numLines++;
      printBuffer.addElement(lineBuffer);

      // wait 4*8ms for exact printer timing
      // considering run-rime for painting the output
      time = ioReg.time_32ms - (System.nanoTime() - time) / 1000000;
      if(time < 0) time = 0;
      
      try {
        Thread.sleep(SoundMedia.isEnabled()? time : 0);
      } catch(InterruptedException e) { }
      
      lineBuffer = new byte[16];
      displayPrintOutput();
    }
  }
  
  // hardcopy output
  public int print(Graphics g, PageFormat pf, int page)
  {
    final int paperColWidth = 7 * (16 + 2);
    byte[] lineBuffer;
    int dotRow;
    int i, j, n, xd;

    pf = pageFormat;

    // number of printer paper columns (16+2 characters each) on paper sheet  
    int numCols = (int)(pf.getImageableWidth() / paperColWidth);
    // number of full printer lines (7 + 3 dot rows) on paper height
    int numFullLines = (int)(pf.getImageableHeight() / 10) * 10;
    
    int yTop = (int)pf.getImageableY();  // topmost print dot position
    int yBottom = (int)pf.getImageableY() + numFullLines - 1;  // lowest print dot positon
    int windowDotRows = numFullLines * numCols;  // # dot rows in output area

    int numPages = numLines / windowDotRows;  // # of pages to display
    if(page > numPages)
      return(NO_SUCH_PAGE);
    
    int x = (int)pf.getImageableX() + 1;
    int y = yTop + 1;
    int rowNum = page * windowDotRows;
    
    g.setColor(Color.BLUE);

    for(i = rowNum; i < rowNum + windowDotRows && i < numLines; i++) {
      lineBuffer = (byte[])printBuffer.elementAt(i);

      for(j = 0; j < 16; j++) {
        dotRow = lineBuffer[j];
        for(n = 4; n >= 0; n--) {
          if((dotRow & 1) != 0) {
            xd = x + 7 * j + n;
            g.drawLine(xd, y, xd, y);
          }

          dotRow >>= 1;
        } // for n
      } // for j

      y++;  // advance one dot row

      if(y > yBottom) { // below lowest visible row?
        x += paperColWidth;  // next print column
        y = yTop;
      }
    } // for i
    
    return(PAGE_EXISTS);
  }

  
  public void displayPrintOutput()
  {
    byte[] lineBuffer;
    int dotRow;
    int i, j, n, xd;

    if(numLines == 0)
      return;
    
    Graphics g = getGraphics();
    if(g == null)
      return;
    
    int x = getInsets().left;
    int y = getInsets().top;
    int maxLine = numLines - page * PAPER_HEIGHT;

    if(maxLine < 0) maxLine = 0;
    int minLine = maxLine - PAPER_HEIGHT - 1;
    if(minLine < 0) minLine = 0;

    x += PAPER_LEFT;
    g.setColor(paperWhite);
    g.fillRect(x, y + PAPER_HEIGHT - maxLine, PAPER_WIDTH, maxLine);

    x += 8;
    y += PAPER_HEIGHT - 1;
    g.setColor(Color.BLUE);

    for(i = maxLine - 1; i >= minLine; i--) {
      lineBuffer = (byte[])printBuffer.elementAt(i);

      for(j = 0; j < 16; j++) {
        dotRow = lineBuffer[j];
        for(n = 4; n >= 0; n--) {
          if((dotRow & 1) != 0) {
            xd = x + 7 * j + n;
            g.drawLine(xd, y, xd, y);
          }

          dotRow >>= 1;
        } // for n
      } // for j
      y--;
    } // for i

    x = getInsets().left + PAPER_LEFT;
    y = getInsets().top + PAPER_EDGE;
    g.setColor(paperGray);
    g.fillRect(x, y, PAPER_WIDTH, 6);
  }
  
  public void displayKeyMatrix()
  {
    int keyCode;
    int x, y;
    String strKey;
    
    if(!showKeycode)
      return;
    
    Graphics g = getGraphics();
    if(g == null)
      return;

    g.setColor(new Color(0, 120, 250));
    Font font = new Font("Sans", Font.BOLD, 12);
    g.setFont(font);
    
    for(int row = 0; row < keyOffsetX.length; row++) {
      y = keyOffsetY - getInsets().top - row * keyWidth;

      for(int col = 0; col < keyCodes[row].length; col++) {
        keyCode = keyCodes[row][col];
        if(keyCode != -1) {
          if(emu.model.startsWith("HP9830") && col > 11)
            x = keyOffsetX[7];
          else
            x = keyOffsetX[row];
            
          x += col * keyWidth + getInsets().left;
          g.drawRect(x, y, keyWidth, keyWidth);
          
          if(keyCode < 0700) {
            strKey = Integer.toString(keyCode);
            strKey = (String)emu.keyStrings.get(strKey);
            if(strKey == null)
              strKey = String.valueOf((char)keyCode);
            g.drawString(strKey, x + 5, y + keyWidth - 5);
          }
        }
      }
    }
  }
}
