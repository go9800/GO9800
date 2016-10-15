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
 * 26.05.2007 Class created 
 * 05.04.2010 Rel. 1.50 Class now inherited from IOdevice and completely reworked
 */

package io;

public class HP11305A extends IOdevice
{
  private static final long serialVersionUID = 1L;
  
  // input fields
  protected static final int FIRST = 0x0400;
  protected static final int PLATTER = 0x0300;
  protected static final int INITIALIZE = 0x0080;
  protected static final int HEAD = 0x0040;
  protected static final int READ = 0x0020;
  protected static final int SECTOR = 0x001f;
  protected static final int CYLINDER = 0x00ff;

  // output status
  protected static final int POWER_ON = 0x0800;           // SI3
  protected static final int ADDRESS_ERROR = 0x0400;      // SI2
  protected static final int CKWORD_ERROR = 0x0200;       // SI1
  protected static final int DRIVE_UNSAFE_ERROR = 0x0100; // SI0

  protected static final int IDLE_TIMER = 500;
  protected static int BUSY_TIMER = 10;
  
  private HP9867A[] hp9867a;
  //private Image hp11305aImage;
  private int platter = 0, head = 0, cylinder = 0, sector = 0;
  private int accessMode = 0, initialize = 0;
  private boolean debug = false;

  public HP11305A(String[] parameters)
  {
    super("HP11305A");

    int numDiscs = 1;
    int numUnits = 1;

    try {
      numDiscs = Integer.parseInt(parameters[0]);
      numUnits = Integer.parseInt(parameters[1]);
    } catch (Exception e) {
      System.out.println(e.toString());
    }

    BUSY_TIMER = IOinterface.ioReg.time_10ms;
    
    hp9867a = new HP9867A[4];
    numUnits *= numDiscs;
    HP9867A.time_10ms = BUSY_TIMER;
    
    for(int unit = 0; unit < numUnits && unit < 4; unit++) {
      hp9867a[unit] = new HP9867A(unit, numDiscs, null); 
      
      if(numDiscs != 1) {
        // create fixed disc of HP9867B
        unit++;
        hp9867a[unit] = new HP9867A(unit, numDiscs, hp9867a[unit-1]);
      }
    }
  }

  public int output(int status)
  {
    debug = IOinterface.ioReg.console.getDebugMode();
    if((status & FIRST) != 0) {
      platter = (status & PLATTER) >> 8;
      initialize = (status & INITIALIZE) >> 7;
      head = (status & HEAD) >> 6;
      accessMode = (status & READ) >> 5;
      sector = status & SECTOR;

      status = POWER_ON;
    } else {
      cylinder = status & CYLINDER;
      if(debug)
        IOinterface.ioReg.console.append("HP9880A Commmand: init=" + initialize + " read=" + accessMode + " unit=" + platter + " head=" + head + " cylinder=" + cylinder + " sector=" + sector + "\n");
      
      // put initialize flag in bit 1, read flag in bit 0
      accessMode += initialize << 1;
      status = hp9867a[platter].output(head, cylinder, sector, accessMode);
    }
    
    return(status);
  }
}
