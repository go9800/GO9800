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
 * 24.11.2006 Class created 
 * 12.07.2007 Rel. 1.20 Changed JAR-file access to Class.getResourceAsStream()
 * 25.10.2017 Rel. 2.04 Added method disposeAll() to close all loaded images
 */

package io;

import java.awt.Image;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

public class ImageMedia
{
  private Image image;
  private static Vector<Image> imageList;
  
  static {
  	// save all images in imageList for later disposeAll()
  	imageList = new Vector<Image>();
  }
  
  public ImageMedia(String imageFile)
  {
    InputStream imageStream = getClass().getResourceAsStream("/" + imageFile);
    try
    {
      if (imageStream != null) {
        byte[] buffer = new byte[0];
        byte[] tmpbuf = new byte[1024];
        while (true) {
          int len = imageStream.read(tmpbuf);
          if (len <= 0)
            break;

          byte[] newbuf = new byte[buffer.length + len];
          System.arraycopy(buffer, 0, newbuf, 0, buffer.length);
          System.arraycopy(tmpbuf, 0, newbuf, buffer.length, len);
          buffer = newbuf;
        }

        //create image
        image = Toolkit.getDefaultToolkit().createImage(buffer);
        imageStream.close();
        
        imageList.add(image);
      }
    } catch (IOException e)
    {
      // nothing
    }
  }

  public Image getImage()
  {
    return(image);
  }
  
  public static void disposeAll()
  {
  	Image image;

  	// delete all images
  	while(!imageList.isEmpty())
  	{
  		image = imageList.lastElement();
  		image.flush();
  		imageList.removeElement(image);
  	}
  	
  	System.out.println("Image threads stopped.");
  }
}
