package net.minecraft.launcher.ui.popups.profile;

import net.minecraft.launcher.profile.Profile;
import net.minecraft.launcher.profile.Profile.ProxyType;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class ProfileProxyPanel extends JPanel {
    private final ProfileEditorPopup editor;
    private final Profile profile;

    private final JCheckBox proxyEnabledCheckbox = new JCheckBox("Habilitar Proxy para este Perfil");
    private final JLabel proxyTypeLabel = new JLabel("Tipo de Proxy:");
    private final JComboBox<Profile.ProxyType> proxyTypeComboBox = new JComboBox<>(Profile.ProxyType.values());
    private final JLabel proxyHostLabel = new JLabel("Endereço (Host):");
    private final JTextField proxyHostField = new JTextField();
    private final JLabel proxyPortLabel = new JLabel("Porta:");
    private final JTextField proxyPortField = new JTextField(); // Pode ser JSpinner para números
    private final JLabel proxyUserLabel = new JLabel("Usuário (Opcional):");
    private final JTextField proxyUserField = new JTextField();
    private final JLabel proxyPasswordLabel = new JLabel("Senha (Opcional):");
    private final JPasswordField proxyPasswordField = new JPasswordField();

    private final Component[] proxyComponents;

    public ProfileProxyPanel(ProfileEditorPopup editor) {
        this.editor = editor;
        this.profile = editor.getProfile();

        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("Configurações de Proxy"));

        proxyComponents = new Component[]{
                proxyTypeLabel, proxyTypeComboBox,
                proxyHostLabel, proxyHostField,
                proxyPortLabel, proxyPortField,
                proxyUserLabel, proxyUserField,
                proxyPasswordLabel, proxyPasswordField
        };

        createInterface();
        fillValuesFromProfile();
        addEventHandlers();
        updateFieldsEnabledState(); // Estado inicial dos campos
    }

    private void createInterface() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Checkbox para habilitar/desabilitar proxy
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2; // Ocupa duas colunas
        add(proxyEnabledCheckbox, gbc);

        // Tipo de Proxy
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        add(proxyTypeLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(proxyTypeComboBox, gbc);

        // Host do Proxy
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        add(proxyHostLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(proxyHostField, gbc);

        // Porta do Proxy
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        add(proxyPortLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(proxyPortField, gbc);

        // Usuário do Proxy
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        add(proxyUserLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(proxyUserField, gbc);

        // Senha do Proxy
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        add(proxyPasswordLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(proxyPasswordField, gbc);
    }

    private void fillValuesFromProfile() {
        proxyEnabledCheckbox.setSelected(profile.isProxyEnabled());
        proxyTypeComboBox.setSelectedItem(profile.getProxyType());
        proxyHostField.setText(profile.getProxyHost() != null ? profile.getProxyHost() : "");
        proxyPortField.setText(profile.getProxyPort() > 0 ? String.valueOf(profile.getProxyPort()) : "");
        proxyUserField.setText(profile.getProxyUser() != null ? profile.getProxyUser() : "");
        proxyPasswordField.setText(profile.getProxyPassword() != null ? profile.getProxyPassword() : ""); // Cuidado
    }

 // Em ProfileProxyPanel.java
    private void addEventHandlers() {
        proxyEnabledCheckbox.addItemListener(e -> {
            profile.setProxyEnabled(proxyEnabledCheckbox.isSelected());
            updateFieldsEnabledState(); // Isso está correto
        });

        proxyTypeComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                profile.setProxyType((Profile.ProxyType) proxyTypeComboBox.getSelectedItem());
                // Re-chamar updateFieldsEnabledState pode ser necessário se NONE desabilitar campos
                updateFieldsEnabledState();
            }
        });

        // Listener para proxyHostField
        proxyHostField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { profile.setProxyHost(proxyHostField.getText().trim()); }
            @Override public void removeUpdate(DocumentEvent e) { profile.setProxyHost(proxyHostField.getText().trim()); }
            @Override public void changedUpdate(DocumentEvent e) { profile.setProxyHost(proxyHostField.getText().trim()); }
        });

        // Listener para proxyPortField
        proxyPortField.getDocument().addDocumentListener(new DocumentListener() {
            private void updatePort() {
                try {
                    String portText = proxyPortField.getText().trim();
                    profile.setProxyPort(portText.isEmpty() ? 0 : Integer.parseInt(portText));
                } catch (NumberFormatException ex) {
                    profile.setProxyPort(0); // Ou um valor padrão em caso de erro
                }
            }
            @Override public void insertUpdate(DocumentEvent e) { updatePort(); }
            @Override public void removeUpdate(DocumentEvent e) { updatePort(); }
            @Override public void changedUpdate(DocumentEvent e) { updatePort(); }
        });

        // Listener para proxyUserField
        proxyUserField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { profile.setProxyUser(proxyUserField.getText().trim()); }
            @Override public void removeUpdate(DocumentEvent e) { profile.setProxyUser(proxyUserField.getText().trim()); }
            @Override public void changedUpdate(DocumentEvent e) { profile.setProxyUser(proxyUserField.getText().trim()); }
        });

        // Listener para proxyPasswordField
        proxyPasswordField.getDocument().addDocumentListener(new DocumentListener() {
            private void updatePass() { profile.setProxyPassword(new String(proxyPasswordField.getPassword())); }
            @Override public void insertUpdate(DocumentEvent e) { updatePass(); }
            @Override public void removeUpdate(DocumentEvent e) { updatePass(); }
            @Override public void changedUpdate(DocumentEvent e) { updatePass(); }
        });
    }

    private void updateProfileFromFields() {
        profile.setProxyEnabled(proxyEnabledCheckbox.isSelected()); // Já deve estar no listener do checkbox
        profile.setProxyType((Profile.ProxyType) proxyTypeComboBox.getSelectedItem()); // Já deve estar no listener do combobox

        profile.setProxyHost(proxyHostField.getText().trim());
        try {
            String portText = proxyPortField.getText().trim();
            if (portText.isEmpty()) {
                profile.setProxyPort(0); // Ou outro valor indicando não definido/inválido
            } else {
                profile.setProxyPort(Integer.parseInt(portText));
            }
        } catch (NumberFormatException e) {
            profile.setProxyPort(0); // Tratar erro de conversão
        }
        profile.setProxyUser(proxyUserField.getText().trim());
        profile.setProxyPassword(new String(proxyPasswordField.getPassword()));
    }


    private void updateFieldsEnabledState() {
        boolean enabled = proxyEnabledCheckbox.isSelected();
        for (Component comp : proxyComponents) {
            comp.setEnabled(enabled);
        }
        // O tipo NONE desabilita os campos de host/porta/user/pass mesmo se o proxy principal estiver habilitado
        if (enabled && proxyTypeComboBox.getSelectedItem() == Profile.ProxyType.NONE) {
             proxyHostField.setEnabled(false);
             proxyPortField.setEnabled(false);
             proxyUserField.setEnabled(false);
             proxyPasswordField.setEnabled(false);
             proxyHostLabel.setEnabled(false);
             proxyPortLabel.setEnabled(false);
             proxyUserLabel.setEnabled(false);
             proxyPasswordLabel.setEnabled(false);
        }

        // Listener adicional no ComboBox para reabilitar/desabilitar campos com base no tipo NONE
         proxyTypeComboBox.removeItemListener(proxyTypeListener); // Remover para evitar duplicação
         proxyTypeComboBox.addItemListener(proxyTypeListener);
    }
    // Listener para o ComboBox de tipo de proxy, para habilitar/desabilitar campos
    private final ItemListener proxyTypeListener = e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            boolean mainEnabled = proxyEnabledCheckbox.isSelected();
            boolean typeAllowsFields = proxyTypeComboBox.getSelectedItem() != Profile.ProxyType.NONE;
            boolean enableSubFields = mainEnabled && typeAllowsFields;

            proxyHostField.setEnabled(enableSubFields);
            proxyPortField.setEnabled(enableSubFields);
            proxyUserField.setEnabled(enableSubFields);
            proxyPasswordField.setEnabled(enableSubFields);
            proxyHostLabel.setEnabled(enableSubFields);
            proxyPortLabel.setEnabled(enableSubFields);
            proxyUserLabel.setEnabled(enableSubFields);
            proxyPasswordLabel.setEnabled(enableSubFields);
        }
    };
}