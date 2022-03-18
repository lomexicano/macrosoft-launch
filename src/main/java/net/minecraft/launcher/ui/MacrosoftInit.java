package net.minecraft.launcher.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class MacrosoftInit extends JFrame {
	
	public MacrosoftInit(String title) {
		
		super(title);
		
		try {
			//InputStream stream = JButton.class.getResourceAsStream("/favicon.png");
			BufferedImage image = ImageIO.read(this.getClass().getResource("/favicon.png"));
	        if (image != null) {
	            this.setIconImage(image);
	        }
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		this.setSize(250,90);
		
		JPanel panel = new JPanel();
		JLabel msg = new JLabel("Initializing Macrosoft Launcher");
		panel.add(msg);
		this.add(panel);
		
		this.setVisible(true);
		this.setLocationRelativeTo(null);
		
	}
}
