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
 * 
 * 19.02.2007 Rel. 0.30: Class created
 * 12.07.2007 Rel. 1.20 Changed JAR-file access
 * 18.01.2009 Rel. 1.40 Added instructionsImage and getInstructions()
 * 15.03.2009 Rel. 1.40 Added Block configuration file
 */

package emu98;

import io.HP9800Mainframe;
import io.ImageController;
import io.ImageMedia;

import java.awt.Image;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.StringTokenizer;
import java.util.Vector;

public class MemoryBlock
{
  private String machineName;
  private String blockName;
  private String blockSlot;
  private String blockType;
  private String title;
  private int blockStart, blockEnd, blockSize = 0;
  private boolean isRW;
  private ImageMedia moduleImageMedia = null;
  private ImageMedia templateImageMedia = null;
  private ImageMedia instructionsImageMedia = null;
  private ImageController imageController;
  private Vector<String> instructionsVector = null;
  private int instrIndex = 0;
  
  public MemoryBlock(HP9800Mainframe mainframe, String machine, String type, int address, int length, String name, String slot)
  {
  	this.imageController = mainframe.imageController;
    machineName = machine;
    blockStart = address;
    blockEnd = address + length - 1;
    blockName = name;
    blockType = type;
    blockSlot = slot;
    isRW = type.equals("RWM");
  }
  
  private String makeFileName(String suffix)
  {
    return("media/" + machineName + "/" + blockName + suffix);
  }
  
  public void setName(String name)
  {
    blockName = name;
  }

  public String getName()
  {
    return(blockName);
  }
  
  public String getTitle()
  {
    return(title);
  }
  
  public Image getModule()
  {
    if(moduleImageMedia == null) {
      moduleImageMedia = new ImageMedia(makeFileName("_Module_" + blockSlot + ".jpg"), imageController);
    }
      
    return(moduleImageMedia.getImage());
  }

  // get universal module graphics, scaled
  public Image getUniModule(ImageController controller)
  {
    if(moduleImageMedia == null) {
      moduleImageMedia = new ImageMedia(makeFileName("_Module.png"), controller);
   }

    return(moduleImageMedia.getImage());
  }

  // get universal module graphics, scaled
  public Image getUniModule(int width, int height)
  {
    if(moduleImageMedia == null) {
      moduleImageMedia = new ImageMedia(makeFileName("_Module.png"), imageController);
   }

    return(moduleImageMedia.getScaledImage(width, height));
  }

  // get universal module graphics, processed
  public Image getUniModule(float factor, float offset)
  {
    if(moduleImageMedia == null) {
      moduleImageMedia = new ImageMedia(makeFileName("_Module.png"), imageController);
   }

    return(moduleImageMedia.getProcessedImage(factor, offset));
  }

  // get universal template graphics
  public Image getUniTemplate()
  {
    if(templateImageMedia == null) {
      templateImageMedia = new ImageMedia(makeFileName("_Template.png"), imageController);
    }
    
    return(templateImageMedia.getImage());
  }
  
  // get universal template graphics, scaled
  public Image getUniTemplate(int width, int height)
  {
    if(templateImageMedia == null) {
      templateImageMedia = new ImageMedia(makeFileName("_Template.png"), imageController);
    }
    
    return(templateImageMedia.getScaledImage(width, height));
  }
  
  // get universal template graphics, processed
  public Image getUniTemplate(float factor, float offset)
  {
    if(templateImageMedia == null) {
      templateImageMedia = new ImageMedia(makeFileName("_Template.png"), imageController);
    }
    
    return(templateImageMedia.getProcessedImage(factor, offset));
  }
  
  public Image getTemplate()
  {
    if(templateImageMedia == null) {
      templateImageMedia = new ImageMedia(makeFileName("_Template_" + blockSlot + ".jpg"), imageController);
    }
    
    return(templateImageMedia.getImage());
  }
  
  // get instructions page
  public Image getInstructions()
  {
    if(instructionsVector.size() == 0)
      return(null);
    
    instrIndex = 0;
    
    return(nextInstructions());
  }
  
  // get instructions page, scaled
  public Image getInstructions(int width, int height)
  {
    if(instructionsVector.size() == 0)
      return(null);
    
    instrIndex = 0;
    
    return(nextInstructions(width, height));
  }
  
  // get instructions page
  public Image nextInstructions()
  {
    if(instructionsVector.size() == 0)
      return(null);
    
    String fileName = "media/" + machineName + "/" + (String)instructionsVector.elementAt(instrIndex);
    instructionsImageMedia = new ImageMedia(fileName, imageController);
    
    if(++instrIndex >= instructionsVector.size())
      instrIndex = 0;

    return(instructionsImageMedia.getImage());
  }
  
  // get instructions page, scaled
  public Image nextInstructions(int width, int height)
  {
    if(instructionsVector.size() == 0)
      return(null);
    
    String fileName = "media/" + machineName + "/" + (String)instructionsVector.elementAt(instrIndex);
    instructionsImageMedia = new ImageMedia(fileName, imageController);
    
    if(++instrIndex >= instructionsVector.size())
      instrIndex = 0;
    
    // scale image proportional
    height -= 100;
    while(instructionsImageMedia.getImage().getWidth(null) <= 0);
    width = (instructionsImageMedia.getImage().getWidth(null) * height) / instructionsImageMedia.getImage().getHeight(null);

    return(instructionsImageMedia.getScaledImage(width, height));
  }
  
  public int getAddress()
  {
    return blockStart;
  }
  
  public void unload()
  {
    if(blockName != null) {
      // dismiss previous images, stop image threads and free all resources
    	if(moduleImageMedia != null && moduleImageMedia.getImage() != null) moduleImageMedia.getImage().flush();
    	if(templateImageMedia != null && templateImageMedia.getImage() != null) templateImageMedia.getImage().flush();
    	if(instructionsImageMedia != null && instructionsImageMedia.getImage() != null) instructionsImageMedia.getImage().flush();
      moduleImageMedia = templateImageMedia = instructionsImageMedia = null;
      instructionsVector = null;

      System.out.println(blockName + " unloaded.");
    }
  }
  
  @SuppressWarnings("deprecation")
  public int initialize(Memory memory[])
  {
    DataInputStream cfgFile = null;
    DataInputStream dumpFile = null;
    String line, keyWord, keyValue;
    int address;
    
    System.out.print(blockName + " " + blockType + " at " + Integer.toOctalString(blockStart) + "-" + Integer.toOctalString(blockEnd) + ", " + blockSlot + " ");

    if(isRW) {
      // Initialize R/W memory
      for(address = blockStart; address <= blockEnd; address++) {
        memory[address] = new Memory(isRW, address, 0);
      }
      
      System.out.println("initialized.");
      return(0);
    }
    
    // vector for instructions file names
    instructionsVector = new Vector<String>();

    // Read config file line by line
    try {
      InputStream cfgStream = getClass().getResourceAsStream("/" + makeFileName(".cfg"));
      // is config file present?
      if(cfgStream != null) {
        cfgFile = new DataInputStream(cfgStream);

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
                    if(keyValue.equals(machineName))
                      continue lineLoop; // read next line
                  }
                  System.out.println("Illegal ROM Block for this model!");
                  return(1);
                }

                // ROM title
                if(keyWord.equals("Title")) {
                  title = tokenline.nextToken();
                  System.out.print("(" + title + ") ");
                  continue; // read next line
                }

                // ROM size
                if(keyWord.equals("Size")) {
                  blockSize = Integer.parseInt(tokenline.nextToken(), 8);
                  if(blockStart + blockSize - 1 <= blockEnd)
                    continue; // read next line

                  System.out.println("Illegal ROM size for this address range!");
                  return(1);
                }

                // min. address
                if(keyWord.equals("MinAddress")) {
                  int addr = Integer.parseInt(tokenline.nextToken(), 8);
                  if(addr <= blockStart)
                    continue; // read next line

                  System.out.println("Illegal ROM Block for this address range!");
                  return(1);
                }

                // max. address
                if(keyWord.equals("MaxAddress")) {
                  int addr = Integer.parseInt(tokenline.nextToken(), 8);
                  if(addr >= blockEnd)
                    continue; // read next line

                  System.out.println("Illegal ROM Block for this address range!");
                  return(1);
                }

                // Instructions image
                if(keyWord.equals("Instructions")) {
                  instructionsVector.addElement(tokenline.nextToken());
                  continue; // read next line
                }

                // Block slots
                if(keyWord.equals("Slots")) {
                  // check if current slot appears in slot list 
                  while(tokenline.hasMoreTokens()) {
                    if(blockSlot.equals(tokenline.nextToken())) {
                      continue lineLoop; // read next line
                    }
                  }
                  System.out.println("Illegal ROM for this slot!");
                  return(1);
                }
              } catch (NumberFormatException e) {
                // format error
                System.err.println(e.toString());
                return(1);
              }
            }
        }

        cfgFile.close();
      }
    } catch (IOException e) {
      // read error
      System.err.println(e.toString());
      System.exit(1);
    }
    
    try{
      dumpFile = new DataInputStream(getClass().getResourceAsStream("/" + makeFileName("_ROM.dmp")));
    } catch (NullPointerException e) {
      System.out.println("ROM dump not found!");
      return(1);
    }

    // Read ASM file line by line
    try {
      for(address = blockStart; address <= blockEnd; address++) {
        if(blockSize > 0 && address >= blockStart + blockSize)
          break;
        
        line = dumpFile.readLine();
        if(line == null) {
          System.err.println("ROM dump file is too short.");
          return(1);
        }
        
        try {
          // read octal value
          int opcode = Integer.parseInt(line, 8);

          //store in ROM
          memory[address] = new Memory(isRW, address, opcode);
        } catch (NumberFormatException e) {
          // format error
          System.err.println(e.toString());
          System.exit(1);
        }
      }
      
      dumpFile.close();
      System.out.println("loaded.");
      
    } catch (IOException e) {
      // read error
      System.err.println(e.toString());
      System.exit(1);
    }
    
    return(0);
  }
}
