/*
 * HP9800 Emulator
 * Copyright (C) 2006-2019 Achim Buerger
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
 * 26.05.2019 Rel. 2.30 Class created 
 */

package io;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.Enumeration;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

public class HP9868A extends IOdevice implements ActionListener
{
  private static final long serialVersionUID = 1L;

  // HP9865A reals size in inches
  double REAL_W = 16.73, REAL_H = 5.62;
  int INTERFACE_W = 44, INTERFACE_H = 262;
  int COVER_W = 45, COVER_H = 198;
  int LABEL_W = 19, LABEL_H = 182;
  int SELECTCODE_W = 32, SELECTCODE_H = 25;
  int[] INTERFACE_X = {0, 70, 122, 172, 223, 274, 324, 374, 424, 474, 524, 574};
  int SELECTCODE_DX = 6, LABEL_DX = 20;
  int INTERFACE_Y = 0, COVER_Y = 39, LABEL_Y = 49, SELECTCODE_Y = 234;

  IOinterface[] interfaceSlots;
  IOinterface intrface;
  IOdevice device;
  ImageMedia coverImageMedia, interfaceImageMedia;
  ImageMedia[] selectcodeImageMedia; 
  Image hp9868Image, coverImage, interfaceImage, labelImage, selectcodeImage;
  Color selectColor;
  int selectedSlot = 0;
  boolean backgroundImage = false;
  boolean isInitialized = false;

  public HP9868A(IOinterface ioInterface)
  {
    super("HP9868A", ioInterface);

    NORMAL_W = 800;
    NORMAL_H = 262;

    addKeyListener(this);

    deviceImageMedia = new ImageMedia("media/HP9868A/HP9868A.png", ioInterface.mainframe.imageController);
    coverImageMedia = new ImageMedia("media/HP9868A/HP9868A_Cover.png", ioInterface.mainframe.imageController);
    interfaceImageMedia = new ImageMedia("media/HP9868A/HP9800_Interface.png", ioInterface.mainframe.imageController);
    selectcodeImageMedia = new ImageMedia[16];

    for(int i = 1; i < 16; i++) {
    	selectcodeImageMedia[i] = new ImageMedia("media/HP9868A/Selectcode_" + i + ".png", ioInterface.mainframe.imageController);
    }
    
    interfaceSlots = new IOinterface[12];
    
    setBackground(Color.BLACK);
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

      JMenu deviceMenu = new JMenu("Devices");
      deviceMenu.add(selectCodeMenu("HP9860A", 12, 12));
      deviceMenu.add(selectCodeMenu("HP9861A", 0, 9));
      deviceMenu.add(selectCodeMenu("HP9862A", 14, 14));
      deviceMenu.add(selectCodeMenu("HP9865A", 1, 10));
      deviceMenu.add(selectCodeMenu("HP9866A", 0, 9));
      deviceMenu.add(selectCodeMenu("HP9866B", 0, 9));
      deviceMenu.add(selectCodeMenu("HP9880B", 11, 11));
      deviceMenu.addSeparator();
      deviceMenu.add(makeMenuItem("Unload Device", KeyEvent.VK_DELETE, 0));
      menuBar.add(deviceMenu);
  	}
  	
		// set size of surrounding JFrame only after loading all window components 
		addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				setScale(true, false);
			}
		});

		setNormalSize();
  }
  
  private JMenu selectCodeMenu(String device, int min, int max)
  {
    JMenu devMenu = new JMenu(device);
    int selectCode;
    
    for(int i = min; i <= max; i++) {
    	if(i == 0)
    		selectCode = 15;
    	else
      	selectCode = i;
    	
      devMenu.add(makeMenuItem("Selectcode " + selectCode, 0, 0, device + " @" + selectCode));
    }
    
    return(devMenu);
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
  	String dev, sc;
  	int i, selectCode;

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
  				if(intrface != null) {
  					intrface.ioDevice.close();
  					interfaceSlots[selectedSlot] = null;
  					selectedSlot = 0;
  					ioInterface.mainframe.hp9800Window.makeDevicesMenu();  // refresh Device menu in mainframe
  					break;
  				}
  				// error condition
  			}
  			
  			if(cmd.startsWith("HP")) {
  				if(intrface == null && selectedSlot != 0) {
  					i = cmd.indexOf(" @");
  					if(i > 0) {
  						sc = cmd.substring(i);
  						selectCode = Integer.valueOf(sc.substring(2));
  						dev = cmd.substring(0, i);

  						device = ioInterface.mainframe.config.loadDevice(dev, selectCode);
  						if(device != null) {
  							device.deviceWindow.setVisible(true);
  							device.deviceWindow.setState(JFrame.NORMAL);
  							device.setNormalSize();

  							interfaceSlots[selectedSlot] = device.ioInterface;
  							selectedSlot = 0;
  							
  	  					ioInterface.mainframe.hp9800Window.makeDevicesMenu();  // refresh Device menu in mainframe
  							break;
  						}
  					}
  				}
  			}
  			
  			// ending up here means an error condition
  			selectColor = Color.RED;
  	}

  	repaint();
  }

  public void mousePressed(MouseEvent event)
  {
  	IOinterface ioInterface; 
  	// get unscaled coordinates of mouse position
  	int x = (int)((event.getX() - getInsets().left) / widthScale); 
  	int y = (int)((event.getY() - getInsets().top) / heightScale);

  	if(x >= 70 && x <= 620) {
  		for(int slot = 1; slot < 12; slot++) {
  			ioInterface = interfaceSlots[slot];
  			
  			// HP9868A interface is not selectable
  			if(x >= INTERFACE_X[slot] && x <= INTERFACE_X[slot] + INTERFACE_W  && ioInterface != this.ioInterface) {
  				
  				// show device if interface is newly selected, otherwise hide device
  				if(ioInterface != null && ioInterface.ioDevice.deviceWindow != null) {
						ioInterface.ioDevice.deviceWindow.setVisible(selectedSlot != slot);
						// put HP9868A on top again
						this.deviceWindow.setVisible(true);
  				}

  				if(selectedSlot != slot) {
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
  	AffineTransform g2dSaveTransform;
    Enumeration<IOinterface> interfaceEnum = null;
    int slot;
  	int x = 0, y = 0; // positioning is done by g2d.translate()
  	
  	// initial assignment of interfaces to slots 
  	if(!isInitialized) {
  		for(slot = 1, interfaceEnum = ioInterface.mainframe.ioInterfaces.elements(); interfaceEnum.hasMoreElements() && slot < 12; ) {
  			intrface = (IOinterface)interfaceEnum.nextElement();
  			// is it an internal interface (keyboard, display, printer)?
  			if(intrface.internalInterface)
  				continue; // then don't show internal interfaces (keyboard, display, printer, mag. card, beeper)

  			interfaceSlots[slot] = intrface;
  			slot++;
  		}
  		
  		isInitialized = true;
  	}
  	
    super.paint(g);
    setScale(true, false);

  	// scale device image to normal size
  	hp9868Image = deviceImageMedia.getScaledImage((int)(NORMAL_W * widthScale), (int)(NORMAL_H * heightScale));
  	backgroundImage = g2d.drawImage(hp9868Image, x, y, NORMAL_W, NORMAL_H, this);
  	
  	if(!backgroundImage)  // don't overlays before background is ready
  		return;

		interfaceImage = interfaceImageMedia.getScaledImage((int)(INTERFACE_W * widthScale), (int)(INTERFACE_H * heightScale));
		coverImage = coverImageMedia.getScaledImage((int)(COVER_W * widthScale), (int)(COVER_H * heightScale));
		
    for(slot = 1; slot < 12; slot++) {
    	if(interfaceSlots[slot] != null) {
    		intrface = interfaceSlots[slot];
    		g2d.drawImage(interfaceImage, INTERFACE_X[slot], INTERFACE_Y, INTERFACE_W, INTERFACE_H, this);
    		
    		labelImage = intrface.labelImageMedia.getScaledImage((int)(LABEL_W * widthScale), (int)(LABEL_H * heightScale));
    		g2d.drawImage(labelImage, INTERFACE_X[slot] + LABEL_DX, LABEL_Y, LABEL_W, LABEL_H, this);
    		
    		if(intrface.selectCode != 0) {
    			// scale selectcode label with exchanged width and height scales because ist will be rotated after that
    			selectcodeImage = selectcodeImageMedia[intrface.selectCode].getScaledImage((int)(SELECTCODE_H * heightScale), (int)(SELECTCODE_W * widthScale));
    			
    			// for correct image rotation the axis must be in the coordinate origin
    	  	g2dSaveTransform = g2d.getTransform();  // save current transformation, changed by ROM blocks
    	  	// translate UR corner of selectcode label to desired position
    			g2d.translate(INTERFACE_X[slot] + SELECTCODE_DX + SELECTCODE_W, SELECTCODE_Y);
    			// rotate label by 90 degrees 
    			g2d.rotate(Math.PI / 2.);			
    			// draw label
    			g2d.drawImage(selectcodeImage, 0, 0, SELECTCODE_H, SELECTCODE_W, this);
    			g2d.setTransform(g2dSaveTransform);  // restore original transformation
    		}
    	} else {
    		// draw cover if slot is empty and not selected
    		if(slot != selectedSlot)
    			g2d.drawImage(coverImage, INTERFACE_X[slot], COVER_Y, COVER_W, COVER_H, this);
    	}
    	
    	if(slot == selectedSlot) {
    		g2d.setColor(selectColor);
    		g2d.drawRect(INTERFACE_X[slot], COVER_Y, COVER_W, COVER_H);
    	}
    }
  }
  
  public void dispose()
  {
  	int i;
  	
 		if(coverImageMedia != null)
 			coverImageMedia.close();

 		if(interfaceImageMedia != null)
 			interfaceImageMedia.close();

 		for(i = 1; i < 16; i++) {
 		if(selectcodeImageMedia[i] != null)
 			selectcodeImageMedia[i].close();
 		}

  	super.close();
  }
 }
