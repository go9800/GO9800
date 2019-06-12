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
 * 26.05.2007 Class created 
 * 05.04.2010 Rel. 1.50 Class now inherited from IOdevice and completely reworked
 * 25.10.2017 Rel. 2.03 Changed static access to ioUnit, removed deprecated use of ioRegister
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 * 02.01.2018 Rel. 2.10 Added use of class DeviceWindow
 * 01.06.2019 Rel. 2.30 Added HP11305A Image, interface management from H9868A
 */

package io;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import emu98.DeviceWindow;

public class HP11305A extends IOdevice implements ActionListener
{
	private static final long serialVersionUID = 1L;

	// HP11305A reals size in inches
	double REAL_W = 16.73, REAL_H = 5.62;

	int INTERFACE_W = 44, INTERFACE_H = 262;
	int COVER_W = 45, COVER_H = 198;
	int LABEL_W = 19, LABEL_H = 182;
	int SELECTCODE_W = 32, SELECTCODE_H = 25;
	int[] INTERFACE_X = {0, 71, 121, 172, 223, 374, 424, 474};
	int SELECTCODE_DX = 6, LABEL_DX = 20;
	int INTERFACE_Y = 0, COVER_Y = 39, LABEL_Y = 49, SELECTCODE_Y = 234;

	IOinterface[] interfaceSlots;
	IOinterface intrface;
	IOdevice device;
	ImageMedia coverImageMedia, interfaceImageMedia, hp11273LabelImageMedia, hp11302LabelImageMedia;
	Image hp11305Image, coverImage, interfaceImage, hp11273Image, hp11302Image;
	Color selectColor;
	int selectedSlot = 0;
	boolean backgroundImage = false;

	// input fields
	protected static final int FIRST = 0x0400;
	protected static final int PLATTER = 0x0300;
	protected static final int INITIALIZE = 0x0080;
	protected static final int HEAD = 0x0040;
	protected static final int READ = 0x0020;
	protected static final int SECTOR = 0x001f;
	protected static final int CYLINDER = 0x00ff;

	// output status
	protected static final int POWER_ON = 0x0800;           // SI3
	protected static final int ADDRESS_ERROR = 0x0400;      // SI2
	protected static final int CKWORD_ERROR = 0x0200;       // SI1
	protected static final int DRIVE_UNSAFE_ERROR = 0x0100; // SI0

	protected static final int IDLE_TIMER = 500;
	protected static int BUSY_TIMER = 10;

	private HP9867B[] hp9867b;
	private int drive = 0, disk = 0, unit = 0, head = 0, cylinder = 0, sector = 0; // variables store disk parameters from FIRST command output
	private int accessMode = 0, initialize = 0;  // variables store disk parameters from FIRST command output
	private boolean debug = false;
	
	public HP11305A(String[] parameters, IOinterface ioInterface)
	{
		super("HP11305A", ioInterface);

		int numDisks = 2, numDrives = 0;

		NORMAL_W = 800;
		NORMAL_H = 262;

		addKeyListener(this);

		deviceImageMedia = new ImageMedia("media/HP9880A/HP11305A.png", ioInterface.mainframe.imageController);
		coverImageMedia = new ImageMedia("media/HP9868A/HP9868A_Cover.png", ioInterface.mainframe.imageController);
		interfaceImageMedia = new ImageMedia("media/HP9868A/HP9800_Interface.png", ioInterface.mainframe.imageController);
		hp11273LabelImageMedia = new ImageMedia("media/HP9880A/HP11273B_Label1.png", ioInterface.mainframe.imageController);
		hp11302LabelImageMedia = new ImageMedia("media/HP9880A/HP11302B_Label.png", ioInterface.mainframe.imageController);

		interfaceSlots = new IOinterface[7];

		setBackground(Color.BLACK);

		try {
			numDisks = Integer.parseInt(parameters[0]); // not used, always = 2
			numDrives = Integer.parseInt(parameters[1]);
		} catch (Exception e) {
			System.err.println(e.toString());
		}

		BUSY_TIMER = ioInterface.ioUnit.time_10ms;

		hp9867b = new HP9867B[2];
		HP9867B.time_10ms = BUSY_TIMER;

		for(int drive = 0; drive < numDrives && drive < 2; drive++) {
			hp9867b[drive] = new HP9867B(drive, ioInterface);  // unit numbers are 0+1 for drive 0 and 2+3 for drive 1
			hp9867b[drive].setDeviceWindow(new DeviceWindow(hp9867b[drive]));  // create JFrame for device
		}
	}

	public int output(int status)
	{
		debug = ioInterface.ioUnit.console.getDebugMode();
		if((status & FIRST) != 0) {
			unit = (status & PLATTER) >> 8;
		initialize = (status & INITIALIZE) >> 7;
		head = (status & HEAD) >> 6;
		accessMode = (status & READ) >> 5;
		sector = status & SECTOR;

		drive = unit / 2;
		disk = unit % 2;

		status = POWER_ON;
		} else {
			cylinder = status & CYLINDER;
			if(debug)
				ioInterface.ioUnit.console.append("HP9880A Commmand: init=" + initialize + " read=" + accessMode + " unit=" + unit + " head=" + head + " cylinder=" + cylinder + " sector=" + sector + "\n");

			// put initialize flag in bit 1, read flag in bit 0
			accessMode += initialize << 1;
			if(hp9867b[drive] != null) {
				status = hp9867b[drive].disks[disk].output(head, cylinder, sector, accessMode);
			}
			else
				status = ADDRESS_ERROR;
		}

		return(status);
	}

	public void setDeviceWindow(JFrame window)
	{
		super.setDeviceWindow(window);

		if(createWindow) {
			deviceWindow.setResizable(true);
			deviceWindow.setLocation(740, 0);

			menuBar.removeAll();  // remove dummy menu

			JMenu runMenu = new JMenu("Run");
			runMenu.add(makeMenuItem("Exit"));
			menuBar.add(runMenu);

			JMenu viewMenu = new JMenu("View");
			viewMenu.add(makeMenuItem("Normal Size", KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK));
			viewMenu.add(makeMenuItem("Real Size", KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK));
			viewMenu.add(makeMenuItem("Hide Menu", KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK));
			menuBar.add(viewMenu);

			JMenu deviceMenu = new JMenu("Drive");
			deviceMenu.add(makeMenuItem("HP9867B"));
			deviceMenu.addSeparator();
			deviceMenu.add(makeMenuItem("Unload", KeyEvent.VK_DELETE, 0));
			menuBar.add(deviceMenu);
		}

		// set size of surrounding JFrame only after loading all window components 
		addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e)
			{
				setScale(true, false);
			}
		});

		window.addComponentListener(new ComponentAdapter() {
			public void componentShown(ComponentEvent e)
			{
				for(int drive = 0; drive < 2 && drive < 2; drive++) {
					if(hp9867b[drive] != null) {
						hp9867b[drive].deviceWindow.setVisible(true);
						hp9867b[drive].setNormalSize();
					}
				}
			}

			public void componentHidden(ComponentEvent e)
			{
				for(int drive = 0; drive < 2 && drive < 2; drive++) {
					if(hp9867b[drive] != null)
						hp9867b[drive].deviceWindow.setVisible(false);
				}
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
		int i, drive = selectedSlot- 5;

		intrface = interfaceSlots[selectedSlot];

		switch(1) { // just to use break for ending cmd compare
			case 1:
				if(cmd.startsWith("Exit")) {
					close();
					break;
				}

				if(cmd.startsWith("Normal Size")) {
					setNormalSize();
					break;
				}

				if(cmd.startsWith("Real Size")) {
					setRealSize(REAL_W, REAL_H);
					break;
				}

				if(cmd.startsWith("Hide Menu")) {
					if(extDeviceWindow != null)
						extDeviceWindow.setFrameSize(!menuBar.isVisible());
					break;
				}

				if(cmd.startsWith("Unload")) {
					if(drive >= 0 && drive < 2 && hp9867b[drive] != null) {
						hp9867b[drive].close();
						hp9867b[drive] = null;
						selectedSlot = 0;
						break;
					}
				}

				if(cmd.startsWith("HP9867B")) {
					if(drive >= 0 && drive < 2 && hp9867b[drive] == null) {
						hp9867b[drive] = new HP9867B(drive, ioInterface);
						hp9867b[drive].setDeviceWindow(new DeviceWindow(hp9867b[drive]));  // create JFrame for device
						hp9867b[drive].deviceWindow.setVisible(true);
						hp9867b[drive].deviceWindow.setState(JFrame.NORMAL);
						hp9867b[drive].setNormalSize();
						selectedSlot = 0;
						break;
					}
				}

				// ending up here means an error condition
				selectColor = Color.RED;
		}

		repaint();
	}

	public void mousePressed(MouseEvent event)
	{
		// get unscaled coordinates of mouse position
		int x = (int)((event.getX() - getInsets().left) / widthScale); 
		int y = (int)((event.getY() - getInsets().top) / heightScale);

		if(x >= 70 && x <= 620) {
			for(int slot = 1; slot < 7; slot++) {
				int drive = slot - 5;

				if(x >= INTERFACE_X[slot] && x <= INTERFACE_X[slot] + INTERFACE_W) {
					// HP11273B interface is not selectable
					if(interfaceSlots[slot] != this.ioInterface) {
						
						// show drive if interface is newly selected, otherwise hide drive
						if(drive >= 0 && drive < 2 && hp9867b[drive] != null) {
							hp9867b[drive].deviceWindow.setVisible(selectedSlot != slot);
							// put HP11305A on top again
							this.deviceWindow.setVisible(true);
	  				}

						if(selectedSlot != slot && interfaceSlots[slot] != this.ioInterface) {
							selectColor = Color.YELLOW;
							selectedSlot = slot;
						} else {
							selectedSlot = 0;
						}

						repaint();
					}
				}
			}
		}
	}

	public void mouseReleased(MouseEvent event)
	{
	}

	public void keyPressed(KeyEvent event)
	{
		ActionEvent actionEvent;
		int keyCode = event.getKeyCode();

		event.consume(); // do not pass key event to other levels (e.g. menuBar)

		switch(keyCode) {
			case 'M':
				if(event.isControlDown())
					if(extDeviceWindow != null)
						extDeviceWindow.setFrameSize(!menuBar.isVisible());
				break;

			case 'N':
				if(event.isControlDown())
					setNormalSize();
				break;

			case 'R':
				if(event.isControlDown())
					setRealSize(REAL_W, REAL_H);
				break;

			case KeyEvent.VK_DELETE:
				actionEvent = new ActionEvent(this, ActionEvent.ACTION_FIRST, "Unload");
				actionPerformed(actionEvent);
				break;
		}
	}

	public void paint(Graphics g)
	{
		int slot;
		int x = 0, y = 0; // positioning is done by g2d.translate()

		super.paint(g);
		setScale(true, false);

		// scale device image to normal size
		hp11305Image = deviceImageMedia.getScaledImage((int)(NORMAL_W * widthScale), (int)(NORMAL_H * heightScale));
		backgroundImage = g2d.drawImage(hp11305Image, x, y, NORMAL_W, NORMAL_H, this);

		if(!backgroundImage)  // don't overlays before background is ready
			return;

		interfaceImage = interfaceImageMedia.getScaledImage((int)(INTERFACE_W * widthScale), (int)(INTERFACE_H * heightScale));
		coverImage = coverImageMedia.getScaledImage((int)(COVER_W * widthScale), (int)(COVER_H * heightScale));
		hp11273Image = hp11273LabelImageMedia.getScaledImage((int)(LABEL_W * widthScale), (int)(LABEL_H * heightScale));
		hp11302Image = hp11302LabelImageMedia.getScaledImage((int)(LABEL_W * widthScale), (int)(LABEL_H * heightScale));


		for(slot = 1; slot < 7; slot++) {
			switch(slot) {
				case 4:
					// draw HP11273B interface
					g2d.drawImage(interfaceImage, INTERFACE_X[slot], INTERFACE_Y, INTERFACE_W, INTERFACE_H, this);
					g2d.drawImage(hp11273Image, INTERFACE_X[slot] + LABEL_DX, LABEL_Y, LABEL_W, LABEL_H, this);
					break;

				case 1:
				case 2:
				case 3:
					// draw cover
					g2d.drawImage(coverImage, INTERFACE_X[slot], COVER_Y, COVER_W, COVER_H, this);
					break;

				case 5:
				case 6:
					// draw HP11302B interface
					int drive = slot - 5;
					if(hp9867b[drive] != null) {
						g2d.drawImage(interfaceImage, INTERFACE_X[slot], INTERFACE_Y, INTERFACE_W, INTERFACE_H, this);
						g2d.drawImage(hp11302Image, INTERFACE_X[slot] + LABEL_DX, LABEL_Y, LABEL_W, LABEL_H, this);
					} else {
		    		// draw cover if slot is empty and not selected
		    		if(slot != selectedSlot)
		    			g2d.drawImage(coverImage, INTERFACE_X[slot], COVER_Y, COVER_W, COVER_H, this);
					}
					break;
			}
			
			if(slot == selectedSlot) {
				g2d.setColor(selectColor);
				g2d.drawRect(INTERFACE_X[slot], COVER_Y, COVER_W, COVER_H);
			}
		}
	}

	public void close()
	{
		for(int drive = 0; drive < 2; drive++) {
			if(hp9867b[drive] != null) {
				hp9867b[drive].close();
			}
		}

		if(coverImageMedia != null)
			coverImageMedia.close();

		if(interfaceImageMedia != null)
			interfaceImageMedia.close();

		if(hp11273LabelImageMedia != null)
			hp11273LabelImageMedia.close();

		if(hp11302LabelImageMedia != null)
			hp11302LabelImageMedia.close();

		super.close();
	}
}

