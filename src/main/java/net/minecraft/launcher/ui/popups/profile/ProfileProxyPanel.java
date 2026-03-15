package net.minecraft.launcher.ui.popups.profile;

import net.minecraft.launcher.Launcher;
import net.minecraft.launcher.profile.Profile;
import net.minecraft.launcher.utils.CryptoUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;

public class ProfileProxyPanel extends JPanel {
    private final Launcher          minecraftLauncher;
    private final ProfileEditorPopup editor;
    private final Profile           profile;

    private final JCheckBox    proxyEnabledCheckbox = new JCheckBox("Habilitar Proxy para este Perfil");
    // Tipo de proxy fixo em SOCKS5 — não editável pelo usuário
    private final JLabel       proxyTypeValueLabel  = new JLabel("SOCKS5");
    private final JLabel       proxyHostLabel       = new JLabel("Endereço (Host):");
    private final JTextField   proxyHostField       = new JTextField();
    private final JLabel       proxyPortLabel       = new JLabel("Porta:");
    private final JTextField   proxyPortField       = new JTextField();
    private final JLabel       proxyUserLabel       = new JLabel("Usuário (Opcional):");
    private final JTextField   proxyUserField       = new JTextField();
    private final JLabel       proxyPasswordLabel   = new JLabel("Senha (Opcional):");
    private final JPasswordField proxyPasswordField = new JPasswordField();

    private final JButton testButton   = new JButton("Testar Conexão");
    private final JLabel  testResult   = new JLabel(" ");

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
            proxyPasswordLabel, proxyPasswordField,
            testButton
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

        // Tipo de proxy (fixo: SOCKS5)
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

        // ── Botão de teste + label de resultado ───────────────────────────
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(testButton, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        testResult.setFont(testResult.getFont().deriveFont(Font.PLAIN, 11f));
        add(testResult, gbc);
    }

    private void fillValuesFromProfile() {
        proxyEnabledCheckbox.setSelected(profile.isProxyEnabled());
        // Sempre garante SOCKS5 no perfil (tipo fixo)
        profile.setProxyType(Profile.ProxyType.SOCKS5);
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

        testButton.addActionListener(e -> runConnectionTest());
    }

    /** Executa o teste de conexão SOCKS5 em thread de fundo. */
    private void runConnectionTest() {
        String host = proxyHostField.getText().trim();
        String portStr = proxyPortField.getText().trim();
        String user = proxyUserField.getText().trim();
        String pass = new String(proxyPasswordField.getPassword());

        if (host.isEmpty() || portStr.isEmpty()) {
            testResult.setForeground(Color.ORANGE);
            testResult.setText("⚠ Preencha host e porta primeiro.");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ex) {
            testResult.setForeground(Color.ORANGE);
            testResult.setText("⚠ Porta inválida.");
            return;
        }

        testButton.setEnabled(false);
        testResult.setForeground(Color.GRAY);
        testResult.setText("⏳ Testando...");

        final String fHost = host;
        final int    fPort = port;
        final String fUser = user;
        final String fPass = pass;

        new Thread(() -> {
            String msg;
            Color  color;
            try {
                Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(fHost, fPort));

                Authenticator temp = null;
                if (!fUser.isEmpty()) {
                    temp = new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(fUser, fPass.toCharArray());
                        }
                    };
                    Authenticator.setDefault(temp);
                }

                URL url = new URL("https://ifconfig.me");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                int code = conn.getResponseCode();
                String ip;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    ip = br.readLine();
                }
                conn.disconnect();

                if (code == 200 && ip != null) {
                    msg   = "✔ Conectado! IP de saída: " + ip.trim();
                    color = new Color(0x4CAF50);
                } else {
                    msg   = "✘ Resposta inesperada: HTTP " + code;
                    color = Color.RED;
                }
            } catch (Exception ex) {
                msg   = "✘ Falha: " + ex.getClass().getSimpleName() + " — " + ex.getMessage();
                color = Color.RED;
            }

            final String fMsg   = msg;
            final Color  fColor = color;
            SwingUtilities.invokeLater(() -> {
                testResult.setForeground(fColor);
                testResult.setText(fMsg);
                testButton.setEnabled(proxyEnabledCheckbox.isSelected());
            });
        }, "proxy-test").start();
    }

    private void updateFieldsEnabledState() {
        boolean enabled = proxyEnabledCheckbox.isSelected();
        for (Component comp : proxyComponents) comp.setEnabled(enabled);
    }
}