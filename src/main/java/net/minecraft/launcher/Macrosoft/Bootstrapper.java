package net.minecraft.launcher.Macrosoft;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.minecraft.launcher.LauncherConstants;

public class Bootstrapper {

    JFrame frame;
    File workingDir;
    ArrayList<JButton> contexts = new ArrayList<>();
    Map<String, String> modpackLinks = new HashMap<>();
    Map<String, String> modpackAuthors = new HashMap<>();

    private static final Color COLOR_BACKGROUND = new Color(22, 13, 28);
    private static final Color COLOR_BUTTON_BG = new Color(60, 60, 60);
    private static final Color COLOR_BUTTON_FG = Color.WHITE;
    private static final Color COLOR_LINK_BUTTON_FG = new Color(135, 206, 250);
    private static final Color COLOR_DOWNLOAD_BUTTON_BG = new Color(76, 175, 80);
    private static final Color COLOR_DOWNLOAD_BUTTON_FG = Color.WHITE;
    private static final int ICON_SIZE = 20;

    // Lista de URLs para buscar informações do launcher. A primeira é a principal.
    private static final String[] API_INFO_URLS = {
        "https://www.macrosoft.website/launcher/info?format=json", // URL principal
        "http://macrosoft.website/launcher/info?format=json",
        // Adicione URLs de fallback aqui, se houver:
        // "http://backup1.macrosoft.website/launcher/info?format=json",
        // "http://backup2.anotherdomain.com/launcher/info?format=json"
    };

    public Bootstrapper(File workingDir) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            System.err.println("Nimbus L&F not found, using default. " + e.getMessage());
        }

        boolean javaVersionOK = System.getProperty("java.runtime.version").matches("^1\\.8\\..*");

        System.out.println("Loading Macrosoft Bootstrapper...");
        System.out.println("Is Java " + System.getProperty("java.runtime.version") + " supported? " + javaVersionOK + " (it requires java 1.8.x)");

        this.workingDir = workingDir;

        // Valores padrão que podem ser sobrescritos pela API
        int apiLauncherVersion = LauncherConstants.MACROSOFT_VERSION; // Assume a versão atual até que a API informe outra
        int frameHeight = 130; // Altura padrão do menu
        String websiteLink = "https://www.macrosoft.website/"; // Link padrão do site
        String discordLink = "https://discord.gg/t7WcjJ4"; // Link padrão do Discord
        String downloadLinkForUpdate = websiteLink; // Link padrão para download de nova versão (inicialmente o site)

        frame = new JFrame("Macrosoft Launcher v" + LauncherConstants.MACROSOFT_VERSION);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBackground(COLOR_BACKGROUND);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel modpackButtonContainer = new JPanel();
        modpackButtonContainer.setLayout(new BoxLayout(modpackButtonContainer, BoxLayout.Y_AXIS));
        modpackButtonContainer.setBackground(COLOR_BACKGROUND);

        JSONObject infoFromApi = null;
        boolean apiInfoLoadedSuccessfully = false;

        for (String apiUrl : API_INFO_URLS) {
            try {
                System.out.println("Tentando carregar informações da API de: " + apiUrl);
                infoFromApi = Connector.get(apiUrl); // Tenta obter o JSONObject
                apiInfoLoadedSuccessfully = true;
                System.out.println("Informações carregadas com sucesso de: " + apiUrl);
                break; // Sai do loop, pois encontramos uma URL funcional
            } catch (IOException e) {
                System.err.println("Falha ao conectar com API em " + apiUrl + ": " + e.getMessage());
                // Continua para a próxima URL da lista
            } catch (JSONException e) {
                System.err.println("Falha ao parsear JSON da API em " + apiUrl + ": " + e.getMessage() + ". A resposta pode não ser um JSON válido.");
                infoFromApi = null; // Descarta JSON potencialmente inválido ou parcial
                // Continua para a próxima URL da lista
            }
        }

        if (apiInfoLoadedSuccessfully && infoFromApi != null) {
            try {
                // Extrai informações do JSON obtido
                apiLauncherVersion = infoFromApi.getInt("version");
                frameHeight = infoFromApi.getInt("menuHeight");
                websiteLink = infoFromApi.getString("site"); // Sobrescreve o padrão
                discordLink = infoFromApi.getString("discord"); // Sobrescreve o padrão
                
                // Tenta obter o link de download específico, senão usa o link do site
                try {
                    downloadLinkForUpdate = infoFromApi.getString("download");
                } catch (JSONException e) {
                    System.err.println("Campo 'download' (link para nova versão) não encontrado na API. Usando o link do site como fallback.");
                    downloadLinkForUpdate = websiteLink; // Fallback
                }

                JSONArray servers = infoFromApi.getJSONArray("servers");
                for (Object object : servers) {
                    JSONObject serverJson = (JSONObject) object;
                    String name = serverJson.getString("name");
                    String linkModPack = serverJson.getString("modpack_zip");
                    String authorModpack = serverJson.getString("modpack_author");

                    JButton button = createStyledModpackButton(name);
                    modpackLinks.put(name, linkModPack);
                    modpackAuthors.put(name, authorModpack);

                    String iconURL = serverJson.getString("icon");
                    try (InputStream stream = new URL(iconURL).openStream()) { // try-with-resources
                        BufferedImage image = ImageIO.read(stream);
                        if (image != null) {
                            Image resized = image.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
                            button.setIcon(new ImageIcon(resized));
                        }
                    } catch (IOException e) {
                        System.out.println("Não foi possível carregar o ícone de " + iconURL + ". Usando fallback.");
                        loadFallbackIcon(button, "/mc.png");
                    }
                    contexts.add(button);
                    modpackButtonContainer.add(button);
                    modpackButtonContainer.add(Box.createRigidArea(new Dimension(0, 5)));
                }
            } catch (JSONException e) {
                // Se o JSON foi obtido mas tem estrutura inválida para os campos esperados
                e.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Erro ao processar dados do servidor (formato inesperado).\nO launcher usará configurações padrão.", "Erro de Dados da API", JOptionPane.ERROR_MESSAGE);
                apiInfoLoadedSuccessfully = false; // Marca como falha para o restante da lógica
            }
        } else {
            System.err.println("Falha ao carregar informações de todas as URLs da API disponíveis.");
            JOptionPane.showMessageDialog(frame,
                    "Não foi possível conectar aos servidores Macrosoft para obter informações atualizadas.\n" +
                    "O launcher usará configurações padrão e pode ter funcionalidades online limitadas.",
                    "Servidores Indisponíveis", JOptionPane.WARNING_MESSAGE);
            // Valores padrão definidos no início do construtor serão usados.
            // Nenhum modpack da API será carregado nesta seção.
        }

        // Aviso de versão do Java (independente da API)
        /*
        if (!javaVersionOK) {
            JOptionPane.showMessageDialog(frame,
                    "Sua versão do Java (" + System.getProperty("java.runtime.version") +
                            ") não é totalmente suportada!\nPor favor, instale a versão Java 1.8.x para compatibilidade ótima.",
                    "Versão do Java Não Suportada", JOptionPane.WARNING_MESSAGE);
        }*/

        boolean newVersionAvailable = false;
        // Aviso de launcher desatualizado (somente se a API foi carregada com sucesso)
        if (apiInfoLoadedSuccessfully && apiLauncherVersion > LauncherConstants.MACROSOFT_VERSION) {
            newVersionAvailable = true;
            JOptionPane.showMessageDialog(frame,
                    "Seu launcher está desatualizado. Por favor, atualize para a versão " + apiLauncherVersion + ".\n" +
                    "Acesse nosso site ou canal do Discord para obter a versão mais recente.",
                    "Launcher Desatualizado", JOptionPane.WARNING_MESSAGE);
        }

        // ----- Construção da UI -----
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        headerPanel.setBackground(COLOR_BACKGROUND);
        try {
            BufferedImage image = ImageIO.read(this.getClass().getResource("/minecraft_logo.png"));
            if (image != null) {
                JLabel logoLabel = new JLabel(new ImageIcon(image));
                headerPanel.add(logoLabel);
            }
        } catch (IOException | NullPointerException e) {
            System.err.println("Falha ao carregar /minecraft_logo.png: " + e.getMessage());
            headerPanel.add(new JLabel("Minecraft Logo"));
        }
        contentPanel.add(headerPanel, BorderLayout.NORTH);

        JButton defaultButton = createStyledModpackButton("Other");
        loadFallbackIcon(defaultButton, "/mc.png");
        contexts.add(defaultButton);
        modpackButtonContainer.add(defaultButton); // Adiciona "Other" mesmo se API falhar

        JScrollPane modpackScrollPane = new JScrollPane(modpackButtonContainer);
        modpackScrollPane.setBorder(BorderFactory.createEmptyBorder());
        modpackScrollPane.getViewport().setBackground(COLOR_BACKGROUND);
        modpackScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        modpackScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        contentPanel.add(modpackScrollPane, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        footerPanel.setBackground(COLOR_BACKGROUND);

        // Botão para baixar nova versão (somente se disponível e API carregada)
        if (newVersionAvailable) {
            JButton downloadLatestButton = new JButton("Baixar v" + apiLauncherVersion);
            styleLinkButton(downloadLatestButton, true);
            downloadLatestButton.setBackground(COLOR_DOWNLOAD_BUTTON_BG);
            downloadLatestButton.setForeground(COLOR_DOWNLOAD_BUTTON_FG);
            // Usa o link de download da API, ou o link do site como fallback
            final String linkParaBaixar = (downloadLinkForUpdate != null && !downloadLinkForUpdate.isEmpty()) ? downloadLinkForUpdate : websiteLink;
            downloadLatestButton.addActionListener(e -> openLink(linkParaBaixar));
            footerPanel.add(downloadLatestButton);
        }

        // Botões de Website e Discord (usam 'websiteLink' e 'discordLink' que podem ter vindo da API ou são padrão)
        JButton websiteButton = new JButton("Website");
        styleLinkButton(websiteButton, false);
        final String finalWebsiteLink = websiteLink;
        websiteButton.addActionListener(e -> openLink(finalWebsiteLink));
        footerPanel.add(websiteButton);

        JButton discordButton = new JButton("Discord");
        styleLinkButton(discordButton, false);
        final String finalDiscordLink = discordLink;
        discordButton.addActionListener(e -> openLink(finalDiscordLink));
        footerPanel.add(discordButton);

        contentPanel.add(footerPanel, BorderLayout.SOUTH);

        try {
            BufferedImage image = ImageIO.read(this.getClass().getResource("/favicon.png"));
            if (image != null) {
                frame.setIconImage(image);
            }
        } catch (IOException | NullPointerException e) {
            System.err.println("Falha ao carregar /favicon.png para ícone do frame: " + e.getMessage());
        }
        
        frame.setContentPane(contentPanel);
        // Usa frameHeight que pode ter sido atualizado pela API ou é o padrão
        frame.setSize(new Dimension(380, Math.max(frameHeight, 400))); 
        frame.setMinimumSize(new Dimension(380, 300));
        frame.setLocationRelativeTo(null);
    }

    // ... métodos createStyledModpackButton, loadFallbackIcon, styleLinkButton, openLink, run, isDirectoryEmpty ...
    // (Estes métodos permanecem os mesmos da sua versão anterior)
    private JButton createStyledModpackButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(COLOR_BUTTON_BG);
        button.setForeground(COLOR_BUTTON_FG);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setMargin(new Insets(8, 15, 8, 15)); // Padding inside button
        button.setHorizontalAlignment(SwingConstants.LEFT); // Align text to left if icon is present
        button.setIconTextGap(10); // Gap between icon and text
        button.setFocusPainted(false);
        button.setAlignmentX(Component.CENTER_ALIGNMENT); 
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height + 10)); // Altura um pouco maior para clique
        return button;
    }

    private void loadFallbackIcon(JButton button, String resourcePath) {
        try {
            BufferedImage image = ImageIO.read(this.getClass().getResource(resourcePath));
            if (image != null) {
                Image resized = image.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
                button.setIcon(new ImageIcon(resized));
            }
        } catch (IOException | NullPointerException e) { // Captura NullPointerException se getResource falhar
            System.err.println("Falha ao carregar ícone de fallback " + resourcePath + ": " + e.getMessage());
        }
    }

    private void styleLinkButton(JButton button, boolean prominent) {
        button.setFont(new Font("SansSerif", prominent ? Font.BOLD : Font.PLAIN, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        if (prominent) {
            button.setOpaque(true); 
            // Cor de fundo e texto já definidas para o botão de download
        } else {
            button.setContentAreaFilled(false);
            button.setForeground(COLOR_LINK_BUTTON_FG);
        }
        button.setMargin(new Insets(5,10,5,10));
    }

    private void openLink(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new URI(url));
            } else {
                JOptionPane.showMessageDialog(frame, "Não é possível abrir o link: operações de Desktop não suportadas.", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Erro ao abrir link: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void run(ActionListener onload, ActionListener listener) {
        for (JButton jButton : contexts) {
            jButton.addActionListener(e -> {
                frame.setVisible(false); 

                String context = ((JButton) e.getSource()).getText();
                String resourceLink = modpackLinks.get(context);
                String authorModPack = modpackAuthors.get(context);

                File contextDir = new File(workingDir, context + File.separator);
                boolean downloadRequired = (!contextDir.exists() || isDirectoryEmpty(contextDir)) && (resourceLink != null && !resourceLink.isEmpty());

                if (downloadRequired) {
                    System.out.println("Baixando modpack para: " + context);
                    if (!contextDir.mkdirs() && !contextDir.exists()) { // Garante que mkdris não falhou e o diretório realmente não existe
                         System.err.println("Não foi possível criar o diretório: " + contextDir.getAbsolutePath());
                         JOptionPane.showMessageDialog(null, "Erro ao criar diretório para " + context, "Erro de Download", JOptionPane.ERROR_MESSAGE);
                         // Se não puder criar o diretório, não adianta prosseguir com Downloader
                         // Chama o listener principal, talvez ele lide com o modpack não baixado.
                         // E garante que o frame do Bootstrapper seja fechado.
                         listener.actionPerformed(e); 
                         frame.dispose(); 
                         return;
                    }
                    new Downloader(resourceLink, contextDir,
                            "Baixando " + context + " modpack por <i>" + authorModPack + "</i>...",
                            listener, e); 
                    frame.dispose(); 
                } else {
                    if (resourceLink == null || resourceLink.isEmpty()) {
                        System.out.println("Nenhuma fonte de modpack definida para " + context + ". Verificação de download pulada.");
                    } else if (contextDir.exists() && !isDirectoryEmpty(contextDir)) {
                        System.out.println("Modpack " + context + " já existe. Download pulado.");
                    }
                    listener.actionPerformed(e); 
                    // Se o listener não fechar este frame, ele permanecerá oculto.
                    // Se necessário, adicionar frame.dispose(); aqui também se o listener não o fizer.
                }
            });
        }

        if (onload != null) {
            onload.actionPerformed(null);
        }

        frame.setVisible(true);
    }
    
    private boolean isDirectoryEmpty(File directory) {
        if (directory.exists() && directory.isDirectory()) {
            String[] files = directory.list();
            return files == null || files.length == 0;
        }
        return true; 
    }
}