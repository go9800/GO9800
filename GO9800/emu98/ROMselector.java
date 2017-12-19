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
 * 18.02.2007 Rel. 0.30: Class created 
 */

package emu98;

import io.HP9800Mainframe;
import io.ImageMedia;

import javax.swing.JPanel;
import java.awt.Frame;
import java.awt.BorderLayout;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.BoxLayout;
import java.awt.Dimension;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Point;


public class ROMselector extends JDialog implements ActionListener
{

  private static final long serialVersionUID = 1L;
  private JPanel jContentPane = null;
  private JScrollPane romScrollPane = null;
  private JPanel romPanel = null;
  private JButton romButton = null;
  private int moduleWidth, moduleHeight;
  
  private HP9800Mainframe mainframe;
  private Frame owner;
  private String romSlot = "";  //  @jve:decl-index=0:

  
  /**
   * @param owner
   */
  public ROMselector(Frame owner, HP9800Mainframe hp9800Mainframe, int w, int h)
  {
    super(owner);
    this.owner = owner;
    moduleWidth = w;
    moduleHeight = h;
    mainframe = hp9800Mainframe;
    initialize();
  }

  /**
   * This method initializes this
   * 
   * @return void
   */
  private void initialize()
  {
    this.setTitle("HP9800 ROM Blocks");
    this.setSize(new Dimension(230, 370));
    this.setLocation(new Point(0, 0));
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
      jContentPane.add(getRomScrollPane(), BorderLayout.CENTER);
    }
    return jContentPane;
  }

  /**
   * This method initializes romScrollPane	
   * 	
   * @return javax.swing.JScrollPane	
   */
  private JScrollPane getRomScrollPane()
  {
    if (romScrollPane == null)
    {
      romScrollPane = new JScrollPane();
      romScrollPane.setPreferredSize(new Dimension(240, 270));
      romScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
      romScrollPane.setViewportView(getRomPanel());
    }
    return romScrollPane;
  }

  /**
   * This method initializes romPanel	
   * 	
   * @return javax.swing.JPanel	
   */
  public JPanel getRomPanel()
  {
    if (romPanel == null)
    {
      romPanel = new JPanel();
      romPanel.setLayout(new BoxLayout(getRomPanel(), BoxLayout.Y_AXIS));
      romPanel.setBackground(Color.black);
      romPanel.setLocation(new Point(0, 0));
      //romPanel.add(getRomButton(), null);
    }
    return romPanel;
  }

  /**
   * This method initializes romButton	
   * 	
   * @return javax.swing.JButton	
   */
  /*
  private JButton getRomButton()
  {
    if (romButton == null)
    {
      romButton = new JButton();
      romButton.setIcon(new ImageIcon(new ImageMedia("media/HP9810A/HP11XXXX_Block.jpg").getImage()));
      romButton.setPreferredSize(new Dimension(200, 58));
      romButton.setBackground(Color.black);
      romButton.setActionCommand("HP11XXXX");
      romButton.addActionListener(this);
    }
    return romButton;
  }
  */
  public void addRomButton(String imageName, String actionCommand)
  {
    romButton = new JButton();
    romButton.setIcon(new ImageIcon(new ImageMedia(imageName).getScaledImage((int)(1.2 * moduleWidth), (int)(1.2 * moduleHeight))));
    romButton.setPreferredSize(new Dimension(moduleWidth + 10, moduleHeight + 10));
    romButton.setBackground(Color.black);
    romPanel.add(romButton, null);
    romButton.setActionCommand(actionCommand);
    romButton.addActionListener(this);
  }
  
  public void setRomSlot(String romSlot)
  {
    this.romSlot = romSlot;
  }

  public void actionPerformed(ActionEvent event)
  {
    String cmd = event.getActionCommand();
    mainframe.config.setROM(romSlot, cmd);
    mainframe.repaint();
    setVisible(false);
  }

}
