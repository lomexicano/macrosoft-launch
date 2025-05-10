package net.minecraft.launcher.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class MacrosoftInit extends JFrame {

    private static final Color COLOR_BACKGROUND = new Color(22, 13, 28);
    private static final Color COLOR_FOREGROUND_TEXT = new Color(1, 131, 129);
    private static final String BASE_INIT_MESSAGE = "Initializing Macrosoft Launcher";

    private JLabel statusLabel;
    private Timer textAnimationTimer;
    private int dotCount = 0;

    public MacrosoftInit(String title) {
        super(title);

        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            System.err.println("Nimbus L&F não encontrado, usando o padrão. " + e.getMessage());
        }

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);

        try {
            BufferedImage image = ImageIO.read(this.getClass().getResource("/favicon.png"));
            if (image != null) {
                this.setIconImage(image);
            }
        } catch (IOException | NullPointerException e) {
            System.err.println("Falha ao carregar o ícone da janela (/favicon.png): " + e.getMessage());
        }

        JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
        contentPanel.setBackground(COLOR_BACKGROUND);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        statusLabel = new JLabel();
        statusLabel.setForeground(COLOR_FOREGROUND_TEXT);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setText(formatHtmlStatus(BASE_INIT_MESSAGE + "..."));
        contentPanel.add(statusLabel, BorderLayout.NORTH);

        JLabel animationLabel = new JLabel();
        animationLabel.setHorizontalAlignment(SwingConstants.CENTER);
        animationLabel.setOpaque(false); // <--- ADICIONADO PARA TENTAR CORRIGIR RASTROS DO GIF
        boolean gifLoaded = false;
        try {
            URL gifUrl = this.getClass().getResource("/macrosoft_animated_bg_small.gif");
            if (gifUrl != null) {
                animationLabel.setIcon(new ImageIcon(gifUrl));
                gifLoaded = true;
            } else {
                System.out.println("Arquivo GIF '/minecraft_loading.gif' não encontrado no classpath. Iniciando animação de texto.");
            }
        } catch (Exception e) {
            System.err.println("Falha ao carregar GIF de animação: " + e.getMessage());
        }
        contentPanel.add(animationLabel, BorderLayout.CENTER);
        
        this.setContentPane(contentPanel);

        if (!gifLoaded) {
            startTextAnimation();
        }

        this.pack();

        int currentWidth = this.getWidth();
        int currentHeight = this.getHeight();
        int minWidth = 250;
        int minHeight = 150;

        this.setMinimumSize(new Dimension(minWidth, minHeight));
        
        if (currentWidth < minWidth || currentHeight < minHeight) {
            this.setSize(Math.max(currentWidth, minWidth), Math.max(currentHeight, minHeight));
        }

        this.setLocationRelativeTo(null);

        SwingUtilities.invokeLater(() -> {
            setVisible(true);
        });
    }

    private String formatHtmlStatus(String text) {
        return "<html><div style='text-align: center;'>" + text + "</div></html>";
    }

    private void startTextAnimation() {
       if (textAnimationTimer != null && textAnimationTimer.isRunning()) {
           textAnimationTimer.stop();
       }
       dotCount = 0;
       textAnimationTimer = new Timer(500, e -> {
           dotCount = (dotCount % 3) + 1;
           StringBuilder dots = new StringBuilder();
           for (int i = 0; i < dotCount; i++) {
               dots.append(".");
           }
           statusLabel.setText(formatHtmlStatus(BASE_INIT_MESSAGE + dots.toString()));
       });
       textAnimationTimer.start();
    }

    private void stopTextAnimation() {
       if (textAnimationTimer != null && textAnimationTimer.isRunning()) {
           textAnimationTimer.stop();
       }
    }

    public void display() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }

    public void close() {
        stopTextAnimation();
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            dispose();
        });
    }

    public static void main(String[] args) {
        MacrosoftInit initWindow = new MacrosoftInit("Macrosoft Launcher Test");
        new Thread(() -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            initWindow.close();
        }).start();
    }
}