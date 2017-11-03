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
 * 13.06.2006 Bugfix: Use keyCode=-1 instead of 0 if no key pressed 
 * 31.07.2006 Don't check serviceRequested before issueing new Service Request
 * 07.09.2006 Implementing interface KeyboardInterface
 * 25.09.2007 Rel. 1.20 Don't run in separate thead
 * 11.12.2007 Rel. 1.20 Added method run() to simulate exact behaviour of keyboard interrupt, which is held only for about 5ms
 * 12.12.2007 Rel. 1.20 Don't input IO-bus value to IO-register in requestInterrupt(). This is now done by ioUnitister.serviceRequestAcknowledge()
 * 13.12.2007 Rel. 1.20 Release keyboard SRQ and STP after 5ms. This is done by KDN single-shot FF.   
 * 27.02.2008 Rel. 1.21 Added method release(). Keyboard is now released after a certain amount of executed instructions  
 * 09.01.2009 Rel. 1.33 Added synchronized(ioUnit){} and ioUnit.notifyAll() in requestInterrupt() 
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 */

package io.HP9810A;

import io.HP9800Mainframe;
import io.IOinterface;
import io.KeyboardInterface;

public class HP9810KeyboardInterface extends IOinterface implements KeyboardInterface
{
  int keyCode = -1;
  
  public HP9810KeyboardInterface(int selectCode, HP9800Mainframe hp9800Mainframe)
  {
    super(selectCode, hp9800Mainframe);
    System.out.println("HP9810 Keyboard loaded.");
  }

  public void release()
  {
    synchronized(ioUnit) {
      if(serviceRequested) {
        // on HP9810 KDN (Key Down) and SSI is held only until for 5ms by a one-shot FF
        // clear Service Request line
        ioUnit.SSI &= ~srqBits;

        // this in turn clears Single Service FF
        ioUnit.SSF = false;
        // clear STP line
        ioUnit.STP = false;
        serviceRequested = false;
      }
    }
  }

  public void setKeyCode(int value)
  {
    keyCode = value;
  }
  
  public void enableInterrupt(boolean enable)
  {
    // not used in HP9810A keyboard
  }
  
  public void requestInterrupt()
  {
    synchronized(ioUnit) {
      serviceRequested = true;

      // set Service Request
      ioUnit.SSI |= srqBits;
      ioUnit.notifyAll(); // Notify waiting threads esp. HP98xxDisplayInterface

      // if SRQ not inhibited
      if(!ioUnit.SIH) {
        // On HP9810A put keyCode on IO-bus for input in interrupt service routine
        ioUnit.bus.din = keyCode;
      }

      // restart KDN-timer for canceling SSI
      ioUnit.keyCounter.restart();
    }
  }
  
  public boolean input()
  {
    // input from keyboard is not used in HP9810A

    return(true);
  }
}