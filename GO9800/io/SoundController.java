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
 * 21.12.217 Class created 
 */

package io;

import java.util.Vector;
import javax.sound.sampled.*;

public class SoundController
{
  private boolean enabled = true;
  public final Mixer smMixer;
  public final boolean DIRECT = false;
  private int maxlines;
  private Vector<Clip> clipList;

  public SoundController()
  {
    Mixer mixer = null;
    
  	// save all sounds in clipList for later disposeAll()
  	clipList = new Vector<Clip>();

  	try {
      mixer = AudioSystem.getMixer(null); // default mixer
      if ((maxlines = mixer.getMaxLines(new Line.Info(SourceDataLine.class))) !=
        AudioSystem.NOT_SPECIFIED && maxlines < 16) {
        // Default sound output on Linux only uses the hardware mixer, often limited
        // to four lines. In this case find the Java Sound software mixer. This also
        // indirectly works around the inability of the Linux hardware mixer
        // to handle mono files.
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (int mixeri = 0; mixeri < mixers.length; mixeri++) {
          if (mixers[mixeri].getName().equals("Java Sound Audio Engine")) {
            mixer = AudioSystem.getMixer(mixers[mixeri]);
            break;
          }
        }
      }
    } catch(IllegalArgumentException e) {
    }
    
    smMixer = mixer;
  }
  
  public int getMaxLines()
  {
  	return(maxlines);
  }

  public void setEnabled(boolean value)
  {
    enabled = value;
  }

  public boolean isEnabled()
  {
    return(enabled);
  }
  
  public void add(Clip soundClip)
  {
  	clipList.add(soundClip);
  }
  
  public void remove(Clip soundClip)
  {
  	clipList.removeElement(soundClip);
  }
  
  public void disposeAll()
  {
  	Clip soundClip;

  	// delete all soundClips
  	while(!clipList.isEmpty())
  	{
  		soundClip = clipList.lastElement();
  		soundClip.stop();
  		soundClip.close();
  		clipList.removeElement(soundClip);
  	}
  	
  	System.out.println("Sound threads stopped.");
  }
}
