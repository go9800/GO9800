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
 * 27.11.2006 Changed loading of icon images from plain file to JAR 
 * 10.12.2006 Bugfix Rel. 0.21: Avoid OutOfMemoryError by limiting output to MAX_LINES.
 *              The first line is deleted when lineCount exceeds MAX_LINES.
 * 12.06.2007 Rel. 1.10 Added CLEAR button and labels.
 * 23.08.2007 Rel. 1.20 Added KeyLog and Trace buttons
 * 23.08.2007 Rel. 1.20 Class renamed to Console
 * 28.10.2007 Rel. 1.20 Added infinite WAIT and breakpoint() method
 * 05.08.2016 Rel. 2.00 Added micro-code select box
 * 03.11.2017 Rel. 2.10 Changed background color
 */

package emu98;

import io.HP9800Mainframe;
import io.ImageController;
import io.ImageMedia;
import javax.swing.JPanel;
import java.awt.Frame;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import java.awt.GridBagLayout;
import javax.swing.JButton;
import java.awt.GridBagConstraints;
import java.awt.Font;
import java.awt.Dimension;
import javax.swing.JScrollPane;
import java.awt.Rectangle;
import java.awt.event.*;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.ImageIcon;
import javax.swing.text.BadLocationException;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.Insets;

public class Console extends JDialog implements ActionListener
{

  private static final long serialVersionUID = 1L;
  private JPanel jContentPane = null;
  private JLabel columnLabels = null;
  private JTextArea consoleOutput = null;
  private JPanel buttons = null;
  private JButton runButton = null;
  private JScrollPane disassemblerOutputScrollPane = null;
  private JButton stepButton = null;
  private JCheckBox disassembleCheckBox = null;
  private JComboBox<String> timerComboBox = null;
  private JLabel timerLabel = null;

  private JButton clearButton = null;
  private JLabel disasmLabel = null;
  private JLabel programLabel = null;
  private JLabel outputLabel = null;
  private JButton keyLogButton = null;
  private JButton traceButton = null;
  private JCheckBox keyLogCheckBox = null;
  private JCheckBox microCodeCheckBox = null;

  private static final int MAX_LINES = 4096;
  private Emulator emu;
  private ImageController imageController;
  private boolean debugMode = false;

  /**
   * @param owner
   */
  public Console(Frame owner, HP9800Mainframe mainframe)
  {
    super(owner);
    initialize();
    emu = mainframe.emu;
    imageController = mainframe.imageController;
  }

  /**
   * This method initializes this
   * 
   * @return void
   */
  private void initialize()
  {
    this.setResizable(true);
    this.setBounds(new Rectangle(0, 0, 550, 350));
    this.setContentPane(getJContentPane());
    this.setTitle("HP9800 Console");
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
      GridBagConstraints gridBagConstraints5 = new GridBagConstraints();
      gridBagConstraints5.fill = GridBagConstraints.BOTH;
      gridBagConstraints5.gridx = 0;
      gridBagConstraints5.gridy = 1;
      gridBagConstraints5.weighty = 1.0D;
      gridBagConstraints5.weightx = 1.0;
      GridBagConstraints gridBagConstraints4 = new GridBagConstraints();
      gridBagConstraints4.gridx = 0;
      gridBagConstraints4.gridy = 2;
      GridBagConstraints gridBagConstraints2 = new GridBagConstraints();
      gridBagConstraints2.ipadx = 500;
      gridBagConstraints2.insets = new Insets(0, 8, 0, 0);
      gridBagConstraints2.gridx = 0;
      gridBagConstraints2.gridy = 0;
      gridBagConstraints2.anchor = GridBagConstraints.WEST;
      columnLabels = new JLabel();
      columnLabels.setText("A      B      E I      mcisKDPMCS      P       OPCODE  INSTR           AR1                     AR2");
      columnLabels.setForeground(new Color(253, 253, 253));
      columnLabels.setPreferredSize(new Dimension(700, 17));
      columnLabels.setFont(new Font("Monospaced", Font.PLAIN, 12));
      jContentPane = new JPanel();
      jContentPane.setLayout(new GridBagLayout());
      jContentPane.setBackground(new Color(85, 83, 81));
      jContentPane.add(columnLabels, gridBagConstraints2);
      jContentPane.add(getDisassemblerOutputScrollPane(), gridBagConstraints5);
      jContentPane.add(getButtons(), gridBagConstraints4);
    }
    return jContentPane;
  }

  /**
   * This method initializes disassemblerOutput	
   * 	
   * @return javax.swing.JTextArea	
   */
  private JTextArea getDisassemblerOutput()
  {
    if (consoleOutput == null)
    {
      consoleOutput = new JTextArea();
      consoleOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
      consoleOutput.setForeground(Color.blue);
      consoleOutput.setBackground(new Color(230, 230, 230));
    }
    return consoleOutput;
  }

  /**
   * This method initializes buttons	
   * 	
   * @return javax.swing.JPanel	
   */
  private JPanel getButtons()
  {
    if (buttons == null)
    {
      GridBagConstraints gridBagConstraints32 = new GridBagConstraints();
      gridBagConstraints32.gridx = 4;
      gridBagConstraints32.gridy = 2;
      GridBagConstraints gridBagConstraints31 = new GridBagConstraints();
      gridBagConstraints31.gridx = 1;
      gridBagConstraints31.insets = new Insets(0, 0, 5, 0);
      gridBagConstraints31.gridy = 2;
      GridBagConstraints gridBagConstraints24 = new GridBagConstraints();
      gridBagConstraints24.gridx = 0;
      gridBagConstraints24.insets = new Insets(5, 5, 5, 5);
      gridBagConstraints24.gridy = 1;
      GridBagConstraints gridBagConstraints13 = new GridBagConstraints();
      gridBagConstraints13.gridx = 1;
      gridBagConstraints13.insets = new Insets(5, 5, 5, 5);
      gridBagConstraints13.gridy = 1;
      GridBagConstraints gridBagConstraints41 = new GridBagConstraints();
      gridBagConstraints41.gridx = 2;
      gridBagConstraints41.gridy = 0;
      outputLabel = new JLabel();
      outputLabel.setText("OUTPUT");
      outputLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
      outputLabel.setForeground(new Color(253, 253, 253));
      GridBagConstraints gridBagConstraints3 = new GridBagConstraints();
      gridBagConstraints3.gridx = 4;
      gridBagConstraints3.gridwidth = 2;
      gridBagConstraints3.gridy = 0;
      programLabel = new JLabel();
      programLabel.setText("______ PROGRAM ______");
      programLabel.setForeground(new Color(253, 253, 253));
      programLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
      GridBagConstraints gridBagConstraints23 = new GridBagConstraints();
      gridBagConstraints23.gridx = 0;
      gridBagConstraints23.gridwidth = 2;
      gridBagConstraints23.gridy = 0;
      disasmLabel = new JLabel();
      disasmLabel.setText("_______ MODE _______");
      disasmLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
      disasmLabel.setForeground(new Color(253, 253, 253));
      GridBagConstraints gridBagConstraints11 = new GridBagConstraints();
      gridBagConstraints11.gridx = 2;
      gridBagConstraints11.insets = new Insets(5, 5, 5, 5);
      gridBagConstraints11.gridy = 1;
      GridBagConstraints gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.gridx = 3;
      gridBagConstraints.gridy = 0;
      timerLabel = new JLabel();
      timerLabel.setText("WAIT");
      timerLabel.setForeground(new Color(253, 253, 253));
      timerLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
      GridBagConstraints gridBagConstraints22 = new GridBagConstraints();
      gridBagConstraints22.gridy = 1;
      gridBagConstraints22.ipadx = 40;
      gridBagConstraints22.ipady = 0;
      gridBagConstraints22.insets = new Insets(5, 5, 5, 5);
      gridBagConstraints22.gridx = 3;
      GridBagConstraints gridBagConstraints12 = new GridBagConstraints();
      gridBagConstraints12.gridx = 0;
      gridBagConstraints12.insets = new Insets(0, 0, 5, 0);
      gridBagConstraints12.gridy = 2;
      GridBagConstraints gridBagConstraints21 = new GridBagConstraints();
      gridBagConstraints21.gridx = 4;
      gridBagConstraints21.insets = new Insets(5, 5, 5, 5);
      gridBagConstraints21.gridy = 1;
      GridBagConstraints gridBagConstraints1 = new GridBagConstraints();
      gridBagConstraints1.gridx = 5;
      gridBagConstraints1.insets = new Insets(5, 5, 5, 5);
      gridBagConstraints1.gridy = 1;
      buttons = new JPanel();
      //buttons.setMinimumSize(new Dimension(650,80));
      buttons.setLayout(new GridBagLayout());
      //buttons.setPreferredSize(new Dimension(550, 10));
      buttons.setBackground(new Color(85, 83, 81));
      buttons.add(getTraceButton(), gridBagConstraints24);
      buttons.add(getDisassembleCheckBox(), gridBagConstraints12);
      buttons.add(getKeyLogButton(), gridBagConstraints13);
      buttons.add(getKeyLogCheckBox(), gridBagConstraints31);
      buttons.add(getClearButton(), gridBagConstraints11);
      buttons.add(getTimerComboBox(), gridBagConstraints22);
      buttons.add(getStepButton(), gridBagConstraints21);
      buttons.add(getRunButton(), gridBagConstraints1);
      buttons.add(timerLabel, gridBagConstraints);
      buttons.add(disasmLabel, gridBagConstraints23);
      buttons.add(programLabel, gridBagConstraints3);
      buttons.add(outputLabel, gridBagConstraints41);
      buttons.add(getMicroCodeCheckBox(), gridBagConstraints32);
    }
    return buttons;
  }

  /**
   * This method initializes runButton	
   * 	
   * @return javax.swing.JButton	
   */
  private JButton getRunButton()
  {
    if (runButton == null)
    {
      runButton = new JButton();
      runButton.setActionCommand("Run");
      runButton.setIcon(new ImageIcon(new ImageMedia("media/HP9800/RUN.jpg", imageController).getImage()));
      runButton.setMnemonic(KeyEvent.VK_UNDEFINED);
      runButton.setPreferredSize(new Dimension(70, 25));
      runButton.addActionListener(this);
    }
    return runButton;
  }

  /**
   * This method initializes clearButton    
   *    
   * @return javax.swing.JButton    
   */
  private JButton getClearButton()
  {
    if (clearButton == null)
    {
      clearButton = new JButton();
      clearButton.setIcon(new ImageIcon(new ImageMedia("media/HP9800/CLEAR.jpg", imageController).getImage()));
      clearButton.setPreferredSize(new Dimension(70, 25));
      clearButton.setActionCommand("Clear");
      clearButton.addActionListener(this);
    }
    return clearButton;
  }

  /**
   * This method initializes disassembleCheckBox    
   *    
   * @return javax.swing.JCheckBox  
   */
  private JCheckBox getDisassembleCheckBox()
  {
    if (disassembleCheckBox == null)
    {
      disassembleCheckBox = new JCheckBox();
      disassembleCheckBox.setForeground(new Color(253, 253, 253));
      disassembleCheckBox.setIcon(new ImageIcon(new ImageMedia("media/HP9810A/HP9810A_LED_Large_Off.jpg", imageController).getImage()));
      disassembleCheckBox.setSelectedIcon(new ImageIcon(new ImageMedia("media/HP9810A/HP9810A_LED_Large_On.jpg", imageController).getImage()));
      disassembleCheckBox.setFont(new Font("Dialog", Font.PLAIN, 12));
      disassembleCheckBox.setBackground(new Color(85, 83, 81));
      disassembleCheckBox.setDisabledIcon(new ImageIcon(new ImageMedia("media/HP9810A/HP9810A_LED_Large_Off.jpg", imageController).getImage()));
      disassembleCheckBox.setActionCommand("TraceLED");
      disassembleCheckBox.addActionListener(this);
    }
    return disassembleCheckBox;
  }

  /**
   * This method initializes keyLogCheckBox 
   *    
   * @return javax.swing.JCheckBox  
   */
  private JCheckBox getKeyLogCheckBox()
  {
    if (keyLogCheckBox == null)
    {
      keyLogCheckBox = new JCheckBox();
      keyLogCheckBox.setBackground(new Color(85, 83, 81));
      keyLogCheckBox.setForeground(new Color(253, 253, 253));
      keyLogCheckBox.setDisabledIcon(new ImageIcon(new ImageMedia("media/HP9810A/HP9810A_LED_Large_Off.jpg", imageController).getImage()));
      keyLogCheckBox.setIcon(new ImageIcon(new ImageMedia("media/HP9810A/HP9810A_LED_Large_Off.jpg", imageController).getImage()));
      keyLogCheckBox.setSelectedIcon(new ImageIcon(new ImageMedia("media/HP9810A/HP9810A_LED_Large_On.jpg", imageController).getImage()));
      keyLogCheckBox.setActionCommand("KeylogLED");
      keyLogCheckBox.addActionListener(this);
    }
    return keyLogCheckBox;
  }

  /**
   * This method initializes timerComboBox  
   *    
   * @return javax.swing.JComboBox  
   */
  private JComboBox<String> getTimerComboBox()
  {
    if (timerComboBox == null)
    {
      timerComboBox = new JComboBox<String>();
      timerComboBox.setEditable(true);
      timerComboBox.setName("TimerValue");
      timerComboBox.setActionCommand("timerChanged");
      timerComboBox.setBackground(new Color(85, 83, 81));
      timerComboBox.setPreferredSize(new Dimension(30, 20));

      timerComboBox.addItem("INFINITE");
      timerComboBox.addItem("100000");
      timerComboBox.addItem("10000");
      timerComboBox.addItem("1000");
      timerComboBox.addItem("100");
      timerComboBox.addItem("10");
      timerComboBox.addItem("1");
      timerComboBox.addActionListener(this);
    }
    return timerComboBox;
  }

  /**
   * This method initializes disassemblerOutputScrollPane	
   * 	
   * @return javax.swing.JScrollPane	
   */
  private JScrollPane getDisassemblerOutputScrollPane()
  {
    if (disassemblerOutputScrollPane == null)
    {
      disassemblerOutputScrollPane = new JScrollPane();
      disassemblerOutputScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      disassemblerOutputScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
      disassemblerOutputScrollPane.setViewportView(getDisassemblerOutput());
    }
    return disassemblerOutputScrollPane;
  }

  /**
   * This method initializes stepButton	
   * 	
   * @return javax.swing.JButton	
   */
  private JButton getStepButton()
  {
    if (stepButton == null)
    {
      stepButton = new JButton();
      stepButton.setIcon(new ImageIcon(new ImageMedia("media/HP9800/STEP.jpg", imageController).getImage()));
      stepButton.setPreferredSize(new Dimension(70, 25));
      stepButton.setActionCommand("Step");
      stepButton.addActionListener(this);
    }
    return stepButton;
  }

  /**
   * This method initializes keyLogButton   
   *    
   * @return javax.swing.JButton    
   */
  private JButton getKeyLogButton()
  {
    if (keyLogButton == null)
    {
      keyLogButton = new JButton();
      keyLogButton.setPreferredSize(new Dimension(70, 25));
      keyLogButton.setIcon(new ImageIcon(new ImageMedia("media/HP9800/KEYLOG.jpg", imageController).getImage()));
      keyLogButton.setActionCommand("KeyLog");
      keyLogButton.addActionListener(this);
    }
    return keyLogButton;
  }

  /**
   * This method initializes traceButton    
   *    
   * @return javax.swing.JButton    
   */
  private JButton getTraceButton()
  {
    if (traceButton == null)
    {
      traceButton = new JButton();
      traceButton.setPreferredSize(new Dimension(70, 25));
      traceButton.setIcon(new ImageIcon(new ImageMedia("media/HP9800/TRACE.jpg", imageController).getImage()));
      traceButton.setActionCommand("Trace");
      traceButton.addActionListener(this);
    }
    return traceButton;
  }

  /**
   * This method initializes microCodeCheckBox	
   * 	
   * @return javax.swing.JCheckBox	
   */
  private JCheckBox getMicroCodeCheckBox() {
    if (microCodeCheckBox == null) {
      microCodeCheckBox = new JCheckBox();
      microCodeCheckBox.setBackground(new Color(85, 83, 81));
      microCodeCheckBox.setForeground(new Color(253, 253, 253));
      microCodeCheckBox.setActionCommand("MicroCodeLED");
      microCodeCheckBox.setDisabledIcon(new ImageIcon(new ImageMedia("media/HP9810A/HP9810A_LED_Large_Off.jpg", imageController).getImage()));
      microCodeCheckBox.setIcon(new ImageIcon(new ImageMedia("media/HP9810A/HP9810A_LED_Large_Off.jpg", imageController).getImage()));
      microCodeCheckBox.setSelectedIcon(new ImageIcon(new ImageMedia("media/HP9810A/HP9810A_LED_Large_On.jpg", imageController).getImage()));
      microCodeCheckBox.setText("µCODE");
      microCodeCheckBox.setActionCommand("MicroCodeLED");
      microCodeCheckBox.addActionListener(this);
    }
    return microCodeCheckBox;
  }
  
  public boolean getDebugMode()
  {
    return debugMode;
  }

  public void setDebugMode(boolean mode)
  {
    debugMode = mode;
  }

  public void append(String line)
  {
    int lines = consoleOutput.getLineCount();
    int num = 0;

    if(lines > MAX_LINES) {
      // delete first line
      try
      {
        num = consoleOutput.getLineEndOffset(0);
      } catch (BadLocationException e) {
        e.printStackTrace();
      }

      consoleOutput.replaceRange(null, 0, num);
    }

    consoleOutput.append(line);
    disassemblerOutputScrollPane.getVerticalScrollBar().setValue(100000000);
  }

  public void clear()
  {
    int lines = consoleOutput.getLineCount();
    int num = 0;

    try
    {
      num = consoleOutput.getLineEndOffset(lines - 1);
    } catch (BadLocationException e) {
      e.printStackTrace();
    }

    consoleOutput.replaceRange(null, 0, num);
  }

  public void breakpoint()
  {
    disassembleCheckBox.setEnabled(false);
    disassembleCheckBox.setSelected(false);
    traceButton.setEnabled(false);
    runButton.setEnabled(true);
    Memory.trace = true;
    setVisible(true);
  }

  public void actionPerformed(ActionEvent event)
  {
    String cmd = event.getActionCommand();
    if (cmd.equals("Trace")) {
      disassembleCheckBox.setSelected(!disassembleCheckBox.isSelected());
      stepButton.setEnabled(!disassembleCheckBox.isSelected());
      emu.setDisassemblerMode(disassembleCheckBox.isSelected());
    }

    if (cmd.equals("TraceLED")) {
      stepButton.setEnabled(!disassembleCheckBox.isSelected());
      emu.setDisassemblerMode(disassembleCheckBox.isSelected());
    }

    if (cmd.equals("timerChanged")) {
      try {
        emu.timerValue = Integer.parseInt(timerComboBox.getSelectedItem().toString());
      } catch (NumberFormatException e) {
        emu.timerValue = 0;
      }
    }

    if (cmd.equals("Clear")) {
      clear();
    }

    if (cmd.equals("Step")) {
      disassembleCheckBox.setEnabled(false);
      disassembleCheckBox.setSelected(false);
      traceButton.setEnabled(false);
      runButton.setEnabled(true);
      Memory.trace = true;
      emu.emuThread.interrupt();
    }

    if (cmd.equals("Run")) {
      disassembleCheckBox.setEnabled(true);
      traceButton.setEnabled(true);
      runButton.setEnabled(false);
      Memory.trace = false;
      emu.emuThread.interrupt();
    }

    if (cmd.equals("KeyLog")) {
      keyLogCheckBox.setSelected(!keyLogCheckBox.isSelected());
      emu.keyLogMode = keyLogCheckBox.isSelected();
    }

    if (cmd.equals("KeyLogLED")) {
      emu.keyLogMode = keyLogCheckBox.isSelected();
    }

    if (cmd.equals("MicroCodeLED")) {
      emu.dumpMicroCode = microCodeCheckBox.isSelected();
      if(microCodeCheckBox.isSelected())
        columnLabels.setText("A      B      E M      T      Q      P      I          mcisqKDPMCS     AR1                     AR2                     bdx     PASA NEXT BRC IQN C XTR RC  SC  XC  TTM TTT AC");
      else
        columnLabels.setText("A      B      E I      mcisKDPMCS      P       OPCODE  INSTR           AR1                     AR2");
    }
  }
  
  public void dispose()
  {
  	
  }

}  //  @jve:decl-index=0:visual-constraint="11,-154"
