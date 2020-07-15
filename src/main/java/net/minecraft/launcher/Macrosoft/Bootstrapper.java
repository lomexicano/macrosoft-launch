package net.minecraft.launcher.Macrosoft;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;

import com.google.gson.JsonArray;

import net.minecraft.launcher.LauncherConstants;
import net.minecraft.launcher.Macrosoft.ui.LaunchButton;

public class Bootstrapper {

	JFrame frame;
	JPanel panel;
	JButton b1;
	ArrayList<JButton> contexts = new ArrayList<JButton>();
	
	public Bootstrapper() {
		frame = new JFrame("Macrosoft");
		frame.setSize(200,200);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		try {
			JSONObject info = Connector.get("http://127.0.0.1:8000/launcher/info?format=json");
			int version = (int)info.get("version");
			if (0 <= LauncherConstants.MACROSOFT_VERSION) {
				JOptionPane.showMessageDialog(frame, "Your laucher is out-dated. Upgrade to version " + version, "Outdated", JOptionPane.WARNING_MESSAGE);
			}
			JSONArray servers = (JSONArray)info.get("servers");
			System.out.println(version);
			System.out.println(servers);
			System.out.println(info);
			for (Object object : servers) {
				contexts.add(new JButton((String)object));
			}
		} catch(IOException e) {
			System.out.println(e.getMessage());
		} catch (JSONException e) {
			System.out.println(e.getMessage());
		}
	
		//contexts.add(new JButton("Legacy"));
		//contexts.add(new JButton("Chronos"));
		//contexts.add(new JButton("Viciante"));
		//contexts.add(new JButton("Outro"));
		
		panel = new JPanel();
		
		for (JButton jButton : contexts) {
			panel.add(jButton);
		}
		
		frame.add(panel);
		
	}
	
	public void run(ActionListener listener) {
		
		for (JButton jButton : contexts) {
			jButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					frame.setVisible(false);
					listener.actionPerformed(e);
					frame.dispose();
				}
			});
		}
	
		frame.setVisible(true);
	}

} 