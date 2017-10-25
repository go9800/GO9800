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
 * 21.10.2017 Rel. 2.04 Added scalability of calculator window
 * 24.10.2017 Rel. 2.04 Added main window and redirection of System.out
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
  HP9800Mainframe mainframe = null;
	private boolean isRunning = false;

	class JTextAreaOutputStream extends OutputStream
	{
		private final JTextArea destination;

		public JTextAreaOutputStream (JTextArea destination)
		{
			if (destination == null)
				throw new IllegalArgumentException("Destination is null");

			this.destination = destination;
		}

		public void write(byte[] buffer, int offset, int length) throws IOException
		{
			final String text = new String(buffer, offset, length);

			SwingUtilities.invokeLater(new Runnable ()
			{
				public void run() 
				{
					destination.append(text);
				}
			});
		}

		public void write(int b) throws IOException
		{
			write (new byte[] {(byte)b}, 0, 1);
		}
	}

	public void actionPerformed(ActionEvent event)
	{
		String cmd = event.getActionCommand();

		//if(mainframe == null || !mainframe.isVisible()) {
			isRunning = true;
			start(cmd, false);
		//}
	}

	public GO9800Window()
	{
		// Frame for main window and stdout
		JFrame frame = new JFrame("GO9800 Emulator");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		Container contentPane = frame.getContentPane();
		contentPane.setLayout (new BorderLayout());
		contentPane.setBackground(new Color(87, 87, 75));

		// Menu bar
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("Calculator");
		JMenuItem hp9810a = new JMenuItem("HP9810A");
		JMenuItem hp9810a2 = new JMenuItem("HP9810A2");
		JMenuItem hp9820a = new JMenuItem("HP9820A");
		JMenuItem hp9821a = new JMenuItem("HP9821A");
		JMenuItem hp9830a = new JMenuItem("HP9830A");
		JMenuItem hp9830b = new JMenuItem("HP9830B");

		fileMenu.add(hp9810a);
		fileMenu.add(hp9810a2);
		fileMenu.add(hp9820a);
		fileMenu.add(hp9821a);
		fileMenu.add(hp9830a);
		fileMenu.add(hp9830b);
		menuBar.add(fileMenu);
		contentPane.add(menuBar, BorderLayout.NORTH);

		hp9810a.addActionListener(this);
		hp9810a2.addActionListener(this);
		hp9820a.addActionListener(this);
		hp9821a.addActionListener(this);
		hp9830a.addActionListener(this);
		hp9830b.addActionListener(this);

		// TextArea for stdout
		JTextArea textArea = new JTextArea(25, 80);
		textArea.setEditable(false);
		textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
		textArea.setBackground(new Color(230, 230, 230));
		textArea.setForeground(Color.blue);

		// ScrollPane for textArea
		JScrollPane scrollPane = new JScrollPane (textArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		contentPane.add(scrollPane, BorderLayout.CENTER);

		frame.pack();
		frame.setVisible(true);

		// redirect System.out
		JTextAreaOutputStream out = new JTextAreaOutputStream(textArea);
		System.setOut (new PrintStream(out));
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
      calc = Class.forName("io." + emu.model + "." + emu.model + "Mainframe" + emu.version);
      
      // find constructor for formal parameters
      cons = calc.getConstructor(formpara);
      
      // create new object instance of calculator mainframe
      mainframe = (HP9800Mainframe)cons.newInstance(actpara);
      
    } catch(Exception e) {
      e.printStackTrace();
      System.err.println(emu.model + emu.version + " not implemented.");
      System.exit(1);      
    }
    
    mainframe.emu.console.setDebugMode(debug);
    if(debug)
      emu.cpu.outputDecoderToConsole(); // transfer decoded micro code to console 

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
    
    GO9800Window go9800 = new GO9800Window();
    
    System.out.println("HP Series 9800 Emulator Release 2.04, Copyright (C) 2006-2018 Achim Buerger\n");
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
