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
 * 12.02.2011 Class completely reworked and adopted from HP9866B.java 
 * 19.03.2011 Rel. 1.50 Added method print()
 * 20.11.2011 Rel. 1.51 SHIFT+DELETE key resizes window to default
*/

package io;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.util.Vector;

public class HP9866A extends IOdevice implements Printable
{
  static int printMatrixValues[][] = {
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
    {0x00, 0x00, 0x00, 0x00, 0x00},
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
    {0x30, 0x40, 0x45, 0x48, 0x30}, // ?
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
    {0x20, 0x10, 0x08, 0x04, 0x02}, // backslash
    {0x00, 0x41, 0x41, 0x7f, 0x00}, // ]
    {0x10, 0x20, 0x7f, 0x20, 0x10}, // up
    {0x01, 0x01, 0x01, 0x01, 0x01}, // _
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
    {0x20, 0x10, 0x08, 0x04, 0x02}, // backslash
    {0x00, 0x41, 0x41, 0x7f, 0x00}, // ]
    {0x10, 0x20, 0x7f, 0x20, 0x10}, // up
    {0x00, 0x00, 0x00, 0x00, 0x00},
  };
  static int numChars = 96;
  
  Image[] printMatrix;
  Image printDot;

  private static final long serialVersionUID = 1L;
  HP9866Interface hp9866Interface;
  //Image hp9866aImage;
  SoundMedia fanSound, printSound;
  Vector<StringBuffer> printBuffer;
  StringBuffer lineBuffer;
  private int printDotHeight = 1, printDotWidth = 1;
  private Color printColor, paperColor;
  private int numLines, numDotRows, page;
  private PrinterJob printJob;
  private PageFormat pageFormat;

  public HP9866A()
  {
    super("HP9866A"); // set window title
    addWindowListener(new windowListener());

    fanSound = new SoundMedia("media/HP9800/HP9800_FAN.wav");
    // load print sound
    printSound = new SoundMedia("media/HP9866A/HP9866_PRINT.wav");
    /*
    fanSound.loop();
     */

    paperColor = Color.WHITE;
    printColor = Color.BLUE;

    setSize(575, 250);
    setLocation(190, 0);
    //hp9866aImage = getToolkit().getImage("media/HP9866A/HP9866A.jpg");
    page = 0;

    initializeBuffer();
    
    // set Printable
    printJob = PrinterJob.getPrinterJob();
    printJob.setPrintable(this);
    pageFormat = printJob.defaultPage();
    
    setVisible(true);
  }

  public void setInterface(IOinterface ioInt)
  {
    super.setInterface(ioInt);
    hp9866Interface = (HP9866Interface)ioInt;
  } 

  class windowListener extends WindowAdapter
  {
    public void windowOpened(WindowEvent event)
    {
      // create LED matrix images after window is set visible
      printMatrix = new Image[128];

      makeFont(printDotHeight);
    }
  }

  public void keyPressed(KeyEvent event)
  {
    int keyCode = event.getKeyCode();
    //System.out.println(keyCode);

    int windowDotRows = getHeight() - 8 -  getInsets().top;  // # dot rows in output area
    int numPages = numDotRows * printDotHeight / windowDotRows;  // # of pages to display
    
    switch(keyCode) {
    case KeyEvent.VK_PAGE_DOWN:
      page++;
      if(page > numPages) page = numPages;
      break;

    case KeyEvent.VK_PAGE_UP:
      if(--page < 0) page = 0;
      break;

    case KeyEvent.VK_END:
      page = 0;
      break;

    case KeyEvent.VK_HOME:
      page = numPages;
      break;

    case KeyEvent.VK_DELETE:
      initializeBuffer();
      page = 0;
      if(event.isShiftDown()) {
        setSize(575, 250);
        printDotHeight = 1;
        makeFont(printDotHeight);
      }
      break;

    case 'S':
      hp9866Interface.highSpeed = !hp9866Interface.highSpeed;
      this.setTitle("HP9866A" + (hp9866Interface.highSpeed? " High Speed" : ""));
      break;

    case 'P':
    case KeyEvent.VK_INSERT:
      if(event.isShiftDown())
        pageFormat = printJob.pageDialog(pageFormat);
      else {
        printJob.printDialog();
        try {
          printJob.print();
        } catch (PrinterException e) { }
      }
      return;

    default:
      switch(event.getKeyChar()) {
      case '+':
        if(printDotHeight < 10) {
          printDotHeight += 1;
          printDotWidth = (int)(printDotHeight / 1.5 + 0.5);
          makeFont(printDotHeight);
        } else {
          return;
        }
        break;
        
      case '-':
        if(printDotHeight > 1) {
          printDotHeight--;
          printDotWidth = (int)(printDotHeight / 1.5 + 0.5);
          makeFont(printDotHeight);
        } else {
          return;
        }
        break;
        
      default:
        return;
      }
    }

    repaint();
  }

  private void makeFont(int printDotHeight)
  {
    Graphics printGraphics;

    // make image for graphics dot
    printDot = createImage(printDotHeight, printDotHeight);
    printGraphics = printDot.getGraphics();
    printGraphics.setColor(printColor);
    if(printDotWidth < 3)
      printGraphics.fillRect(0, 0, printDotWidth, printDotHeight);
    else
      printGraphics.fillOval(0, 0, printDotWidth, printDotHeight);

    // make images for character matrices
    for(int i = 0; i < numChars ; i++) {
      printMatrix[i] = createImage(5 * printDotHeight, 7 * printDotHeight);
      printGraphics = printMatrix[i].getGraphics();
      printGraphics.setColor(paperColor);
      printGraphics.fillRect(0, 0, 5 * printDotHeight, 7 * printDotHeight);

      printGraphics.setColor(printColor);
      for(int x = 0; x < 5; x++) {
        int printColumn = printMatrixValues[i][x];

        for(int y = 6; y >= 0; y--) {
          if((printColumn & 1) != 0) {
            if(printDotWidth < 3)
              printGraphics.fillRect(x * printDotHeight, y * printDotHeight, printDotWidth, printDotHeight);
            else
              printGraphics.fillOval(x * printDotHeight, y * printDotHeight, printDotWidth, printDotHeight);
          }

          printColumn >>= 1;
        }
      }
    }
  }
  
  public int print(Graphics g, PageFormat pf, int page)
  {
    String printLine;
    int charCode;

    Graphics2D g2 = (Graphics2D)g;
    pf = pageFormat;
    // HP9866A/B print width: 8" (80 chars)
    double scale = pf.getImageableWidth() / 520. / printDotHeight;

    int x = (int)(pf.getImageableX() / scale) + 1;  // leftmost print positon
    int yTop = (int)(pf.getImageableY() / scale);  // topmost print dot position
    int yBottom = (int)((pf.getImageableHeight() + pf.getImageableY()) / scale);  // lowest print dot positon
    int windowDotRows = (int)(pf.getImageableHeight() / scale);  // # dot rows in output area

    int numPages = numDotRows * printDotHeight / windowDotRows;  // # of pages to display
    if(page > numPages)
      return(NO_SUCH_PAGE);

    // print pages in reverse order
    int y = yBottom + (numPages - page) * windowDotRows;  // y-position of actual displayed page

    g2.scale(scale, scale);
    g.setColor(printColor);

    for(int i = numLines - 1; i >= 0; i--) {
      printLine = printBuffer.elementAt(i).toString();

      y -= 10 * printDotHeight;  // advance one character line (10 dot rows)
      if(y > yBottom) continue;  // is print y-position below lowest visible dot row? If yes: try next line
      if(y < yTop - 10 * printDotHeight) break;  // is print y-position above highest visible dot row? If yes: we are done

      for(int j = 0; j < printLine.length(); j++) {
        charCode = (int)printLine.charAt(j) & 0x7f;
        g.drawImage(printMatrix[charCode], x + j * (7 * printDotHeight), y, 5 * printDotHeight, 7 * printDotHeight, this);
      }
    }

    return(PAGE_EXISTS);
  }

  
  public void paint(Graphics g)
  {
    String printLine;
    int charCode;
    //int x = getInsets().left;
    //int y = getInsets().top;

    //boolean backgroundImage = g.drawImage(hp9866AImage, x, y, 1000, 370, this);

    //if(backgroundImage) {
      //x = getInsets().left + 270;
      //y = getInsets().top + 180;
      int x = getInsets().left + 4;  // leftmost print positon
      int yTop = getInsets().top;  // topmost print dot position
      int yBottom = getHeight() - 8;  // lowest print dot positon
      int windowDotRows = yBottom - yTop;  // # dot rows in output area
      int y = yBottom + page * windowDotRows;  // y-position of actual displayed page

      g.setColor(printColor);

      for(int i = numLines - 1; i >= 0; i--) {
        printLine = printBuffer.elementAt(i).toString();

        y -= 10 * printDotHeight;  // advance one character line (10 dot rows)
        if(y > yBottom) continue;  // is print y-position below lowest visible dot row? If yes: try next line
        if(y < yTop - 10 * printDotHeight) break;  // is print y-position above highest visible dot row? If yes: we are done

        for(int j = 0; j < printLine.length(); j++) {
          charCode = (int)printLine.charAt(j) & 0x7f;
          if(charCode >= 96) charCode -= 32;
          g.drawImage(printMatrix[charCode], x + j * (7 * printDotHeight), y, 5 * printDotHeight, 7 * printDotHeight, this);
        }
      }
    //}
  }

  public void initializeBuffer()
  {
    numLines = 0;
    numDotRows = 0;
    lineBuffer = new StringBuffer();
    printBuffer = new Vector<StringBuffer>();
  }
  
  public int output(int status, int value)
  {
    lineBuffer.append((char)value);

    if(value == '\n') {
      if(!hp9866Interface.highSpeed) {
        printSound.start(); // play print sound
      }

      numDotRows += 10;
      hp9866Interface.timerValue = 270;

      numLines++;
      printBuffer.addElement(lineBuffer);
      lineBuffer = new StringBuffer();
      repaint();

      // status=not ready -> delay for line output
      return(0);
    }

    return(IOregister.devStatusReady);
  }
}
