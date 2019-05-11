/*
 * HP9800 Emulator
 * Copyright (C) 2006-2019 Achim Buerger
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
 * 17.12.2017 Rel. 2.10 Class created
 */

package emu98;

import io.*;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.util.Enumeration;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.border.Border;

public class HP9800Window extends JFrame implements ActionListener
{
  private static final long serialVersionUID = 1L;

  HP9800Mainframe mainframe;
  Emulator emu;  // class variable from HP9800Mainframe
  Console console;  // class variable from HP9800Mainframe
  HP2116Panel hp2116panel;  // class variable from HP9800Mainframe

  Color hpBeige = new Color(215, 213, 178);
  Color hpBrown = new Color(87, 87, 75);
  Color gray = new Color(230, 230, 230);

  int MENU_H = 23;

  JMenuBar menuBar;
  JCheckBoxMenuItem keyMapItem, consoleItem, hp2116PanelItem;
  JCheckBoxMenuItem debugItem, fanSoundItem, allSoundItem;


  public HP9800Window(HP9800Mainframe mainframe, String machine) 
  {
    super(machine);

    this.mainframe = mainframe;
    emu = mainframe.emu;
    console = mainframe.console;
    hp2116panel = mainframe.hp2116panel;
    mainframe.setHP9800Window(this);

    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();

    JPanel contentPane = new JPanel();
    contentPane.setLayout(gridbag);
    setContentPane(contentPane);
    //JTabbedPane tabbedPane = new JTabbedPane(); // see setFrameSize()
    //tabbedPane.addTab(machine, contentPane);
    //setContentPane(tabbedPane);
    //setUndecorated(true);
    //setOpacity(0.8f);

    UIManager.put("MenuBar.background", hpBeige);
    UIManager.put("Menu.background", hpBeige);
    UIManager.put("MenuItem.background", hpBeige);
    UIManager.put("CheckBoxMenuItem.background", hpBeige);

    // Menu bar
    menuBar = new JMenuBar();
    menuBar.setMinimumSize(new Dimension(0, MENU_H));

    JMenu runMenu = new JMenu("Run");
    runMenu.add(new JMenuItem("Restart     Alt+Ctrl+R")).addActionListener(this);
    runMenu.addSeparator();
    runMenu.add(new JMenuItem("Exit")).addActionListener(this);
    menuBar.add(runMenu);

    JMenu viewMenu = new JMenu("View");
    viewMenu.add(new JMenuItem("Normal Size      Ctrl+N")).addActionListener(this);
    viewMenu.add(new JMenuItem("Real Size           Ctrl+R")).addActionListener(this);
    viewMenu.add(new JMenuItem("Hide Menu         Ctrl+M")).addActionListener(this);
    viewMenu.addSeparator();
    viewMenu.add(keyMapItem = new JCheckBoxMenuItem("Key Map            Ctrl+K")).addActionListener(this);
    viewMenu.add(consoleItem = new JCheckBoxMenuItem("Console             Ctrl+D")).addActionListener(this);
    viewMenu.add(hp2116PanelItem = new JCheckBoxMenuItem("HP2116 Panel  Ctrl+C")).addActionListener(this);
    menuBar.add(viewMenu);

    // add print menu only for models with internal printer
    if(!machine.startsWith("HP9830")) {
      JMenu printMenu = new JMenu("Print");
      printMenu.add(new JMenuItem("Page Format    Shift+Ctrl+P")).addActionListener(this);
      printMenu.add(new JMenuItem("Hardcopy       	              Ctrl+P")).addActionListener(this);
      printMenu.addSeparator();
      printMenu.add(new JMenuItem("Clear                          Ctrl+Del")).addActionListener(this);
      menuBar.add(printMenu);
    }

    JMenu optionsMenu = new JMenu("Options");
    optionsMenu.add(debugItem = new JCheckBoxMenuItem("Debug")).addActionListener(this);
    optionsMenu.add(fanSoundItem = new JCheckBoxMenuItem("Fan Sound      Ctrl+F")).addActionListener(this);
    optionsMenu.add(allSoundItem = new JCheckBoxMenuItem("All Sounds     Ctrl+S")).addActionListener(this);
    debugItem.setSelected(console.getDebugMode());
    fanSoundItem.setSelected(true);
    allSoundItem.setSelected(true);
    menuBar.add(optionsMenu);

    IOdevice device;
    JMenu devicesMenu = new JMenu("Devices");
    for(Enumeration<IOdevice> devices = mainframe.ioDevices.elements(); devices.hasMoreElements(); ) {
      device = devices.nextElement();
      if(device.needsWindow()) {
        devicesMenu.add(new JMenuItem(device.hpName)).addActionListener(this);
        //tabbedPane.addTab(device.hpName, device);
      }
    }
    menuBar.add(devicesMenu);

    c.gridx = 0;
    c.gridy = 0;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.WEST;
    contentPane.add(menuBar, c);

    // Panel for drawing of calculator mainframe
    mainframe.setBackground(hpBrown);
    c.gridy = 1;
    c.weightx = 1.;
    c.weighty = 1.;
    c.fill = GridBagConstraints.BOTH;
    contentPane.add(mainframe, c);

    //setUndecorated(true);
    pack(); // put components on their correct places

    addWindowListener(new WindowListener());
    addKeyListener(new HP9800KeyListener());

    setResizable(true); // this changes size of insets
  }

  public void actionPerformed(ActionEvent event)
  {
    String cmd = event.getActionCommand();

    if(cmd.startsWith("Restart")) {
      mainframe.ioUnit.reset = true;
    } else if(cmd.startsWith("Exit")) {
      exit();
    } else if(cmd.startsWith("Debug")) {
      console.setDebugMode(!console.getDebugMode());
      debugItem.setSelected(console.getDebugMode());
    } else if(cmd.startsWith("Hide Menu")) {
      menuBar.setVisible(false);
    } else if(cmd.startsWith("Normal Size")) {
      mainframe.setNormalSize();
    } else if(cmd.startsWith("Real Size")) {
      mainframe.setRealSize();
    } else if(cmd.startsWith("Key Map")) {
      keyMapItem.setSelected(mainframe.showKeycode = !mainframe.showKeycode);
      mainframe.repaint();
    } else if(cmd.startsWith("Console")) {
      console.setVisible(!console.isVisible());
      consoleItem.setSelected(console.isVisible());
    } else if(cmd.startsWith("HP2116 Panel")) {
      hp2116panel.setVisible(!hp2116panel.isVisible());
      hp2116PanelItem.setSelected(hp2116panel.isVisible());
    } else if(cmd.startsWith("Fan Sound")) {
      fanSoundItem.setSelected(mainframe.fanSound.toggle());
    } else if(cmd.startsWith("All Sounds")) {
      if(mainframe.soundController.isEnabled()) {
        mainframe.fanSound.stop();
        mainframe.soundController.setEnabled(false);
      } else {
        mainframe.soundController.setEnabled(true);
        mainframe.fanSound.loop();
      }
      allSoundItem.setSelected(mainframe.soundController.isEnabled());
      fanSoundItem.setSelected(mainframe.soundController.isEnabled());
    } else if(cmd.startsWith("Page Format")) {
      mainframe.pageFormat = mainframe.printJob.pageDialog(mainframe.pageFormat);
    } else if(cmd.startsWith("Hardcopy")) {
      mainframe.printJob.printDialog();
      try {
        mainframe.printJob.print();
      } catch (PrinterException e) { }
    } else if(cmd.startsWith("Clear")) {
      mainframe.initializeBuffer();
      mainframe.page = 0;
      mainframe.repaint();
    }

    IOdevice device;
    for(Enumeration<IOdevice> devices = mainframe.ioDevices.elements(); devices.hasMoreElements(); ) {
      device = devices.nextElement();
      if(device.hpName.equals(cmd)) {
        if(!device.deviceWindow.isVisible()) {
          device.deviceWindow.setVisible(true);
          device.deviceWindow.setState(NORMAL);
        }
        else
          device.deviceWindow.setState(1 - device.deviceWindow.getState()); // toggle ICONIFIED / NORMAL
      }
    }

  }

  class WindowListener extends WindowAdapter
  {
    public void windowClosing(WindowEvent event)
    {
      exit();
    }
  }

  void exit()
  {
    System.out.println("\nHP9800 Emulator shutdown initiated ...");

    mainframe.close();
    dispose();
    System.out.println("HP9800 Emulator terminated.");
  }

  public void setFrameSize(Dimension panelSize)
  {
    setSize(panelSize.width + getInsets().left + getInsets().right, panelSize.height + (menuBar.isVisible() ? menuBar.getHeight() : 0) + getInsets().top + getInsets().bottom);
    // add 5 and 28 to FrameSize for tabbedPane
    //setSize(panelSize.width + 5 + getInsets().left + getInsets().right, panelSize.height + 28 + (menuBar.isVisible() ? menuBar.getHeight() : 0) + getInsets().top + getInsets().bottom);
  }

  public void setFrameSize(Boolean showMenuBar)
  {
    setSize(mainframe.getWidth() + getInsets().left + getInsets().right, mainframe.getHeight() + (showMenuBar ? menuBar.getHeight() : 0) + getInsets().top + getInsets().bottom);
    menuBar.setVisible(showMenuBar);
  }

  class HP9800KeyListener implements KeyListener
  {
    public void keyPressed(KeyEvent event)
    {
      int numPages = mainframe.numLines / mainframe.PAPER_HEIGHT;
      int keyChar = event.getKeyChar();
      int keyCode = event.getKeyCode();
      String strCode = "";
      String modifier = "";

      switch(keyCode) {
      case KeyEvent.VK_UNDEFINED:
        if(keyChar != KeyEvent.CHAR_UNDEFINED)
          strCode = Integer.toString(keyChar);
        else
          return;
        break;

      case KeyEvent.VK_SHIFT:     // don't send modifier keys
      case KeyEvent.VK_CONTROL:
      case KeyEvent.VK_ALT:
      case KeyEvent.VK_NUM_LOCK:
        return;


      default:
        if(event.isControlDown()) {
          switch(keyCode) {
          /*
          case 'Q':
            for(int i = 01000; i <= 01777; i++) {
              emu.console.append(Integer.toOctalString(emu.memory[i].getValue()) + "\n");
            }
            break;
           */ 

          case 'C':
            hp2116panel.setVisible(!hp2116panel.isVisible());
            hp2116PanelItem.setSelected(hp2116panel.isVisible());
            break;

          case 'D':
            console.setVisible(!console.isVisible());
            consoleItem.setSelected(console.isVisible());
            break;

          case 'F':
            fanSoundItem.setSelected(mainframe.fanSound.toggle());
            break;

          case 'K':
            keyMapItem.setSelected(mainframe.showKeycode = !mainframe.showKeycode);
            mainframe.repaint();
            break;

          case 'M':
            setFrameSize(!menuBar.isVisible());
            break;

          case 'N':
            mainframe.setNormalSize();
            break;

          case 'P':
            if(event.isShiftDown())
              mainframe.pageFormat = mainframe.printJob.pageDialog(mainframe.pageFormat);
            else {
              mainframe.printJob.printDialog();
              try {
                mainframe.printJob.print();
              } catch (PrinterException e) { }
            }
            break;

          case 'R':
            if(event.isAltDown())
              synchronized(mainframe.ioUnit) {
                mainframe.ioUnit.reset = true;
              } else {
                mainframe.setRealSize();
              }
            break;

          case 'S':
            if(mainframe.soundController.isEnabled()) {
              mainframe.fanSound.stop();
              mainframe.soundController.setEnabled(false);
            } else {
              mainframe.soundController.setEnabled(true);
              mainframe.fanSound.loop();
            }
            allSoundItem.setSelected(mainframe.soundController.isEnabled());
            fanSoundItem.setSelected(mainframe.soundController.isEnabled());
            break;

          case 'T':
            if(emu.measure) {
              emu.stopMeasure();
              console.append("NumOps=" + emu.numOps);
              console.append(" Min=" + emu.minExecTime);
              console.append(" Max=" + emu.maxExecTime);
              console.append(" Mean=" + emu.sumExecTime / emu.numOps + "[ns]\n");
              if(!console.isVisible())
                console.setVisible(true);
            } else
              emu.startMeasure();
            break;

          case KeyEvent.VK_PAGE_UP:
            // paper page up
            if(--mainframe.page < 0) mainframe.page = 0;
            mainframe.displayPrintOutput(null);
            break;

          case KeyEvent.VK_PAGE_DOWN:
            // paper page down
            mainframe.page++;
            if(mainframe.page >= numPages) {
              mainframe.page = numPages;
              mainframe.repaint();
            }
            else
              mainframe.displayPrintOutput(null);
            break;

          case KeyEvent.VK_DELETE:
            // clear print buffer
            mainframe.initializeBuffer();
            mainframe.page = 0;
            mainframe.repaint();
            break;

          case KeyEvent.VK_HOME:
            // PAPER
            mainframe.paper(2);
            keyCode = -1;
            break;

          }

          return;
        }

        strCode = Integer.toString(keyCode);
      }

      if(event.isAltDown())
        modifier = "A";

      if(event.isControlDown())
        modifier += "C";

      if(event.isShiftDown())
        modifier += "S";

      if(emu.keyLogMode) {
        emu.console.append(strCode);
        if(modifier != "")
          emu.console.append(" " + modifier);
        emu.console.append(" ; " + (char)keyChar + "\n");
        return;
      }

      strCode = (String)mainframe.config.hostKeyCodes.get(strCode + modifier);
      if(strCode != null)
        keyCode = Integer.parseInt(strCode, 8);
      else if(keyChar >= 040 && keyChar <= 0172)
        if(Character.isLowerCase(keyChar))
          keyCode = Character.toUpperCase(keyChar);
        else if(Character.isUpperCase(keyChar ))
          keyCode = keyChar + 0200;
        else
          keyCode = keyChar;
      else
        return;

      if(keyCode == mainframe.STOP_KEYCODE) {
        // set STP flag
        mainframe.ioUnit.STP = true;
      }

      event.consume(); // do not pass key event to host system 
      mainframe.ioUnit.bus.keyboard.setKeyCode(keyCode);
      mainframe.ioUnit.bus.keyboard.requestInterrupt();
    }

    public void keyReleased(KeyEvent event)
    {}

    public void keyTyped(KeyEvent event)
    {}
  }
}
