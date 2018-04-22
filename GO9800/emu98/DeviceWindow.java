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
 * 01.01.2018 Class created
 */ 

package emu98;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.UIManager;

import io.IOdevice;

public class DeviceWindow extends JFrame
{
  private static final long serialVersionUID = 1L;

  IOdevice ioDevice;

  Color gray = new Color(230, 230, 230);
  Color beige = new Color(215, 213, 178);
  Color brown = new Color(87, 87, 75);

  int MENU_H = 23;

  public JMenuBar menuBar;

  public DeviceWindow(IOdevice device)
  {
    super(device.hpName); // set window title

    ioDevice = device;

    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();

    JPanel contentPane = new JPanel();
    contentPane.setLayout(gridbag);
    setContentPane(contentPane);

    UIManager.put("MenuBar.background", beige);
    UIManager.put("Menu.background", beige);
    UIManager.put("MenuItem.background", beige);
    UIManager.put("CheckBoxMenuItem.background", beige);

    // Menu bar
    menuBar = new JMenuBar();
    menuBar.setMinimumSize(new Dimension(0, MENU_H));

    c.gridx = 0;
    c.gridy = 0;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.WEST;
    contentPane.add(menuBar, c);
    menuBar.setVisible(false); // hide menuBar until needed

    device.setMenuBar(menuBar); // menuBar is filled by device if needed

    // ScrollPane for device panel
    //JScrollPane deviceScrollPane = new JScrollPane (ioDevice, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    // Panel for drawing of device
    c.gridy = 1;
    c.weightx = 1.;
    c.weighty = 1.;
    c.fill = GridBagConstraints.BOTH;
    contentPane.add(ioDevice, c);

    pack(); // put components on their correct places
    addWindowListener(new windowListener());
  }

  public void setFrameSize()
  {
    setSize(ioDevice.getWidth() + getInsets().left + getInsets().right, ioDevice.getHeight() + (menuBar.isVisible() ? menuBar.getHeight() : 0) + getInsets().top + getInsets().bottom);
  }

  public void setFrameSize(Boolean showMenuBar)
  {
    setSize(ioDevice.getWidth() + getInsets().left + getInsets().right, ioDevice.getHeight() + (showMenuBar ? menuBar.getHeight() : 0) + getInsets().top + getInsets().bottom);
    menuBar.setVisible(showMenuBar);
  }

  class windowListener extends WindowAdapter
  {
    public void windowClosing(WindowEvent event)
    {
      setVisible(false);
      //ioDevice.close();
      //dispose();
    }
  }
}
