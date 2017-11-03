/*
 * HP9800 Emulator
 * Copyright (C) 2006 - 2018 Achim Buerger
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
 * 07.09.2006 Implementing interface KayboardInterface
 * 24.09.2006 Added handling of KLS to clear SSI
 * 12.11.2006 Bugfix: Don't call super.input() when no SRQ otherwise HP9820A printer output maybe corrupted
 * 14.11.2006 Added reset of SSF on KLS (for HP9820)
 * 25.09.2007 Rel. 1.20 Don't run in separate thead
 * 29.09.2007 Rel. 1.20 Added enableInterrupt()
 * 12.12.2007 Rel. 1.20 Don't input IO-bus value to IO-register in requestInterrupt(). This is now done by ioUnitister.serviceRequestAcknowledge()
 * 13.12.2007 Rel. 1.20 Release keyboard SRQ and STP after 5ms. This is done by KDN single-shot FF.
 * 27.02.2008 Rel. 1.21 Added method release(). Keyboard is now released after a certain amount of executed instructions   
 * 09.01.2009 Rel. 1.33 Added synchronized(ioUnit){} and ioUnit.notifyAll() in requestInterrupt() 
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 */

package io.HP9830A;

import io.HP9800Mainframe;
import io.IOinterface;
import io.KeyboardInterface;

public class HP9830KeyboardInterface extends IOinterface implements KeyboardInterface
{
  int keyCode = -1;
  boolean interruptEnabled = true;
  
  public HP9830KeyboardInterface(int selectCode, HP9800Mainframe hp9800Mainframe)
  {
    super(selectCode, hp9800Mainframe);
    System.out.println("HP9800 Keyboard, select code " + selectCode + " loaded.");
  }

  public void release()
  {
    synchronized(ioUnit) {
      if(serviceRequested) {
        // KDN (Key Down) and SSI is held only until for 5ms
        // clear Service Request line
        ioUnit.SSI &= ~srqBits;

        // this in turn clears Single Service FF
        ioUnit.SSF = false;
        // clear STP line
        ioUnit.STP = false;
      }
    }
  }

  public void setKeyCode(int value)
  {
    keyCode = value;
  }
  
  public void enableInterrupt(boolean enable)
  {
    // disable keyboard SRQ while KLS is active
    interruptEnabled = enable;
  }
  
  public void requestInterrupt()
  {
    synchronized(ioUnit) {
      if(interruptEnabled) {
        serviceRequested = true;

        // set Service Request
        ioUnit.SSI |= srqBits;
        ioUnit.notifyAll(); // Notify waiting threads esp. HP98xxDisplayInterface

        // restart KDN-timer for clearing of SSI
        ioUnit.keyCounter.restart();
      }
    }
  }
  
  public boolean input()
  {
    synchronized(ioUnit) {
      if(serviceRequested && (keyCode != -1)) {
        ioUnit.bus.din = keyCode;
        keyCode = -1;

        // clear Service Request
        ioUnit.SSI &= ~srqBits;
        // clear Single Service FF (necessary for STP processing)
        // this is done when SRA=false and SSI=false
        ioUnit.SSF = false;
        serviceRequested = false;
      }

      return(true);
    }
  }
}