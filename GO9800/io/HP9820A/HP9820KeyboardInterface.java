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
 * 08.11.2006 Class inheriteded from HP9830KeyboardInterface
 * 12.11.2006 Bugfix in HP9830KeyboardInterface: Don't call super.input() when no SRQ otherwise HP9820A printer output maybe corrupted
 * 28.10.2017 Rel. 2.10: Added new linking between Mainframe and other components
 */

package io.HP9820A;

import io.HP9800Mainframe;
import io.HP9830A.HP9830KeyboardInterface;

public class HP9820KeyboardInterface extends HP9830KeyboardInterface
{
  public HP9820KeyboardInterface(int selectCode, HP9800Mainframe hp9800Mainframe)
  {
    super(selectCode, hp9800Mainframe);
  }
}