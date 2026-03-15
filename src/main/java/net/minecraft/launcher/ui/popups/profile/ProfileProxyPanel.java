package net.minecraft.launcher.ui.popups.profile;

import net.minecraft.launcher.Launcher;
import net.minecraft.launcher.profile.Profile;
import net.minecraft.launcher.utils.CryptoUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class ProfileProxyPanel extends JPanel {
    private final Launcher          minecraftLauncher;
    private final ProfileEditorPopup editor;
    private final Profile           profile;

    private final JCheckBox    proxyEnabledCheckbox = new JCheckBox("Habilitar Proxy para este Perfil");
    // Tipo de proxy fixo em SOCKS — não editável pelo usuário
    private final JLabel       proxyTypeValueLabel  = new JLabel("SOCKS");
    private final JLabel       proxyHostLabel       = new JLabel("Endereço (Host):");
    private final JTextField   proxyHostField       = new JTextField();
    private final JLabel       proxyPortLabel       = new JLabel("Porta:");
    private final JTextField   proxyPortField       = new JTextField();
    private final JLabel       proxyUserLabel       = new JLabel("Usuário (Opcional):");
    private final JTextField   proxyUserField       = new JTextField();
    private final JLabel       proxyPasswordLabel   = new JLabel("Senha (Opcional):");
    private final JPasswordField proxyPasswordField = new JPasswordField();

    /** Componentes que ficam habilitados/desabilitados junto com o checkbox. */
    private final Component[] proxyComponents;

    public ProfileProxyPanel(ProfileEditorPopup editor) {
        this.editor           = editor;
        this.profile          = editor.getProfile();
        this.minecraftLauncher = editor.getMinecraftLauncher();

        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("Configurações de Proxy"));

        proxyComponents = new Component[]{
            proxyTypeValueLabel,
            proxyHostLabel, proxyHostField,
            proxyPortLabel, proxyPortField,
            proxyUserLabel, proxyUserField,
            proxyPasswordLabel, proxyPasswordField
        };

        createInterface();
        fillValuesFromProfile();
        addEventHandlers();
        updateFieldsEnabledState();
    }

    private void createInterface() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(2, 2, 2, 2);
        gbc.anchor  = GridBagConstraints.WEST;
        gbc.fill    = GridBagConstraints.HORIZONTAL;

        // Checkbox habilitar proxy
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        add(proxyEnabledCheckbox, gbc);

        // Tipo de proxy (fixo: SOCKS)
        gbc.gridy++; gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.weightx = 0.0;
        add(new JLabel("Tipo de Proxy:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        proxyTypeValueLabel.setFont(proxyTypeValueLabel.getFont().deriveFont(Font.BOLD));
        add(proxyTypeValueLabel, gbc);

        // Host
        gbc.gridy++;
        gbc.gridx = 0; gbc.weightx = 0.0; add(proxyHostLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; add(proxyHostField, gbc);

        // Porta
        gbc.gridy++;
        gbc.gridx = 0; gbc.weightx = 0.0; add(proxyPortLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; add(proxyPortField, gbc);

        // Usuário
        gbc.gridy++;
        gbc.gridx = 0; gbc.weightx = 0.0; add(proxyUserLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; add(proxyUserField, gbc);

        // Senha
        gbc.gridy++;
        gbc.gridx = 0; gbc.weightx = 0.0; add(proxyPasswordLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; add(proxyPasswordField, gbc);
    }

    private void fillValuesFromProfile() {
        proxyEnabledCheckbox.setSelected(profile.isProxyEnabled());
        // Sempre garante SOCKS no perfil (tipo fixo)
        profile.setProxyType(Profile.ProxyType.SOCKS);
        proxyHostField.setText(profile.getProxyHost() != null ? profile.getProxyHost() : "");
        proxyPortField.setText(profile.getProxyPort() > 0 ? String.valueOf(profile.getProxyPort()) : "");
        proxyUserField.setText(profile.getProxyUser() != null ? profile.getProxyUser() : "");

        String decryptedPassword = CryptoUtils.decrypt(profile.getProxyPassword(), minecraftLauncher);
        proxyPasswordField.setText(decryptedPassword != null ? decryptedPassword : "");
    }

    private void addEventHandlers() {
        proxyEnabledCheckbox.addItemListener(e -> {
            profile.setProxyEnabled(proxyEnabledCheckbox.isSelected());
            updateFieldsEnabledState();
        });

        proxyHostField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { profile.setProxyHost(proxyHostField.getText().trim()); }
            @Override public void removeUpdate(DocumentEvent e)  { profile.setProxyHost(proxyHostField.getText().trim()); }
            @Override public void changedUpdate(DocumentEvent e) { profile.setProxyHost(proxyHostField.getText().trim()); }
        });

        proxyPortField.getDocument().addDocumentListener(new DocumentListener() {
            private void updatePort() {
                try {
                    String t = proxyPortField.getText().trim();
                    profile.setProxyPort(t.isEmpty() ? 0 : Integer.parseInt(t));
                } catch (NumberFormatException ex) { profile.setProxyPort(0); }
            }
            @Override public void insertUpdate(DocumentEvent e)  { updatePort(); }
            @Override public void removeUpdate(DocumentEvent e)  { updatePort(); }
            @Override public void changedUpdate(DocumentEvent e) { updatePort(); }
        });

        proxyUserField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { profile.setProxyUser(proxyUserField.getText().trim()); }
            @Override public void removeUpdate(DocumentEvent e)  { profile.setProxyUser(proxyUserField.getText().trim()); }
            @Override public void changedUpdate(DocumentEvent e) { profile.setProxyUser(proxyUserField.getText().trim()); }
        });

        proxyPasswordField.getDocument().addDocumentListener(new DocumentListener() {
            private void updatePass() {
                String plain = new String(proxyPasswordField.getPassword());
                if (plain.isEmpty()) {
                    profile.setProxyPassword(null);
                } else {
                    profile.setProxyPassword(CryptoUtils.encrypt(plain, minecraftLauncher));
                }
            }
            @Override public void insertUpdate(DocumentEvent e)  { updatePass(); }
            @Override public void removeUpdate(DocumentEvent e)  { updatePass(); }
            @Override public void changedUpdate(DocumentEvent e) { updatePass(); }
        });
    }

    private void updateFieldsEnabledState() {
        boolean enabled = proxyEnabledCheckbox.isSelected();
        for (Component comp : proxyComponents) comp.setEnabled(enabled);
    }
}