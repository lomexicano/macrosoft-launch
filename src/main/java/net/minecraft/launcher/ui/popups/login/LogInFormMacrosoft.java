package net.minecraft.launcher.ui.popups.login;

import com.mojang.authlib.Agent;
import com.mojang.authlib.AuthenticationService;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.UserAuthentication;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.exceptions.UserMigratedException;
import com.mojang.launcher.OperatingSystem;
import com.mojang.launcher.updater.VersionManager;
import com.mojang.util.UUIDTypeAdapter;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.minecraft.launcher.Launcher;
import net.minecraft.launcher.LauncherConstants;
import net.minecraft.launcher.profile.AuthenticationDatabase;
import net.minecraft.launcher.profile.ProfileManager;
import net.minecraft.launcher.ui.popups.login.AuthErrorForm;
import net.minecraft.launcher.ui.popups.login.LogInPopup;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LogInFormMacrosoft extends JPanel implements ActionListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private final LogInPopup popup;
    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JComboBox userDropdown = new JComboBox();
    private final JPanel userDropdownPanel = new JPanel();
    //private final UserAuthentication authentication;
    private final MacrosoftMockAuth authentication;

    public LogInFormMacrosoft(LogInPopup popup) {
        this.popup = popup;
        //this.authentication = popup.getMinecraftLauncher().getProfileManager().getAuthDatabase().getAuthenticationService().createUserAuthentication(Agent.MINECRAFT);
        this.authentication = new MacrosoftMockAuth(popup.getMinecraftLauncher().getProfileManager().getAuthDatabase().getAuthenticationService());
        this.usernameField.addActionListener(this);
        this.passwordField.addActionListener(this);
        this.createInterface();
    }

    protected void createInterface() {
        this.setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = 2;
        constraints.gridx = 0;
        constraints.gridy = -1;
        constraints.weightx = 1.0;
        this.add(Box.createGlue());
        JLabel usernameLabel = new JLabel("Nick");
        Font labelFont = usernameLabel.getFont().deriveFont(1);
        Font smalltextFont = usernameLabel.getFont().deriveFont((float)labelFont.getSize() - 2.0f);
        usernameLabel.setFont(labelFont);
        this.add((Component)usernameLabel, constraints);
        this.add((Component)this.usernameField, constraints);
        this.add(Box.createVerticalStrut(10), constraints);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == this.usernameField || e.getSource() == this.passwordField) {
            this.tryLogIn();
        }
    }

    public void tryLogIn() {
        if (this.authentication.isLoggedIn() && this.authentication.getSelectedProfile() == null && ArrayUtils.isNotEmpty(this.authentication.getAvailableProfiles())) {
            this.popup.setCanLogIn(false);
            GameProfile selectedProfile = null;
            for (GameProfile profile : this.authentication.getAvailableProfiles()) {
                if (!profile.getName().equals(this.userDropdown.getSelectedItem())) continue;
                selectedProfile = profile;
                break;
            }
            if (selectedProfile == null) {
                selectedProfile = this.authentication.getAvailableProfiles()[0];
            }
            final GameProfile finalSelectedProfile = selectedProfile;
            this.popup.getMinecraftLauncher().getLauncher().getVersionManager().getExecutorService().execute(new Runnable(){

                @Override
                public void run() {
                    try {
                        LogInFormMacrosoft.this.authentication.selectGameProfile(finalSelectedProfile);
                        LogInFormMacrosoft.this.popup.getMinecraftLauncher().getProfileManager().getAuthDatabase().register(UUIDTypeAdapter.fromUUID(LogInFormMacrosoft.this.authentication.getSelectedProfile().getId()), LogInFormMacrosoft.this.authentication);
                        LogInFormMacrosoft.this.popup.setLoggedIn(UUIDTypeAdapter.fromUUID(LogInFormMacrosoft.this.authentication.getSelectedProfile().getId()));
                    }
                    catch (InvalidCredentialsException ex) {
                        LOGGER.error("Couldn't log in", (Throwable)ex);
                        LogInFormMacrosoft.this.popup.getErrorForm().displayError(ex, "Sorry, but we couldn't log you in right now.", "Please try again later.");
                        LogInFormMacrosoft.this.popup.setCanLogIn(true);
                    }
                    catch (AuthenticationException ex) {
                        LOGGER.error("Couldn't log in", (Throwable)ex);
                        LogInFormMacrosoft.this.popup.getErrorForm().displayError(ex, "Sorry, but we couldn't connect to our servers.", "Please make sure that you are online and that Minecraft is not blocked.");
                        LogInFormMacrosoft.this.popup.setCanLogIn(true);
                    }
                }
            });
        } else {
            this.popup.setCanLogIn(false);
            this.authentication.logOut();
            this.authentication.setUsername(this.usernameField.getText());
            this.authentication.setPassword(String.valueOf(this.passwordField.getPassword()));
            final int passwordLength = this.passwordField.getPassword().length;
            this.passwordField.setText("");
            this.popup.getMinecraftLauncher().getLauncher().getVersionManager().getExecutorService().execute(new Runnable(){

                @Override
                public void run() {
                    try {
                        LogInFormMacrosoft.this.authentication.logIn();
                        AuthenticationDatabase authDatabase = LogInFormMacrosoft.this.popup.getMinecraftLauncher().getProfileManager().getAuthDatabase();
                        if (LogInFormMacrosoft.this.authentication.getSelectedProfile() == null) {
                            if (ArrayUtils.isNotEmpty(LogInFormMacrosoft.this.authentication.getAvailableProfiles())) {
                                for (GameProfile profile : LogInFormMacrosoft.this.authentication.getAvailableProfiles()) {
                                    LogInFormMacrosoft.this.userDropdown.addItem(profile.getName());
                                }
                                SwingUtilities.invokeLater(new Runnable(){

                                    @Override
                                    public void run() {
                                        LogInFormMacrosoft.this.usernameField.setEditable(false);
                                        LogInFormMacrosoft.this.passwordField.setEditable(false);
                                        LogInFormMacrosoft.this.userDropdownPanel.setVisible(true);
                                        LogInFormMacrosoft.this.popup.repack();
                                        LogInFormMacrosoft.this.popup.setCanLogIn(true);
                                        LogInFormMacrosoft.this.passwordField.setText(StringUtils.repeat('*', passwordLength));
                                    }
                                });
                            } else {
                                String uuid = "demo-" + LogInFormMacrosoft.this.authentication.getUserID();
                                authDatabase.register(uuid, LogInFormMacrosoft.this.authentication);
                                LogInFormMacrosoft.this.popup.setLoggedIn(uuid);
                            }
                        } else {
                            authDatabase.register(UUIDTypeAdapter.fromUUID(LogInFormMacrosoft.this.authentication.getSelectedProfile().getId()), LogInFormMacrosoft.this.authentication);
                            LogInFormMacrosoft.this.popup.setLoggedIn(UUIDTypeAdapter.fromUUID(LogInFormMacrosoft.this.authentication.getSelectedProfile().getId()));
                        }
                    }
                    catch (UserMigratedException ex) {
                        LOGGER.error("Couldn't log in", (Throwable)ex);
                        LogInFormMacrosoft.this.popup.getErrorForm().displayError(ex, "Sorry, but we can't log you in with your username.", "You have migrated your account, please use your email address.");
                        LogInFormMacrosoft.this.popup.setCanLogIn(true);
                    }
                    catch (InvalidCredentialsException ex) {
                        LOGGER.error("Couldn't log in", (Throwable)ex);
                        LogInFormMacrosoft.this.popup.getErrorForm().displayError(ex, "Sorry, but your username or password is incorrect!", "Please try again. If you need help, try the 'Forgot Password' link.");
                        LogInFormMacrosoft.this.popup.setCanLogIn(true);
                    }
                    catch (AuthenticationException ex) {
                        LOGGER.error("Couldn't log in", (Throwable)ex);
                        LogInFormMacrosoft.this.popup.getErrorForm().displayError(ex, "Sorry, but we couldn't connect to our servers.", "Please make sure that you are online and that Minecraft is not blocked.");
                        LogInFormMacrosoft.this.popup.setCanLogIn(true);
                    }
                }

            });
        }
    }

}

