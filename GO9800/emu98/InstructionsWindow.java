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
 * 18.01.2009 Rel. 1.40: Class created 
 * 15.03.2009 Rel. 1.40: Added setROMblock
 */

package emu98;

import javax.swing.JPanel;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.BorderLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.*;

import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.SystemColor;

public class InstructionsWindow extends JDialog implements ActionListener
{
  private static final long serialVersionUID = 1L;
  private JPanel jContentPane = null;
  private JScrollPane instructionsScrollPane = null;
  private JButton instructionsButton = null;
  private MemoryBlock romBlock;  //  @jve:decl-index=0:


  public InstructionsWindow(Frame owner)
  {
    super(owner);
    initialize();
    addWindowListener(new windowListener());
  }

  /**
   * This method initializes this
   * 
   * @return void
   */
  private void initialize()
  {
    this.setSize(300, 200);
    this.setBackground(SystemColor.control);
    this.setContentPane(getJContentPane());
  }

  /**
   * This method initializes jContentPane
   * 
   * @return javax.swing.JPanel
   */
  private JPanel getJContentPane()
  {
    if (jContentPane == null)
    {
      jContentPane = new JPanel();
      jContentPane.setLayout(new BorderLayout());
      jContentPane.add(getInstructionsScrollPane(), BorderLayout.CENTER);
    }
    return jContentPane;
  }

  /**
   * This method initializes instructionsScrollPane	
   * 	
   * @return javax.swing.JScrollPane	
   */
  private JScrollPane getInstructionsScrollPane()
  {
    if (instructionsScrollPane == null)
    {
      instructionsButton = new JButton();
      instructionsButton.setText("");
      instructionsButton.setBackground(Color.white);
      instructionsButton.setForeground(Color.white);
      instructionsButton.setMnemonic(KeyEvent.VK_SPACE);
      instructionsButton.setActionCommand("next");
      instructionsButton.addActionListener(this);
      instructionsScrollPane = new JScrollPane();
      instructionsScrollPane.setViewportView(instructionsButton);
    }
    return instructionsScrollPane;
  }

  public void update(Graphics g)
  {
    paint(g);
  }
  
  public void paint(Graphics g)
  {
  	showInstructions();
  }

  public void showInstructions()
  {
    Image instructionsImage = romBlock.getInstructions();
    if(instructionsImage != null) {
      setTitle(romBlock.getName() + " User Instructions");
      setVisible(true);

      Dimension s = Toolkit.getDefaultToolkit().getScreenSize();
      s.height -= 100;
      Dimension d = this.getSize();
      if(d.height > s.height) {
        d.height = s.height;
        this.setSize(d);
      }
      
      instructionsImage = romBlock.getInstructions(getWidth(), getHeight());
      instructionsButton.setIcon(new ImageIcon(instructionsImage));
    }
  }
  
  public void setROMblock(MemoryBlock romBlock)
  {
    this.romBlock = romBlock;
  }
  
  public void actionPerformed(ActionEvent event)
  {
    String cmd = event.getActionCommand();
    if (cmd.equals("next")) {
      Image instructionsImage = romBlock.nextInstructions(getWidth(), getHeight());
      if(instructionsImage != null) {
        instructionsButton.setIcon(new ImageIcon(instructionsImage));
      }
    }
  }

	class windowListener extends WindowAdapter
	{
	  public void windowClosing(WindowEvent event)
	  {
	    romBlock.closeInstructions();
	  }
	}
}
