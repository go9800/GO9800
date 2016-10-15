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
 * 26.05.2006 Added 100ms delay in keyReleased() if STOP key is pressed 
 * 27.05.2006 Added output of fan sound
 * 28.05.2006 Added bitmap of keyboard and mouse input
 * 13.05.2006 Added tapeDevice, key column 20 for cassette OPEN, keycodes for REWIND+OPEN
 * 31.07.2006 Changed Mainframe parameter to Emulator
 * 31.07.2006 Added switching of disassembler mode (Ctrl+D, Ctrl+N)
 * 13.08.2006 Added LED matrix display
 * 29.08.2006 Changed data directory structure
 * 05.09.2006 Moved instantiation of system RWM from emulator class
 * 07.09.2006 Implementing interface Mainframe
 * 14.09.2006 Added display() method for single character output
 * 20.09.2006 Optimized LED matrix output (character spacing, background color)
 * 24.09.2006 Bugfix: Clear SSI if key released
 * 04.10.2006 Moved beeper to new class HP9830Beeper inherited from MagneticCardReader
 * 29.10.2006 Changed sound output to Sound class 
 * 05.11.2006 Changed Ctrl+D: opens disasm dialog instead starting the disassembler  
 * 09.11.2006 Bugfix: fillRect of ledMatrix with backGround color was too small by 1 
 * 24.11.2006 Changed loading of images from file to JAR
 * 15.12.2006 Rel. 0.21 New: window setLocation
 * 05.01.2007 Rel. 0.23 Changed setTapeDevice() to draw tape status indicators
 * 03.02.2007 Rel. 0.30 New: Ctrl+T to start/stop opcode timing measurement
 * 03.02.2007 Rel. 0.30 Changed Ctrl+D: now toggles disasmOutput visibility
 * 06.03.2007 Rel. 0.30 Added use of configuration file and removed memory initialization 
 * 10.03.2007 Rel. 0.30 Added ROMselector 
 * 10.03.2007 Rel. 0.30 Added machine reset (Ctrl+R) 
 * 09.04.2007 Rel. 1.00 Code reorganized for usage of class HP9800Mainframe
 * 17.07.2007 Rel. 1.20 Added use of keyCode Hashtable
 * 24.09.2007 Rel. 1.20 Changed display blanking control from fixed timer to instruction counter
 * 13.12.2007 Rel. 1.20 Don't release keyboard by mouseReleased() or keyReleased(). This is now done after 5ms by KeyboardInterface.run()   
 */

package io.HP9830B;

import emu98.*;
import io.HP9830A.HP9830AMainframe;

public class HP9830BMainframe extends HP9830AMainframe
{
  private static final long serialVersionUID = 1L;

  public HP9830BMainframe(Emulator emu)
  {
    super(emu, "HP9830B");
  }
}
