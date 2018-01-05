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
 * 27.01.2007 Rel. 0.30 Class created
 * 06.10.2008 Rel. 1.31 Added file IO mode
 * 07.04.2010 Rel. 1.50 Class now inherited from IOdevice and completely reworked
 * 09.05.2012 Rel. 1.60 Added SO status flags for file closing
 * 09.05.2012 Rel. 1.60 Bugfix: added missing method setInterface (fixes null pointer error when canceling file dialog)
 * 11.05.2012 Rel. 1.60 Added buffered output window 
 * 28.10.2017 Rel. 2.10 Added new linking between Mainframe and other components
 * 02.01.2018 Rel. 2.10 Added use of class DeviceWindow
*/

package io;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.io.*;
import java.util.Vector;

import emu98.DeviceWindow;

public class HP11202HostFileIO extends HostIO implements Printable
{
  public static final long serialVersionUID = 1L;
  
  HP11202A hp11202Interface;
  private RandomAccessFile inFile = null, outFile = null;
  private boolean fileSelector = true;
  private String fileMode;
  private int base;
  private Vector<StringBuffer> printBuffer;
  private StringBuffer lineBuffer;
  private int fontSize;
  private int numLines;
  private int page;
  private Font font;
  private PrinterJob printJob;
  private PageFormat pageFormat;

  
  public HP11202HostFileIO(String[] parameters, IOinterface ioInterface)
  {
    super("HP11202A File-I/O", ioInterface); // set window title
    createWindow = false;  // this device doesn't need a window
    hp11202Interface = (HP11202A)ioInterface;

    fontSize = 12;
    font = new Font("Monospaced", Font.PLAIN, fontSize);
    setFont(font);

    initializeBuffer();
    
    // set Printable
    printJob = PrinterJob.getPrinterJob();
    printJob.setPrintable(this);
    pageFormat = printJob.defaultPage();

    fileMode = parameters[0];
    System.out.print(" mode=" + fileMode + " ");
    try {
      base = Integer.parseInt(fileMode);
    } catch (NumberFormatException e) {
      base = 8;
    }
  }
  
  public void keyPressed(KeyEvent event)
  {
    int keyCode = event.getKeyCode();

    int windowDotRows = getHeight() - 8 -  getInsets().top;  // # dot rows in output area
    int numPages = numLines * fontSize / windowDotRows;  // # of pages to display

    switch(keyCode) {
    case KeyEvent.VK_PAGE_DOWN:
      page++;
      if(page > numPages) page = numPages;
      break;

    case KeyEvent.VK_PAGE_UP:
      if(--page < 0) page = 0;
      break;

    case KeyEvent.VK_END:
      page = 0;
      break;

    case KeyEvent.VK_HOME:
      page = numPages;
      break;

    case KeyEvent.VK_DELETE:
      initializeBuffer();
      page = 0;
      if(event.isShiftDown()) {
        setSize(1010, 250);
        fontSize = 12;
        font = new Font("Monospaced", Font.PLAIN, fontSize);
        setFont(font);
      }
      break;

    case 'P':
    case KeyEvent.VK_INSERT:
      if(event.isShiftDown())
        pageFormat = printJob.pageDialog(pageFormat);
      else {
        printJob.printDialog();
        try {
          printJob.print();
        } catch (PrinterException e) { }
      }
      return;

    default:
      switch(event.getKeyChar()) {
      case '+':
        if(fontSize < 40) {
          fontSize += 1;
          font = new Font("Monospaced", Font.PLAIN, fontSize);
          setFont(font);
        } else {
          return;
        }
        break;

      case '-':
        if(fontSize > 8) {
          fontSize -= 1;
          font = new Font("Monospaced", Font.PLAIN, fontSize);
          setFont(font);
        } else {
          return;
        }
        break;
      
      default:
        return;
      }
    }

    repaint();
  }

  public int print(Graphics g, PageFormat pf, int page)
  {
    Graphics2D g2 = (Graphics2D)g;
    pf = pageFormat;

    double scale = pf.getImageableWidth() / 40. / fontSize;

    int windowLines = (int)(pf.getImageableHeight() / 12.);  // # lines in output area
    int numPages = numLines / windowLines;  // # of pages to print
    if(page > numPages)
      return(NO_SUCH_PAGE);

    int x = (int)(pf.getImageableX() / scale);  // leftmost print positon
    int y = (int)(pf.getImageableY() / scale);  // topmost print position
    int lineNum = page * windowLines;

    g2.scale(scale, scale);
    g.setFont(font);

    for(int i = lineNum; i < lineNum + windowLines && i < numLines; i++) {
      y += fontSize;  // advance one character line
      g.drawString(printBuffer.elementAt(i).toString(), x, y);
    }

    return(PAGE_EXISTS);
  }

  public void paint(Graphics g)
  {
    int x = getInsets().left + 4;  // leftmost print positon
    int yTop = getInsets().top;  // topmost print position
    int yBottom = getHeight() - 8;  // lowest print positon
    int windowDotRows = yBottom - yTop;  // # dot rows in output area
    int y = yBottom + page * windowDotRows;  // y-position of actual displayed page

    g.drawString(lineBuffer.toString(), x, y);

    for(int i = numLines - 1; i >= 0; i--) {
      y -= fontSize;  // advance one character line
      if(y > yBottom) continue;  // is print y-position below lowest visible line? If yes: try next line
      if(y < yTop - fontSize) break;  // is print y-position above highest visible line? If yes: we are done
      g.drawString(printBuffer.elementAt(i).toString(), x, y);
    }
  }
  
  void timerCallback()
  {
    // re-enable FileDialog after timeout in HP11202A.run()  
    fileSelector = true;
  }
  
  RandomAccessFile openFile(boolean out)
  {
    if(!fileSelector)
      return(null);
    
    FileDialog fileDialog = new FileDialog(deviceWindow, "Open HP11202A " + (out? "Output" : "Input") + " File");
    fileDialog.setBackground(Color.WHITE);
    fileDialog.setVisible(true);
    RandomAccessFile ioFile = null;
    
    String fileName = fileDialog.getFile();
    String dirName = fileDialog.getDirectory();

    // was fileDialog canceled?
    if(fileName == null) {
      // disable fileDialog 
      fileSelector = false;
      // restart timer for auto enable fileDialog after 5sec 
      hp11202Interface.devThread.interrupt();
      return(null);
    }

    fileName = dirName + fileName;

    while(true) {
      try{
        ioFile = new RandomAccessFile(fileName, "rw");
        break;
      } catch (FileNotFoundException e) {
          return(null);
      }
    }

    return(ioFile);
  }
  
  RandomAccessFile closeFile(RandomAccessFile ioFile)
  {
    if(ioFile != null) {
      try {
        ioFile.close();
      } catch (IOException e) { }
    }
    
    return(null);
  }
  
  public void initializeBuffer()
  {
    numLines = 0;
    lineBuffer = new StringBuffer();
    printBuffer = new Vector<StringBuffer>();
  }
  
  public int output(int status, int value)
  {
    String outStr;
    
    if(outFile == null) {
      outFile = openFile(true);

      if(outFile == null)
        return(-1);
      
      setVisible(true);
    }
    
    // write to output file
    try {
      if(fileMode.equals("Bin")) {
        outFile.writeByte(value);
        lineBuffer.append((char)value);

        if(page == 0) {
          int x = getInsets().left + 4;  // leftmost print position
          int y = getHeight() - 8;
          getGraphics().drawString(lineBuffer.toString(), x, y);
        }
      } else {
        outStr = Integer.toString(value, base) + "\r\n";
        outFile.writeBytes(outStr);
        lineBuffer.append(outStr);
        value = '\n';
      }

      if(value == '\n') {
        numLines++;
        printBuffer.addElement(lineBuffer);
        lineBuffer = new StringBuffer();
        repaint();
      }

      return(0);

    } catch (IOException e) {
      e.printStackTrace();
    }

    return(-1);
  }

  public int input(int status)
  {
    if(inFile == null) {
      inFile = openFile(false);

      if(inFile == null)
        return(-1);
      
      setVisible(true);
    }

    // read from input file and store in buffer
    try {
      if(fileMode.equals("Bin"))
        return(inFile.readByte());
      else
        return(Integer.parseInt(inFile.readLine(), base));
    } catch (Exception e) {
      inFile = closeFile(inFile);
      //ioReg.STP = true;
    }
    
    return(-1);
  }
  
  public void close()
  {
    outFile = closeFile(outFile);
    inFile = closeFile(inFile);
    super.close();
  }
}