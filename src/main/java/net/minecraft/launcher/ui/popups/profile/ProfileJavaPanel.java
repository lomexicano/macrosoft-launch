package net.minecraft.launcher.ui.popups.profile;

import com.mojang.launcher.OperatingSystem;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import net.minecraft.launcher.profile.Profile;
import net.minecraft.launcher.ui.popups.profile.ProfileEditorPopup;

import javax.swing.JButton; // Adicionar esta importação
import java.util.List;    // Adicionar esta importação
import javax.swing.JList;   // Adicionar esta importação
import javax.swing.JScrollPane; // Adicionar esta importação
import javax.swing.JOptionPane; // Adicionar esta importação
// Supondo que você criará JavaLocator em um pacote utils
import net.minecraft.launcher.utils.JavaLocator;

public class ProfileJavaPanel
extends JPanel {
    private final ProfileEditorPopup editor;
    private final JCheckBox javaPathCustom = new JCheckBox("Executável:");
    private final JTextField javaPathField = new JTextField();
    private final JButton detectJavaButton = new JButton("Detectar JAVA");
    private final JCheckBox javaArgsCustom = new JCheckBox("Argumentos JVM:");
    private final JTextField javaArgsField = new JTextField();

    public ProfileJavaPanel(ProfileEditorPopup editor) {
        this.editor = editor;
        this.setLayout(new GridBagLayout());
        this.setBorder(BorderFactory.createTitledBorder("Configurações Java (Avançado)"));

     // Tenta deixar a fonte em negrito para mais destaque
        Font buttonFont = detectJavaButton.getFont();
        detectJavaButton.setFont(buttonFont.deriveFont(Font.BOLD));
        
        this.createInterface();
        this.fillDefaultValues();
        this.addEventHandlers();
    }

    protected void createInterface() {
    	GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.anchor = GridBagConstraints.WEST;

        // Linha 0: Checkbox Executable, Campo Executable, Botão Detectar
        constraints.gridy = 0;

        constraints.gridx = 0;
        constraints.weightx = 0.0; // Checkbox não expande
        constraints.fill = GridBagConstraints.NONE;
        add(this.javaPathCustom, constraints);

        constraints.gridx = 1;
        constraints.weightx = 1.0; // Campo expande
        constraints.fill = GridBagConstraints.HORIZONTAL;
        add(this.javaPathField, constraints);

        constraints.gridx = 2; // Nova coluna para o botão
        constraints.weightx = 0.0; // Botão não expande
        constraints.fill = GridBagConstraints.NONE;
        add(this.detectJavaButton, constraints); // Adiciona o novo botão

        // Linha 1: Checkbox JVM Arguments e Campo JVM Arguments
        constraints.gridy = 1;

        constraints.gridx = 0;
        constraints.gridwidth = 1; // Reset gridwidth
        constraints.weightx = 0.0;
        constraints.fill = GridBagConstraints.NONE;
        add(this.javaArgsCustom, constraints);

        constraints.gridx = 1;
        constraints.gridwidth = 2; // Campo de argumentos ocupa as 2 colunas restantes
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        add(this.javaArgsField, constraints);
    }

    protected void fillDefaultValues() {
        String javaPath = this.editor.getProfile().getJavaPath();
        if (javaPath != null) {
            this.javaPathCustom.setSelected(true);
            this.javaPathField.setText(javaPath);
        } else {
            this.javaPathCustom.setSelected(false);
            this.javaPathField.setText(OperatingSystem.getCurrentPlatform().getJavaDir());
        }
        this.updateJavaPathState();
        String args = this.editor.getProfile().getJavaArgs();
        if (args != null) {
            this.javaArgsCustom.setSelected(true);
            this.javaArgsField.setText(args);
        } else {
            this.javaArgsCustom.setSelected(false);
            this.javaArgsField.setText("-Xmx1G -XX:+UseConcMarkSweepGC -XX:+CMSIncrementalMode -XX:-UseAdaptiveSizePolicy -Xmn128M");
        }
        this.updateJavaArgsState();
    }

    protected void addEventHandlers() {
        this.javaPathCustom.addItemListener(new ItemListener(){

            @Override
            public void itemStateChanged(ItemEvent e) {
                ProfileJavaPanel.this.updateJavaPathState();
            }
        });
        this.javaPathField.getDocument().addDocumentListener(new DocumentListener(){

            @Override
            public void insertUpdate(DocumentEvent e) {
                ProfileJavaPanel.this.updateJavaPath();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                ProfileJavaPanel.this.updateJavaPath();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                ProfileJavaPanel.this.updateJavaPath();
            }
        });
        this.javaArgsCustom.addItemListener(new ItemListener(){

            @Override
            public void itemStateChanged(ItemEvent e) {
                ProfileJavaPanel.this.updateJavaArgsState();
            }
        });
        this.javaArgsField.getDocument().addDocumentListener(new DocumentListener(){

            @Override
            public void insertUpdate(DocumentEvent e) {
                ProfileJavaPanel.this.updateJavaArgs();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                ProfileJavaPanel.this.updateJavaArgs();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                ProfileJavaPanel.this.updateJavaArgs();
            }
        });
        
        this.detectJavaButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                detectAndSetJavaPath();
            }
        });
        
     // Listener para o checkbox javaPathCustom (ajustado para não limpar o campo se desmarcado após detecção)
        this.javaPathCustom.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                // A lógica de updateJavaPathState já lida com isso,
                // mas é importante que ela não apague um caminho detectado se o usuário desmarcar
                // e remarcar o checkbox sem intenção de resetar para o padrão do OS.
                updateJavaPathState();
            }
        });
        
     // Listener para o campo javaPathField (para quando o usuário edita manualmente)
        this.javaPathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateJavaPathFromField(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateJavaPathFromField(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateJavaPathFromField(); }
        });
        
        
    }
    
    private void detectAndSetJavaPath() {
        List<String> javaPaths = JavaLocator.findJava8Installations();

        if (javaPaths.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Nenhuma instalação do Java 8 foi encontrada automaticamente.\n" +
                "Por favor, defina o caminho manualmente ou instale o Java 8.",
                "Detecção de Java", JOptionPane.INFORMATION_MESSAGE);
        } else if (javaPaths.size() == 1) {
            String foundPath = javaPaths.get(0);
            this.javaPathField.setText(foundPath);
            this.javaPathCustom.setSelected(true);
            updateJavaPathState();
            JOptionPane.showMessageDialog(this,
                "Java 8 encontrado e configurado:\n" + foundPath,
                "Detecção de Java", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JList<String> list = new JList<>(javaPaths.toArray(new String[0]));
            JScrollPane scrollPane = new JScrollPane(list);
            scrollPane.setPreferredSize(new Dimension(450, 150));
            int option = JOptionPane.showOptionDialog(
                this,
                scrollPane,
                "Várias instalações do Java 8 encontradas",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null, null, null
            );
            if (option == JOptionPane.OK_OPTION) {
                String selectedPath = list.getSelectedValue();
                if (selectedPath != null) {
                    this.javaPathField.setText(selectedPath);
                    this.javaPathCustom.setSelected(true);
                    updateJavaPathState();
                }
            }
        }
    }


    private void updateJavaPathFromField() {
        // Este método é chamado quando o usuário digita no campo.
        // Se o checkbox customizado estiver marcado, atualiza o perfil.
        if (this.javaPathCustom.isSelected()) {
            this.editor.getProfile().setJavaDir(this.javaPathField.getText());
        }
    }

    private void updateJavaPathState() {
        if (this.javaPathCustom.isSelected()) {
            this.javaPathField.setEnabled(true);
            // Se o campo estiver vazio ao marcar o checkbox, preenche com o padrão do OS.
            // Caso contrário, mantém o valor atual (que pode ter sido definido pela detecção ou manualmente).
            if (this.javaPathField.getText().trim().isEmpty()) {
                 this.javaPathField.setText(OperatingSystem.getCurrentPlatform().getJavaDir());
            }
            this.editor.getProfile().setJavaDir(this.javaPathField.getText());
        } else {
            this.javaPathField.setEnabled(false);
            // Não limpa o campo de texto, apenas o valor no perfil.
            // O usuário pode querer desmarcar temporariamente.
            this.editor.getProfile().setJavaDir(null);
        }
    }

    private void updateJavaPath() {
        if (this.javaPathCustom.isSelected()) {
            this.editor.getProfile().setJavaDir(this.javaPathField.getText());
        } else {
            this.editor.getProfile().setJavaDir(null);
        }
    }

    /*
    private void updateJavaPathState() {
        if (this.javaPathCustom.isSelected()) {
            this.javaPathField.setEnabled(true);
            this.editor.getProfile().setJavaDir(this.javaPathField.getText());
        } else {
            this.javaPathField.setEnabled(false);
            this.editor.getProfile().setJavaDir(null);
        }
    }*/

    private void updateJavaArgs() {
        if (this.javaArgsCustom.isSelected()) {
            this.editor.getProfile().setJavaArgs(this.javaArgsField.getText());
        } else {
            this.editor.getProfile().setJavaArgs(null);
        }
    }

    private void updateJavaArgsState() {
        if (this.javaArgsCustom.isSelected()) {
            this.javaArgsField.setEnabled(true);
            this.editor.getProfile().setJavaArgs(this.javaArgsField.getText());
        } else {
            this.javaArgsField.setEnabled(false);
            this.editor.getProfile().setJavaArgs(null);
        }
    }

}

