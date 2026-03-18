package net.minecraft.launcher.ui.popups.profile;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import net.minecraft.launcher.profile.Profile;
import net.minecraft.launcher.ui.popups.profile.ProfileEditorPopup;

public class ProfileInfoPanel
extends JPanel {
    private final ProfileEditorPopup editor;
    private final JCheckBox gameDirCustom    = new JCheckBox("Diretório do Jogo:");
    private final JTextField profileName     = new JTextField();
    private final JTextField gameDirField    = new JTextField();
    private final JCheckBox resolutionCustom = new JCheckBox("Resolução:");
    private final JTextField resolutionWidth  = new JTextField();
    private final JTextField resolutionHeight = new JTextField();
    private final JTextField nicknameField    = new JTextField();

    public ProfileInfoPanel(ProfileEditorPopup editor) {
        this.editor = editor;
        this.setLayout(new GridBagLayout());
        this.setBorder(BorderFactory.createTitledBorder("Informações do Perfil"));
        this.createInterface();
        this.fillDefaultValues();
        this.addEventHandlers();
    }

    protected void createInterface() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.anchor = 17;
        constraints.gridy = 0;

        // Nome do Perfil
        this.add((Component) new JLabel("Nome do Perfil:"), constraints);
        constraints.fill = 2;
        constraints.weightx = 1.0;
        this.add((Component) this.profileName, constraints);
        constraints.weightx = 0.0;
        constraints.fill = 0;
        ++constraints.gridy;

        // Nickname (por modpack)
        this.add((Component) new JLabel("Nickname (Minecraft):"), constraints);
        constraints.fill = 2;
        constraints.weightx = 1.0;
        this.nicknameField.setToolTipText("Nick exibido no servidor para este modpack. Deixe vazio para usar o nick do login.");
        this.add((Component) this.nicknameField, constraints);
        constraints.weightx = 0.0;
        constraints.fill = 0;
        ++constraints.gridy;

        // Diretório do Jogo
        this.add((Component) this.gameDirCustom, constraints);
        constraints.fill = 2;
        constraints.weightx = 1.0;
        this.add((Component) this.gameDirField, constraints);
        constraints.weightx = 0.0;
        constraints.fill = 0;
        ++constraints.gridy;

        // Resolução
        JPanel resolutionPanel = new JPanel();
        resolutionPanel.setLayout(new BoxLayout(resolutionPanel, 0));
        resolutionPanel.add(this.resolutionWidth);
        resolutionPanel.add(Box.createHorizontalStrut(5));
        resolutionPanel.add(new JLabel("x"));
        resolutionPanel.add(Box.createHorizontalStrut(5));
        resolutionPanel.add(this.resolutionHeight);
        this.add((Component) this.resolutionCustom, constraints);
        constraints.fill = 2;
        constraints.weightx = 1.0;
        this.add((Component) resolutionPanel, constraints);
        constraints.weightx = 0.0;
        constraints.fill = 0;
        ++constraints.gridy;
    }

    protected void fillDefaultValues() {
        this.profileName.setText(this.editor.getProfile().getName());
        String nick = this.editor.getProfile().getNickname();
        this.nicknameField.setText(nick != null ? nick : "");
        File gameDir = this.editor.getProfile().getGameDir();
        if (gameDir != null) {
            this.gameDirCustom.setSelected(true);
            this.gameDirField.setText(gameDir.getAbsolutePath());
        } else {
            this.gameDirCustom.setSelected(false);
            this.gameDirField.setText(
                this.editor.getMinecraftLauncher().getLauncher().getWorkingDirectory().getAbsolutePath());
        }
        this.updateGameDirState();

        Profile.Resolution resolution = this.editor.getProfile().getResolution();
        this.resolutionCustom.setSelected(resolution != null);
        if (resolution == null) resolution = Profile.DEFAULT_RESOLUTION;
        this.resolutionWidth.setText(String.valueOf(resolution.getWidth()));
        this.resolutionHeight.setText(String.valueOf(resolution.getHeight()));
        this.updateResolutionState();
    }

    protected void addEventHandlers() {
        this.profileName.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { updateProfileName(); }
            @Override public void removeUpdate(DocumentEvent e)  { updateProfileName(); }
            @Override public void changedUpdate(DocumentEvent e) { updateProfileName(); }
        });

        this.nicknameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { updateNickname(); }
            @Override public void removeUpdate(DocumentEvent e)  { updateNickname(); }
            @Override public void changedUpdate(DocumentEvent e) { updateNickname(); }
        });

        this.gameDirCustom.addItemListener(e -> updateGameDirState());

        this.gameDirField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { updateGameDir(); }
            @Override public void removeUpdate(DocumentEvent e)  { updateGameDir(); }
            @Override public void changedUpdate(DocumentEvent e) { updateGameDir(); }
        });

        this.resolutionCustom.addItemListener(e -> updateResolutionState());

        DocumentListener resolutionListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { updateResolution(); }
            @Override public void removeUpdate(DocumentEvent e)  { updateResolution(); }
            @Override public void changedUpdate(DocumentEvent e) { updateResolution(); }
        };
        this.resolutionWidth.getDocument().addDocumentListener(resolutionListener);
        this.resolutionHeight.getDocument().addDocumentListener(resolutionListener);
    }

    private void updateProfileName() {
        if (this.profileName.getText().length() > 0)
            this.editor.getProfile().setName(this.profileName.getText());
    }

    private void updateNickname() {
        String nick = this.nicknameField.getText().trim();
        this.editor.getProfile().setNickname(nick.isEmpty() ? null : nick);
    }

    private void updateGameDirState() {
        if (this.gameDirCustom.isSelected()) {
            this.gameDirField.setEnabled(true);
            this.editor.getProfile().setGameDir(new File(this.gameDirField.getText()));
        } else {
            this.gameDirField.setEnabled(false);
            this.editor.getProfile().setGameDir(null);
        }
    }

    private void updateGameDir() {
        this.editor.getProfile().setGameDir(new File(this.gameDirField.getText()));
    }

    private void updateResolutionState() {
        if (this.resolutionCustom.isSelected()) {
            this.resolutionWidth.setEnabled(true);
            this.resolutionHeight.setEnabled(true);
            this.updateResolution();
        } else {
            this.resolutionWidth.setEnabled(false);
            this.resolutionHeight.setEnabled(false);
            this.editor.getProfile().setResolution(null);
        }
    }

    private void updateResolution() {
        try {
            int width  = Integer.parseInt(this.resolutionWidth.getText());
            int height = Integer.parseInt(this.resolutionHeight.getText());
            this.editor.getProfile().setResolution(new Profile.Resolution(width, height));
        } catch (NumberFormatException ignored) {
            this.editor.getProfile().setResolution(null);
        }
    }
}
