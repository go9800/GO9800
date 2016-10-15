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
 * 13.09.2006 Class created
 * 18.09.2006 Added output to keyboard LEDs
 * 20.09.2006 Added output to internal printer
 * 01.10.2006 Changed sleep for display timing
 * 04.10.2006 Removed method setWindow and changed constructor
 * 18.12.2006 Rel. 0.21 Bugfix: restart display-off timer after complete display output
 * 26.12.2006 Rel. 0.22 Bugfix: display-off problem finally fixed
 * 04.01.2007 Rel. 0.22 Added monitoring of SRQ during display output and abandoning of display timing
 * 09.04.2007 Rel. 1.00 Code reorganized for usage of class HP9800Mainframe
 * 24.09.2007 Rel. 1.20 Changed display blanking control from fixed timer to instruction counter
 * 09.01.2009 Rel. 1.33 Changed Thread.sleep() to ioReg.wait() for SRQ notification 
 * 22.04.2009 Rel. 1.41 Changed display timing to use Ioregister.time_xx() and use only one wait() per complete scan
*/

package io.HP9810A;

import io.DisplayInterface;
import io.IOinterface;
import io.HP9800Mainframe;

public class HP9810DisplayInterface extends IOinterface implements DisplayInterface
{
  HP9800Mainframe mainframe;
  int[][] displayBuffer;
  int keyLEDs = 0;
  boolean equal;  // false if display contents has changed
  
  public HP9810DisplayInterface(HP9800Mainframe mainframe)
  {
    super(0);
    this.mainframe = mainframe;

    displayBuffer = new int[3][16];
    
    for(int i = 0; i < 3; i++)
      for(int j = 0; j < 15; j++)
        displayBuffer[i][j] = 0;
    
    equal = false;

    System.out.println("HP9810 Display loaded.");
    System.out.println("HP9810 KeyLEDs loaded.");
  }
  
  public int[][] getDisplayBuffer()
  {
    return(displayBuffer);
  }
  
  public int getKeyLEDs()
  {
    return(keyLEDs);
  }

  public void blank()
  {
    // switch Display off if necessary
    for(int i = 0; i < 3; i++)
      for(int j = 0; j < 15; j++) {
        displayBuffer[i][j] = 0;
        mainframe.display(i, j);
      }
  }

  public boolean output(int printValue)
  {
    if(ioReg.PEN) {
      printValue |= ioReg.getValue();
      mainframe.printOutput(printValue);

      ioReg.PEN = false;
    }

    return(false);
  }
  
  public boolean output()
  {
    int reg, pos, segments;

    synchronized(ioReg) {
      if(ioReg.KLS) {
        //System.out.println(Integer.toHexString(ioReg.getValue()));
        segments = ioReg.getValue() >> 8;
        // output only if a LED has changed
        if(segments != keyLEDs) {
          keyLEDs = segments;
          mainframe.displayLEDs(keyLEDs);
        }

        return(false);
      }

      if(ioReg.DEN) {
        // digit position
        pos = ioReg.getValue() & 0x000f;
        // register number
        reg = ((ioReg.getValue() & 0x0030) >> 4);
        if(reg == 3) reg = 0;

        // get digit segments
        segments = (ioReg.getValue() & 0x7f80) >> 7;

        equal = displayBuffer[reg][pos] == segments;

        if(!equal) {
          displayBuffer[reg][pos] = segments;
          mainframe.display(reg, pos);
        }

        // with beginning of display output clear SRQ flag 
        if(reg == 1 && pos == 0)
          ioReg.dispSRQ = false;

        /*
         * With last digit of last register sleep 12ms 
         * for approximate realistic display timing.
         * This also reduces host CPU load.
         * Do this only if no SRQ occured during the display phase,
         * otherwise timing critical SRQs may be lost (esp. from HP9860A).
         */

        if(pos == 14) {
          try {
            if(reg == 0 && !ioReg.dispSRQ) {
              ioReg.wait(ioReg.time_12ms);
            } else
              Thread.yield();
          } catch(InterruptedException e) { }
        }
      }

      return(false);
    }
  }
}
