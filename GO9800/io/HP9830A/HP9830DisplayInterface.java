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
 * 26.05.2006 Inserted sleeps in output() method for timing
 * 28.05.2006 Repaint only partial window containg LEDs
 * 13.08.2006 Changed character re-coding for LED matrix
 * 07.09.2006 Implementing interface DisplayInterface
 * 14.09.2006 Type of displayBuffer int[] instead char[]
 * 14.09.2006 Display single (changed) characters instead of whole line to improve performance
 * 18.09.2006 Added method getKeyLEDs()
 * 19.09.2006 Ignore KLS (output to keyboard LEDs)
 * 18.12.2006 Rel. 0.21 Bugfix: Wait 16ms after output of one display line instead 1ms for each position.
 *            This avoids timing problems on Linux plattforms, where Thead.sleep(1) takes 3-4ms. 
 * 18.12.2006 Rel. 0.21 Bugfix: restart display-off timer after complete display output
 * 26.12.2006 Rel. 0.22 Bugfix: display-off problem finally fixed (display buffer cleared when off)
 * 04.01.2007 Rel. 0.22 Added monitoring of SRQ during display output and abandoning of display timing
 * 09.04.2007 Rel. 1.00 Code reorganized for usage of class HP9800Mainframe
 * 05.09.2007 Rel. 1.20 Display-off logic reworked
 * 24.09.2007 Rel. 1.20 Changed display blanking control from fixed timer to instruction counter
 * 09.01.2009 Rel. 1.33 Changed Thread.sleep() to ioUnit.wait() for SRQ notification 
 * 22.04.2009 Rel. 1.41 Changed display timing to use ioUnitister.time_xx() and use only one wait() per complete scan
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 */

package io.HP9830A;

import io.DisplayInterface;
import io.IOinterface;
import io.HP9800Mainframe;

public class HP9830DisplayInterface extends IOinterface implements DisplayInterface
{
  int[][] displayBuffer;
  boolean equal;  // false if display contents has changed
  
  public HP9830DisplayInterface(HP9800Mainframe hp9800Mainframe)
  {
    super(0, hp9800Mainframe);
    
    // one display line with 32 positions
    displayBuffer = new int[1][32];
    
    for(int i = 0; i < 32; i++)
      displayBuffer[0][i] = 0;
    
    equal = false;
    
    System.out.println("HP9830 Display loaded.");
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
    for(int j = 0; j < 32; j++)
    {
      displayBuffer[0][j] = ' ';
      mainframe.display(null, 0, j); // blank display
    }

    /* do NOT repaint mainframe as this may change g2d
    int l = mainframe.getInsets().left;
    int t = mainframe.getInsets().top;
    mainframe.repaint(l + mainframe.DISPLAY_X, t + mainframe.DISPLAY_Y, mainframe.DISPLAY_W, mainframe.DISPLAY_H);
    */
  }

  public boolean output(int printValue)
  {
    return(false);
  }
  
  public boolean output()
  {
    int pos, c1, c2;

    synchronized(ioUnit) {
      if(ioUnit.KLS)
        // ignore output to keyboard LEDs
        return(false);

      if(ioUnit.DEN) {
        // character position
        pos = ioUnit.getValue() & 0x000f;
        // get 2 characters
        c1 = (ioUnit.getValue() & 0xfc00) >> 10;
        c2 = (ioUnit.getValue() & 0x03f0) >> 4;

        equal = displayBuffer[0][pos] == c1;
        equal &= displayBuffer[0][pos+16] == c2;

        if(!equal) {
          displayBuffer[0][pos] = c1;
          displayBuffer[0][pos+16] = c2;

          mainframe.display(null, 0, pos);
          mainframe.display(null, 0, pos+16);
        }

        // with beginning of display output clear SRQ flag 
        if(pos == 0)
          ioUnit.dispSRQ = false;

        /*
         * With last character wait 13ms for approximate realistic display timing.
         * This is used by WAIT statement and also reduces host CPU load.
         * Do this only if no SRQ occured during the display phase,
         * otherwise timing critical SRQs may be lost (esp. from HP9860A).
         */

        try {
          if(pos == 15 && !ioUnit.dispSRQ) {
            ioUnit.wait(13);
          } else
            Thread.yield();
        } catch(InterruptedException e) { }
      }

      return(false);
    }
  }
}
