package net.minecraft.launcher.Macrosoft;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import static javax.swing.JFrame.EXIT_ON_CLOSE;

import javax.swing.JProgressBar;

public class Downloader {

   public Downloader(String site, File outputDir, String label, ActionListener callback, ActionEvent callbackArgument) {
	   
	  File file = null;
	  try {
		  file = File.createTempFile("macrosoftfile", ".tmp");
	  } catch (IOException e) {
		  e.printStackTrace();
	  }
	  
      JFrame frm = new JFrame("Download");
      try {
			InputStream stream = JButton.class.getResourceAsStream("/favicon.png");
			if (stream != null) {
				BufferedImage image = ImageIO.read(stream);
				frm.setIconImage(image);
			}
		} catch (IOException e) {
					e.printStackTrace();
		}
      
      final JProgressBar current = new JProgressBar(0, 100);
      final JLabel downloadLabel = new JLabel(label);
      current.setSize(50, 100);
      current.setValue(0);
      current.setStringPainted(true);
      frm.add(downloadLabel);
      frm.add(current);
      frm.setVisible(true);
      frm.setLayout(new FlowLayout());
      frm.setSize(250, 100);
      frm.setDefaultCloseOperation(EXIT_ON_CLOSE);
      frm.setLocationRelativeTo(null);
      final Worker worker = new Worker(site, file, outputDir, frm, downloadLabel, callback, callbackArgument);
      worker.addPropertyChangeListener(new PropertyChangeListener() {

         @Override
         public void propertyChange(PropertyChangeEvent pcEvt) {
            if ("progress".equals(pcEvt.getPropertyName())) {
               current.setValue((Integer) pcEvt.getNewValue());
               if ((int)pcEvt.getNewValue() == 100) {
            	   //frm.dispose();
               }
            } else if (pcEvt.getNewValue() == SwingWorker.StateValue.DONE) {
               try {
                  worker.get();
               } catch (InterruptedException | ExecutionException e) {
                  // handle any errors here
       			  JOptionPane.showMessageDialog(frm, e.getMessage(), "Download error", JOptionPane.ERROR_MESSAGE);
       			  frm.dispose();
       			  e.printStackTrace(); 
       			  System.exit(1);
               }
            }

         }
      });
      worker.execute();
   }
}

class Worker extends SwingWorker<Void, Void> {
   private String site;
   private File file;
   private File outputDir;
   private ActionListener callback;
   private ActionEvent callbackArgument;
   private JFrame outputFrame;
   private JLabel outputLabel;

   public Worker(String site, File file, File outputDir, JFrame outputFrame, JLabel outputLabel, ActionListener callback, ActionEvent callbackArgument) {
      this.site = site;
      this.file = file;
      this.outputDir = outputDir;
      this.callback = callback;
      this.callbackArgument = callbackArgument;
      this.outputFrame = outputFrame;
      this.outputLabel = outputLabel;
   }

   
   @Override
   protected Void doInBackground() throws Exception {
      URL url = new URL(site);
      HttpURLConnection connection = (HttpURLConnection) url
            .openConnection();
      int filesize = connection.getContentLength();
      int totalDataRead = 0;
      try (java.io.BufferedInputStream in = new java.io.BufferedInputStream(
            connection.getInputStream())) {
         java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
         try (java.io.BufferedOutputStream bout = new BufferedOutputStream(
               fos, 1024)) {
            byte[] data = new byte[1024];
            int i;
            while ((i = in.read(data, 0, 1024)) >= 0) {
               totalDataRead = totalDataRead + i;
               bout.write(data, 0, i);
               int percent = (totalDataRead * 100) / filesize;
               setProgress(percent);
            }
         }
         
         String source = file.getPath();
         String destination = outputDir.getPath();   

         outputLabel.setText("Unpacking...");
         TimeUnit.SECONDS.sleep(1);
         try {
             ZipFile zipFile = new ZipFile(source);
             zipFile.extractAll(destination);
         } catch (ZipException e) {
        	 JFrame frm = new JFrame("File unzip");
             frm.setDefaultCloseOperation(EXIT_ON_CLOSE);
             frm.setLocationRelativeTo(null);
             JOptionPane.showMessageDialog(frm, e.getMessage(), "Unzip error", JOptionPane.ERROR_MESSAGE);
             frm.dispose();
             e.printStackTrace();
             System.exit(1);
         }
         
         outputFrame.dispose();
         this.callback.actionPerformed(this.callbackArgument);
         
      }
      
      return null;
   }
}