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
 * 25.06.2016 Rel. 1.61 Changed wait time in printOutput() and paper() to consider run-time of painting the output
 * 07.09.2016 Rel. 2.01 Changed parameters and handling of printOutput()
 * 21.10.2017 Rel. 2.10 Added Graphics scaling using class Graphics2D
 * 23.10.2017 Rel. 2.10 Changed color ledRed
 * 27.10.2017 Rel. 2.10: Changed drawing in displayKeyMatrix(), added methode displayClickAreas()
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 * 02.11.2017 Rel. 2.10: Added method imageProcessing() used to draw ROM modules and templates
 * 02.11.2017 Rel. 2.10: Changed drawing of ROM modules and templates using imageProcessing() and RGBA images with transparency
 */

package io;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.awt.print.*;
import java.util.*;
import javax.sound.sampled.*;
import javax.swing.SwingUtilities;

import emu98.*;

public class HP9800Mainframe extends Frame implements KeyListener, LineListener, Printable
{
  private static final long serialVersionUID = 1L;

  public int keyWidth = (920 - 80) / 20;  // with of one key area in pix
  public int keyOffsetY = 525;            // offset of lowest key row from image boarder 
  public int[] keyOffsetX; // offset of leftmost key in row
  public int[][] keyCodes;

  public int KEYB_W = 1000;
  public int KEYB_H = 578;
  
  public int DISPLAY_X = 100;
  public int DISPLAY_Y = 105;
  public int DISPLAY_W = 320;
  public int DISPLAY_H = 118;
  public int LED_X = +100;
  public int LED_Y = +25;
  public int LED_DOT_SIZE = 3; // used in HP9820/21/30 only


  public int PAPER_HEIGHT = 168;
  public int PAPER_WIDTH = 124;
  public int PAPER_LEFT = 548;
  public int PAPER_EDGE = 126;

  public int BLOCK1_X = 24;
  public int BLOCK1_Y = 3;
  public int BLOCK2_X = 174;
  public int BLOCK2_Y = 2;
  public int BLOCK3_X = 325;
  public int BLOCK3_Y = 1;
  public int BLOCK_W = 152;
  public int BLOCK_H = 54;
  
  public int STOP_KEYCODE = 041; // code of STOP key
  
  // mainframe ressources used by all modules, interfaces, devices  
  public CPU cpu;
  public Memory[] memory;
  public IOunit ioUnit;
  public Console console;
  
  // List of all IOinterfaces and IOdevices for cleanup
  public Vector<IOinterface> ioInterfaces; // also used by IObus
  public Vector<IOdevice> ioDevices;

  protected Emulator emu;
  public Configuration config;
  protected HP2116Panel hp2116panel; 
  protected ROMselector romSelector;
  protected Hashtable<String, String> hostKeyCodes, hostKeyStrings;
  public InstructionsWindow instructionsWindow;
  
  public Graphics2D g2d;
  public double aspectRatio = 1.;
	public double widthScale = 1., heightScale = 1.;
	
  protected Image keyboardImage, displayImage, blockImage;
  protected Image ledLargeOn, ledLargeOff;
  protected Image ledSmallOn, ledSmallOff;

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
    emu.setMainframe(this);
    
    // List of all loaded IOinterfaces
    ioInterfaces = new Vector<IOinterface>();
    
    // List of loaded IOdevices
    ioDevices = new Vector<IOdevice>();
    
    // console output of emulator (disassembler)
    console = new Console(this, emu);
    emu.setConsole(console);

    // initialize complete memory to 'unused'  
    memory = new Memory[0100000];
    //unusedMemory = new Memory(false, -1, 0);
    for(int i = 0; i <= 077777; i++)
      memory[i] = new Memory(false, i, 0);

    // set object variable for trace outputs
    Memory.emu = emu;

    // initialize CPU
    cpu = new CPU(this);

    // initialize IO-unit
    ioUnit = new IOunit(cpu);
    
    cpu.setIOunit(ioUnit);
    cpu.setDisassemblerOutput(console);
    ioUnit.setDisassemblerOutput(console);
    
    // HP2116 like lamp panel (just for fun)
    hp2116panel = new HP2116Panel(cpu);
    hp2116panel.setVisible(false);

    addKeyListener(this);
    addWindowListener(new windowListener());
    
    // fixed window size ratio
    addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
          	normalizeSize();
          }
        });
      }
    });
		
    fanSound = new SoundMedia("media/HP9800/HP9800_FAN.wav", false);
    fanSound.loop();

    romSelector = new ROMselector(this, this);
    instructionsWindow = new InstructionsWindow(this);
    instructionsWindow.setSize(860, 800);
    ledRed = new Color(255, 120, 80);
    ledBack = new Color(31, 10, 9);
    
    setBackground(new Color(85, 83, 81));
    setLocation(0, 100);
    
    if(!machine.startsWith("HP9830")) {
      printSound = new SoundMedia("media/HP9810A/HP9810A_PRINT_LINE.wav", false);
      paperSound = new SoundMedia("media/HP9810A/HP9810A_PAPER.wav", true);
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
  
  public void setConfiguration(Configuration config)
  {
  	this.config = config;
  }
  
  public void setSize()
  {
  	Dimension normalSize;
	
    setResizable(true); // this changes size of insets
    setVisible(true);
    // wait until background image has been loaded
    synchronized(keyboardImage) {
      try
      {
        keyboardImage.wait(500);
      } catch (InterruptedException e)
      { }
    }
    
    // fixed aspect ratio of keyboard
    aspectRatio = (double)KEYB_W / (double)KEYB_H;

    // set window to standard size
    normalSize = new Dimension(KEYB_W + getInsets().left + getInsets().right, KEYB_H + getInsets().top + getInsets().bottom);
    this.setPreferredSize(normalSize);
    this.setSize(normalSize);
  }
  
  public void normalizeSize()
  {
  	// actual size of keyboard area
  	Dimension actualSize = new Dimension(getWidth() - getInsets().left - getInsets().right, getHeight() - getInsets().top - getInsets().bottom);
  	double aspectMismatch = actualSize.getWidth() / actualSize.getHeight() / aspectRatio;
  	
  	if(aspectMismatch > 1.02) {  // is actual aspect ratio more than 2% bigger than normal?
  		actualSize.width = (int)(actualSize.getHeight() * aspectRatio);  // then make width smaller
  	} else if(aspectMismatch < 0.98) {  // is actual aspect ratio more than 2% smaller than normal?
  		actualSize.height = (int)(actualSize.getWidth() / aspectRatio);  // then make height smaller
  	}
  	
  	// scale factors for drawing
  	widthScale = actualSize.getWidth() / KEYB_W;
  	heightScale = actualSize.getHeight() / KEYB_H;

  	actualSize.width += getInsets().left + getInsets().right;
  	actualSize.height += getInsets().top + getInsets().bottom;
  	
  	setSize(actualSize);
  }
  
  public void setTapeDevice(HP9865A tapeDevice)
  {}
  
  protected void closeAllDevices()
  {
  	// close all open devices one by one
  	while(!ioDevices.isEmpty())
  	{
			ioDevices.lastElement().close(); // close() also removes the device from the list
  	}
  }

  protected void closeAllInterfaces()
  {
  	// close all open devices one by one
  	while(!ioInterfaces.isEmpty())
  	{
			ioInterfaces.lastElement().stop(); // close() also removes the device from the list
  	}
  }

  class windowListener extends WindowAdapter
  {
    public void windowClosing(WindowEvent event)
    {
    	System.out.println("\nHP9800 Emulator shutdown initiated ...");
    	
    	closeAllDevices(); // close all loaded devices
    	closeAllInterfaces(); // close remaining interfaces without device (MCR, Beeper etc.)
    	emu.stop();
    	hp2116panel.stop();
      ImageMedia.disposeAll();
      SoundMedia.disposeAll();
      config.dispose();
      setVisible(false);
      dispose();
      System.out.println("HP9800 Emulator terminated.");
      
      g2d = null;
      //System.exit(0);
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
  {
  	g2d = (Graphics2D)g.create();
  	normalizeSize();  // normalize aspect ratio and get scaling factors
  	
  	g2d.translate(getInsets().left, getInsets().top); // translate graphics to painting area
  	g2d.scale(widthScale, heightScale);  // scale graphics to required size
  	
  	// enable antialiasing for higher quality of scaled graphics
    g2d.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));
  }

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
        
        case 'C':
          hp2116panel.setVisible(!hp2116panel.isVisible());
          break;
        
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
            synchronized(ioUnit) {
              ioUnit.reset = true;
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
      emu.console.append(" ; " + (char)keyChar + "\n");
      return;
    }

    strCode = (String)config.hostKeyCodes.get(strCode + modifier);
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
      ioUnit.STP = true;
    }

    event.consume(); // do not pass key event to host system 
    ioUnit.bus.keyboard.setKeyCode(keyCode);
    ioUnit.bus.keyboard.requestInterrupt();
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

  public void printOutput(int dotGroup1, int dotGroup2)
  {
    // dot group number
    int i = (dotGroup2 & 0x03) * 4;
    long time;

    if(dotLine == 0 && !printing) {
      printSound.start();
      printing = true;
    }

    // store character 1 in line buffer
    lineBuffer[i++] = (byte)((dotGroup1) & 0x1f);
    // store character 2 in line buffer
    lineBuffer[i++] = (byte)((dotGroup1 >> 5) & 0x1f);
    // store character 3 in line buffer
    lineBuffer[i++] = (byte)((dotGroup2 >> 6) & 0x1f);
    // store character 4 in line buffer
    lineBuffer[i++] = (byte)((dotGroup2 >> 11) & 0x1f);

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
      time = ioUnit.time_32ms - (System.nanoTime() - time) / 1000000;
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
      time = ioUnit.time_32ms - (System.nanoTime() - time) / 1000000;
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
    
    int x = 0, y = 0; // positioning is done by g2d.translate()
    int maxLine = numLines - page * PAPER_HEIGHT;

    if(maxLine < 0) maxLine = 0;
    int minLine = maxLine - PAPER_HEIGHT - 1;
    if(minLine < 0) minLine = 0;

    x += PAPER_LEFT;
    g2d.setColor(paperWhite);
    g2d.fillRect(x, y + PAPER_HEIGHT - maxLine, PAPER_WIDTH, maxLine);

    x += 8;
    y += PAPER_HEIGHT - 1;
    g2d.setColor(Color.BLUE);

    for(i = maxLine - 1; i >= minLine; i--) {
      lineBuffer = (byte[])printBuffer.elementAt(i);

      for(j = 0; j < 16; j++) {
        dotRow = lineBuffer[j];
        for(n = 4; n >= 0; n--) {
          if((dotRow & 1) != 0) {
            xd = x + 7 * j + n;
            if(heightScale < 10.2)
              g2d.drawLine(xd, y, xd, y);
            else
            	g2d.fillOval(xd, y, 1, 1);
          }

          dotRow >>= 1;
        } // for n
      } // for j
      y--;
    } // for i

    // draw transparent paper cover
    x = PAPER_LEFT;
    y = PAPER_EDGE;
    g2d.setColor(paperGray);
    g2d.fillRect(x, y, PAPER_WIDTH, 6);
  }
  
  public BufferedImage imageProcessing(Image image, float factor, float offset)
  {
  	Graphics2D bufferedGraphics;
  	BufferedImage bufferedImage; // for image processing
  	RescaleOp imageOp;
  	float[] factors;  // contrast factors (RGBA)
  	float[] offsets;  // brightness offsets (RGBA)
  	
  	// processing of RGB with Alpha (RGBA) requires vector values
  	factors = new float[]{factor, factor, factor, 1f};
  	offsets = new float[]{offset, offset, offset, 0f};

  	// created buffered RGB image with alpha channel
  	while(image.getWidth(null) < 0); // wait for image to be loaded
  	bufferedImage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
  	bufferedGraphics = bufferedImage.createGraphics();
  	bufferedGraphics.drawImage(image, 0, 0, null); // draw moduleImage into bufferedImage
  	imageOp = new RescaleOp(factors, offsets, null); // image processing operation
  	
  	// return processed image
  	return(imageOp.filter(bufferedImage, null));
  }
    
  public void displayKeyMatrix()
  {
    float[] dashArray = {2f, 2f};
    int keyCode;
    int x, y;
    String strKey;
    
    if(!showKeycode)
      return;
    
    BasicStroke stroke = new BasicStroke(1, 0, 0, 1f, dashArray, 0f);
    g2d.setStroke(stroke);

    g2d.setColor(Color.white);
    Font font = new Font("Sans", Font.BOLD, 12);
    g2d.setFont(font);
    
    
    for(int row = 0; row < keyOffsetX.length; row++) {
      //y = keyOffsetY - getInsets().top - row * keyWidth + 15; // why is +15 necessary ???
      y = keyOffsetY - (row + 1) * keyWidth;

      for(int col = 0; col < keyCodes[row].length; col++) {
        keyCode = keyCodes[row][col];
        if(keyCode != -1) {
          if(config.model.startsWith("HP9830") && col > 11)
            x = keyOffsetX[7];
          else
            x = keyOffsetX[row];
            
          x += col * keyWidth;
          g2d.drawRect(x, y, keyWidth, keyWidth);
          
          if(keyCode < 0700) {
            strKey = Integer.toString(keyCode);
            strKey = (String)config.hostKeyStrings.get(strKey);
            if(strKey == null)
              strKey = String.valueOf((char)keyCode);
            g2d.drawString(strKey, x + 5, y + keyWidth - 5);
          }
        }
      }
    }
    
    // draw model specific click areas
    displayClickAreas();
  }
  
  public void displayClickAreas()
  {
  }
}
