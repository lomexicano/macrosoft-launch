package net.minecraft.launcher;

import com.mojang.launcher.OperatingSystem;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.minecraft.launcher.Macrosoft.MacrosoftModpackBrowser;
import net.minecraft.launcher.ui.MacrosoftInit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger();
    private static String macrosoftLauncherContext = "";

    /** Define qual modpack/contexto será usado ao inicializar o Launcher. */
    public static void setContext(String context) {
        macrosoftLauncherContext = context;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Tela de splash enquanto o frame principal é montado
            MacrosoftInit initPanel = new MacrosoftInit("Welcome ♥️");

            // ── Criar frame principal ──────────────────────────────────────
            JFrame frame = new JFrame("Macrosoft Launcher");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setPreferredSize(new Dimension(640, 520));
            frame.setMinimumSize(new Dimension(500, 400));
            try {
                BufferedImage icon = ImageIO.read(Main.class.getResource("/favicon.png"));
                if (icon != null) frame.setIconImage(icon);
            } catch (IOException | NullPointerException ignored) {}

            // ── Diretório base .macrosoft (junto do jar/cwd) ──────────────
            File macrosoftDir = new File(System.getProperty("user.dir", "."), ".macrosoft");

            // ── Painel de seleção de modpacks (carrega dados async) ────────
            MacrosoftModpackBrowser browser = new MacrosoftModpackBrowser(macrosoftDir, frame, args);
            frame.setContentPane(browser);
            frame.pack();
            frame.setLocationRelativeTo(null);

            // Fechar splash e exibir o browser após breve momento
            javax.swing.Timer t = new javax.swing.Timer(400, e -> {
                initPanel.close();
                frame.setVisible(true);
            });
            t.setRepeats(false);
            t.start();
        });
    }

    /**
     * Inicializa o Launcher de Minecraft dentro do frame já existente,
     * usando o contexto (modpack) previamente definido em {@link #setContext}.
     */
    public static void startLauncherInFrame(JFrame frame, String[] args) {
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
        parser.accepts("winTen");
        ArgumentAcceptingOptionSpec<String> proxyHostOption =
            parser.accepts("proxyHost").withRequiredArg();
        ArgumentAcceptingOptionSpec<Integer> proxyPortOption =
            parser.accepts("proxyPort").withRequiredArg()
                  .defaultsTo("8080", new String[0]).ofType(Integer.class);
        ArgumentAcceptingOptionSpec<File> workDirOption =
            parser.accepts("workDir").withRequiredArg()
                  .ofType(File.class).defaultsTo(Main.getWorkingDirectory(), new File[0]);
        NonOptionArgumentSpec<String> nonOption = parser.nonOptions();

        OptionSet optionSet = parser.parse(args);
        List<String> leftoverArgs = optionSet.valuesOf(nonOption);

        String hostName = optionSet.valueOf(proxyHostOption);
        Proxy proxy = Proxy.NO_PROXY;
        if (hostName != null) {
            try {
                proxy = new Proxy(Proxy.Type.SOCKS,
                    new InetSocketAddress(hostName, (int) optionSet.valueOf(proxyPortOption)));
            } catch (Exception ignored) {}
        }

        File workingDirectory = optionSet.valueOf(workDirOption);
        workingDirectory.mkdirs();

        if (optionSet.has("winTen")) {
            System.setProperty("os.name", "Windows 10");
            System.setProperty("os.version", "10.0");
        }

        LOGGER.debug("Starting launcher in existing frame for context: " + macrosoftLauncherContext);
        Proxy finalProxy = proxy;
        Launcher launcher = new Launcher(
            frame, workingDirectory, finalProxy, null,
            leftoverArgs.toArray(new String[0]), 100);

        if (optionSet.has("winTen")) {
            launcher.setWinTenHack();
        }
        LOGGER.debug("Launcher initialized.");
    }

    /**
     * Inicializa um Launcher headless para um modpack, opcionalmente iniciando o jogo.
     * O browser frame é passado apenas como referência para diálogos filhos.
     *
     * @param modpackName  nome do subdiretório em .macrosoft/
     * @param playerName   nick do player (pode ser null se autoPlay=false)
     * @param autoPlay     true = lança o jogo imediatamente após login
     * @param browserFrame frame do browser (não será modificado)
     * @param args         argumentos originais do lançador (proxy, etc.)
     * @return a instância de Launcher criada
     */
    public static Launcher launchModpack(String modpackName, String playerName,
                                          boolean autoPlay, JFrame browserFrame, String[] args) {
        setContext(modpackName);

        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
        parser.accepts("winTen");
        ArgumentAcceptingOptionSpec<String> proxyHostOpt =
            parser.accepts("proxyHost").withRequiredArg();
        ArgumentAcceptingOptionSpec<Integer> proxyPortOpt =
            parser.accepts("proxyPort").withRequiredArg()
                  .defaultsTo("8080", new String[0]).ofType(Integer.class);
        NonOptionArgumentSpec<String> nonOpt = parser.nonOptions();
        OptionSet opts = parser.parse(args);

        Proxy proxy = Proxy.NO_PROXY;
        String host = opts.valueOf(proxyHostOpt);
        if (host != null) {
            try {
                proxy = new Proxy(Proxy.Type.SOCKS,
                    new InetSocketAddress(host, (int) opts.valueOf(proxyPortOpt)));
            } catch (Exception ignored) {}
        }
        if (opts.has("winTen")) {
            System.setProperty("os.name", "Windows 10");
            System.setProperty("os.version", "10.0");
        }

        File workingDir = getWorkingDirectory();
        workingDir.mkdirs();
        String[] leftover = opts.valuesOf(nonOpt).toArray(new String[0]);

        Launcher.configureNext(playerName, autoPlay, true /* suppressUI */);
        Launcher launcher = new Launcher(browserFrame, workingDir, proxy, null, leftover, 100);
        if (opts.has("winTen")) launcher.setWinTenHack();

        LOGGER.debug("launchModpack: context={} autoPlay={}", modpackName, autoPlay);
        return launcher;
    }

    public static File getWorkingDirectory() {
        String userHome = System.getProperty("user.dir", ".");
        switch (OperatingSystem.getCurrentPlatform()) {
            case LINUX:
            case WINDOWS:
                return new File(userHome, ".macrosoft/" + macrosoftLauncherContext + "/");
            case OSX:
                return new File(userHome, "Library/Application Support/macrosoft/" + macrosoftLauncherContext);
            default:
                return new File(userHome, "macrosoft/.macrosoft/" + macrosoftLauncherContext + "/");
        }
    }
}
