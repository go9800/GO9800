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
 * 14.06.2006 Class created 
 * 06.08.2006 Added ComponentRepaintAdapter
 * 29.08.2006 Changed data directory structure
 * 29.08.2006 Changed loading of sound files
 * 29.10.2006 Changed sound output to Sound class 
 * 03.12.2006 Changed default window size to 500x500
 * 30.07.2008 Rel. 1.30 Bugfix: set byteCount=0 after 4 bytes are received to avoid IndexOutOfBounds exception
 * 10.01.2009 Rel. 1.33 Added speed toggle (key S)
 * 22.12.2009 Rel. 1.42 Changed plotter movement delay and sound output
 * 09.02.2010 Rel. 1.42 Added constructor with HP9862Interface parameter (for HP9862 HW emulator)
 * 09.02.2010 Rel. 1.42 Added stopping of HP9862Interface thread before unloading
 * 15.02.2010 Rel. 1.42 Bugfix: problem with reference point in plot / repaint fixed by complete code rework 
 * 01.04.2010 Rel. 1.50 Class now inherited from IOdevice and completely reworked
 * 19.03.2011 Rel. 1.50 Added method print()
 * 20.11.2011 Rel. 1.51 SHIFT+DELETE key resizes window to default
 */

package io;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.util.Vector;

import emu98.IOunit;

public class HP9862A extends IOdevice implements Printable
{
  private static final long serialVersionUID = 1L;
  
  // calculator output status bits 
  static final int SYNC = 0x0100;
  static final int CODE = 0x0200;
  static final int PEN = 0x0400;
  static final int MANEUVER = 0x0800;
  
  // calculator input status bits 
  public static final int POWER = 0x0200;
  public static final int PEN_IN = 0x0800;
  
  static final int DEFAULT_SIZE = 500;
  
  HP9862Interface hp9862Interface;
  //Image hp9862aImage;
  SoundMedia plotSound, moveSound, penDownSound, penUpSound;
  Vector<PlotterPoint> points;
  int numPoints;
  int x0, y0, plotSize = DEFAULT_SIZE;
  
  int[] outByte;
  int byteCount;
  PlotterPoint refPoint;
  int color = 0;
  int penColor = 1;
  boolean bcdMode = false;
  
  private PrinterJob printJob;
  private PageFormat pageFormat;
  
  private class PlotterPoint
  {
    int x, y, color;
    
    PlotterPoint(int x, int y, int color)
    {
      this.x = x;
      this.y = y;
      this.color = color;
    }
  }

  public HP9862A()
  {
    super("HP9862A"); // set window title

    plotSound = new SoundMedia("media/HP9862A/HP9862_PLOT.wav", true);
    moveSound = new SoundMedia("media/HP9862A/HP9862_MOVE.wav", true);
    penDownSound = new SoundMedia("media/HP9862A/HP9862_PEN_DOWN.wav", true);
    penUpSound = new SoundMedia("media/HP9862A/HP9862_PEN_UP.wav", true);

    setSize(DEFAULT_SIZE + 30, DEFAULT_SIZE + 20);
    //hp9862aImage = getToolkit().getImage("media/HP9862A/HP9862A.jpg");

    refPoint = new PlotterPoint(0, 0, 0);

    // inititalize buffer for coordinate byte
    outByte = new int[4];
    byteCount = 0;
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
    hp9862Interface = (HP9862Interface)ioInt;
  } 
  
  public void keyPressed(KeyEvent event)
  {
    int keyCode = event.getKeyCode();

    switch(keyCode) {
    case KeyEvent.VK_END:
      break;

    case KeyEvent.VK_HOME:
      break;

    case KeyEvent.VK_DELETE:
      initializeBuffer();
      if(event.isShiftDown())
        setSize(DEFAULT_SIZE + 30, DEFAULT_SIZE + 20);
      break;

    case KeyEvent.VK_UP:
      break;

    case KeyEvent.VK_DOWN:
      break;

    case KeyEvent.VK_LEFT:
      break;

    case KeyEvent.VK_RIGHT:
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

    case 'S':
      hp9862Interface.highSpeed = !hp9862Interface.highSpeed;
      this.setTitle("HP9862A" + (hp9862Interface.highSpeed? " High Speed" : ""));
      break;

    default:
      return;
    }

    repaint();
  }
  
  public int print(Graphics g, PageFormat pf, int page)
  {
    PlotterPoint point, tempRef;
    
    pf = pageFormat;
    
    x0 = (int)pf.getImageableX();
    y0 = (int)(pf.getImageableHeight() + pf.getImageableY());
    if(pf.getImageableWidth() > pf.getImageableHeight())
      plotSize = (int)pf.getImageableHeight();
    else
      plotSize = (int)pf.getImageableWidth();
    tempRef = new PlotterPoint(0, 0, 0);
    
    int x = (int)pf.getImageableX();
    int y = (int)pf.getImageableY();

    g.setColor(Color.WHITE);
    g.fillRect(x, y, (int)pf.getImageableWidth(), (int)pf.getImageableHeight());

    for(int i = 0; i < numPoints; i++) {
      point = (PlotterPoint)points.elementAt(i);
      plot(g, point, tempRef);
    }
    
    // only one page to print
    if(page == 0)
      return(PAGE_EXISTS);
    else
      return(NO_SUCH_PAGE);
  }

  public void paint(Graphics g)
  {
    PlotterPoint point, tempRef;
    
    x0 = getInsets().left;
    y0 = getHeight() - 10;
    plotSize = y0 - getInsets().top;
    tempRef = new PlotterPoint(0, 0, 0);
    
    int x = getInsets().left;
    int y = getInsets().top;

    //boolean backgroundImage = g.drawImage(hp9862aImage, x, y, getWidth(), getHeight(), this);

    //if(backgroundImage) {
    g.setColor(Color.WHITE);
    g.fillRect(x, y, getWidth() - x, getHeight() - y);

    for(int i = 0; i < numPoints; i++) {
      point = (PlotterPoint)points.elementAt(i);
      plot(g, point, tempRef);
    }
    //}
  }
  
  public void plot(Graphics g, PlotterPoint pos, PlotterPoint ref)
  {
    if(pos.color != 0) {
      switch(pos.color) {
      case 2:
        g.setColor(Color.GREEN);
        break;
        
      case 3:
        g.setColor(Color.RED);
        break;
        
      case 4:
        g.setColor(Color.BLUE);
        break;
        
      case 5:
        g.setColor(Color.CYAN);
        break;
        
      case 6:
        g.setColor(Color.MAGENTA);
        break;
        
      case 7:
        g.setColor(Color.YELLOW);
        break;
        
      case 8:
        g.setColor(Color.ORANGE);
        break;
        
      case 9:
        g.setColor(Color.PINK);
        break;
        
      default:
        g.setColor(Color.BLACK);
      }
      
      g.drawLine(x0 + ref.x * plotSize/10000, y0 - ref.y * plotSize/10000, x0 + pos.x * plotSize/10000, y0 - pos.y * plotSize/10000);
    }
    
    ref.x = pos.x;
    ref.y = pos.y;
    ref.color = pos.color;
  }
  
  public void initializeBuffer()
  {
    numPoints = 0;
    points = new Vector<PlotterPoint>();
  }
  
  public int output(int status, int value)
  {
    int x, y;
    
    // data output or pen control? 
    if((status & MANEUVER) != 0) {
      //moveSound.stop();
      hp9862Interface.timerValue = 100;
      
      // pen control
      if((status & PEN) != 0) {
        if(color == 0 && !hp9862Interface.highSpeed)
          penDownSound.start();
        color = penColor;
      } else {
        if(color != 0 && !hp9862Interface.highSpeed)
          penUpSound.start();
        color = 0;
      }
      
      PlotterPoint point = new PlotterPoint(refPoint.x, refPoint.y, color);
      points.addElement(point);
      numPoints++;
      plot(getGraphics(), point, refPoint);

      // status = not ready -> delay for pen movement
      status = POWER;
    } else {
      hp9862Interface.timerValue = 0;
      // data
      if((status & SYNC) == 0) {
        // first of four data bytes
        byteCount = 0;
        bcdMode = ((status & CODE) == 0); // is data BCD encoded?
      }
      
      if(bcdMode)
        value = 10 * (value >> 4) + (value & 0x0f);
      
      outByte[byteCount] = value;
      
      // last of 4 bytes?
      if(++byteCount == 4) {
        byteCount = 0;
        //plotSound.stop();
        
        try {
        if(bcdMode) {
          x = outByte[0] * 100 + outByte[1];
          y = outByte[2] * 100 + outByte[3];
        } else {
          x = (outByte[0] << 8) + outByte[1];
          y = (outByte[2] << 8) + outByte[3];
        }
        } catch (NullPointerException e) {
          // possible communication initialization problem with calculator 
          byteCount = 0;
          return(POWER);
        }
        
        // relative movement?
        if((status & CODE) != 0) {
          x = refPoint.x + (short)x;  // treat x as 16bit signed integer
          y = refPoint.y + (short)y;  // treat y as 16bit signed integer
          if(x < 0) x = 0;
          if(y < 0) y = 0;
        }
        
        if((x == 9999) && (color == 0)) {
          if(y < 16) penColor = y;
        } else {
          // length of plot track, converted to time in ms
          int l = (int)Math.round(Math.hypot((double)(x - refPoint.x), (double)(y - refPoint.y)) * 0.35);
          hp9862Interface.timerValue = l;
          
          if((x != refPoint.x || y != refPoint.y) && !hp9862Interface.highSpeed) {
            if(l > 200)
              moveSound.loop(); // play plot sound only if real move
            //else if(l > 50)
              //plotSound.start(); // play plot sound only if real move
          }
          
          PlotterPoint point = new PlotterPoint(x, y, color);
          points.addElement(point);
          numPoints++;
          plot(getGraphics(), point, refPoint);
        }

        // status = not ready -> delay for plotter movement
        status = POWER;
      } else {
        // status = ready for next byte;
        status = POWER | IOunit.devStatusReady;
      }
    }
    
    if(color != 0)
      status |= PEN_IN;

    return(status);
  }
  
  public void soundStop()
  {
    moveSound.stop();
  }
}
