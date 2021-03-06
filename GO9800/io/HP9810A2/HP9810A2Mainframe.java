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
 * 07.04.2007 Class derived from HP9810A mainframe 
 * 05.03.2008 Rel. 1.21 Bugfix: DEN is now longer evaluated in display() method
 * 21.10.2017 Rel. 2.10 Added Graphics scaling using class Graphics2D
 */

package io.HP9810A2;


import io.*;
import io.HP9810A.HP9810AMainframe;

import java.awt.Graphics2D;
import java.awt.RenderingHints;

import emu98.*;

public class HP9810A2Mainframe extends HP9810AMainframe
{
  private static final long serialVersionUID = 1L;

  public HP9810A2Mainframe(Emulator emu)
  {
    super(emu);

    LED_X = +137;
    LED_Y = +28;
    LED_SEGMENT_SIZE = 3;
    displayImageMedia = new ImageMedia("media/HP9810A/HP9810A_Display_2.png", imageController);
  }
  
  public void display(Graphics2D g2d, int reg, int i)
  {
    int[][] displayBuffer = ioUnit.bus.display.getDisplayBuffer();
    int x = 0, y = 0; // positioning is done by g2d.translate()
    int x1, y1, y2, segments;

    if(backgroundImage && this.getGraphics() != null) {
      if(g2d == null)
      	g2d = getG2D(getGraphics());  // get current graphics if not given by paint()
      
      segments = displayBuffer[reg][i];

      // graphic digit position
      x += DISPLAY_X + LED_X + i * (LED_SEGMENT_SIZE + 6);
      y += DISPLAY_Y + LED_Y + reg * (2 * LED_SEGMENT_SIZE + 32);

      // segment end points
      x1 = x + LED_SEGMENT_SIZE;
      y1 = y + LED_SEGMENT_SIZE;
      y2 = y1 + LED_SEGMENT_SIZE;


      //g.fillRect(x, y, LED_SEGMENT_SIZE+1, 2 * LED_SEGMENT_SIZE+1);
      g2d.setColor(ledBack);
      g2d.fillRect(x - 1, y - 1, LED_SEGMENT_SIZE + 3, 2 * LED_SEGMENT_SIZE + 3); // draw digit background slightly greater than segment area
      g2d.setColor(ledRed);

      if(segments == 0)
        return;
      
  		// enable antialiasing for higher quality of line graphics
  		g2d.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));

      // segment a
      if((segments & 0x40) != 0) {
        g2d.drawLine(x, y, x1, y);
      }

      // segment b
      if((segments & 0x20) != 0) {
        g2d.drawLine(x1, y, x1, y1);
      }

      // segment c
      if((segments & 0x02) != 0) {
        g2d.drawLine(x1, y1, x1, y2);
      }

      // segment d
      if((segments & 0x04) != 0) {
        g2d.drawLine(x, y2, x1, y2);
      }

      // segment e
      if((segments & 0x08) != 0) {
        g2d.drawLine(x, y1, x, y2);
      }

      // segment f
      if((segments & 0x80) != 0) {
        g2d.drawLine(x, y, x, y1);
      }

      // segment g
      if((segments & 0x10) != 0) {
        g2d.drawLine(x, y1, x1, y1);
      }

      // segment p
      if((segments & 0x01) != 0) {
        g2d.drawLine(x1-1, y2, x1-1, y2);
      }
      
  		// disable antialiasing for higher speed
  		g2d.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF));
    }
  }
}
