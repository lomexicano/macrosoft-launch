package net.minecraft.launcher.Macrosoft;

import net.minecraft.launcher.Launcher;
import net.minecraft.launcher.LauncherConstants;
import net.minecraft.launcher.Main;
import net.minecraft.launcher.game.GameLaunchDispatcher;
import net.minecraft.launcher.profile.Profile;
import net.minecraft.launcher.ui.popups.profile.ProfileEditorPopup;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Painel principal do launcher:
 *  – Mostra GIF animado enquanto carrega modpacks da API / disco
 *  – Botão de ação com estados: Preparar → Preparando… → ▶ Jogar → Instalando… → ⏹ Parar
 *  – Botão ⚙ abre o editor de perfil sem sair do browser
 */
public class MacrosoftModpackBrowser extends JPanel {

    // ── Cores ──────────────────────────────────────────────────────────────
    private static final Color BG           = new Color(22, 13, 28);
    private static final Color CARD_BG      = new Color(35, 22, 45);
    private static final Color CARD_BORDER  = new Color(70, 50, 90);
    private static final Color TEXT_WHITE   = Color.WHITE;
    private static final Color TEXT_TEAL    = new Color(1, 131, 129);
    private static final Color TEXT_GRAY    = new Color(170, 170, 170);
    private static final Color BTN_PREPARE  = new Color(55, 90, 140);   // azul: "Preparar"
    private static final Color BTN_PLAY     = new Color(0, 110, 75);    // verde: "▶ Jogar"
    private static final Color BTN_STOP     = new Color(180, 40, 40);   // vermelho: "⏹ Parar"
    private static final Color BTN_DL       = new Color(60, 160, 70);   // verde claro: "⬇ Baixar"
    private static final Color BTN_CFG      = new Color(70, 60, 100);   // roxo: "⚙"
    private static final Color BTN_DISABLED = new Color(55, 50, 65);    // cinza: estados de espera
    private static final Color LINK_COLOR   = new Color(135, 206, 250);

    // ── API ────────────────────────────────────────────────────────────────
    private static final String[] API_URLS = {
        "https://www.macrosoft.website/launcher/info?format=json",
        "http://macrosoft.website/launcher/info?format=json"
    };
    private static final int ICON_SIZE = 40;

    // ── Modelo ─────────────────────────────────────────────────────────────
    public static class ModpackEntry {
        public final String name, downloadUrl, author, iconUrl;
        public boolean isDownloaded;
        public ImageIcon icon;
        public Launcher playLauncher      = null;
        public Launcher configureLauncher = null;

        public ModpackEntry(String name, String downloadUrl, String author, String iconUrl) {
            this.name        = name;
            this.downloadUrl = downloadUrl;
            this.author      = (author != null && !author.isEmpty()) ? author : "Desconhecido";
            this.iconUrl     = iconUrl;
        }
    }

    /**
     * Estados visíveis do botão de ação por card.
     */
    private enum ActionState {
        PREPARE,      // launcher não criado → "Preparar"
        PREPARING,    // launcher criando, versões/perfis carregando → "Preparando…"
        READY,        // pronto para jogar → "▶ Jogar"
        DOWNLOADING,  // baixando/instalando arquivos do jogo → "Instalando…"
        PLAYING       // jogo em execução → "⏹ Parar"
    }

    private static class CardUi {
        final ModpackEntry entry;
        final JButton actionBtn;   // único botão de ação, muda de estado
        final JButton configBtn;
        CardUi(ModpackEntry e, JButton action, JButton cfg) {
            this.entry = e; this.actionBtn = action; this.configBtn = cfg;
        }
    }

    // ── Campos ─────────────────────────────────────────────────────────────
    private final File     macrosoftBaseDir;
    private final JFrame   parentFrame;
    private final String[] launcherArgs;
    private final JPanel   cardsPanel;
    private JTextField     playerNameField;

    private List<ModpackEntry> entries    = new ArrayList<>();
    private List<CardUi>       cardUiList = new ArrayList<>();
    private javax.swing.Timer  statePoller;

    private String websiteLink = "https://www.macrosoft.website/";
    private String discordLink = "https://discord.gg/t7WcjJ4";

    // ── Construtor ─────────────────────────────────────────────────────────
    public MacrosoftModpackBrowser(File macrosoftBaseDir, JFrame parentFrame, String[] launcherArgs) {
        this.macrosoftBaseDir = macrosoftBaseDir;
        this.parentFrame      = parentFrame;
        this.launcherArgs     = launcherArgs;

        setBackground(BG);
        setLayout(new BorderLayout(0, 0));
        add(buildHeader(), BorderLayout.NORTH);

        cardsPanel = new JPanel();
        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
        cardsPanel.setBackground(BG);
        cardsPanel.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        showLoadingState();     // GIF + texto enquanto carrega

        JScrollPane scroll = new JScrollPane(cardsPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        loadEntriesAsync();
        startStatePoller();
    }

    // ── Estado de carregamento (GIF animado) ───────────────────────────────
    private void showLoadingState() {
        cardsPanel.removeAll();

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBackground(BG);
        inner.setAlignmentX(Component.CENTER_ALIGNMENT);

        // GIF animado
        URL gifUrl = getClass().getResource("/macrosoft_animated_bg_small.gif");
        if (gifUrl != null) {
            JLabel gifLabel = new JLabel(new ImageIcon(gifUrl));
            gifLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            inner.add(gifLabel);
            inner.add(Box.createRigidArea(new Dimension(0, 10)));
        }

        JLabel textLabel = new JLabel("Carregando modpacks...", SwingConstants.CENTER);
        textLabel.setForeground(TEXT_TEAL);
        textLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        textLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(textLabel);

        cardsPanel.add(Box.createVerticalGlue());
        cardsPanel.add(inner);
        cardsPanel.add(Box.createVerticalGlue());
        cardsPanel.revalidate();
        cardsPanel.repaint();
    }

    // ── Header: logo + campo nick ──────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(BG);
        header.setBorder(BorderFactory.createEmptyBorder(12, 20, 8, 20));

        try {
            BufferedImage logo = ImageIO.read(getClass().getResource("/minecraft_logo.png"));
            if (logo != null) {
                JLabel lbl = new JLabel(new ImageIcon(logo));
                lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
                header.add(lbl);
            }
        } catch (Exception ignored) {
            JLabel title = new JLabel("Macrosoft Launcher", SwingConstants.CENTER);
            title.setForeground(TEXT_TEAL);
            title.setFont(new Font("SansSerif", Font.BOLD, 22));
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            header.add(title);
        }

        header.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel nickRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        nickRow.setBackground(BG);
        JLabel nickLbl = new JLabel("Nick:");
        nickLbl.setForeground(TEXT_TEAL);
        nickLbl.setFont(new Font("SansSerif", Font.BOLD, 13));

        playerNameField = new JTextField(loadPlayerName(), 18);
        playerNameField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        playerNameField.setBackground(new Color(50, 35, 65));
        playerNameField.setForeground(TEXT_WHITE);
        playerNameField.setCaretColor(TEXT_WHITE);
        playerNameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        nickRow.add(nickLbl);
        nickRow.add(playerNameField);
        header.add(nickRow);
        header.add(Box.createRigidArea(new Dimension(0, 4)));
        return header;
    }

    // ── Footer ─────────────────────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        footer.setBackground(BG);
        footer.add(linkButton("Website", () -> openLink(websiteLink)));
        footer.add(linkButton("Discord", () -> openLink(discordLink)));
        return footer;
    }

    private JButton linkButton(String text, Runnable action) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setForeground(LINK_COLOR);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    // ── Carga assíncrona ───────────────────────────────────────────────────
    private void loadEntriesAsync() {
        new SwingWorker<List<ModpackEntry>, Void>() {
            String site = websiteLink, disc = discordLink;

            @Override
            protected List<ModpackEntry> doInBackground() {
                List<ModpackEntry> result = new ArrayList<>();
                JSONObject api = null;
                for (String url : API_URLS) {
                    try { api = Connector.get(url); break; }
                    catch (Exception e) { System.err.println("[Browser] API: " + e.getMessage()); }
                }
                if (api != null) {
                    site = api.optString("site", websiteLink);
                    disc = api.optString("discord", discordLink);
                    try {
                        int v = api.getInt("version");
                        if (v > LauncherConstants.MACROSOFT_VERSION) {
                            final int fv = v;
                            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parentFrame,
                                "Atualize para a versão " + fv + "!", "Launcher Desatualizado", JOptionPane.WARNING_MESSAGE));
                        }
                    } catch (JSONException ignored) {}
                    try {
                        JSONArray servers = api.getJSONArray("servers");
                        for (int i = 0; i < servers.length(); i++) {
                            JSONObject s = servers.getJSONObject(i);
                            result.add(new ModpackEntry(s.getString("name"), s.optString("modpack_zip", null),
                                s.optString("modpack_author", "Desconhecido"), s.optString("icon", null)));
                        }
                    } catch (JSONException e) { System.err.println("[Browser] servers: " + e.getMessage()); }
                }
                if (macrosoftBaseDir.exists() && macrosoftBaseDir.isDirectory()) {
                    File[] dirs = macrosoftBaseDir.listFiles(File::isDirectory);
                    if (dirs != null) {
                        for (File dir : dirs) {
                            if (isDirectoryEmpty(dir)) continue;
                            String n = dir.getName();
                            boolean found = false;
                            for (ModpackEntry e : result) { if (e.name.equalsIgnoreCase(n)) { e.isDownloaded = true; found = true; break; } }
                            if (!found) { ModpackEntry loc = new ModpackEntry(n, null, "Local", null); loc.isDownloaded = true; result.add(loc); }
                        }
                    }
                }
                ImageIcon defIcon = loadDefaultIcon();
                for (ModpackEntry entry : result) {
                    if (entry.iconUrl != null && !entry.iconUrl.isEmpty()) {
                        try (InputStream is = new URL(entry.iconUrl).openStream()) {
                            BufferedImage img = ImageIO.read(is);
                            if (img != null) entry.icon = new ImageIcon(img.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH));
                        } catch (IOException ignored) {}
                    }
                    if (entry.icon == null) entry.icon = defIcon;
                }
                return result;
            }

            @Override
            protected void done() {
                websiteLink = site; discordLink = disc;
                try { entries = get(); } catch (Exception e) { entries = new ArrayList<>(); }
                refreshCards();
            }
        }.execute();
    }

    // ── Cards ──────────────────────────────────────────────────────────────
    private void refreshCards() {
        cardsPanel.removeAll();
        cardUiList.clear();

        if (entries.isEmpty()) {
            JLabel empty = new JLabel("<html><center>Nenhuma modpack disponível.<br>Verifique sua conexão.</center></html>", SwingConstants.CENTER);
            empty.setForeground(TEXT_TEAL);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            cardsPanel.add(Box.createVerticalGlue());
            cardsPanel.add(empty);
            cardsPanel.add(Box.createVerticalGlue());
        } else {
            for (ModpackEntry entry : entries) {
                JPanel card = createCard(entry);
                card.setAlignmentX(Component.LEFT_ALIGNMENT);
                cardsPanel.add(card);
                cardsPanel.add(Box.createRigidArea(new Dimension(0, 8)));
            }
            cardsPanel.add(Box.createVerticalGlue());
        }
        cardsPanel.revalidate();
        cardsPanel.repaint();
    }

    private JPanel createCard(ModpackEntry entry) {
        JPanel card = new JPanel(new BorderLayout(12, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        if (entry.icon != null) {
            JLabel iconLbl = new JLabel(entry.icon);
            iconLbl.setPreferredSize(new Dimension(ICON_SIZE + 8, ICON_SIZE + 8));
            card.add(iconLbl, BorderLayout.WEST);
        }

        JPanel info = new JPanel(new GridLayout(2, 1, 0, 2));
        info.setBackground(CARD_BG);
        JLabel nameLbl = new JLabel(entry.name);
        nameLbl.setForeground(TEXT_WHITE);
        nameLbl.setFont(nameLbl.getFont().deriveFont(Font.BOLD, 14f));
        JLabel authorLbl = new JLabel("por " + entry.author);
        authorLbl.setForeground(TEXT_GRAY);
        authorLbl.setFont(authorLbl.getFont().deriveFont(Font.PLAIN, 11f));
        info.add(nameLbl);
        info.add(authorLbl);
        card.add(info, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.setBackground(CARD_BG);

        if (entry.isDownloaded) {
            // Botão ⚙ Configurar
            JButton configBtn = styledButton("⚙", BTN_CFG);
            configBtn.setToolTipText("Configurar perfil");
            configBtn.addActionListener(e -> configureModpack(entry));

            // Botão de ação único (estado variável)
            JButton actionBtn = styledButton("Preparar", BTN_PREPARE);
            actionBtn.addActionListener(e -> onActionClicked(entry, actionBtn));

            btnPanel.add(configBtn);
            btnPanel.add(actionBtn);
            cardUiList.add(new CardUi(entry, actionBtn, configBtn));

        } else if (entry.downloadUrl != null) {
            JButton dlBtn = styledButton("⬇  Baixar", BTN_DL);
            dlBtn.addActionListener(e -> downloadModpack(entry, dlBtn));
            btnPanel.add(dlBtn);
        }

        card.add(btnPanel, BorderLayout.EAST);
        return card;
    }

    // ── Lógica do botão de ação ────────────────────────────────────────────
    /**
     * Dispatcher único: decide o que fazer baseado no estado atual da entrada.
     */
    private void onActionClicked(ModpackEntry entry, JButton actionBtn) {
        ActionState state = getActionState(entry);
        switch (state) {
            case PREPARE:
                prepareEntry(entry, actionBtn);
                break;
            case READY:
                // Launcher pronto → lançar jogo
                entry.playLauncher.getLaunchDispatcher().play();
                break;
            case PLAYING:
                entry.playLauncher.getLaunchDispatcher().stopAll();
                break;
            default:
                // PREPARING / DOWNLOADING → botão desabilitado, nunca chega aqui
                break;
        }
    }

    private void prepareEntry(ModpackEntry entry, JButton actionBtn) {
        String playerName = playerNameField.getText().trim();
        if (playerName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Digite seu nick antes de preparar!", "Nick vazio", JOptionPane.WARNING_MESSAGE);
            playerNameField.requestFocus();
            return;
        }
        savePlayerName(playerName);
        // Feedback imediato
        applyButtonState(actionBtn, ActionState.PREPARING);
        // Cria launcher com autoPlay=false; o poller detecta quando fica READY
        entry.playLauncher = Main.launchModpack(entry.name, playerName, false, parentFrame, launcherArgs);
    }

    private void configureModpack(ModpackEntry entry) {
        if (entry.configureLauncher == null) {
            entry.configureLauncher = Main.launchModpack(entry.name, null, false, parentFrame, launcherArgs);
        }
        waitAndShowEditor(entry.configureLauncher);
    }

    private void waitAndShowEditor(Launcher launcher) {
        javax.swing.Timer[] ref = new javax.swing.Timer[1];
        ref[0] = new javax.swing.Timer(200, e -> {
            if (!launcher.getProfileManager().getProfiles().isEmpty()) {
                ref[0].stop();
                SwingUtilities.invokeLater(() -> {
                    Profile profile = launcher.getProfileManager().getSelectedProfile();
                    ProfileEditorPopup.showEditProfileDialog(launcher, profile);
                });
            }
        });
        javax.swing.Timer timeout = new javax.swing.Timer(10_000, e -> {
            ref[0].stop();
            JOptionPane.showMessageDialog(parentFrame, "Tempo esgotado ao carregar perfis.", "Erro", JOptionPane.ERROR_MESSAGE);
        });
        timeout.setRepeats(false);
        timeout.start();
        ref[0].start();
    }

    private void downloadModpack(ModpackEntry entry, JButton triggerBtn) {
        triggerBtn.setEnabled(false);
        triggerBtn.setText("...");
        File targetDir = new File(macrosoftBaseDir, entry.name);
        if (!targetDir.exists()) targetDir.mkdirs();
        ActionEvent fakeEvent = new ActionEvent(new JButton(entry.name), ActionEvent.ACTION_PERFORMED, "");
        new Downloader(entry.downloadUrl, targetDir,
            "Baixando <b>" + entry.name + "</b> por <i>" + entry.author + "</i>...",
            ev -> { entry.isDownloaded = true; SwingUtilities.invokeLater(this::refreshCards); },
            fakeEvent);
    }

    // ── Polling de estado ──────────────────────────────────────────────────
    private void startStatePoller() {
        statePoller = new javax.swing.Timer(600, e -> updateCardStates());
        statePoller.start();
    }

    private void updateCardStates() {
        for (CardUi cu : cardUiList) {
            ActionState state = getActionState(cu.entry);
            applyButtonState(cu.actionBtn, state);
            if (cu.configBtn != null) {
                cu.configBtn.setEnabled(state != ActionState.PLAYING && state != ActionState.DOWNLOADING);
            }
        }
    }

    /**
     * Determina o estado atual do botão de ação para uma entrada.
     */
    private ActionState getActionState(ModpackEntry entry) {
        if (entry.playLauncher == null) return ActionState.PREPARE;
        GameLaunchDispatcher.PlayStatus s = entry.playLauncher.getLaunchDispatcher().getStatus();
        switch (s) {
            case ALREADY_PLAYING: return ActionState.PLAYING;
            case DOWNLOADING:     return ActionState.DOWNLOADING;
            case LOADING:         return ActionState.PREPARING;
            default:              return ActionState.READY;   // CAN_PLAY_*
        }
    }

    /**
     * Aplica aparência visual ao botão conforme o estado.
     */
    private void applyButtonState(JButton btn, ActionState state) {
        switch (state) {
            case PREPARE:
                btn.setText("Preparar");
                btn.setBackground(BTN_PREPARE);
                btn.setEnabled(true);
                break;
            case PREPARING:
                btn.setText("Preparando…");
                btn.setBackground(BTN_DISABLED);
                btn.setEnabled(false);
                break;
            case READY:
                btn.setText("▶  Jogar");
                btn.setBackground(BTN_PLAY);
                btn.setEnabled(true);
                break;
            case DOWNLOADING:
                btn.setText("Instalando…");
                btn.setBackground(BTN_DISABLED);
                btn.setEnabled(false);
                break;
            case PLAYING:
                btn.setText("⏹  Parar");
                btn.setBackground(BTN_STOP);
                btn.setEnabled(true);
                break;
        }
    }

    // ── Utilitários ────────────────────────────────────────────────────────
    private JButton styledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(TEXT_WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg.darker()),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private ImageIcon loadDefaultIcon() {
        try {
            BufferedImage img = ImageIO.read(getClass().getResource("/mc.png"));
            if (img != null) return new ImageIcon(img.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH));
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isDirectoryEmpty(File dir) { String[] f = dir.list(); return f == null || f.length == 0; }

    private File playerNameFile() { return new File(macrosoftBaseDir, "player_name.txt"); }

    private String loadPlayerName() {
        try { if (playerNameFile().exists()) return new String(Files.readAllBytes(playerNameFile().toPath())).trim(); }
        catch (IOException ignored) {}
        return "";
    }

    private void savePlayerName(String name) {
        try { macrosoftBaseDir.mkdirs(); Files.write(playerNameFile().toPath(), name.getBytes()); }
        catch (IOException ignored) {}
    }

    private void openLink(String url) {
        try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new URI(url)); }
        catch (Exception ex) { JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE); }
    }
}
