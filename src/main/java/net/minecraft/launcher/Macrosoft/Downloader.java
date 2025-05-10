package net.minecraft.launcher.Macrosoft;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

public class Downloader {

    private JDialog dialog;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JButton cancelButton; // Added cancel button
    private Worker worker;
    private File tempDownloadFile;

    private static final Color COLOR_BACKGROUND_DIALOG = new Color(30, 30, 30);
    private static final Color COLOR_TEXT_FOREGROUND = Color.WHITE;
    private static final Color COLOR_PROGRESS_BAR_FOREGROUND = new Color(76, 175, 80);
    private static final Color COLOR_CANCEL_BUTTON_BG = new Color(100, 100, 100);
    private static final Color COLOR_CANCEL_BUTTON_FG = Color.WHITE;


    public Downloader(String site, File outputDir, String label, ActionListener callback, ActionEvent callbackArgument) {

        try {
            if (!"Nimbus".equals(UIManager.getLookAndFeel().getName())) {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not set Nimbus L&F for Downloader: " + e.getMessage());
        }

        JFrame parentFrame = findActiveFrame();
        dialog = new JDialog(parentFrame, "Download em Progresso", true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        try {
            BufferedImage image = ImageIO.read(this.getClass().getResource("/favicon.png"));
            if (image != null) {
                dialog.setIconImage(image);
            }
        } catch (IOException | NullPointerException e) {
            System.err.println("Failed to load dialog icon /favicon.png: " + e.getMessage());
        }

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(COLOR_BACKGROUND_DIALOG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel titleMessageLabel = new JLabel("<html><div style='text-align: center; width: 280px;'><b>" + label + "</b></div></html>");
        titleMessageLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        titleMessageLabel.setForeground(COLOR_TEXT_FOREGROUND);
        titleMessageLabel.setHorizontalAlignment(JLabel.CENTER);
        mainPanel.add(titleMessageLabel, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("SansSerif", Font.BOLD, 12));
        progressBar.setForeground(COLOR_PROGRESS_BAR_FOREGROUND);
        progressBar.setBackground(COLOR_BACKGROUND_DIALOG.brighter());

        statusLabel = new JLabel("<html><span>Conectando ao servidor...</span></html>");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(COLOR_TEXT_FOREGROUND.brighter());
        statusLabel.setHorizontalAlignment(JLabel.CENTER);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(COLOR_BACKGROUND_DIALOG);
        centerPanel.add(progressBar);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        centerPanel.add(statusLabel);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Cancel Button
        cancelButton = new JButton("Cancelar");
        styleCancelButton(cancelButton);
        cancelButton.addActionListener(e -> {
            if (worker != null && !worker.isDone()) {
                worker.cancel(true); // Attempt to cancel the SwingWorker
                cancelButton.setEnabled(false); // Disable button after clicking
                statusLabel.setText("<html><span>Cancelando...</span></html>");
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); // Center the cancel button
        buttonPanel.setBackground(COLOR_BACKGROUND_DIALOG);
        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);


        dialog.setContentPane(mainPanel);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(400, dialog.getHeight() + 10)); // Adjusted height for button
        dialog.setLocationRelativeTo(parentFrame);

        try {
            tempDownloadFile = File.createTempFile("macrosoft_download_", ".zip");
            tempDownloadFile.deleteOnExit();
        } catch (IOException e) {
            e.printStackTrace();
            showErrorDialog("Erro ao criar arquivo temporário: " + e.getMessage(), true);
            // No callback should be involved here as it's a setup error.
            return;
        }

        worker = new Worker(site, tempDownloadFile, outputDir, statusLabel, progressBar, callback, callbackArgument);
        worker.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("progress".equals(evt.getPropertyName())) {
                    progressBar.setValue((Integer) evt.getNewValue());
                } else if ("state".equals(evt.getPropertyName()) && SwingWorker.StateValue.DONE == evt.getNewValue()) {
                    handleWorkerDone(callback, callbackArgument);
                }
            }
        });

        worker.execute();
        dialog.setVisible(true);
    }

    private void styleCancelButton(JButton button) {
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setBackground(COLOR_CANCEL_BUTTON_BG);
        button.setForeground(COLOR_CANCEL_BUTTON_FG);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(COLOR_CANCEL_BUTTON_BG.darker(), 1),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private JFrame findActiveFrame() {
        for (java.awt.Frame frame : JFrame.getFrames()) {
            if (frame.isActive() && frame.isVisible()) {
                return (JFrame) frame;
            }
        }
        return null;
    }


    private void handleWorkerDone(ActionListener callback, ActionEvent callbackArgument) {
        try {
            worker.get();
            // Success: callback is called only if not cancelled
            if (!worker.isCancelled() && callback != null) {
                callback.actionPerformed(callbackArgument);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            showErrorDialog("Download interrompido.", false);
        } catch (CancellationException e) {
            showErrorDialog("Download cancelado!", false);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            showErrorDialog("Falha no download: " + cause.getMessage(), false);
            cause.printStackTrace();
        } finally {
            cancelButton.setEnabled(false); // Ensure cancel button is disabled on completion
            cleanupAndCloseDialog();
        }
    }

    private void showErrorDialog(String message, boolean isSetupError) {
        // Ensure dialog exists and is visible before trying to use it as parent for JOptionPane
        Component parentComponent = (dialog != null && dialog.isVisible()) ? dialog : findActiveFrame();
        JOptionPane.showMessageDialog(parentComponent, message, "Status do Download", 
                                      (message.toLowerCase().contains("cancelado") || message.toLowerCase().contains("interrompido")) ? JOptionPane.WARNING_MESSAGE : JOptionPane.ERROR_MESSAGE);
    }
    
    private void cleanupAndCloseDialog() {
        if (tempDownloadFile != null && tempDownloadFile.exists()) {
            if (!tempDownloadFile.delete()) {
                System.err.println("Falha ao deletar arquivo temporário: " + tempDownloadFile.getAbsolutePath());
            }
        }
        if (dialog != null && dialog.isVisible()) {
            dialog.dispose();
        }
    }
}

class Worker extends SwingWorker<Void, String> {
    private String site;
    private File tempFile;
    private File outputDir;
    private ActionListener callback;
    private ActionEvent callbackArgument;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    public Worker(String site, File tempFile, File outputDir,
                  JLabel statusLabel, JProgressBar progressBar,
                  ActionListener callback, ActionEvent callbackArgument) {
        this.site = site;
        this.tempFile = tempFile;
        this.outputDir = outputDir;
        this.statusLabel = statusLabel;
        this.progressBar = progressBar;
        this.callback = callback;
        this.callbackArgument = callbackArgument;
    }

    @Override
    protected Void doInBackground() throws Exception {
        URL url = new URL(site);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        long filesize = connection.getContentLengthLong();
        if (filesize == -1) {
            publish("Tamanho do arquivo desconhecido. Prosseguindo...");
        }

        long totalDataRead = 0;

        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream fos = new FileOutputStream(tempFile);
             BufferedOutputStream bout = new BufferedOutputStream(fos, 8192)) {

            byte[] data = new byte[8192];
            int bytesRead;
            publish("Iniciando download...");

            while ((bytesRead = in.read(data, 0, data.length)) >= 0) {
                if (isCancelled()) { // Check for cancellation
                    throw new CancellationException("Download cancelado durante o progresso.");
                }
                bout.write(data, 0, bytesRead);
                totalDataRead += bytesRead;

                if (filesize > 0) {
                    int progress = (int) ((totalDataRead * 100) / filesize);
                    setProgress(progress);
                    publish(String.format("%.2f MB / %.2f MB (%d%%)",
                            totalDataRead / (1024.0 * 1024.0),
                            filesize / (1024.0 * 1024.0),
                            progress));
                } else {
                    publish(String.format("%.2f MB baixados", totalDataRead / (1024.0 * 1024.0)));
                }
            }
        } catch (IOException e) {
             if (isCancelled()) { // If IOException was due to cancellation (e.g. stream closed)
                throw new CancellationException("Download cancelado devido a interrupção.");
            }
            throw new IOException("Erro durante o download: " + e.getMessage(), e);
        } finally {
             connection.disconnect();
        }

        if (isCancelled()) { // Check before unzipping
            throw new CancellationException("Download cancelado antes de descompactar.");
        }

        publish("Download concluído. Descompactando...");
        TimeUnit.MILLISECONDS.sleep(200); // Brief pause for user to read

        try {
            ZipFile zipFile = new ZipFile(tempFile);
            if (!outputDir.exists()) {
                if (!outputDir.mkdirs()) {
                    throw new IOException("Não foi possível criar o diretório de destino: " + outputDir.getAbsolutePath());
                }
            }
            // Note: zip4j's extractAll is blocking. If the thread is interrupted
            // during this, it might not respond immediately to cancellation.
            // The isCancelled() checks are primarily for before/after major blocking operations.
            zipFile.extractAll(outputDir.getPath());
            
            if (isCancelled()) { // Check if cancelled during the (unlikely) short period extractAll might have taken
                 throw new CancellationException("Descompactação cancelada.");
            }
            publish("Arquivos descompactados com sucesso!");
        } catch (ZipException e) {
            if (isCancelled()) {
                 throw new CancellationException("Descompactação cancelada.");
            }
            throw new ZipException("Erro ao descompactar o arquivo: " + e.getMessage(), e);
        }
        
        TimeUnit.MILLISECONDS.sleep(1000);
        return null;
    }

    @Override
    protected void process(List<String> chunks) {
        if (!chunks.isEmpty()) {
            String latestStatus = chunks.get(chunks.size() - 1);
            statusLabel.setText("<html><span>" + latestStatus + "</span></html>");
        }
    }

    @Override
    protected void done() {
        try {
            get(); 
            if (!isCancelled()) {
                 progressBar.setValue(100);
            }
        } catch (CancellationException e) {
             statusLabel.setText("<html><span style='color:orange;'>Download cancelado.</span></html>"); // Orange for cancelled
        } catch (Exception e) {
            // Errors are primarily handled by the PropertyChangeListener's call to handleWorkerDone
            // but can set a final status here too.
            statusLabel.setText("<html><span style='color:red;'>Falha no processo.</span></html>"); // Red for error
        }
    }
}