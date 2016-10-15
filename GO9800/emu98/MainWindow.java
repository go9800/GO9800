package emu98;

import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;


public class MainWindow
{

  private JDialog jDialog = null;  //  @jve:decl-index=0:visual-constraint="57,30"
  private JMenuBar goMenuBar = null;
  private JMenu fileMenu = null;
  private JMenuItem jMenuItem = null;
  private JMenuItem jMenuItem1 = null;
  private JMenu emuMenu = null;
  private JMenu confMenu = null;
  private JMenu helpMenu = null;
  private JMenuItem machineItem = null;
  private JMenuItem runItem = null;
  private JMenuItem stopItem = null;
  private JMenuItem debugItem = null;
  private JMenuItem restartItem = null;
  private JMenuItem romItem = null;
  /**
   * This method initializes jDialog	
   * 	
   * @return javax.swing.JDialog	
   */
  private JDialog getJDialog()
  {
    if (jDialog == null)
    {
      jDialog = new JDialog();
      jDialog.setSize(new Dimension(658, 271));
      jDialog.setJMenuBar(getGoMenuBar());
    }
    return jDialog;
  }
  /**
   * This method initializes goMenuBar	
   * 	
   * @return javax.swing.JMenuBar	
   */
  private JMenuBar getGoMenuBar()
  {
    if (goMenuBar == null)
    {
      goMenuBar = new JMenuBar();
      goMenuBar.add(getFileMenu());
      goMenuBar.add(getEmuMenu());
      goMenuBar.add(getConfMenu());
      goMenuBar.add(getHelpMenu());
    }
    return goMenuBar;
  }
  /**
   * This method initializes fileMenu	
   * 	
   * @return javax.swing.JMenu	
   */
  private JMenu getFileMenu()
  {
    if (fileMenu == null)
    {
      fileMenu = new JMenu();
      fileMenu.setText("File");
      fileMenu.add(getJMenuItem());
      fileMenu.add(getJMenuItem1());
    }
    return fileMenu;
  }
  /**
   * This method initializes jMenuItem	
   * 	
   * @return javax.swing.JMenuItem	
   */
  private JMenuItem getJMenuItem()
  {
    if (jMenuItem == null)
    {
      jMenuItem = new JMenuItem();
      jMenuItem.setText("item1");
    }
    return jMenuItem;
  }
  /**
   * This method initializes jMenuItem1	
   * 	
   * @return javax.swing.JMenuItem	
   */
  private JMenuItem getJMenuItem1()
  {
    if (jMenuItem1 == null)
    {
      jMenuItem1 = new JMenuItem();
      jMenuItem1.setText("item2");
    }
    return jMenuItem1;
  }
  /**
   * This method initializes emuMenu	
   * 	
   * @return javax.swing.JMenu	
   */
  private JMenu getEmuMenu()
  {
    if (emuMenu == null)
    {
      emuMenu = new JMenu();
      emuMenu.setText("Emulator");
      emuMenu.add(getRunItem());
      emuMenu.add(getStopItem());
      emuMenu.add(getDebugItem());
      emuMenu.add(getRestartItem());
    }
    return emuMenu;
  }
  /**
   * This method initializes confMenu	
   * 	
   * @return javax.swing.JMenu	
   */
  private JMenu getConfMenu()
  {
    if (confMenu == null)
    {
      confMenu = new JMenu();
      confMenu.setText("Configuration");
      confMenu.add(getMachineItem());
      confMenu.add(getRomItem());
    }
    return confMenu;
  }
  /**
   * This method initializes helpMenu	
   * 	
   * @return javax.swing.JMenu	
   */
  private JMenu getHelpMenu()
  {
    if (helpMenu == null)
    {
      helpMenu = new JMenu();
      helpMenu.setText("Help");
    }
    return helpMenu;
  }
  /**
   * This method initializes machineItem	
   * 	
   * @return javax.swing.JMenuItem	
   */
  private JMenuItem getMachineItem()
  {
    if (machineItem == null)
    {
      machineItem = new JMenuItem();
      machineItem.setText("Machine");
    }
    return machineItem;
  }
  /**
   * This method initializes runItem	
   * 	
   * @return javax.swing.JMenuItem	
   */
  private JMenuItem getRunItem()
  {
    if (runItem == null)
    {
      runItem = new JMenuItem();
    }
    return runItem;
  }
  /**
   * This method initializes stopItem	
   * 	
   * @return javax.swing.JMenuItem	
   */
  private JMenuItem getStopItem()
  {
    if (stopItem == null)
    {
      stopItem = new JMenuItem();
    }
    return stopItem;
  }
  /**
   * This method initializes debugItem	
   * 	
   * @return javax.swing.JMenuItem	
   */
  private JMenuItem getDebugItem()
  {
    if (debugItem == null)
    {
      debugItem = new JMenuItem();
    }
    return debugItem;
  }
  /**
   * This method initializes restartItem	
   * 	
   * @return javax.swing.JMenuItem	
   */
  private JMenuItem getRestartItem()
  {
    if (restartItem == null)
    {
      restartItem = new JMenuItem();
    }
    return restartItem;
  }
  /**
   * This method initializes romItem	
   * 	
   * @return javax.swing.JMenuItem	
   */
  private JMenuItem getRomItem()
  {
    if (romItem == null)
    {
      romItem = new JMenuItem();
    }
    return romItem;
  }

}
