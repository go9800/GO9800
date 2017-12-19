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
 * 26.05.2006 Restructuration of machine dependend mainframe and IO-devices 
 * 06.06.2006 HP9866A added
 * 09.06.2006 HP9860A added
 * 14.06.2006 HP9862A added
 * 12.07.2006 HP9865A added
 * 26.07.2006 External HP9865A added
 * 13.09.2006 HP9810A added
 * 08.11.2006 HP9820A added
 * 26.12.2006 Rel. 0.22 Bugfix: Added HP9865A.setModel()
 * 16.01.2006 Rel. 0.23 Changed integration of HP11202A
 * 25.02.2007 Rel. 0.30 Added handling of configuration file
 * 04.04.2007 Rel. 1.00 Added HP9821A
 * 12.07.2007 Rel. 1.12 Changed JAR-file access
 * 01.05.2008 Rel. 1.30 Moved emu.setDisassemblerMode() from main() to Emulator contructor
 * 10.05.2010 Rel. 1.50 Create instance of calculator mainframe class dynamically using Reflection API 
 * 01.08.2016 Rel. 2.00 Changed to emulation of CPU micro-code
 * 15.08.2016 Rel. 2.01 Added debug mode (command line option -d) for output of ROM decoding
 * 21.10.2017 Rel. 2.10 Added scalability of calculator window
 * 24.10.2017 Rel. 2.10 Added main window and redirection of System.out
 * 28.10.2017 Rel. 2.10 Added new linking between Mainframe and other components
 * 10.12.2017 Rel. 2.10 Changed window layout mananger, added menus
 * 17.12.2017 Rel. 2.10 Added HP9800Window
 */

package emu98;

import java.lang.reflect.Constructor;
import io.*;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;


class GO9800Window extends JDialog implements ActionListener
{
	private static final long serialVersionUID = 1L;
  private HP9800Mainframe mainframe = null;
  private Configuration config;

	class JTextAreaOutputStream extends OutputStream
	{
		private final JTextArea stdout;

		public JTextAreaOutputStream (JTextArea stdout)
		{
			this.stdout = stdout;
		}

		public void write(byte[] buffer, int offset, int length)
		{
			stdout.append(new String(buffer, offset, length));
		}

		public void write(int b) throws IOException
		{
			write (new byte[] {(byte)b}, 0, 1);
		}
	}

	public void actionPerformed(ActionEvent event)
	{
		String cmd = event.getActionCommand();
  	start(cmd, false);
	}

	public GO9800Window()
	{
		Color gray = new Color(230, 230, 230);
		Color brown = new Color(87, 87, 75);
		
    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    
		// Frame for main window and stdout
		JFrame frame = new JFrame("GO9800 Emulator");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		Container contentPane = frame.getContentPane();
		contentPane.setLayout(gridbag);
		contentPane.setBackground(brown);

		// Menu bar
		JMenuBar menuBar = new JMenuBar();
		menuBar.setBackground(gray);
		menuBar.setMinimumSize(new Dimension(0, 23));
		
		JMenu runMenu = new JMenu("Run");
		JMenuItem hp9810a = new JMenuItem("HP9810A");
		JMenuItem hp9810a2 = new JMenuItem("HP9810A2");
		JMenuItem hp9820a = new JMenuItem("HP9820A");
		JMenuItem hp9821a = new JMenuItem("HP9821A");
		JMenuItem hp9830a = new JMenuItem("HP9830A");
		JMenuItem hp9830b = new JMenuItem("HP9830B");
		runMenu.add(hp9810a);
		runMenu.add(hp9810a2);
		runMenu.add(hp9820a);
		runMenu.add(hp9821a);
		runMenu.add(hp9830a);
		runMenu.add(hp9830b);
		menuBar.add(runMenu);
		
		c.gridx = 0;
		c.gridy = 0;
    c.gridheight = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.WEST;
		contentPane.add(menuBar, c);
		
    JButton logo = new JButton();
    logo.setIcon(new ImageIcon(new ImageMedia("media/HP9800/HP9800_Emulator.jpg").getImage()));
    logo.setBackground(gray);
    logo.setMargin(new Insets(0, 0, 0, 20));
    logo.setHorizontalAlignment(SwingConstants.LEFT);
    
		c.gridy = 1;
		contentPane.add(logo, c);
		
		hp9810a.addActionListener(this);
		hp9810a2.addActionListener(this);
		hp9820a.addActionListener(this);
		hp9821a.addActionListener(this);
		hp9830a.addActionListener(this);
		hp9830b.addActionListener(this);

		// TextArea for stdout
		JTextArea textArea = new JTextArea(20, 80);
		textArea.setEditable(false);
		textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
		textArea.setBackground(gray);
		textArea.setForeground(Color.blue);

		// ScrollPane for textArea
		JScrollPane scrollPane = new JScrollPane (textArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		
		c.gridy = 2;
    c.weightx = 1.;
    c.weighty = 1.;
    c.fill = GridBagConstraints.BOTH;
    contentPane.add(scrollPane, c);

		frame.pack();
		frame.setVisible(true);

		// redirect System.out
		JTextAreaOutputStream out = new JTextAreaOutputStream(textArea);
		System.setOut(new PrintStream(out));
		System.setErr(new PrintStream(out));
	}
	
	public void start(String machine, boolean debug)
	{
		// Reflection API for loading of calculator classes:
	  Class<?>[] formpara;  // formal parameter class
	  Object[] actpara;     // actual parameter object
	  Class<?> calc;        // calculator class
	  Constructor<?> cons;  // constructor method

    Emulator emu = new Emulator(machine);
		SoundMedia.enable(true);

    // create object for mainframe class dynamically using Reflection API
    formpara = new Class[]{Emulator.class};
    actpara = new Object[]{emu};
    
    try {
      // find Class for calculator mainframe by name
      calc = Class.forName("io." + machine + "." + machine + "Mainframe");
      
      // find constructor for formal parameters
      cons = calc.getConstructor(formpara);
      
      // create new object instance of calculator mainframe
      mainframe = (HP9800Mainframe)cons.newInstance(actpara);
      
    } catch(Exception e) {
      e.printStackTrace();
      System.err.println(machine + " not implemented.");
      System.exit(1);      
    }
    
    emu.setMainframe(mainframe);
		config = new Configuration(machine, mainframe);
		mainframe.setConfiguration(config);

		// load configuration files, memory blocks, interfaces, and devices
    config.loadConfig(machine);
    // load host keyboard configuration
    config.loadKeyConfig(machine);

    mainframe.console.setDebugMode(debug);
    if(debug)
      mainframe.cpu.outputDecoderToConsole(); // transfer decoded micro code to console 
    
    // create window for HP9800Mainframe 
    HP9800Window hp9800Window = new HP9800Window(mainframe, machine);
    hp9800Window.setVisible(true);
    emu.start();
	}
}

public class GO9800
{
  public static void usage()
  {
    System.out.println("Usage: GO9800.jar [-d] [Machine-Configuration]");
  }
  
  public static void main(String[] args)
  {
    boolean debug = false;
    String machine = "";

    if(args.length < 0 || args.length > 3){
      usage();
      System.exit(1);
    }
    
    for(int i = 0; i < args.length; i++) {
      if(args[i].startsWith("-")) {
      	switch(args[i].charAt(1)) {
      		case 'd':
      			debug = true;
      	}
      } else { 
        machine = args[i];
      }
    }
    
    System.setProperty("awt.image.incrementalDraw", "false");
    
    GO9800Window go9800 = new GO9800Window();
    
    System.out.println("HP Series 9800 Emulator Release 2.1, Copyright (C) 2006-2018 Achim Buerger\n");
    System.out.println("GO9800 comes with ABSOLUTELY NO WARRANTY.");
    System.out.println("This is free software, and you are welcome to");
    System.out.println("redistribute it under certain conditions.\n");
    System.out.println("GO9800 is in no way associated with the Hewlett-Packard Company.");
    System.out.println("Hewlett-Packard, HP, and the HP logos are all trademarks of the Hewlett-Packard Company.");
    System.out.println("This software is intended solely for research and education purposes.\n\n");
    
    if(machine != "")
    	go9800.start(machine, debug);
  }
}
