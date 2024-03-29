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
 * 28.10.2017 Rel. 2.10 Added new linking between Mainframe and other components
 * 30.12.2017 Rel. 2.10 Use Graphics2D for scaling, positioning, and rendering
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
import javax.swing.KeyStroke;

import emu98.IOunit;

public class HP9862A extends IOdevice implements ActionListener, Printable
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

  double REAL_W = 15.0, REAL_H = 10.0;
  int PLOT_W = 10000, PLOT_H = 10000;

  HP9862Interface hp9862Interface;
  //Image hp9862aImage;
  SoundMedia plotSound, moveSound, penDownSound, penUpSound;
  Stroke stroke;
  Vector<PlotterPoint> points;
  int numPoints;
  int[] outByte;
  int byteCount;
  PlotterPoint refPoint;
  int color = 0;
  int penColor = 1;
  boolean bcdMode = false;

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

  public HP9862A(IOinterface ioInterface)
  {
    super("HP9862A", ioInterface); // set window title
    hp9862Interface = (HP9862Interface)ioInterface;

    plotSound = new SoundMedia("media/HP9862A/HP9862_PLOT.wav", ioInterface.mainframe.soundController, true);
    moveSound = new SoundMedia("media/HP9862A/HP9862_MOVE.wav", ioInterface.mainframe.soundController, true);
    penDownSound = new SoundMedia("media/HP9862A/HP9862_PEN_DOWN.wav", ioInterface.mainframe.soundController, true);
    penUpSound = new SoundMedia("media/HP9862A/HP9862_PEN_UP.wav", ioInterface.mainframe.soundController, true);
    //hp9862aImage = getToolkit().getImage("media/HP9862A/HP9862A.jpg");

    NORMAL_W = 750;
    NORMAL_H = 500;

    refPoint = new PlotterPoint(0, 0, 0);

    // set line width
    stroke = new BasicStroke(7);

    // inititalize buffer for coordinate byte
    outByte = new int[4];
    byteCount = 0;
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

  		menuBar.removeAll();  // remove dummy menu

  		JMenu runMenu = new JMenu("Run");
      runMenu.add(makeMenuItem("High Speed", KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
      runMenu.addSeparator();
      runMenu.add(makeMenuItem("Exit"));
  		menuBar.add(runMenu);

  		JMenu viewMenu = new JMenu("View");
      viewMenu.add(makeMenuItem("Normal Size", KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK));
      viewMenu.add(makeMenuItem("Double Size", KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
      viewMenu.add(makeMenuItem("Real Size", KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK));
      viewMenu.add(makeMenuItem("Hide Menu", KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK));
  		menuBar.add(viewMenu);

  		JMenu printMenu = new JMenu("Print");
      printMenu.add(makeMenuItem("Page Format", KeyEvent.VK_P, KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK));
      printMenu.add(makeMenuItem("Hardcopy", KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK));
  		printMenu.addSeparator();
      printMenu.add(makeMenuItem("Clear", KeyEvent.VK_DELETE, 0));
  		menuBar.add(printMenu);
  	}

		// set size of surrounding JFrame only after loading all window components 
		addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				setScale(false, false);
			}
		});

		setNormalSize();
  }

	public JMenuItem makeMenuItem(String menuText)
	{
		return(makeMenuItem(menuText, 0, 0, null));
	}

	public JMenuItem makeMenuItem(String menuText, int key, int accelerator)
	{
		return(makeMenuItem(menuText, key, accelerator, null));
	}

	public JMenuItem makeMenuItem(String menuText, int key, int accelerator, String cmd)
	{
		JMenuItem menuItem = new JMenuItem(menuText);
		menuItem.addActionListener(this);
		if(cmd != null)
			menuItem.setActionCommand(cmd);

		if(key != 0) {
			KeyStroke ks = KeyStroke.getKeyStroke(key, accelerator);
			menuItem.setAccelerator(ks);
		}

		return(menuItem);
	}

  public void actionPerformed(ActionEvent event)
  {
    String cmd = event.getActionCommand();

    if(cmd.startsWith("High Speed")) {
      hp9862Interface.highSpeed = !hp9862Interface.highSpeed;
      deviceWindow.setTitle(hpName + (hp9862Interface.highSpeed? " High Speed" : ""));
    } else if(cmd.startsWith("Exit")) {
      close();
    } else if(cmd.startsWith("Normal Size")) {
      setNormalSize();
    } else if(cmd.startsWith("Double Size")) {
      setDoubleSize();
    } else if(cmd.startsWith("Real Size")) {
      setRealSize(REAL_W, REAL_H);
    } else if(cmd.startsWith("Clear")) {
      initializeBuffer();
    } else if(cmd.startsWith("Hide Menu")) {
      if(extDeviceWindow != null)
        extDeviceWindow.setFrameSize(!menuBar.isVisible());
    } else if(cmd.startsWith("Page Format")) {
      pageFormat = printJob.pageDialog(pageFormat);
    } else if(cmd.startsWith("Hardcopy")) {
      printJob.printDialog();
      try {
        printJob.print();
      } catch (PrinterException e) { }
    }

    repaint();
  }

  public Graphics2D getG2D(Graphics g)
  {
    Graphics2D g2d = (Graphics2D)g;

    if(g2d != null) {
      g2d.translate(getInsets().left, getInsets().top); // translate graphics to painting area
      g2d.scale(widthScale * NORMAL_W / PLOT_W, heightScale * NORMAL_H / PLOT_H);  // scale graphics to required size

      // enable antialiasing for higher quality of plotter output
      g2d.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));
    }

    return(g2d);
  }

  public void keyPressed(KeyEvent event)
  {
    int keyCode = event.getKeyCode();

    event.consume(); // do not pass key event to other levels (e.g. menuBar)

    switch(keyCode) {
    case KeyEvent.VK_END:
      break;

    case KeyEvent.VK_HOME:
      break;

    case KeyEvent.VK_DELETE:
      initializeBuffer();
      break;

    case KeyEvent.VK_UP:
      break;

    case KeyEvent.VK_DOWN:
      break;

    case KeyEvent.VK_LEFT:
      break;

    case KeyEvent.VK_RIGHT:
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

    case 'O':
      if(event.isControlDown())
        setDoubleSize();
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
        hp9862Interface.highSpeed = !hp9862Interface.highSpeed;
        deviceWindow.setTitle(hpName + (hp9862Interface.highSpeed? " High Speed" : ""));
      }
      break;

    default:
      return;
    }

    repaint();
  }

  public int print(Graphics g, PageFormat pf, int page)
  {
    PlotterPoint point, tempRef;

    Graphics2D g2d = (Graphics2D)g;

    // enable antialiasing for higher quality of plotter output
    g2d.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));
    g2d.translate(pf.getImageableX(), pf.getImageableY()); // translate graphics to painting area
    g2d.scale(pf.getImageableWidth() / PLOT_W, pf.getImageableHeight() / PLOT_H);  // scale graphics to page size

    pf = pageFormat;
    tempRef = new PlotterPoint(0, 0, 0);

    g2d.setColor(Color.WHITE);
    //g2d.fillRect(0, 0, PLOT_W, PLOT_H);

    for(int i = 0; i < numPoints; i++) {
      point = (PlotterPoint)points.elementAt(i);
      plot(g2d, point, tempRef);
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

    super.paint(g);
    setScale(false, false);

    tempRef = new PlotterPoint(0, 0, 0);

    //boolean backgroundImage = g2d.drawImage(hp9862aImage, x, y, getWidth(), getHeight(), this);

    //if(backgroundImage) {
    g2d.setColor(Color.WHITE);
    g2d.fillRect(0, 0, PLOT_W, PLOT_H);

    for(int i = 0; i < numPoints; i++) {
      point = (PlotterPoint)points.elementAt(i);
      plot(g2d, point, tempRef);
    }
    //}
  }

  public void plot(Graphics2D g2d, PlotterPoint pos, PlotterPoint ref)
  {
    if(g2d == null)
      g2d = getG2D(getGraphics());  // get current graphics if not given by paint()

    if(pos.color != 0) {
      switch(pos.color) {
      case 2:
        g2d.setColor(Color.GREEN);
        break;

      case 3:
        g2d.setColor(Color.RED);
        break;

      case 4:
        g2d.setColor(Color.BLUE);
        break;

      case 5:
        g2d.setColor(Color.CYAN);
        break;

      case 6:
        g2d.setColor(Color.MAGENTA);
        break;

      case 7:
        g2d.setColor(Color.YELLOW);
        break;

      case 8:
        g2d.setColor(Color.ORANGE);
        break;

      case 9:
        g2d.setColor(Color.PINK);
        break;

      default:
        g2d.setColor(Color.BLACK);
      }

      g2d.setStroke(stroke);
      g2d.drawLine(ref.x, PLOT_H - ref.y, pos.x, PLOT_H - pos.y);
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
      plot(null, point, refPoint);

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
          plot(null, point, refPoint);
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

  public void close()
  {
    // stop all sound threads
    plotSound.close();
    moveSound.close();
    penDownSound.close();
    penUpSound.close();

    super.close();
  }
}
