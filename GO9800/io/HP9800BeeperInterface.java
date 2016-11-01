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
 * 02.10.2006 Class created
 * 22.10.2006 Changed inheritation from MagneticCardReader to MagneticCardReaderInterface
 * 29.10.2006 Changed sound output to Sound class 
 * 09.04.2007 Rel. 1.00 removed dispWindow
 * 03.10.2007 Rel. 1.20 removed sleep(150)
 * 30.10.2011 Rel. 1.50 class renamed to HP9800BeeperInterface and moved to package io for use with HP9821A
 */

package io;

public class HP9800BeeperInterface extends HP9800MagneticCardReaderInterface
{
  SoundMedia beepSound;

  public HP9800BeeperInterface(HP9800Mainframe mainframe)
  {
    this.mainframe = mainframe;

    // generate beep sound
    beepSound = new SoundMedia("media/HP9800/HP9800_BEEP.wav", false);

    System.out.println("HP9800 Audio loaded.");
  }

  public boolean output()
  {
    synchronized(ioReg) {
      if(ioReg.MLS) {
        beepSound.start();
      }
    }
    
    return(false);
  }
}
