/*
 * HP9800 Emulator
 * Copyright (C) 2006-2010 Achim Buerger
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
 * 19.12.2007 Class created 
 * 23.05.2016 Rel. 1.61: Changed drawRegister(): draw lamp only if changed from previous state
 * 02.08.2016 Rel. 2.00: Changed access to M- and T-Register for micro-code CPU
 */

package emu98;

import io.ImageMedia;

import java.awt.*;
import java.awt.event.*;

public class HP2116Panel extends Frame implements KeyListener, Runnable
{
  private static final long serialVersionUID = 1L;
  Image hp2116PanelImage;
  Color lampOff, lampOn;
  CPU cpu;
  Thread panelThread;
  int timerValue = 100;
  int previous1 = 0177777, previous2 = 0177777, previous3 = 0177777, previous4 = 0177777, previous5 = 0177777; 

  public HP2116Panel(CPU cpu)
  {
    super("HP9800 Panel");

    this.cpu = cpu;

    addKeyListener(this);
    addWindowListener(new windowListener());
    addMouseListener(new mouseListener());

    hp2116PanelImage = new ImageMedia("media/HP9800/HP2116Panel.jpg").getImage();
    setResizable(false);
    setLocation(1000,0);

    setBackground(Color.BLACK);
    setForeground(Color.WHITE);
    lampOff = new Color(30, 30, 30);
    lampOn = new Color(255, 255, 100);

    panelThread = new Thread(this, "HP2116 Panel");
    // Set emulator priority lower to guarantee that events such as keypresses
    // and device thread timer expirations get service immediately
    panelThread.setPriority(Thread.NORM_PRIORITY);

    // wait until background image has been loaded
    while(hp2116PanelImage.getWidth(this) < 0) {
      try
      {
        Thread.sleep(100);
      } catch (InterruptedException e)
      { }
    }

    setSize(hp2116PanelImage.getWidth(this) + getInsets().left + getInsets().right, hp2116PanelImage.getHeight(this) + getInsets().top + getInsets().bottom);

    panelThread.start();

    System.out.println("HP2116 Panel loaded.");
  }

  public void run()
  {
    while(true) {
      try {
        Thread.sleep(timerValue);
      } catch(InterruptedException e) {
        // restart timer with (changed) timerValue
        continue;
      }

      if(this.isVisible()) {
        previous1 = drawRegister(0, cpu.Tregister.getValue(), previous1);
        previous2 = drawRegister(1, cpu.Pregister.getValue(), previous2);
        previous3 = drawRegister(2, cpu.Mregister.getValue(), previous3);
        previous4 = drawRegister(3, cpu.Aregister.getValue(), previous4);
        previous5 = drawRegister(4, cpu.Bregister.getValue(), previous5);
        timerValue = 10;
      }
      else
        timerValue = 100;
    }
  }
  
  public void stop()
  {
  	panelThread.stop();
  	panelThread = null;
  }

  class windowListener extends WindowAdapter
  {
    public void windowClosing(WindowEvent event)
    {
      setVisible(false);
    }
  }

  class mouseListener extends MouseAdapter
  {
    public void mousePressed(MouseEvent event)
    {
    }

    public void mouseReleased(MouseEvent event)
    {
    }
  }

  public void keyPressed(KeyEvent event)
  {
    int keyCode = event.getKeyCode();

    switch(keyCode) {
    }
  }

  public void keyReleased(KeyEvent event)
  {
  }

  public void keyTyped(KeyEvent event)
  {
  }

  public int drawRegister(int pos, int value, int previous)
  {
    Graphics g = this.getGraphics();
    int x, y;
    int i, actual = value;

    y = 62 * pos + 71;

    for(i = 0; i < 16; i++) {
      //if((value & 1) != (previous & 1)) {
      x = 833 - 39 * i - 17 * (i / 3);
      if((value & 1) == 0)
        g.setColor(lampOff);
      else
        g.setColor(lampOn);

      g.fillOval(x, y, 17, 17);
      //}

      value >>= 1;
    previous >>= 1;
    }

    return(actual);
  }

  public void paint(Graphics g)
  {
    int x = getInsets().left;
    int y = getInsets().top;

    g.drawImage(hp2116PanelImage, x, y, hp2116PanelImage.getWidth(this), hp2116PanelImage.getHeight(this), this);
  }
}
