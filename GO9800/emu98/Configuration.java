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
 * 28.10.2017 Rel. 2.10: Code transfered from class Emulator 
 */

package emu98;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;

import io.HP9800Mainframe;
import io.IOdevice;
import io.IOinterface;

public class Configuration
{
	private HP9800Mainframe mainframe;
	public String model, version;
  public Hashtable<String, MemoryBlock> memoryBlocks;
  public Hashtable<String, String> hostKeyCodes, hostKeyStrings;
	
	public Configuration(String machine, HP9800Mainframe hp9800Mainframe)
	{
		mainframe = hp9800Mainframe;
		this.model = machine;
		
    memoryBlocks = new Hashtable<String, MemoryBlock>();
    hostKeyCodes = new Hashtable<String, String>();
    hostKeyStrings = new Hashtable<String, String>();
	}
	
  private DataInputStream openConfigFile(String fileName, boolean logging)
  {
    DataInputStream cfgFile = null;
    String filePath = null;
    String jarPath = System.getProperty("java.class.path");
    int pos;

    pos = jarPath.lastIndexOf('/');
    if(pos < 0)
      pos = jarPath.lastIndexOf('\\');

    // try to open customized config file
    try{

      cfgFile = new DataInputStream(new BufferedInputStream(new FileInputStream(fileName)));
      if(logging)
        System.out.println("Custom configuration file " + fileName + " loaded.");

    } catch (FileNotFoundException e) {

      // try to open standard config file
      try{

        filePath = jarPath.substring(0, pos + 1) + fileName;
        cfgFile = new DataInputStream(new BufferedInputStream(new FileInputStream(filePath)));
        if(logging)
          System.out.println("Standard configuration file " + filePath + " loaded.");

      } catch (FileNotFoundException e1) {

        // open default config file from JAR-file
        filePath = "/config/" + fileName;
        if(getClass().getResourceAsStream(filePath) == null) {
          System.out.println("Configuration file " + fileName + " not found.");
          System.out.println("HP9800 Emulator terminated.");
          System.exit(1);
        }

        cfgFile = new DataInputStream(new BufferedInputStream(getClass().getResourceAsStream(filePath)));
        if(logging)
          System.out.println("Default configuration file " + jarPath + filePath + " loaded.");

      }
    }

    return(cfgFile);
  }

  @SuppressWarnings({ "deprecation" })
  private void loadDevice(String deviceName, int selectCode)
  {
    DataInputStream cfgFile = null;
    Class<?>[] formpara;
    Object[] actpara;
    Class<?> ioDev, ioInt;
    Constructor<?> constr;
    IOdevice ioDevice = null;
    IOinterface ioInterface = null;
    String line, keyWord, keyValue;
    String hpDevice = "", title = "", hpInterface = "", sc;
    int address = 0, length = 0;
    String[] parameters = null;

    sc = Integer.toString(selectCode);

    // Read config file line by line
    try {
      cfgFile = openConfigFile(deviceName + ".cfg", false);

      while ((line = cfgFile.readLine()) != null && line.length() != 0) {

        // ignore comment lines
        if(line.charAt(0) == ';')
          continue;

        // and tokenize
        StringTokenizer tokenline = new StringTokenizer(line, " \t");

        lineLoop:
          while(tokenline.hasMoreTokens()) {
            try {
              // read key word
              keyWord = tokenline.nextToken();

              // is it a model definition?
              if(keyWord.equals("Model")) {
                // check if current machine appears in model list 
                while(tokenline.hasMoreTokens()) {
                  keyValue = tokenline.nextToken();
                  if(keyValue.equals(model))
                    continue lineLoop; // read next line
                }
                System.out.println("Illegal peripheral device for this model!");
                return;
              }

              // HP-number of physical device
              if(keyWord.equals("Name")) {
                hpDevice = tokenline.nextToken();
                System.out.print(hpDevice + " ");
                continue; // read next line
              }

              // Descriptive name of physical device
              if(keyWord.equals("Title")) {
                title = tokenline.nextToken();
                System.out.print(title + " ");
                continue; // read next line
              }

              // HP-number of calculator interface
              if(keyWord.equals("Interface")) {
                hpInterface = tokenline.nextToken();
                System.out.print(hpInterface);
                continue; // read next line
              }

              // allowed select codes
              if(keyWord.equals("Selectcode")) {
                // check if current SC appears in SC list 
                while(tokenline.hasMoreTokens()) {
                  keyValue = tokenline.nextToken();
                  // if no select code is given, use first (default) value of SC list
                  if(selectCode == 0) {
                    sc = keyValue;
                    selectCode = Integer.parseInt(sc);
                  }

                  if(keyValue.equals(sc)) {
                    System.out.print(", select code " + sc + " ");

                    continue lineLoop; // read next line
                  }
                }
                System.out.println("- Illegal select code " + sc + "!");
                return;
              }

              // connection of main memory to extended memory (used by HP11273A)
              if(keyWord.equals("RWM")) {
                address = Integer.parseInt(tokenline.nextToken(), 8);
                length = Integer.parseInt(tokenline.nextToken(), 8);
                MemoryBlock memoryBlock = new MemoryBlock(mainframe, model, "RWM", address, length, "BUFFER", hpInterface);
                memoryBlocks.put(hpInterface, memoryBlock);
                if(memoryBlock.initialize(mainframe.memory) != 0)
                  System.exit(1);
                continue; // read next line
              }

              // more parameters for device or interface
              if(keyWord.equals("Parameters")) {
                int n = tokenline.countTokens();
                parameters = new String[n];
                for(int i = 0; i < n; i++) {
                  parameters[i] = tokenline.nextToken();
                }
                continue; // read next line
              }

            } catch (NumberFormatException e) {
              // format error
              System.err.println(e.toString());
            }
          }
      }

      cfgFile.close();
    } catch (IOException e) {
      // read error
      System.out.println(e.toString());
      System.exit(1);
    }

    // create object for device interface dynamically
    formpara = new Class[]{Integer.class, HP9800Mainframe.class};
    actpara = new Object[]{Integer.valueOf(selectCode), mainframe};
    try {
      // find Class for device interface by name
      ioInt = Class.forName("io." + hpInterface);
      
      // find constructor for formal parameters
      constr = ioInt.getConstructor(formpara);
      
      // create new object instance of device interface
      ioInterface = (IOinterface)constr.newInstance(actpara);
      
    } catch(Exception e) {
      System.err.println("\nClass for interface " + hpInterface + " not found.");
      System.exit(1);      
    }

    // is a peripheral device configured? 
    if(hpDevice != "") {
      // create object for peripheral device dynamically
      if(parameters != null) { // additional parameters?
        formpara = new Class[]{String[].class, IOinterface.class}; // parameters for HP11202A, HP11305A etc.
        actpara = new Object[]{parameters, ioInterface};
        
      } else { // no parameters
      	
        formpara = new Class[]{IOinterface.class}; // IOinterface, no additional parameters for other devices
        actpara = new Object[]{ioInterface};
      }

      try {
        // find Class for device by name
        ioDev = Class.forName("io." + hpDevice);
        // find constructor for formal parameters
        constr = ioDev.getConstructor(formpara);
        // create new object instance of device
        ioDevice = (IOdevice)constr.newInstance(actpara);
        
      } catch(Exception e) {
      	e.printStackTrace();
        System.err.println("\nClass for device " + hpDevice + " not loaded.");
        System.exit(1);      
      }
    }

    if(ioInterface != null) {
      // set link from interface to device
      ioInterface.setDevice(ioDevice);
      
      // start IOinterface thread at last 
      ioInterface.start();
    }

    if(length == 0)
      System.out.println("loaded.");
  }

  @SuppressWarnings("deprecation")
  public void loadConfig(String machineName)
  {
    DataInputStream cfgFile = null;
    String line;
    String blockName, slot, blockType;
    int address, length;

    model = machineName;
    version = "";

    // Read config file line by line
    try {
      cfgFile = openConfigFile(machineName + ".cfg", true);

      while ((line = cfgFile.readLine()) != null && line.length() != 0) {

        // ignore comment lines
        if(line.charAt(0) == ';')
          continue;

        // and tokenize
        StringTokenizer tokenline = new StringTokenizer(line, " \t");

        while(tokenline.hasMoreTokens()) {
          try {

            // read memory type
            blockType = tokenline.nextToken();

            // is it a model definition?
            if(blockType.equals("Model")) {
              model = tokenline.nextToken();
              if(tokenline.hasMoreTokens())
                version = tokenline.nextToken();
              continue; // read next line
            }

            // is it a IO-device definition?
            if(blockType.equals("DEV")) {
              // read device name
              blockName = tokenline.nextToken();

              if(tokenline.hasMoreTokens()) {
                // read select code
                address = Integer.parseInt(tokenline.nextToken());
              } else {
                address = 0; // no select code given, use interface default value
              }

              // load IO device and interface
              loadDevice(blockName, address);

              continue; // read next line
            }

            // read octal start address
            address = Integer.parseInt(tokenline.nextToken(), 8);

            // is it a breakpoint definition?
            if(blockType.startsWith("Break")) {
              if(mainframe.memory[address] != null)
              	mainframe.memory[address].breakPoint = true;
              continue; // read next line
            }

            // is it a watchpoint definition?
            if(blockType.startsWith("Watch")) {
              if(mainframe.memory[address] != null) {
              	mainframe.memory[address].watchPoint = true;
                if(tokenline.hasMoreTokens()) {
                	mainframe.memory[address].watchValue = Integer.parseInt(tokenline.nextToken(), 8);
                	mainframe.memory[address].watchCondition = tokenline.nextToken().charAt(0);
                }
              }
              continue; // read next line
            }

            // read octal block length
            length = Integer.parseInt(tokenline.nextToken(), 8);
            // read ROM name
            blockName = tokenline.nextToken();

            // read ROM slot number if applicable
            if(tokenline.hasMoreTokens()) {
              slot = tokenline.nextToken();
            } else {
              slot = "";
            }

            MemoryBlock memoryBlock = new MemoryBlock(mainframe, model, blockType, address, length, blockName, slot);
            memoryBlocks.put(slot, memoryBlock);
            if(memoryBlock.initialize(mainframe.memory) != 0)
              System.exit(1);

          } catch (Exception e) {
            // format error
            System.err.println(e.toString());
          }
        }

      }

      cfgFile.close();
      
      if(machineName.equals("HP9821A"))
      	setMAW();

    } catch (IOException e) {
      // read error
      System.err.println(e.toString());
      System.exit(1);
    }
  }

	// HP9821A only: generate Maximum Address of RWM (hardwired in original HP9821A)
  public void setMAW()
  {
    // check if RW memory extension is installed
    int MAW;
    // check every block of 04000 words
    for(MAW = 032000; MAW > 022000; MAW -= 004000)
    {
      if(mainframe.memory[MAW-1].isRW)
        break;
    }

    // set MAW
    mainframe.memory[01377] = new Memory(false, 01377, MAW);
  }

  public void setROM(String slot, String romName)
  {
    MemoryBlock memoryBlock = (MemoryBlock)memoryBlocks.get(slot);

    String prevName = memoryBlock.getName();
    memoryBlock.unload();
    memoryBlock.setName(romName);
    if(memoryBlock.initialize(mainframe.memory) != 0) {
      // on error reload previous block
      memoryBlock.setName(prevName);
      memoryBlock.initialize(mainframe.memory);
    }
  }

  @SuppressWarnings("deprecation")
  public void loadKeyConfig(String machineName)
  {
    Hashtable<String, String> keyNames;
    DataInputStream cfgFile = null;
    String line;
    int keyCode, scanCode;
    String modifier, keyName, keyString;

    keyNames = new Hashtable<String, String>();

    // Read key name file line by line
    try {
      cfgFile = openConfigFile("keynames.cfg", true);
      while ((line = cfgFile.readLine()) != null) {

        // ignore empty and comment lines
        if(!line.equals(""))
          if(line.charAt(0) == ';')
            continue;

        // and tokenize
        StringTokenizer tokenline = new StringTokenizer(line, " \t");

        if(tokenline.hasMoreTokens()) {
          try {

            // read host PC key code
            keyCode = Integer.parseInt(tokenline.nextToken());

            // read host key name
            keyName = tokenline.nextToken();

            keyNames.put(Integer.toString(keyCode), keyName);

          } catch (NumberFormatException e) {
            // format error
            System.err.println(e.toString());
          }
        }

      }

      cfgFile.close();

    } catch (IOException e) {
      // read error
      System.err.println(e.toString());
      System.exit(1);
    }

    // Read config file line by line
    try {
      cfgFile = openConfigFile(machineName + "-keyb.cfg", true);
      while ((line = cfgFile.readLine()) != null) {

        // ignore empty and comment lines
        if(!line.equals(""))
          if(line.charAt(0) == ';')
            continue;

        // and tokenize
        StringTokenizer tokenline = new StringTokenizer(line, " \t");

        if(tokenline.hasMoreTokens()) {
          try {

            // read calculator key code
            keyCode = Integer.parseInt(tokenline.nextToken(), 8);

            // read host key code
            scanCode = Integer.parseInt(tokenline.nextToken());

            // read modifier keys
            if(tokenline.hasMoreTokens()) {
              modifier = tokenline.nextToken().toUpperCase();
              if(modifier.charAt(0) == ';')
                modifier = "";
            }
            else
              modifier = "";
            
            hostKeyCodes.put(Integer.toString(scanCode) + modifier, Integer.toOctalString(keyCode));

            keyString = Integer.toString(scanCode);
            keyName = (String)keyNames.get(keyString);
            if(keyName == null)
              keyName = String.valueOf((char)scanCode);
            keyString = (modifier != "")? modifier + "+" + keyName : keyName;
            hostKeyStrings.put(Integer.toString(keyCode), keyString);

          } catch (NumberFormatException e) {
            // format error
            System.err.println(e.toString());
          }
        }
      }

      cfgFile.close();

    } catch (IOException e) {
      // read error
      System.err.println(e.toString());
      System.exit(1);
    }
  }
  
  public void dispose()
  {
  	// unload all memory block and free ressources
  	for(Enumeration<MemoryBlock> enumBlock = memoryBlocks.elements(); enumBlock.hasMoreElements(); ) {
  		enumBlock.nextElement().unload();
  	}
  }
}
