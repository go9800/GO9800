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
 * 01.04.2010 Class created 
 * 24.10.2017 Rel. 2.10 Added method close()
 * 25.10.2017 Rel. 2.10 Changed static access to IOregister
 * 25.10.2017 Rel. 2.10 Added method close() to stop thread
 * 28.10.2017 Rel. 2.10 Added new linking between Mainframe and other components
 * 02.01.2018 Rel. 2.10 Added use of class DeviceWindow
 */

package io;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;

import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;

import emu98.DeviceWindow;


public class IOdevice extends JPanel implements KeyListener, MouseListener
{
  public String hpName = null;

  private static final long serialVersionUID = 1L;
  public IOinterface ioInterface;
  public JFrame deviceWindow;
  public DeviceWindow extDeviceWindow;
  public Boolean createWindow = true; // set to false if no separate DeviceWindow is needed
  public ImageMedia deviceImageMedia, interfaceImageMedia;
  public int interfaceSlot = 0;
  JPanel devicePanel;
  JMenuBar menuBar;
  Graphics2D g2d;
  PrinterJob printJob;
  PageFormat pageFormat;
  Color hpBeige = new Color(215, 213, 178);
  double aspectRatio = 1.;
  double widthScale = 1., heightScale = 1.;
  int unscaledHeight;
  int MENU_H = 23;
  public int NORMAL_W, NORMAL_H;


  public IOdevice()
  {
    // dummy constructor for Reflection API
  }

  public IOdevice(String hpName, IOinterface ioInterface)
  {
		this.hpName = hpName; // device name comes from child class
    
		// device without own interface (HP9867B)?
		if(ioInterface != null) {
			// extend device name with #selectCode
			if(ioInterface.selectCode != 0)
				this.hpName += " @" + ioInterface.selectCode;

			// connection to ioInterface
			this.ioInterface = ioInterface;

			// add device to list for later cleanup
			ioInterface.mainframe.ioDevices.add(this);
		}
    
    addMouseListener(this);

    setBackground(Color.WHITE);
    setForeground(Color.BLACK);
  }

  public IOdevice(String hpName, IOinterface ioInterface, int slot)
  {
  	this(hpName, ioInterface);
		interfaceSlot = slot;
  }

  public boolean needsWindow()
  {
    return(createWindow);
  }

  // only register JFrame of DeviceWindow (e.g. from HP9800Window)
  public void setDeviceWindow(JFrame window)
  {
    deviceWindow = window;

    if(createWindow) {
      // do only for a separate window
      deviceWindow.addKeyListener(this);
      deviceWindow.setState(Frame.NORMAL);
      deviceWindow.setVisible(false);
    }
  }

  // register extended JFrame for use of extended methods
  public void setDeviceWindow(DeviceWindow window)
  {
    menuBar.setVisible(createWindow); // hide menu bar for internal devices

    extDeviceWindow = window;
    setDeviceWindow((JFrame)window);
  }

  public void setMenuBar(JMenuBar menuBar)
  {
    this.menuBar = menuBar;
  }

  // set graphics scale with fixed aspect ratio
  public void setScale(boolean fixedAspect, boolean xScaleOnly)
  {
  	// actual size of device without insets
  	Dimension actualSize = new Dimension(getWidth() - getInsets().left - getInsets().right, getHeight() - getInsets().top - getInsets().bottom);

  	// limit minimum size to 20% of normal size to avoid scaling problems (small overlay images may have size 0)
  	if(actualSize.width < NORMAL_W / 5)
  		actualSize.width = NORMAL_W / 5;

  	if(actualSize.height < NORMAL_H / 5)
  		actualSize.height = NORMAL_H / 5;

  	if(fixedAspect) {
    	double aspectMismatch = (double)actualSize.getWidth() / (double)actualSize.getHeight() / aspectRatio;

    	if(aspectMismatch > 1.02) {  // is actual aspect ratio more than 2% bigger than normal?
  			actualSize.width = (int)(actualSize.getHeight() * aspectRatio);  // then make width smaller
  		} else if(aspectMismatch < 0.98) {  // is actual aspect ratio more than 2% smaller than normal?
  			actualSize.height = (int)(actualSize.getWidth() / aspectRatio);  // then make height smaller
  		}
  	}
  	
  	// scale factors for drawing
  	widthScale = actualSize.getWidth() / NORMAL_W;
  	
  	if(xScaleOnly)
  		heightScale = widthScale;  // scale is only determined by window width (for printers and plotters)
  	else
  		heightScale = actualSize.getHeight() / NORMAL_H;

  	// add insets again
  	actualSize.width += getInsets().left + getInsets().right;
  	actualSize.height += getInsets().top + getInsets().bottom;
  	
  	if(extDeviceWindow != null)
  		extDeviceWindow.setFrameSize(actualSize);
  }

  // set standard size of device panel
  public void setNormalSize()
  {
    Dimension normalSize;
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    
    aspectRatio = (double)NORMAL_W / (double)NORMAL_H;

    // set panel to standard size
    normalSize = new Dimension(NORMAL_W + getInsets().left + getInsets().right, NORMAL_H + getInsets().top + getInsets().bottom);
    setPreferredSize(normalSize);

    // check if normalSize fits in screenSize
    if(normalSize.getHeight() > screenSize.getHeight())
      setSize(screenSize); // resize to screen on smaller devices
    else
      setSize(normalSize);
  }

  public void setRealSize(double width, double height)
  {
    setSize((int)(width * Toolkit.getDefaultToolkit().getScreenResolution()), (int)(height * Toolkit.getDefaultToolkit().getScreenResolution()));
  }
/*
  public void setWindowSize()
  {
  	if(extDeviceWindow != null)
  		extDeviceWindow.setFrameSize(new Dimension(getWidth(), getHeight()));
  }
*/
  // MouseListener methods

  public void mousePressed(MouseEvent event)
  {
  }

  public void mouseReleased(MouseEvent event)
  {
  }

  public void mouseClicked(MouseEvent event)
  {
  }

  public void mouseEntered(MouseEvent event)
  {
  }

  public void mouseExited(MouseEvent event)
  {
  }

  // KeyListener methods

  public void keyPressed(KeyEvent event)
  {
    int keyCode = event.getKeyCode();

    switch(keyCode) {
    case KeyEvent.VK_END:
      break;

    case KeyEvent.VK_HOME:
      break;

    case KeyEvent.VK_DELETE:
      break;

    case KeyEvent.VK_INSERT:
      return;

    case KeyEvent.VK_UP:
      break;

    case KeyEvent.VK_DOWN:
      break;

    case KeyEvent.VK_LEFT:
      break;

    case KeyEvent.VK_RIGHT:
      break;

    case 'S':
      ioInterface.highSpeed = !ioInterface.highSpeed;
      deviceWindow.setTitle(hpName + (ioInterface.highSpeed? " High Speed" : ""));
      break;

    default:
      return;
    }

    repaint();
  }

  public void keyReleased(KeyEvent event)
  {
  }

  public void keyTyped(KeyEvent event)
  {
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
  
  public void paint(Graphics g)
  {
  	g2d = getG2D(g);
  }

  public int output(int status, int value)
  {
    return(status);
  }

  public void soundStop()
  {
  }

  public void close()
  {
    // stop all threads including sounds and images and free all resources
  	if(ioInterface != null) {
  		ioInterface.stop();  // stop interface thread
  		ioInterface.mainframe.ioDevices.removeElement(this);  // remove device object from devices list
  	}
  	
    setVisible(false);  // close panel

    if(createWindow)
      deviceWindow.dispose();  // close window
    
    if(deviceImageMedia != null)
    	deviceImageMedia.close();

    System.out.println(hpName + " unloaded.");
  }
}
