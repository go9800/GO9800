/*
 * HP9800 Emulator
 * Copyright (C) 2006 Achim Buerger
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
 */

package io.HP9810A;

import java.awt.*;

import io.*;
import emu98.*;

public class HP9810AMainframe2 extends HP9810AMainframe
{
  private static final long serialVersionUID = 1L;

  public HP9810AMainframe2(Emulator emu)
  {
    super(emu);

    LED_X = 137;
    LED_Y = 27;
    LED_SEGMENT_SIZE = 3;
    displayImage = new ImageMedia("media/HP9810A/HP9810A_Display_2.jpg").getImage();
  }
  
  public void display(int reg, int i)
  {
    int[][] displayBuffer = ioUnit.bus.display.getDisplayBuffer();
    int x = getInsets().left;
    int y = getInsets().top;
    int x1, y1, y2, segments;
    Graphics g = this.getGraphics();

    if(backgroundImage && g != null) {
      segments = displayBuffer[reg][i];

      // graphic digit position
      x += DISPLAY_X + LED_X + i * (LED_SEGMENT_SIZE + 6);
      y += DISPLAY_Y + LED_Y + reg * (2 * LED_SEGMENT_SIZE + 32);

      // segment end points
      x1 = x + LED_SEGMENT_SIZE;
      y1 = y + LED_SEGMENT_SIZE;
      y2 = y1 + LED_SEGMENT_SIZE;


      g.setColor(ledBack);
      g.fillRect(x, y, LED_SEGMENT_SIZE+1, 2 * LED_SEGMENT_SIZE+1);
      g.setColor(ledRed);

      if(segments == 0)
        return;

      // segment a
      if((segments & 0x40) != 0) {
        g.drawLine(x, y, x1, y);
      }

      // segment b
      if((segments & 0x20) != 0) {
        g.drawLine(x1, y, x1, y1);
      }

      // segment c
      if((segments & 0x02) != 0) {
        g.drawLine(x1, y1, x1, y2);
      }

      // segment d
      if((segments & 0x04) != 0) {
        g.drawLine(x, y2, x1, y2);
      }

      // segment e
      if((segments & 0x08) != 0) {
        g.drawLine(x, y1, x, y2);
      }

      // segment f
      if((segments & 0x80) != 0) {
        g.drawLine(x, y, x, y1);
      }

      // segment g
      if((segments & 0x10) != 0) {
        g.drawLine(x, y1, x1, y1);
      }

      // segment p
      if((segments & 0x01) != 0) {
        g.drawLine(x1-1, y2, x1-1, y2);
      }
    }
  }
}
