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
 * 25.10.2017 Rel. 2.10 Added method disposeAll() to close all loaded images
 * 10.11.2017 Tel. 2.10 Added methods getScaledImage() and getProcessedImage() 
 */

package io;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

public class ImageMedia
{
  private Image image, scaledImage, processedImage;
  private static Vector<Image> imageList;
  private int width = -1, height = -1;
  
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
        scaledImage = image = Toolkit.getDefaultToolkit().createImage(buffer);
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
  
  public Image getScaledImage(int width, int height)
  {
  	// generate new scaled image only if size request has changed
  	if(scaledImage == null || width != this.width || height != this.height) {
  		if(scaledImage != null) {
  			imageList.removeElement(scaledImage);
  			scaledImage.flush(); // dispose previous image
  		}
  		scaledImage = null;
  		
  		// generate new scaled instance
  		if(image != null) {
  			scaledImage = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);

  			// wait for processing to finish
  			try {
					Thread.sleep(width / 4);
				} catch (InterruptedException e) { }

  			this.width = width;
  			this.height = height;
  			
  			imageList.add(scaledImage);
  		}
  	}
  	
    return(scaledImage);
  }
  
  public Image getProcessedImage(float factor, float offset)
  {
  	Graphics2D bufferedGraphics;
  	BufferedImage bufferedImage; // for image processing
  	RescaleOp imageOp;
  	float[] factors;  // contrast factors (RGBA)
  	float[] offsets;  // brightness offsets (RGBA)
    
  	// generate new processed image only if scaledImage is ready
  	if((scaledImage != null) && (scaledImage.getWidth(null) > 0)) {
    	// generate new processed image only if there is no valid present or size has changed
  		if(processedImage == null || processedImage.getWidth(null) != scaledImage.getWidth(null) || processedImage.getHeight(null) != scaledImage.getHeight(null)) {
    		if(processedImage != null) {
    			imageList.removeElement(processedImage);
    			processedImage.flush(); // dispose previous image
    		}

  			// processing of RGB with Alpha (R,G,B,A) requires vector values
  			factors = new float[]{factor, factor, factor, 1f};
  			offsets = new float[]{offset, offset, offset, 0f};

  			// created buffered RGB image with alpha channel
  			bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
  			bufferedGraphics = bufferedImage.createGraphics();
  			bufferedGraphics.drawImage(scaledImage, 0, 0, null); // draw moduleImage into bufferedImage
  			imageOp = new RescaleOp(factors, offsets, null); // image processing operation

  			// generate processed image
  			processedImage = (Image)imageOp.filter(bufferedImage, null);
  			
  			// wait for processing to finish
  			try {
					Thread.sleep(width / 4);
				} catch (InterruptedException e) { }
  			
    		imageList.add(processedImage);
  		}
  	}
  	
  	return(processedImage);
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
