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
 * 18.03.2008 Rel. 1.30 Class created based on HP9866A 
 * 10.01.2009 Rel. 1.33 Added speed toggle (key S)
 * 26.03.2010 Rel. 1.42 Added constructor with HP11201Interface parameter (for HP9861 HW emulator)
 * 26.03.2010 Rel. 1.42 Added stopping of HP11201Interface thread before unloading
 * 03.04.2010 Rel. 1.50 Class now inherited from IOdevice and completely reworked
 * 12.02.2011 Rel. 1.50 Added variable font size (keys '+' and '-')
 * 12.02.2011 Rel. 1.50 Scrolling and paint method reworked
 * 19.03.2011 Rel. 1.50 Added method print()
 * 20.11.2011 Rel. 1.51 SHIFT+DELETE key resizes window to default
 * 28.10.2017 Rel. 2.10 Added new linking between Mainframe and other components
 * 02.01.2018 Rel. 2.10 Added use of class DeviceWindow
 */

package io;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.util.Vector;
import javax.swing.JFrame;
import emu98.IOunit;

public class HP9861A extends IOdevice implements Printable
{
  private static final long serialVersionUID = 1L;
  HP11201A hp11201a;
  //Image hp9861aImage;
  SoundMedia typeSound, spaceSound, crSound, lfSound;
  SoundController soundController;
  private Vector<StringBuffer> printBuffer;
  private StringBuffer lineBuffer;
  private int fontSize;
  private int numLines, pos;
  private int page, ribbon;
  private boolean tab[];
  private Font font;
  private PrinterJob printJob;
  private PageFormat pageFormat;
  private boolean debug = false;
  
  static final int WIDTH = 162;
  static final int LINE_END = 0x800;
  
  static final int NOP = 0x00;
  static final int TAB_SET = 0x01;
  static final int BLACK_RBN = 0x07;
  static final int RED_RBN = 0x06;
  static final int BSP = 0x08;
  static final int TAB = 0x09;
  static final int TAB_CLR_ALL = 0x0b;
  static final int TAB_CLR = 0x0c;
  
  
  public HP9861A(IOinterface ioInterface)
  {
    super("HP9861A", ioInterface); // set window title
    hp11201a = (HP11201A)ioInterface;

    // load print sound
    soundController = ioInterface.mainframe.soundController;
    typeSound = new SoundMedia("media/HP9861A/HP9861_TYPE.wav", soundController, true);
    spaceSound = new SoundMedia("media/HP9861A/HP9861_SPC.wav", soundController, true);
    crSound = new SoundMedia("media/HP9861A/HP9861_CR.wav", soundController, true);
    lfSound = new SoundMedia("media/HP9861A/HP9861_LF.wav", soundController, true);

    //hp9861aImage = getToolkit().getImage("media/HP9861A/HP9861A.jpg");
    page = 0;
    ribbon = BLACK_RBN;

    fontSize = 12;
    font = new Font("Monospaced", Font.PLAIN, fontSize);
    setFont(font);
    
    tab = new boolean[WIDTH];
    for(int i = 0; i < WIDTH; i++)
      tab[i] = false;
    pos = 0;
    
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
  		deviceWindow.setSize(1010, 250);
  		deviceWindow.setState(Frame.ICONIFIED);
  		deviceWindow.setVisible(true);
  	}
  }

  public void keyPressed(KeyEvent event)
  {
    int keyCode = event.getKeyCode();

    int windowDotRows = getHeight() - 8 -  getInsets().top;  // # dot rows in output area
    int numPages = numLines * fontSize / windowDotRows;  // # of pages to display

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
        setSize(1010, 250);
        fontSize = 12;
        font = new Font("Monospaced", Font.PLAIN, fontSize);
        setFont(font);
      }
      break;

    case 'S':
      hp11201a.highSpeed = !hp11201a.highSpeed;
      deviceWindow.setTitle("HP9861A" + (hp11201a.highSpeed? " High Speed" : ""));
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
        if(fontSize < 40) {
          fontSize += 1;
          font = new Font("Monospaced", Font.PLAIN, fontSize);
          setFont(font);
        } else {
          return;
        }
        break;

      case '-':
        if(fontSize > 8) {
          fontSize -= 1;
          font = new Font("Monospaced", Font.PLAIN, fontSize);
          setFont(font);
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

  private void typeLine(Graphics g, String line, int x, int y)
  {
    char c;
    int xmin = x;
    
    for(int i = 0; i < line.length(); i++) {
      c = line.charAt(i);
      switch(c) {
      case BLACK_RBN:
        g.setColor(Color.BLACK);
        break;
        
      case RED_RBN:
        g.setColor(Color.RED);
        break;
        
      case BSP:
        x -= g.getFontMetrics().charWidth(' ');
        if(x < xmin)
          x = xmin;
        break;
        
      case '\r':
        x = xmin;
        break;
        
      default:
        g.drawString(Character.toString(c), x, y);
        x += g.getFontMetrics().charWidth(c); 
      }
    }
  }
  
  public int print(Graphics g, PageFormat pf, int page)
  {
    Graphics2D g2 = (Graphics2D)g;
    pf = pageFormat;

    double scale = pf.getImageableWidth() / 40. / fontSize;

    int windowLines = (int)(pf.getImageableHeight() / 12.);  // # lines in output area
    int numPages = numLines / windowLines;  // # of pages to print
    if(page > numPages)
      return(NO_SUCH_PAGE);

    int x = (int)(pf.getImageableX() / scale);  // leftmost print positon
    int y = (int)(pf.getImageableY() / scale);  // topmost print position
    int lineNum = page * windowLines;

    g2.scale(scale, scale);
    g.setFont(font);

    for(int i = lineNum; i < lineNum + windowLines && i < numLines; i++) {
      y += fontSize;  // advance one character line
      typeLine(g, printBuffer.elementAt(i).toString(), x, y);
    }

    return(PAGE_EXISTS);
  }

  public void paint(Graphics g)
  {
    int x = getInsets().left + 4;  // leftmost print positon
    int yTop = getInsets().top;  // topmost print position
    int yBottom = getHeight() - 8;  // lowest print positon
    int windowDotRows = yBottom - yTop;  // # dot rows in output area
    int y = yBottom + page * windowDotRows;  // y-position of actual displayed page

    g.setColor(Color.WHITE);
    g.fillRect(0, 0, getWidth(), getHeight());

    typeLine(g, lineBuffer.toString(), x, y);

    for(int i = numLines - 1; i >= 0; i--) {
      y -= fontSize;  // advance one character line
      if(y > yBottom) continue;  // is print y-position below lowest visible line? If yes: try next line
      if(y < yTop - fontSize) break;  // is print y-position above highest visible line? If yes: we are done
      typeLine(g, printBuffer.elementAt(i).toString(), x, y);
    }
  }

  public void initializeBuffer()
  {
    numLines = pos = 0;
    lineBuffer = new StringBuffer();
    printBuffer = new Vector<StringBuffer>();
    lineBuffer.append((char)ribbon);
  }
  
  public int output(int status, int value)
  {
    int i;
    
    if(debug)
      ioInterface.ioUnit.console.append("HP9861A out: " + Integer.toHexString(value) + "\n");
    
    hp11201a.timerValue = 30;
    status = IOunit.devStatusReady;
    
    switch(value) {
    case NOP:
      pos = 0;
      return(status);
      
    case TAB_SET:
      tab[pos] = true;
      break;
      
    case BLACK_RBN:
    case RED_RBN:
      lineBuffer.append((char)value);
      ribbon = value;
      break;
      
    case BSP:
      lineBuffer.append((char)value);
      if(!hp11201a.highSpeed)
        spaceSound.start(); // play space character sound
      if(pos > 0)
        pos--;
      hp11201a.timerValue = 70;
      status = 0;
      break;
      
    case TAB:
      i = 1;
      if(!hp11201a.highSpeed)
        spaceSound.start(); // play space character sound
      for(pos++; pos < WIDTH; pos++) {
        lineBuffer.append(' ');
        i++;
        if(tab[pos])
          break;
      }
      hp11201a.timerValue *= i;
      status = 0;
      break;
      
    case TAB_CLR:
      tab[pos] = false;
      break;
      
    case TAB_CLR_ALL:
      for(i = 0; i <= pos ; i++)
        tab[i] = false;
      
    case '\r':
      if(!hp11201a.highSpeed)
        crSound.start(); // play carriage return sound
      hp11201a.timerValue = 100 + 600 * pos / WIDTH;
      status = 0;
      lineBuffer.append((char)value);
      pos = 0;
      if(value == TAB_CLR_ALL)
        value = '\r';
      else
        break;
    
    case '\n':
      if(!hp11201a.highSpeed)
        lfSound.start(); // play line feed sound
      numLines++;
      printBuffer.addElement(lineBuffer);
      lineBuffer = new StringBuffer();
      lineBuffer.append((char)ribbon);
      for(i = 0; i < pos; i++)
        lineBuffer.append(' ');
        
      repaint();
      hp11201a.timerValue = 120;
      status = 0;
      break;
    
    default:
      lineBuffer.append((char)value);
      if(value == ' ') {
        if(!hp11201a.highSpeed)
          spaceSound.start(); // play space character sound
        hp11201a.timerValue = 60;
      } else {
        if(!hp11201a.highSpeed)
          typeSound.start(); // play character type sound
        hp11201a.timerValue = 70;
      }
      status = 0;

      if(page == 0) {
        int x = getInsets().left + 4;  // leftmost print position
        int y = getHeight() - 8;
        typeLine(getGraphics(), lineBuffer.toString(), x, y);
      }
      
      pos++;
    }

    if(pos >= WIDTH) {
      pos = WIDTH - 1;
      status |= LINE_END;
    }
    
    return(status);
  }
  
  public void soundStop()
  {
    crSound.stop();
  }
  
  public void close()
  {
  	// stop all sound threads
    typeSound.close();
    spaceSound.close();
    crSound.close();
    lfSound.close();

  	super.close();
  }
}