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
 * 24.10.2017 Rel. 2.10: Added method close()
 * 25.10.2017 Rel. 2.03 Changed static access to IOregister
 * 25.10.2017 Rel. 2.10 Added method close() to stop thread
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 */

package io;

import java.awt.*;
import java.awt.event.*;

public class IOdevice extends Frame implements KeyListener, MouseListener
{
  public String hpName = null;

  private static final long serialVersionUID = 1L;
  protected IOinterface ioInterface;
  protected WindowAdapter winListener;
  
  public IOdevice()
  {
    // dummy constructor for Reflection API
  }
  
  public IOdevice(String hpName, IOinterface ioInterface)
  {
    super(hpName);
    this.hpName = hpName; // device name comes from child class
    
    // connection to ioInterface
    this.ioInterface = ioInterface;
    
    // add device to list for later cleanup
    ioInterface.mainframe.ioDevices.add(this);
    
    addKeyListener(this);
    addMouseListener(this);
    addWindowListener(winListener = new windowListener());
    addComponentListener(new ComponentRepaintAdapter());

    setBackground(Color.WHITE);
    setForeground(Color.BLACK);
  }
  
  class windowListener extends WindowAdapter
  {
    public void windowClosing(WindowEvent event)
    {
      close();
    }
  }

  // Repaint after window resize
  class ComponentRepaintAdapter extends ComponentAdapter
  {
    public void componentResized(ComponentEvent event)
    {
      event.getComponent().repaint();
    }
  }  
  
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
      this.setTitle(hpName + (ioInterface.highSpeed? " High Speed" : ""));
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

  public void paint(Graphics g)
  {
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
  	// stop all threads including sounds and images and free all ressources
    ioInterface.stop();  // stop interface thread
    ioInterface.mainframe.ioDevices.removeElement(this);  // remove device object from devices list
    setVisible(false);  // close window
    dispose();  // and remove it
    
    System.out.println(hpName + " unloaded.");
  }
}
