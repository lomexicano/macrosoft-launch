package net.minecraft.launcher.Macrosoft;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;

import com.google.gson.JsonArray;

import net.minecraft.launcher.LauncherConstants;
import net.minecraft.launcher.Macrosoft.ui.LaunchButton;
import net.minecraft.launcher.ui.popups.login.LogInPopup;

public class Bootstrapper {

	JFrame frame;
	JPanel panel;
	JButton b1;
	File workingDir;
	ArrayList<JButton> contexts = new ArrayList<JButton>();
	Map<String, String> modpackLinks = new HashMap<String, String>();
	Map<String, String> modpackAuthors = new HashMap<String, String>();
	
	public Bootstrapper(File workingDir) {
		
		System.out.println("Loading Macrosoft Bootstrapper...");
		
		this.workingDir = workingDir;
		
		int frameHeight = 130;
		String websiteLink = "https://webmacrosoft.herokuapp.com/";
		String discordLink = "https://discord.gg/t7WcjJ4";
		
		frame = new JFrame("Macrosoft Launcher v" + LauncherConstants.MACROSOFT_VERSION);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		try {
			JSONObject info = Connector.get("https://webmacrosoft.herokuapp.com/launcher/info?format=json");
			int version = (int)info.get("version");
			frameHeight = (int)info.get("menuHeight");
			websiteLink = (String)info.get("site");
			discordLink = (String)info.get("discord");
			
			JSONArray servers = (JSONArray)info.get("servers");
			for (Object object : servers) {
							
				String name = (String)((JSONObject)object).get("name");
				String linkModPack = (String)((JSONObject)object).get("modpack_zip");
				String authorModpack = (String)((JSONObject)object).get("modpack_author");
				JButton button = new JButton(name);
				
				modpackLinks.put(name, linkModPack);
				modpackAuthors.put(name, authorModpack);
				
				String iconURL = (String)((JSONObject)object).get("icon");
				try {
					InputStream stream = new URL(iconURL).openStream();
			        if (stream != null) {
			            BufferedImage image = ImageIO.read(stream);
			            Image resized = new ImageIcon(image).getImage().getScaledInstance(15, 15, java.awt.Image.SCALE_SMOOTH);
			            button.setIcon(new ImageIcon(resized));
			        }
				} catch (IOException e) {
					System.out.println("Unable to load icon from " + iconURL);
				}
				
				contexts.add(button);
			}
			
			if (version > LauncherConstants.MACROSOFT_VERSION) {
				JOptionPane.showMessageDialog(frame, "Your launcher is out-dated. Upgrade to version " + version + ". Access our website or Discord channel", "Outdated", JOptionPane.WARNING_MESSAGE);
			}
			
		} catch(IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(frame, "Could not stablish connection with Macrosoft Server", "Error connection", JOptionPane.ERROR_MESSAGE);
		} catch (JSONException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(frame, "Could not parse response from Macrosoft Server", "Server issue", JOptionPane.ERROR_MESSAGE);
		}
		
		frame.setSize(new Dimension(300,frameHeight));
	
		panel = new JPanel();

		JButton defaultButton = new JButton("Other");
		
		try {
			InputStream stream = JButton.class.getResourceAsStream("/minecraft_logo.png");
	        if (stream != null) {
	            BufferedImage image = ImageIO.read(stream);
	            JLabel label = new JLabel(new ImageIcon(image));
                panel.add(label);
	        }
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			InputStream stream = JButton.class.getResourceAsStream("/mc.png");
	        if (stream != null) {
	            BufferedImage image = ImageIO.read(stream);
	            Image resized = new ImageIcon(image).getImage().getScaledInstance(15, 15, java.awt.Image.SCALE_SMOOTH);
	            defaultButton.setIcon(new ImageIcon(resized));
	        }
		} catch (IOException e) {
			e.printStackTrace();
		}
					
		contexts.add(defaultButton);
		
		for (JButton jButton : contexts) {
			panel.add(jButton);
		}
		
		JButton link = new JButton("Website");
		link.setBorderPainted(false);
		
		String site = websiteLink;
		String discord = discordLink;
		
		link.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		    	try {
		    		java.awt.Desktop.getDesktop().browse(java.net.URI.create(site));
		    	} catch (IOException ex) {
		    		ex.printStackTrace();
		    	}
	        }
		});
		panel.add(link);
		
		JButton linkSocial = new JButton("Discord");
		linkSocial.setBorderPainted(false);
		
		linkSocial.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		    	try {
		    		java.awt.Desktop.getDesktop().browse(java.net.URI.create(discord));
		    	} catch (IOException ex) {
		    		ex.printStackTrace();
		    	}
	        }
		});
		panel.add(linkSocial);
		
		try {
			InputStream stream = JButton.class.getResourceAsStream("/favicon.png");
	        if (stream != null) {
	            BufferedImage image = ImageIO.read(stream);
	            frame.setIconImage(image);
	        }
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		frame.setLocationRelativeTo(null);
		frame.add(panel);
		
	}
	
	public void run(ActionListener onload, ActionListener listener) {
		
		for (JButton jButton : contexts) {
			jButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					frame.setVisible(false);
					
					String context = ((JButton)e.getSource()).getText();
					
					String resourceLink = modpackLinks.get(context);
					String authorModPack = modpackAuthors.get(context);	
					
					File contextDir = new File(workingDir, context + "/");
					if((!contextDir.exists()) && (resourceLink != null) && (!resourceLink.isEmpty())) {
						System.out.println("Downloading modpack...");
						new Downloader(resourceLink, contextDir, "Downloading " + context + " modpack by <i>" + authorModPack + "</i>...", listener, e);
						frame.dispose();
					} else {
						if((resourceLink != null) && (resourceLink.isEmpty())) {
							System.out.println("No modpack source defined");
						}
						System.out.println("Modpack download skipped");
						listener.actionPerformed(e);
					}
					
				}
			});
		}
		
		onload.actionPerformed(null);
	
		frame.setVisible(true);
	}

} 