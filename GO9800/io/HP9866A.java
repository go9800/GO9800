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
 * 12.02.2011 Class completely reworked and adopted from HP9866B.java 
 * 19.03.2011 Rel. 1.50 Added method print()
 * 20.11.2011 Rel. 1.51 SHIFT+DELETE key resizes window to default
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 * 02.01.2018 Rel. 2.10 Added use of class DeviceWindow
 */

package io;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import emu98.IOunit;

public class HP9866A extends IOdevice implements Printable, ActionListener

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
  
  // HP9866A/B print width: 8" (80 chars)
  double REAL_W = 8.0, REAL_H = 5.0;

  Image[] printMatrix;

  private static final long serialVersionUID = 1L;
  HP9866Interface hp9866Interface;
  //Image hp9866aImage;
  SoundMedia fanSound, printSound;
  Vector<StringBuffer> printBuffer;
  StringBuffer lineBuffer;
  private int printDotHeight = 1, printDotWidth = 1;
  private Color printColor, paperColor;
  private int numLines, numDotRows, page;

  public HP9866A(IOinterface ioInterface)
  {
    super("HP9866A", ioInterface); // set window title
    hp9866Interface = (HP9866Interface)ioInterface;

    NORMAL_W = 570;
    NORMAL_H = 260;

    printSound = new SoundMedia("media/HP9866A/HP9866_PRINT.wav", ioInterface.mainframe.soundController, false);

    paperColor = Color.WHITE;
    printColor = Color.BLUE;
    page = 0;

    initializeBuffer();
    
    // set Printable
    printJob = PrinterJob.getPrinterJob();
    printJob.setPrintable(this);
    pageFormat = printJob.defaultPage();
  }
  
	public void setDeviceWindow(JFrame window)
	{
  	super.setDeviceWindow(window);

		if(createWindow) {
			deviceWindow.setResizable(true);
			deviceWindow.setLocation(0, 0);
			deviceWindow.setState(Frame.ICONIFIED);
			deviceWindow.setVisible(true);
			
	  	JMenu runMenu = new JMenu("Run");
	  	runMenu.add(new JMenuItem("High Speed")).addActionListener(this);
	  	runMenu.addSeparator();
	  	runMenu.add(new JMenuItem("Exit")).addActionListener(this);
	  	menuBar.add(runMenu);

	  	JMenu viewMenu = new JMenu("View");
	  	viewMenu.add(new JMenuItem("Normal Size")).addActionListener(this);
	  	viewMenu.add(new JMenuItem("Real Size")).addActionListener(this);
	  	viewMenu.addSeparator();
	  	viewMenu.add(new JMenuItem("First Page")).addActionListener(this);
	  	viewMenu.add(new JMenuItem("Previous Page")).addActionListener(this);
	  	viewMenu.add(new JMenuItem("Next Page")).addActionListener(this);
	  	viewMenu.add(new JMenuItem("Last Page")).addActionListener(this);
	  	viewMenu.add(new JMenuItem("Clear")).addActionListener(this);
	  	viewMenu.addSeparator();
	  	viewMenu.add(new JMenuItem("Hide Menu")).addActionListener(this);
	  	menuBar.add(viewMenu);
	  	
			JMenu printMenu = new JMenu("Print");
			printMenu.add(new JMenuItem("Page Format")).addActionListener(this);
			printMenu.add(new JMenuItem("Hardcopy")).addActionListener(this);
			menuBar.add(printMenu);
			
			menuBar.setVisible(true);
		}
		
		setNormalSize();
	}
	
	public void actionPerformed(ActionEvent event)
	{
		String cmd = event.getActionCommand();
		
    int windowDotRows = unscaledHeight - 8;  // # dot rows in output area
    int numPages = numDotRows * printDotHeight / windowDotRows;  // # of pages to display
		
		if(cmd.equals("High Speed")) {
			hp9866Interface.highSpeed = !hp9866Interface.highSpeed;
      deviceWindow.setTitle("HP9866B" + (hp9866Interface.highSpeed? " High Speed" : ""));
	  } else if(cmd.equals("Exit")) {
	  	close();
  	} else if(cmd.equals("Normal Size")) {
  		setNormalSize();
  	} else if(cmd.equals("Real Size")) {
  		setRealSize(REAL_W, REAL_H);
  	} else if(cmd.equals("First Page")) {
      page = numPages;
  	} else if(cmd.equals("Previous Page")) {
      if(++page > numPages) page = numPages;
  	} else if(cmd.equals("Next Page")) {
      if(--page < 0) page = 0;
  	} else if(cmd.equals("Last Page")) {
      page = 0;
  	} else if(cmd.equals("Clear")) {
    	initializeBuffer();
  	} else if(cmd.equals("Hide Menu")) {
  		if(extDeviceWindow != null)
  			extDeviceWindow.setFrameSize(!menuBar.isVisible());
  	} else if(cmd.equals("Page Format")) {
    	pageFormat = printJob.pageDialog(pageFormat);
  	} else if(cmd.equals("Hardcopy")) {
    	printJob.printDialog();
      try {
      	printJob.print();
      } catch (PrinterException e) { }
  	}
		
    repaint();
	}
	
	public void normalizeSize(int width, int height)
  {
  	super.normalizeSize(width, height);
  	heightScale = widthScale; //scale is determined only by window width
  }
  
  public Graphics2D getG2D(Graphics g)
  {
  	Graphics2D g2d = (Graphics2D)g;

  	if(g2d != null) {
  		g2d.translate(getInsets().left, getInsets().top); // translate graphics to painting area
  		g2d.scale(widthScale, heightScale);  // scale graphics to required size
  		
  		// enable bicubic interpolation for higher quality of scaled bitmaps
  		g2d.setRenderingHints(new RenderingHints(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC));
  	}

  	return(g2d);
  }

  public void keyPressed(KeyEvent event)
  {
    int keyCode = event.getKeyCode();

    int windowDotRows = unscaledHeight - 8;  // # dot rows in output area
    int numPages = numDotRows * printDotHeight / windowDotRows;  // # of pages to display
    
    switch(keyCode) {
    case KeyEvent.VK_PAGE_DOWN:
      if(--page < 0) page = 0;
      break;

    case KeyEvent.VK_PAGE_UP:
      page++;
      if(page > numPages) page = numPages;
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
        setNormalSize();
        printDotHeight = 1;
        makeFont(printDotHeight);
      }
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
    	
    case 'P':
    case KeyEvent.VK_INSERT:
    	if(event.isControlDown()) {
    		if(event.isShiftDown())
    			pageFormat = printJob.pageDialog(pageFormat);
    		else {
    			printJob.printDialog();
    			try {
    				printJob.print();
    			} catch (PrinterException e) { }
    		}
    	}
      return;

    case 'R':
      if(event.isControlDown())
      	setRealSize(REAL_W, REAL_H);
      break;
    	
    case 'S':
      if(event.isControlDown()) {
      	hp9866Interface.highSpeed = !hp9866Interface.highSpeed;
      	deviceWindow.setTitle("HP9866B" + (hp9866Interface.highSpeed? " High Speed" : ""));
      }
      break;

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
    double scale;

    Graphics2D g2d = (Graphics2D)g;
    pf = pageFormat;
		
    scale = pf.getImageableWidth() / 72. / REAL_W; // scale graphics to fit page width
    
    // enable bicubic interpolation for higher quality of scaled bitmaps
		g2d.setRenderingHints(new RenderingHints(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC));
		g2d.scale(scale, scale);  // scale graphics to page size
		g2d.translate(pf.getImageableX(), pf.getImageableY()); // translate graphics to painting area

  	unscaledHeight = (int)(pf.getImageableHeight() / scale);

    int x = 1;  // leftmost print positon
    int yTop = 1;  // topmost print dot position
    int yBottom = unscaledHeight;  // lowest print dot positon
    int windowDotRows = unscaledHeight;  // # dot rows in output area

    int numPages = numDotRows * printDotHeight / windowDotRows;  // # of pages to display
    if(page > numPages)
      return(NO_SUCH_PAGE);

    // print pages in regular order
    int y = yTop - page * windowDotRows;  // y-position relative to actual printed page 
    
    g2d.setColor(printColor);

    for(int i = 0; i < numLines; i++) {
    	printLine = printBuffer.elementAt(i).toString();

    	// is print y-position in visible area? If no: skip line
    	if(y >= yTop) {
    		// is print y-position below lowest visible dot row? If yes: page is done
    		if(y >= yBottom) 
    			break; 

    		for(int j = 0; j < printLine.length(); j++) {
    			charCode = (int)printLine.charAt(j) & 0x7f;
    			if(j < 80)
    				g2d.drawImage(printMatrix[charCode], x + j * (7 * printDotHeight), y, 5 * printDotHeight, 7 * printDotHeight, this);
    		}
    	}
    	y += 10 * printDotHeight;  // advance one character line (10 dot rows)
    }

    return(PAGE_EXISTS);
  }

  
  public void paint(Graphics g)
  {
  	String printLine;
  	int charCode;

  	if(printMatrix == null) {
  		// create LED matrix images once
  		printMatrix = new Image[128];

  		makeFont(printDotHeight);
  	}

  	super.paint(g);
  	g2d = getG2D(g);
  	normalizeSize(NORMAL_W, NORMAL_H);
  	int unscaledHeight = (int)((getHeight() - getInsets().top - getInsets().bottom) / heightScale);

  	int x = 4;    // leftmost print positon
  	int yTop = 0; // topmost print dot position
  	int yBottom = unscaledHeight - 8;  // lowest print dot positon
  	int windowDotRows = yBottom - yTop;  // # dot rows in output area
  	int y = yBottom + page * windowDotRows;  // y-position of actual displayed page

  	g2d.setColor(Color.WHITE);
  	g2d.fillRect(0, 0, NORMAL_W, unscaledHeight);

  	g2d.setColor(printColor);

  	for(int i = numLines - 1; i >= 0; i--) {
  		printLine = printBuffer.elementAt(i).toString();

  		y -= 10 * printDotHeight;  // advance one character line (10 dot rows)
  		if(y > yBottom) continue;  // is print y-position below lowest visible dot row? If yes: try next line
  		if(y < yTop - 10 * printDotHeight) break;  // is print y-position above highest visible dot row? If yes: we are done

  		for(int j = 0; j < printLine.length(); j++) {
  			charCode = (int)printLine.charAt(j) & 0x7f;
  			if(j < 80)
  				g2d.drawImage(printMatrix[charCode], x + j * (7 * printDotHeight), y, 5 * printDotHeight, 7 * printDotHeight, this);
  		}
  	}
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
      setVisible(true);
      repaint();

      // status=not ready -> delay for line output
      return(0);
    }

    return(IOunit.devStatusReady);
  }
  
  public void close()
  {
  	// stop all sound threads
		printSound.close();

  	super.close();
  }
}
