package net.minecraft.launcher.ui.tabs;

import com.mojang.launcher.events.GameOutputLogProcessor;
import com.mojang.launcher.game.process.GameProcess;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import net.minecraft.launcher.Launcher;

public class GameOutputTab
extends JScrollPane
implements GameOutputLogProcessor {
    private static final Font MONOSPACED = new Font("Monospaced", 0, 12);
    private static final int MAX_LINE_COUNT = 1000;
    /** Intervalo de flush do buffer de linhas para a EDT (milissegundos). */
    private static final int FLUSH_INTERVAL_MS = 100;

    private final JTextArea console = new JTextArea();
    private final JPopupMenu popupMenu = new JPopupMenu();
    private final JMenuItem copyTextButton = new JMenuItem("Copy All Text");
    private final Launcher minecraftLauncher;
    private boolean alreadyCensored = false;

    /**
     * Quando false (launcher oculto durante o jogo), linhas recebidas são descartadas
     * imediatamente em vez de acumuladas na fila — evita crescimento ilimitado da heap
     * e a pressão de memória/GC que afeta o processo do Minecraft.
     * Volatile porque é escrito pela EDT e lido pela thread do monitor.
     */
    private volatile boolean active = true;

    /** Buffer thread-safe para acumular linhas antes de despejar na EDT. */
    private final ConcurrentLinkedQueue<String> pendingLines = new ConcurrentLinkedQueue<>();
    /** Timer Swing (roda na EDT) que drena o buffer periodicamente. */
    private final Timer flushTimer;

    public GameOutputTab(Launcher minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
        this.popupMenu.add(this.copyTextButton);
        this.console.setComponentPopupMenu(this.popupMenu);
        this.copyTextButton.addActionListener(new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    StringSelection ss = new StringSelection(GameOutputTab.this.console.getText());
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
                } catch (Exception ex) {
                    // ignorado
                }
            }
        });
        this.console.setFont(MONOSPACED);
        this.console.setEditable(false);
        this.console.setMargin(null);
        this.setViewportView(this.console);
        this.console.getDocument().addDocumentListener(new DocumentListener(){
            @Override
            public void insertUpdate(DocumentEvent e) {
                // Remoção de linhas antigas já ocorre no flush — sem invokeLater extra.
                Document document = GameOutputTab.this.console.getDocument();
                Element root = document.getDefaultRootElement();
                while (root.getElementCount() > MAX_LINE_COUNT + 1) {
                    try {
                        document.remove(0, root.getElement(0).getEndOffset());
                    } catch (BadLocationException ignored) {}
                }
            }
            @Override public void removeUpdate(DocumentEvent e) {}
            @Override public void changedUpdate(DocumentEvent e) {}
        });

        // Timer que roda na EDT e drena o buffer de linhas pendentes de uma vez.
        this.flushTimer = new Timer(FLUSH_INTERVAL_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                GameOutputTab.this.flushPendingLines();
            }
        });
        this.flushTimer.setCoalesce(true);
        this.flushTimer.start();
    }

    /** Drena o buffer de linhas pendentes e as insere no console (deve rodar na EDT). */
    private void flushPendingLines() {
        if (pendingLines.isEmpty()) return;
        Document document = this.console.getDocument();
        JScrollBar scrollBar = this.getVerticalScrollBar();
        boolean shouldScroll = this.getViewport().getView() == this.console
                && (double) scrollBar.getValue() + scrollBar.getSize().getHeight()
                        + (double) (MONOSPACED.getSize() * 4) > (double) scrollBar.getMaximum();

        // Drena todas as linhas de uma vez para minimizar repinturas.
        List<String> batch = new ArrayList<>();
        String line;
        while ((line = pendingLines.poll()) != null) {
            batch.add(line);
        }
        if (batch.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (String l : batch) {
            sb.append(l);
        }
        try {
            document.insertString(document.getLength(), sb.toString(), null);
        } catch (BadLocationException ignored) {}

        if (shouldScroll) {
            scrollBar.setValue(Integer.MAX_VALUE);
        }
    }

    public Launcher getMinecraftLauncher() {
        return this.minecraftLauncher;
    }

    /**
     * Para o timer e descarta linhas enquanto o launcher está oculto.
     * A thread do monitor continua drenando o pipe (sem backpressure),
     * mas nenhuma linha é enfileirada — heap do launcher permanece estável.
     */
    public void pauseFlush() {
        active = false;
        flushTimer.stop();
        pendingLines.clear();
    }

    /**
     * Retoma o processamento de linhas e o timer de flush.
     */
    public void resumeFlush() {
        active = true;
        flushTimer.start();
    }

    /**
     * Encaminha uma linha para o buffer assíncrono.
     * Nunca bloqueia a thread chamadora; a EDT consome o buffer via timer.
     */
    public void print(final String line) {
        pendingLines.add(line);
        // Se já estivermos na EDT e o timer ainda não disparou, fazemos flush imediato
        // para manter responsividade quando o output é gerado dentro da EDT.
        if (SwingUtilities.isEventDispatchThread()) {
            flushPendingLines();
        }
    }

    @Override
    public void onGameOutput(GameProcess process, String logLine) {
        // Quando oculto: descarta a linha sem enfileirar.
        // A detecção do crash magic (#@!@#) ocorre na thread do monitor,
        // independente deste método — crash detection não é afetado.
        if (!active) return;
        if (!this.alreadyCensored) {
            int index = logLine.indexOf("(Session ID is");
            if (index > 0) {
                this.alreadyCensored = true;
                logLine = logLine.substring(0, index) + "(Session ID is <censored>)";
            }
        }
        this.print(logLine + "\n");
    }
}

