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
 * 08.11.2006 Class derived from HP9810DisplayInterface
 * 15.12.2006 Bugfix Rel. 0.21: Loop over all 16 display positions (not only 15)
 * 18.12.2006 Rel. 0.21 Bugfix: restart display-off timer after complete display output
 * 26.12.2006 Rel. 0.22 Bugfix: display-off problem finally fixed
 * 04.01.2007 Rel. 0.22 Added monitoring of SRQ during display output and abandoning of display timing
 * 09.04.2007 Rel. 1.00 Code reorganized for usage of class HP9800Mainframe
 * 05.09.2007 Rel. 1.20 Display-off logic reworked
 * 24.09.2007 Rel. 1.20 Changed display blanking control from fixed timer to instruction counter
 * 09.01.2009 Rel. 1.33 Changed Thread.sleep() to ioUnit.wait() for SRQ notification 
 * 22.04.2009 Rel. 1.41 Changed display timing to use ioUnitister.time_xx() and use only one wait() per complete scan
 */

package io.HP9820A;

import io.DisplayInterface;
import io.IOinterface;
import io.HP9800Mainframe;

public class HP9820DisplayInterface extends IOinterface implements DisplayInterface
{
  HP9800Mainframe mainframe;
  int[][] displayBuffer;
  boolean equal;  // false if display contents has changed
  
  public HP9820DisplayInterface(HP9800Mainframe mainframe)
  {
    super(0);

    this.mainframe = mainframe;
    
    displayBuffer = new int[5][16];
    
    for(int i = 0; i < 5; i++)
      for(int j = 0; j < 16; j++)
        displayBuffer[i][j] = 0;
    
    System.out.println("HP9820 Display loaded.");
  }
  
  public int[][] getDisplayBuffer()
  {
    return(displayBuffer);
  }
  
  public int getKeyLEDs()
  {
    return(0);
  }

  public void blank()
  {
    for(int i = 0; i < 5; i++)
      for(int j = 0; j < 16; j++)
        displayBuffer[i][j] = 0;

    int l = mainframe.getInsets().left;
    int t = mainframe.getInsets().top;
    mainframe.repaint(l + HP9800Mainframe.DISPLAY_X + HP9800Mainframe.LED_X, t + HP9800Mainframe.DISPLAY_Y + HP9800Mainframe.LED_Y, 320, 20);
  }

  public boolean output(int printBuffer)
  {
    synchronized(ioUnit) {
      if(ioUnit.PEN) {
        mainframe.printOutput(printBuffer, ioUnit.getValue());

        ioUnit.PEN = false;
      }

      return(false);
    }
  }
  
  public boolean output()
  {
    int col, chr, dots;

    synchronized(ioUnit) {
      if(ioUnit.KLS) {
        return(false);
      }

      if(ioUnit.DEN) {
        // quadrant number
        chr = (ioUnit.getValue() & 0x3000) >> 12;

        // column number
        col = ((ioUnit.getValue() & 0x0f80) >> 7);
        if(col >= 16)
          col -= 6;

        // get LED column data
        dots = ioUnit.getValue() & 0x007f;

        // character number
        chr = chr * 4 + col / 5;
        // column number in character
        col = col % 5;

        equal = displayBuffer[col][chr] == dots;

        if(!equal) {
          displayBuffer[col][chr] = dots;
          mainframe.display(col, chr);
        }

        // with beginning of display output clear SRQ flag 
        if(chr == 0 && col == 0)
          ioUnit.dispSRQ = false;

        /*
         * With last column of last character wait 14ms
         * for approximate realistic display timing.
         * This also reduces host CPU load.
         * Do this only if no SRQ occured during the display phase,
         * otherwise timing critical SRQs may be lost (esp. from HP9860A).
         */

        if(col == 4) {
          try {
            if(chr == 15 && !ioUnit.dispSRQ) {
              ioUnit.wait(14);
            } else
              Thread.yield();
          } catch(InterruptedException e) { }
        }
      }

      return(false);
    }
  }
}