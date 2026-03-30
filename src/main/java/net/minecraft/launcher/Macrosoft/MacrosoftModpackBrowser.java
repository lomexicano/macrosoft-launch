package net.minecraft.launcher.Macrosoft;

import net.minecraft.launcher.Launcher;
import net.minecraft.launcher.LauncherConstants;
import net.minecraft.launcher.Main;
import net.minecraft.launcher.SwingUserInterface;
import net.minecraft.launcher.game.GameLaunchDispatcher;
import net.minecraft.launcher.profile.Profile;
import net.minecraft.launcher.ui.popups.profile.ProfileEditorPopup;
import net.minecraft.launcher.ui.tabs.LauncherTabPanel;
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
    private static final Color BTN_LOGS     = new Color(40, 80, 110);   // azul escuro: "📋 Logs"
    private static final Color BTN_DISABLED = new Color(55, 50, 65);    // cinza: estados de espera
    private static final Color BTN_JAVA     = new Color(180, 110, 0);   // âmbar: botão Java
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
        /** Janela de logs reutilizável; recriada quando playLauncher muda. */
        public JDialog  logDialog         = null;

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
        final JButton logsBtn;
        final JButton nickBtn;     // botão 👤 para editar o nick desta modpack
        final JLabel  nickLabel;   // exibe o nick atual no card
        CardUi(ModpackEntry e, JButton action, JButton cfg, JButton logs, JButton nick, JLabel nickLbl) {
            this.entry = e; this.actionBtn = action; this.configBtn = cfg; this.logsBtn = logs;
            this.nickBtn = nick; this.nickLabel = nickLbl;
        }
    }

    // ── Campos ─────────────────────────────────────────────────────────────
    private final File     macrosoftBaseDir;
    private final JFrame   parentFrame;
    private final String[] launcherArgs;
    private final JPanel   cardsPanel;

    private List<ModpackEntry> entries    = new ArrayList<>();
    private List<CardUi>       cardUiList = new ArrayList<>();
    private javax.swing.Timer  statePoller;

    private String  websiteLink        = "https://www.macrosoft.website/";
    private String  discordLink        = "https://discord.gg/t7WcjJ4";
    /** URL personalizada da API (persiste em api_server.txt). null = usar padrão. */
    private String  customApiUrl       = null;
    /** true quando a última tentativa de contatar a API falhou. */
    private boolean lastLoadHadApiError = false;
    private List<JavaRuntimeManager.JavaRuntimeOption> javaRuntimeOptions = new ArrayList<>();

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

        // Wrapper BorderLayout garante que o cardsPanel (BoxLayout) preencha
        // toda a largura do viewport — sem ele os cards ficam deslocados.
        JPanel scrollWrapper = new JPanel(new BorderLayout());
        scrollWrapper.setBackground(BG);
        scrollWrapper.add(cardsPanel, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(scrollWrapper);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        loadCustomApiUrl();
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

        header.add(Box.createRigidArea(new Dimension(0, 6)));
        return header;
    }

    // ── Footer ─────────────────────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        footer.setBackground(BG);
        footer.add(createJavaManagerButton());
        footer.add(linkButton("Website", () -> openLink(websiteLink)));
        footer.add(linkButton("Discord", () -> openLink(discordLink)));
        footer.add(linkButton("⚙ Servidor", this::showServerSettings));
        return footer;
    }

    private JButton createJavaManagerButton() {
        JButton button = styledButton("Java", BTN_JAVA);
        button.setToolTipText("Gerenciar runtimes Java");
        try {
            BufferedImage javaImage = ImageIO.read(getClass().getResource("/java.png"));
            if (javaImage != null) {
                Image scaled = javaImage.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
                button.setIcon(new ImageIcon(scaled));
                button.setIconTextGap(6);
            }
        } catch (Exception ignored) {}
        button.addActionListener(e -> showJavaManagerDialog());
        return button;
    }

    private void showJavaManagerDialog() {
        JavaRuntimeManagerDialog dialog = new JavaRuntimeManagerDialog(
            parentFrame,
            macrosoftBaseDir.toPath(),
            javaRuntimeOptions
        );
        dialog.setVisible(true);
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
            String  site     = websiteLink;
            String  disc     = discordLink;
            boolean apiError = false;
            List<JavaRuntimeManager.JavaRuntimeOption> javaOptions = new ArrayList<>();

            @Override
            protected List<ModpackEntry> doInBackground() {
                List<ModpackEntry> result = new ArrayList<>();

                // URLs a tentar: personalizada primeiro, depois padrões
                List<String> urlsToTry = new ArrayList<>();
                if (customApiUrl != null && !customApiUrl.isEmpty()) {
                    urlsToTry.add(customApiUrl);
                } else {
                    for (String u : API_URLS) urlsToTry.add(u);
                }

                JSONObject api = null;
                for (String url : urlsToTry) {
                    try { api = Connector.get(url); break; }
                    catch (Exception e) { System.err.println("[Browser] API falhou (" + url + "): " + e.getMessage()); }
                }

                if (api == null) {
                    apiError = true;
                } else {
                    site = api.optString("site", websiteLink);
                    disc = api.optString("discord", discordLink);
                    javaOptions = JavaRuntimeManager.listOptionsFromApi(api);
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

                // Diretórios locais
                if (macrosoftBaseDir.exists() && macrosoftBaseDir.isDirectory()) {
                    File[] dirs = macrosoftBaseDir.listFiles(File::isDirectory);
                    if (dirs != null) {
                        for (File dir : dirs) {
                            if (isDirectoryEmpty(dir)) continue;
                            String n = dir.getName();
                            if (n.startsWith(".")) continue; // pasta interna (ex: .java, .tmp-java-*)
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
                websiteLink         = site;
                discordLink         = disc;
                lastLoadHadApiError = apiError;
                javaRuntimeOptions  = javaOptions;
                try { entries = get(); } catch (Exception e) { entries = new ArrayList<>(); lastLoadHadApiError = true; }
                refreshCards();
            }
        }.execute();
    }

    // ── Cards ──────────────────────────────────────────────────────────────
    private void refreshCards() {
        cardsPanel.removeAll();
        cardUiList.clear();

        // Banner sutil de erro de rede (só aparece quando a API falhou)
        if (lastLoadHadApiError) {
            cardsPanel.add(buildNetworkErrorBanner());
            cardsPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        }

        if (entries.isEmpty()) {
            JLabel empty = new JLabel(lastLoadHadApiError
                    ? "<html><center>Sem conexão com o servidor.<br>Nenhuma modpack local encontrada.</center></html>"
                    : "<html><center>Nenhuma modpack disponível.<br>Verifique sua conexão.</center></html>",
                    SwingConstants.CENTER);
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

        // Recalcular tamanho para remover espaços ociosos
        if (parentFrame != null) {
            // Remove qualquer tamanho forçado para deixar o Swing calcular livremente
            parentFrame.setPreferredSize(null);
            parentFrame.setMinimumSize(null);
            this.setPreferredSize(null);

            // Remove espaços ociosos, agora que não há mais tamanhos forçados na janela
            parentFrame.pack();

            // Opcional: Centraliza a janela novamente na tela do usuário após mudar o tamanho
            parentFrame.setLocationRelativeTo(null);
        }
    }

    /** Banner discreto exibido no topo da lista quando a API não responde. */
    private JPanel buildNetworkErrorBanner() {
        JPanel banner = new JPanel(new BorderLayout(8, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        // DEVE ter o mesmo alinhamento que os cards (LEFT) para o BoxLayout
        // Y_AXIS não deslocar os cards para a direita ao calcular o spanX.
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        Color bannerBg  = new Color(60, 30, 20);
        Color bannerBdr = new Color(140, 60, 30);
        banner.setBackground(bannerBg);
        banner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bannerBdr),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)));

        JLabel msg = new JLabel("⚠  Sem conexão com o servidor — exibindo modpacks locais.");
        msg.setForeground(new Color(255, 180, 100));
        msg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        banner.add(msg, BorderLayout.CENTER);

        JButton reloadBtn = new JButton("↺ Recarregar");
        reloadBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        reloadBtn.setForeground(TEXT_WHITE);
        reloadBtn.setBackground(bannerBdr);
        reloadBtn.setOpaque(true);
        reloadBtn.setFocusPainted(false);
        reloadBtn.setBorderPainted(false);
        reloadBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        reloadBtn.addActionListener(e -> {
            showLoadingState();
            loadEntriesAsync();
        });
        banner.add(reloadBtn, BorderLayout.EAST);
        return banner;
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

        // Para modpacks baixadas exibimos 3 linhas: nome / autor / nick atual
        JPanel info = new JPanel(new GridLayout(entry.isDownloaded ? 3 : 2, 1, 0, 2));
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
            // ── Nick label (3ª linha do info) ─────────────────────────────────────
            String currentNick = loadNickForEntry(entry);
            JLabel nickLabel = new JLabel(nickLabelText(currentNick));
            nickLabel.setForeground(TEXT_TEAL);
            nickLabel.setFont(nickLabel.getFont().deriveFont(Font.PLAIN, 11f));
            info.add(nickLabel);

            // ── Botão 📋 Logs ──────────────────────────────────────────────────────
            JButton logsBtn = styledButton("📋 Logs", BTN_LOGS);
            logsBtn.setToolTipText("Ver logs do launcher e do jogo");
            logsBtn.setVisible(false);
            logsBtn.addActionListener(e -> openLogWindow(entry));

            // ── Botão 👤 Nick ──────────────────────────────────────────────────────
            JButton nickBtn = styledButton("", BTN_CFG);
            nickBtn.setIcon(createUserIcon(14));
            nickBtn.setToolTipText("Definir nome de usuário para esta modpack");
            nickBtn.addActionListener(e -> {
                String cur     = loadNickForEntry(entry);
                String newNick = showNickPickerDialog(entry.name, cur);
                if (newNick != null) {
                    saveNickForEntry(entry, newNick);
                    updateNickLabel(entry, newNick);
                    // Nick trocado → descarta o launcher atual para forçar novo "Preparar"
                    // com a autenticação correta do nick novo.
                    resetEntryLauncher(entry);
                }
            });

            // ── Botão ⚙ Configurar ────────────────────────────────────────────────
            JButton configBtn = styledButton("⚙", BTN_CFG);
            configBtn.setToolTipText("Configurar perfil");
            configBtn.addActionListener(e -> configureModpack(entry));

            // ── Botão de ação único (estado variável) ─────────────────────────────
            JButton actionBtn = styledButton("Preparar", BTN_PREPARE);
            actionBtn.addActionListener(e -> onActionClicked(entry, actionBtn));

            btnPanel.add(logsBtn);
            btnPanel.add(nickBtn);
            btnPanel.add(configBtn);
            btnPanel.add(actionBtn);
            cardUiList.add(new CardUi(entry, actionBtn, configBtn, logsBtn, nickBtn, nickLabel));

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
        // ── Obtém o nick desta modpack específica ──────────────────────────────────
        String nick = loadNickForEntry(entry);

        if (nick == null || nick.isEmpty()) {
            // Sem nick definido: abre o popup de escolha (obrigatório)
            nick = showNickPickerDialog(entry.name, null);
            if (nick == null) return; // usuário cancelou — não lança o jogo
            saveNickForEntry(entry, nick);
            updateNickLabel(entry, nick);
        }

        final String playerName = nick;
        // Feedback imediato no botão
        applyButtonState(actionBtn, ActionState.PREPARING);
        // Reseta janela de logs para o novo launcher
        if (entry.logDialog != null) { entry.logDialog.dispose(); entry.logDialog = null; }
        // Cria launcher com autoPlay=false; o poller detecta quando fica READY
        entry.playLauncher = Main.launchModpack(entry.name, playerName, false, parentFrame, launcherArgs);
    }

    private void configureModpack(ModpackEntry entry) {
        if (entry.configureLauncher == null) {
            entry.configureLauncher = Main.launchModpack(entry.name, null, false, parentFrame, launcherArgs);
        }
        waitAndShowEditor(entry.configureLauncher, () -> resetEntryLauncher(entry));
    }

    private void waitAndShowEditor(Launcher launcher, Runnable onSaved) {
        javax.swing.Timer[] ref     = new javax.swing.Timer[1];
        javax.swing.Timer[] timeout = new javax.swing.Timer[1];

        ref[0] = new javax.swing.Timer(200, e -> {
            if (!launcher.getProfileManager().getProfiles().isEmpty()) {
                ref[0].stop();
                timeout[0].stop();   // ← cancela o timeout ao ter sucesso
                SwingUtilities.invokeLater(() -> {
                    Profile profile = launcher.getProfileManager().getSelectedProfile();
                    ProfileEditorPopup.showEditProfileDialog(launcher, profile, onSaved);
                });
            }
        });

        timeout[0] = new javax.swing.Timer(10_000, e -> {
            ref[0].stop();
            JOptionPane.showMessageDialog(parentFrame, "Tempo esgotado ao carregar perfis.", "Erro", JOptionPane.ERROR_MESSAGE);
        });
        timeout[0].setRepeats(false);
        timeout[0].start();
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
            boolean hasLauncher = cu.entry.playLauncher != null;
            if (cu.configBtn != null)
                cu.configBtn.setEnabled(state != ActionState.PLAYING && state != ActionState.DOWNLOADING);
            // Botão de logs aparece assim que o launcher for criado
            if (cu.logsBtn != null)
                cu.logsBtn.setVisible(hasLauncher);
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

    /**
     * Cria um ícone de usuário (silhueta de pessoa) desenhado via Graphics2D.
     * Usa desenho vetorial para garantir renderização correta em qualquer SO/JVM,
     * evitando a dependência de emojis que não renderizam no Java Swing do Linux.
     */
    private static ImageIcon createUserIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        // Cabeça: círculo centrado no terço superior
        int headDiam = Math.max(1, size * 5 / 12);
        int headX    = (size - headDiam) / 2;
        int headY    = size / 10;
        g.fillOval(headX, headY, headDiam, headDiam);
        // Corpo: semicírculo (ombros) no terço inferior
        int bodyW = Math.max(1, size * 3 / 4);
        int bodyH = Math.max(1, size / 2);
        int bodyX = (size - bodyW) / 2;
        int bodyY = size * 55 / 100;
        g.fillArc(bodyX, bodyY, bodyW, bodyH, 0, 180);
        g.dispose();
        return new ImageIcon(img);
    }

    private boolean isDirectoryEmpty(File dir) { String[] f = dir.list(); return f == null || f.length == 0; }

    // ── Nick por modpack ───────────────────────────────────────────────────
    /** Arquivo onde o nick desta modpack é salvo: <macrosoftBaseDir>/<modpack>/nick.txt */
    private File nickFileForEntry(ModpackEntry entry) {
        return new File(macrosoftBaseDir, entry.name + File.separator + "nick.txt");
    }

    /** Carrega o nick salvo para esta modpack, ou null se não definido. */
    private String loadNickForEntry(ModpackEntry entry) {
        try {
            File f = nickFileForEntry(entry);
            if (f.exists()) {
                String v = new String(Files.readAllBytes(f.toPath())).trim();
                return v.isEmpty() ? null : v;
            }
        } catch (IOException ignored) {}
        return null;
    }

    /** Persiste o nick da modpack em disco. */
    private void saveNickForEntry(ModpackEntry entry, String nick) {
        try {
            File f = nickFileForEntry(entry);
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), nick.getBytes());
        } catch (IOException ignored) {}
    }

    /** Texto exibido no label de nick do card. */
    private String nickLabelText(String nick) {
        return (nick != null && !nick.isEmpty()) ? "👤 " + nick : "👤 Sem nick definido";
    }

    /** Atualiza o nick label do card correspondente à entry. */
    private void updateNickLabel(ModpackEntry entry, String nick) {
        for (CardUi cu : cardUiList) {
            if (cu.entry == entry && cu.nickLabel != null) {
                cu.nickLabel.setText(nickLabelText(nick));
                break;
            }
        }
    }

    /**
     * Descarta o launcher atual da entry para que o processo de preparo seja
     * refeito com as configurações novas (nick ou perfil alterado).
     * – Se o jogo estiver JOGANDO: pede confirmação e encerra o processo.
     * – Se estiver INSTALANDO: pede confirmação antes de cancelar.
     * – Nos demais estados (PREPARANDO / PRONTO): reseta silenciosamente.
     */
    private void resetEntryLauncher(ModpackEntry entry) {
        if (entry.playLauncher == null) return; // já está no estado PREPARE, nada a fazer

        ActionState state = getActionState(entry);

        if (state == ActionState.PLAYING) {
            int opt = JOptionPane.showConfirmDialog(parentFrame,
                "O jogo está em execução.\nPara aplicar as alterações, o processo será encerrado. Deseja continuar?",
                "Jogo em execução", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (opt != JOptionPane.YES_OPTION) return;
            entry.playLauncher.getLaunchDispatcher().stopAll();

        } else if (state == ActionState.DOWNLOADING) {
            int opt = JOptionPane.showConfirmDialog(parentFrame,
                "Uma instalação está em andamento.\nPara aplicar as alterações, o processo será reiniciado. Deseja continuar?",
                "Instalação em andamento", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (opt != JOptionPane.YES_OPTION) return;
        }
        // PREPARING / READY / PLAYING (após parar) / DOWNLOADING (após confirmar): descarta o launcher
        entry.playLauncher = null;
        if (entry.logDialog != null) {
            entry.logDialog.dispose();
            entry.logDialog = null;
        }
        // O state poller detecta playLauncher == null e reverte o botão para "Preparar"
    }

    /**
     * Exibe popup modal para o usuário escolher/alterar o nick de uma modpack.
     * Valida: não vazio, sem espaços, entre 3 e 16 caracteres.
     * Retorna o nick escolhido, ou null se cancelado.
     */
    private String showNickPickerDialog(String modpackName, String currentNick) {
        while (true) {
            Object input = JOptionPane.showInputDialog(
                parentFrame,
                "<html><b>Nome de usuário para a modpack:</b> " + modpackName + "<br>"
                    + "<font color='gray'>Entre 3 e 16 caracteres, sem espaços.</font></html>",
                "👤 Usuário da Modpack",
                JOptionPane.PLAIN_MESSAGE,
                null, null,
                currentNick != null ? currentNick : "");

            if (input == null) return null; // usuário clicou em Cancelar

            String nick = input.toString().trim();

            if (nick.isEmpty()) {
                JOptionPane.showMessageDialog(parentFrame,
                    "O nome não pode estar vazio!", "Nome inválido", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (nick.contains(" ")) {
                JOptionPane.showMessageDialog(parentFrame,
                    "O nome não pode conter espaços!", "Nome inválido", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (nick.length() < 3 || nick.length() > 16) {
                JOptionPane.showMessageDialog(parentFrame,
                    "O nome deve ter entre 3 e 16 caracteres!", "Nome inválido", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            return nick;
        }
    }

    // ── Janela de logs ─────────────────────────────────────────────────────
    /**
     * Abre (ou traz ao foco) a janela de logs da modpack.
     * A janela tem duas abas: "Launcher" e "Jogo".
     * Usa o LauncherTabPanel do launcher interno, que já recebe os logs automaticamente.
     */
    private void openLogWindow(ModpackEntry entry) {
        if (entry.playLauncher == null) return;

        if (entry.logDialog == null || !entry.logDialog.isDisplayable()) {
            SwingUserInterface ui = (SwingUserInterface) entry.playLauncher.getUserInterface();
            LauncherTabPanel tabPanel = ui.getTabPanel();

            JDialog dialog = new JDialog(parentFrame, "Logs da " + entry.name, false);
            dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            dialog.setPreferredSize(new Dimension(720, 460));
            dialog.add(tabPanel, BorderLayout.CENTER);
            dialog.pack();
            dialog.setLocationRelativeTo(parentFrame);
            entry.logDialog = dialog;
        }

        entry.logDialog.setVisible(true);
        entry.logDialog.toFront();
    }

    private void openLink(String url) {
        try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new URI(url)); }
        catch (Exception ex) { JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE); }
    }

    // ── Configuração de servidor (para testes de rede) ─────────────────────
    /** Arquivo onde a URL personalizada da API é persistida. */
    private File apiServerFile() { return new File(macrosoftBaseDir, "api_server.txt"); }

    private void loadCustomApiUrl() {
        try {
            File f = apiServerFile();
            if (f.exists()) {
                String val = new String(Files.readAllBytes(f.toPath())).trim();
                customApiUrl = val.isEmpty() ? null : val;
            }
        } catch (IOException ignored) {}
    }

    private void saveCustomApiUrl(String url) {
        try {
            macrosoftBaseDir.mkdirs();
            Files.write(apiServerFile().toPath(), (url == null ? "" : url.trim()).getBytes());
        } catch (IOException ignored) {}
    }

    /**
     * Diálogo de configuração da URL da API.
     * Permite apontar para um servidor inexistente para testar comportamento offline.
     */
    private void showServerSettings() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));

        JLabel desc = new JLabel("<html><b>URL da API do servidor de modpacks</b><br>"
            + "<font color='gray'>Mantenha a URL padrão para usar o servidor oficial da Macrosoft<br>"
            + "</font></html>");
        panel.add(desc, BorderLayout.NORTH);

        // Mostra URL personalizada ou a URL padrão, para o usuário saber qual é
        String current = (customApiUrl != null) ? customApiUrl : API_URLS[0];
        JTextField urlField = new JTextField(current, 38);
        urlField.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JPanel fieldRow = new JPanel(new BorderLayout(4, 0));
        fieldRow.add(new JLabel("URL: "), BorderLayout.WEST);
        fieldRow.add(urlField, BorderLayout.CENTER);
        panel.add(fieldRow, BorderLayout.CENTER);

        String[] options = {"Salvar e recarregar", "Cancelar", "Restaurar padrão"};
        int choice = JOptionPane.showOptionDialog(parentFrame, panel,
            "⚙ Configuração do Servidor",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
            null, options, options[0]);

        if (choice == 0) {          // Salvar e recarregar
            String val = urlField.getText().trim();
            // Campo vazio ou igual à URL padrão → volta ao modo padrão (com fallback)
            boolean isDefault = val.isEmpty() || val.equals(API_URLS[0]);
            customApiUrl = isDefault ? null : val;
            saveCustomApiUrl(customApiUrl != null ? customApiUrl : "");
            showLoadingState();
            loadEntriesAsync();
        } else if (choice == 2) {   // Restaurar padrão
            customApiUrl = null;
            saveCustomApiUrl("");
            showLoadingState();
            loadEntriesAsync();
        }
    }
}
