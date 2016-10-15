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
 * 
 * 29.09.2007 Rel. 1.20 Added method enableInterrupt()
 * 27.02.2008 Rel. 1.21 Added method release()
 */

package io;

public interface KeyboardInterface
{
  public abstract void run();
  
  public abstract void release();
  
  public abstract void setKeyCode(int code);
  
  public abstract void enableInterrupt(boolean enable);
  
  public abstract void requestInterrupt();

  public abstract boolean input();

}