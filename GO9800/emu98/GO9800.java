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
 * 26.05.2006 Restructuration of machine dependend mainframe and IO-devices 
 * 06.06.2006 HP9866A added
 * 09.06.2006 HP9860A added
 * 14.06.2006 HP9862A added
 * 12.07.2006 HP9865A added
 * 26.07.2006 External HP9865A added
 * 13.09.2006 HP9810A added
 * 08.11.2006 HP9820A added
 * 26.12.2006 Rel. 0.22 Bugfix: Added HP9865A.setModel()
 * 16.01.2006 Rel. 0.23 Changed integration of HP11202A
 * 25.02.2007 Rel. 0.30 Added handling of configuration file
 * 04.04.2007 Rel. 1.00 Added HP9821A
 * 12.07.2007 Rel. 1.12 Changed JAR-file access
 * 01.05.2008 Rel. 1.30 Moved emu.setDisassemblerMode() from main() to Emulator contructor
 * 10.05.2010 Rel. 1.50 Create instance of calculator mainframe class dynamically using Reflection API 
 */

package emu98;

import java.lang.reflect.Constructor;
import io.*;

public class GO9800
{
  public static void usage()
  {
    System.out.println("Usage: GO9800.jar < Machine-Configuration >");
  }

  public static void main(String[] args)
  {
    Class<?>[] formpara;
    Object[] actpara;
    Class<?> calc;
    Constructor<?> cons;

    if(args.length < 1 || args.length > 2){
      usage();
      System.exit(1);
    }
    
    System.out.println("HP Series 9800 Emulator Release 1.61, Copyright (C) 2006-2014 Achim Buerger\n");
    System.out.println("GO9800 comes with ABSOLUTELY NO WARRANTY.");
    System.out.println("This is free software, and you are welcome to");
    System.out.println("redistribute it under certain conditions.\n");
    System.out.println("GO9800 is in no way associated with the Hewlett-Packard Company.");
    System.out.println("Hewlett-Packard, HP, and the HP logos are all trademarks of the Hewlett-Packard Company.");
    System.out.println("This software is intended solely for research and education purposes.\n\n");
    
    String machine = args[0];
    HP9800Mainframe mainframe = null;
    SoundMedia.enable(true);
    
    Emulator emu = new Emulator(machine);

    // create object for mainframe class dynamically
    formpara = new Class[]{Emulator.class};
    actpara = new Object[]{emu};
    try {
      // find Class for calculator mainframe by name
      calc = Class.forName("io." + emu.model + "." + emu.model + "Mainframe" + emu.version);
      // find constructor for formal parameters
      cons = calc.getConstructor(formpara);
      // create new object instance of calculator mainframe
      mainframe = (HP9800Mainframe)cons.newInstance(actpara);
    } catch(Exception e) {
      e.printStackTrace();
      System.err.println(emu.model + emu.version + " not implemented.");
      System.exit(1);      
    }

    emu.start();
  }
}
