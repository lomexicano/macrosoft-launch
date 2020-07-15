package net.minecraft.launcher.ui.popups.login;

import com.mojang.launcher.OperatingSystem;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import net.minecraft.launcher.Launcher;
import net.minecraft.launcher.LauncherConstants;
import net.minecraft.launcher.profile.AuthenticationDatabase;
import net.minecraft.launcher.profile.ProfileManager;
import net.minecraft.launcher.ui.popups.login.AuthErrorForm;
import net.minecraft.launcher.ui.popups.login.ExistingUserListForm;
import net.minecraft.launcher.ui.popups.login.LogInForm;
import net.minecraft.launcher.ui.popups.login.LogInFormMacrosoft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LogInPopup extends JPanel implements ActionListener {
	
	private static final Logger LOGGER = LogManager.getLogger();
    private final Launcher minecraftLauncher;
    private final Callback callback;
    private final AuthErrorForm errorForm;
    private final ExistingUserListForm existingUserListForm;
    private final LogInFormMacrosoft logInForm;
    private final LogInForm logInFormMojang;
    //private final LogInForm logInForm;
    private final JButton loginButton = new JButton("Log In");
    private final JButton loginMojangButton = new JButton("Log In with Mojang");
    private final JButton registerButton = new JButton("Register");
    private final JButton mojangButton = new JButton("Mojang Account");
    private final JProgressBar progressBar = new JProgressBar();
    private JPanel buttonPanel = new JPanel();

    public LogInPopup(Launcher minecraftLauncher, Callback callback) {
        super(true);
        this.minecraftLauncher = minecraftLauncher;
        this.callback = callback;
        this.errorForm = new AuthErrorForm(this);
        this.existingUserListForm = new ExistingUserListForm(this);
        this.logInForm = new LogInFormMacrosoft(this);
        this.logInFormMojang = new LogInForm(this);
        this.logInFormMojang.setVisible(false);
        //this.logInForm = new LogInForm(this);
        this.createInterface();
        
        try {
			InputStream stream = JButton.class.getResourceAsStream("/mojang.png");
	        if (stream != null) {
	            BufferedImage image = ImageIO.read(stream);
	            Image resized = new ImageIcon(image).getImage().getScaledInstance(15, 15, java.awt.Image.SCALE_SMOOTH);
	            mojangButton.setIcon(new ImageIcon(resized));
	        }
		} catch (IOException e) {
			e.printStackTrace();
		}
        
        this.mojangButton.addActionListener(this);
        this.loginButton.addActionListener(this);
        this.registerButton.addActionListener(this);
        this.loginMojangButton.addActionListener(this);
       
    }

    protected void createInterface() {
        this.setLayout(new BoxLayout(this, 1));
        this.setBorder(new EmptyBorder(5, 15, 5, 15));
        try {
            InputStream stream = LogInPopup.class.getResourceAsStream("/minecraft_logo.png");
            if (stream != null) {
                BufferedImage image = ImageIO.read(stream);
                JLabel label = new JLabel(new ImageIcon(image));
                JPanel imagePanel = new JPanel();
                imagePanel.add(label);
                this.add(imagePanel);
                this.add(Box.createVerticalStrut(10));
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        /*if (!this.minecraftLauncher.getProfileManager().getAuthDatabase().getKnownNames().isEmpty()) {
            this.add(this.existingUserListForm);
        }*/
        this.add(this.errorForm);
        this.add(this.logInForm);
        this.add(this.logInFormMojang);
        this.add(Box.createVerticalStrut(15));
        
        buttonPanel.setLayout(new GridLayout(1, 2, 10, 0));
        buttonPanel.add(this.mojangButton);
        buttonPanel.add(this.loginButton);
        this.add(buttonPanel);
        this.progressBar.setIndeterminate(true);
        this.progressBar.setVisible(false);
        this.add(this.progressBar);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == this.loginButton) {
            this.logInForm.tryLogIn();
        } else if (e.getSource() == this.mojangButton) {
        	this.logInForm.setVisible(false);
        	this.logInFormMojang.setVisible(true);
        	buttonPanel.add(this.registerButton);
        	buttonPanel.add(this.loginMojangButton);
        	buttonPanel.remove(this.loginButton);
        	buttonPanel.remove(this.mojangButton);
        } else if (e.getSource() == this.registerButton) {
        	OperatingSystem.openLink(LauncherConstants.URL_REGISTER);
        } else if (e.getSource() == this.loginMojangButton) {
        	this.logInFormMojang.tryLogIn();
        }
    }

    public Launcher getMinecraftLauncher() {
        return this.minecraftLauncher;
    }

    public void setCanLogIn(final boolean enabled) {
        if (SwingUtilities.isEventDispatchThread()) {
            this.loginButton.setEnabled(enabled);
            this.progressBar.setIndeterminate(false);
            this.progressBar.setIndeterminate(true);
            this.progressBar.setVisible(!enabled);
            this.repack();
        } else {
            SwingUtilities.invokeLater(new Runnable(){

                @Override
                public void run() {
                    LogInPopup.this.setCanLogIn(enabled);
                }
            });
        }
    }

    public LogInFormMacrosoft getLogInForm() {
    //public LogInForm getLogInForm() {
        return this.logInForm;
    }

    public AuthErrorForm getErrorForm() {
        return this.errorForm;
    }

    public ExistingUserListForm getExistingUserListForm() {
        return this.existingUserListForm;
    }

    public void setLoggedIn(String uuid) {
        this.callback.onLogIn(uuid);
    }

    public void repack() {
        Window window = SwingUtilities.windowForComponent(this);
        if (window != null) {
            window.pack();
        }
    }

    public static interface Callback {
        public void onLogIn(String var1);
    }

}

