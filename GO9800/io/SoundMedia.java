/*
 * HP9800 Emulator
 * Copyright (C) 2006-2010 Achim Buerger
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
 * 29.10.2006 Class created
 * 24.11.2006 Changed sound access from plain file to JAR archive 
 * 18.12.2006 Rel. 0.21: Added method getClip()
 * 12.07.2007 Rel. 1.20 Changed JAR-file access to Class.getResourceAsStream()
 * 27.07.2007 Rel. 1.20 [tre] Use software mixer if hardware mixer has insufficient lines
 *            (common on Linux) and work around bugs; output errors for
 *            exceptions; add toggle() method
 * 01.09.2007 Rel. 1.20 Added method isEnabled()
 * 13.08.2008 Rel. 1.30 Bugfix: handle exception if sound output completely unvailable.
 * 19.11.2011 Rel. 1.51 Bugfix: added BufferedInputStream to creation of AudioInputStream for OpenJDK
 */

package io;

import java.io.*;
import javax.sound.sampled.*;

public class SoundMedia
{
  private AudioInputStream ais;
  private Clip soundClip;
  private static boolean enabled = true;
  private static final Mixer smMixer;
  private static final boolean DIRECT = false;
  
  static {
    int maxlines;
    Mixer mixer = null;
    
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

  public SoundMedia(String soundFile)
  {
    try {
      ais = AudioSystem.getAudioInputStream(new BufferedInputStream(getClass().getResourceAsStream("/" + soundFile)));
      DataLine.Info info = new DataLine.Info(Clip.class, ais.getFormat());
      if(DIRECT)
        soundClip = (Clip)AudioSystem.getLine(info);
      else {
        if(smMixer != null)
          soundClip = (Clip)smMixer.getLine(info);
        else {
          soundClip = null;
          return;
        }
      }
        
      soundClip.open(ais);
    } catch(IOException e) {
      System.err.println(soundFile + ": " + e);
    } catch (UnsupportedAudioFileException e) {
      System.err.println(soundFile + ": " + e);
    } catch (LineUnavailableException e) {
      System.err.println(soundFile + ": " + e);
    } catch (IllegalArgumentException e) {
      System.err.println(soundFile + ": " + e);
    } catch (NullPointerException e) {
      System.err.println(soundFile + ": " + e);
    }
  }
  
  public static void enable(boolean value)
  {
    enabled = value;
  }

  public static boolean isEnabled()
  {
    return(enabled);
  }
  
  public void loop()
  {
    if(enabled && (soundClip != null)) {
      soundClip.setFramePosition(0);
      soundClip.loop(Clip.LOOP_CONTINUOUSLY);
    }
  }

  public void loop(int count)
  {
    if(enabled && (soundClip != null)) {
      soundClip.setFramePosition(0);
      soundClip.loop(count);
    }
  }

  public void start()
  {
    if(enabled && (soundClip != null)) {
      if(soundClip.isRunning())
        soundClip.stop();

      soundClip.setFramePosition(0);
      soundClip.loop(0);
    }
  }

  public void finish()
  {
    if(enabled && (soundClip != null)) {
      if(soundClip.isRunning())
        soundClip.loop(0);
    }
  }

  public void stop()
  {
    if(enabled && (soundClip != null))
      soundClip.stop();
  }

  public void toggle()
  {
    if(enabled && (soundClip != null)) {
      if(soundClip.isRunning())
        soundClip.stop();
      else {
        soundClip.setFramePosition(0);
        soundClip.loop(Clip.LOOP_CONTINUOUSLY);
      }
    }
  }

  public Clip getClip()
  {
    return(soundClip);
  }
}