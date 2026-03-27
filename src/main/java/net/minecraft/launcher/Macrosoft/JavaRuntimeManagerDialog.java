package net.minecraft.launcher.Macrosoft;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JavaRuntimeManagerDialog extends JDialog {

    private final Path macrosoftBaseDir;
    private final List<JavaRuntimeManager.JavaRuntimeOption> platformOptions;

    private final DefaultListModel<String> listModel = new DefaultListModel<String>();
    private final JList<String> runtimeList = new JList<String>(listModel);
    private List<Row> rows = new ArrayList<Row>();
    private final JProgressBar installProgress = new JProgressBar(0, 100);
    private final JLabel progressLabel = new JLabel("Pronto");

    private static class Row {
        final JavaRuntimeManager.JavaRuntimeOption option;
        final JavaRuntimeManager.InstalledRuntime installed;

        Row(JavaRuntimeManager.JavaRuntimeOption option, JavaRuntimeManager.InstalledRuntime installed) {
            this.option = option;
            this.installed = installed;
        }
    }

    public JavaRuntimeManagerDialog(JFrame owner,
                                    Path macrosoftBaseDir,
                                    List<JavaRuntimeManager.JavaRuntimeOption> allApiOptions) {
        super(owner, "Gerenciar Java (Macrosoft)", true);
        this.macrosoftBaseDir = macrosoftBaseDir;
        this.platformOptions = JavaRuntimeManager.filterForCurrentPlatform(allApiOptions);

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel desc = new JLabel("Selecione um Java da plataforma atual para instalar/remover.");
        add(desc, BorderLayout.NORTH);

        runtimeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        runtimeList.setVisibleRowCount(8);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.add(new JScrollPane(runtimeList), BorderLayout.CENTER);

        installProgress.setStringPainted(true);
        installProgress.setValue(0);
        installProgress.setForeground(new Color(41, 171, 226));
        installProgress.setBackground(new Color(36, 36, 36));
        installProgress.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));

        progressLabel.setFont(progressLabel.getFont().deriveFont(Font.BOLD, 12f));
        progressLabel.setForeground(new Color(25, 110, 180));

        JPanel progressPanel = new JPanel(new BorderLayout(0, 4));
        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(installProgress, BorderLayout.CENTER);
        center.add(progressPanel, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton refreshBtn = new JButton("Atualizar");
        JButton installBtn = new JButton("Instalar");
        JButton uninstallBtn = new JButton("Desinstalar");
        JButton closeBtn = new JButton("Fechar");

        refreshBtn.addActionListener(e -> refreshList());
        installBtn.addActionListener(e -> installSelected());
        uninstallBtn.addActionListener(e -> uninstallSelected());
        closeBtn.addActionListener(e -> dispose());

        actions.add(refreshBtn);
        actions.add(installBtn);
        actions.add(uninstallBtn);
        actions.add(closeBtn);
        add(actions, BorderLayout.SOUTH);

        refreshList();
        setSize(700, 360);
        setLocationRelativeTo(owner);
    }

    private void refreshList() {
        listModel.clear();
        rows = new ArrayList<Row>();
        Map<String, JavaRuntimeManager.InstalledRuntime> installedById = JavaRuntimeManager.mapInstalledById(macrosoftBaseDir);

        if (platformOptions.isEmpty()) {
            listModel.addElement("Nenhuma opção de Java disponível para este sistema no JSON do servidor.");
            installProgress.setValue(0);
            installProgress.setString("Sem opções");
            progressLabel.setText("Sem opções para esta plataforma");
            return;
        }

        for (JavaRuntimeManager.JavaRuntimeOption option : platformOptions) {
            JavaRuntimeManager.InstalledRuntime installed = installedById.get(option.id);
            String status = (installed == null) ? "[não instalado]" : "[instalado]";
            String line = String.format("%s  %s  -  %s", status, option.id, option.url);
            listModel.addElement(line);
            rows.add(new Row(option, installed));
        }

        if (!rows.isEmpty()) {
            runtimeList.setSelectedIndex(0);
        }
        installProgress.setValue(0);
        installProgress.setString("Pronto");
        progressLabel.setText("Pronto");
    }

    private void installSelected() {
        Row row = selectedRow();
        if (row == null) {
            return;
        }
        final JavaRuntimeManager.JavaRuntimeOption option = row.option;

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        runtimeList.setEnabled(false);
        installProgress.setValue(0);
        installProgress.setString("0%");
        progressLabel.setText("Preparando instalação de " + option.id + "...");

        new SwingWorker<JavaRuntimeManager.InstalledRuntime, Void>() {
            @Override
            protected JavaRuntimeManager.InstalledRuntime doInBackground() throws Exception {
                return JavaRuntimeManager.install(macrosoftBaseDir, option, (stage, percent, detail) ->
                    SwingUtilities.invokeLater(() -> {
                        installProgress.setValue(Math.max(0, Math.min(100, percent)));
                        installProgress.setString(percent + "%");
                        String icon = "download".equals(stage) ? "⬇" : ("extract".equals(stage) ? "📦" : "✅");
                        progressLabel.setText(icon + " " + detail);
                    })
                );
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                runtimeList.setEnabled(true);
                try {
                    JavaRuntimeManager.InstalledRuntime runtime = get();
                    installProgress.setValue(100);
                    installProgress.setString("100%");
                    progressLabel.setText("✅ Instalação concluída");
                    JOptionPane.showMessageDialog(JavaRuntimeManagerDialog.this,
                        "Java instalado com sucesso:\n" + runtime.javaExecutable,
                        "Instalação concluída", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    installProgress.setValue(0);
                    installProgress.setString("Erro");
                    progressLabel.setText("❌ Falha na instalação");
                    JOptionPane.showMessageDialog(JavaRuntimeManagerDialog.this,
                        "Falha ao instalar Java:\n" + ex.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);
                }
                refreshList();
            }
        }.execute();
    }

    private void uninstallSelected() {
        Row row = selectedRow();
        if (row == null) {
            return;
        }
        if (row.installed == null) {
            JOptionPane.showMessageDialog(this,
                "Esse Java ainda não está instalado.",
                "Desinstalação", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Deseja remover o Java '" + row.installed.id + "'?",
            "Confirmar desinstalação",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            JavaRuntimeManager.uninstall(macrosoftBaseDir, row.installed);
            JOptionPane.showMessageDialog(this,
                "Java removido com sucesso.",
                "Desinstalação", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Falha ao remover Java:\n" + ex.getMessage(),
                "Erro", JOptionPane.ERROR_MESSAGE);
        }
        refreshList();
    }

    private Row selectedRow() {
        int index = runtimeList.getSelectedIndex();
        if (index < 0 || index >= rows.size()) {
            JOptionPane.showMessageDialog(this,
                "Selecione uma opção de Java na lista.",
                "Gerenciador de Java", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return rows.get(index);
    }
}
