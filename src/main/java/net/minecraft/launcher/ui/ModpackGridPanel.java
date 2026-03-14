package net.minecraft.launcher.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.minecraft.launcher.Launcher;
import net.minecraft.launcher.SwingUserInterface;
import net.minecraft.launcher.game.GameLaunchDispatcher;
import net.minecraft.launcher.profile.Profile;
import net.minecraft.launcher.profile.ProfileManager;
import net.minecraft.launcher.profile.RefreshedProfilesListener;
import net.minecraft.launcher.ui.popups.profile.ProfileEditorPopup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModpackGridPanel extends JPanel implements RefreshedProfilesListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Launcher minecraftLauncher;
    private final JPanel cardsPanel;

    public ModpackGridPanel(Launcher minecraftLauncher) {
        super(new BorderLayout(0, 10));
        this.minecraftLauncher = minecraftLauncher;
        this.cardsPanel = new JPanel(new GridBagLayout());
        this.cardsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        this.add(new JLabel("Modpacks"), BorderLayout.NORTH);
        this.add(this.cardsPanel, BorderLayout.CENTER);
        this.minecraftLauncher.getProfileManager().addRefreshedProfilesListener(this);
        this.reloadProfiles();
    }

    private void reloadProfiles() {
        this.cardsPanel.removeAll();

        List<Profile> profiles = new ArrayList<Profile>(this.minecraftLauncher.getProfileManager().getProfiles().values());
        profiles.sort(Comparator.comparing(Profile::getName, String.CASE_INSENSITIVE_ORDER));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 10, 0);

        for (Profile profile : profiles) {
            this.cardsPanel.add(this.createProfileCard(profile), constraints);
            constraints.gridy++;
        }

        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        this.cardsPanel.add(new JPanel(), constraints);

        this.cardsPanel.revalidate();
        this.cardsPanel.repaint();
    }

    private JPanel createProfileCard(final Profile profile) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(card.getForeground()),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JLabel title = new JLabel(profile.getName());
        title.setFont(title.getFont().deriveFont(title.getFont().getSize2D() + 2.0f));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton settingsButton = new JButton("⚙ Configurações");
        JButton playButton = new JButton("Jogar");

        settingsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ProfileEditorPopup.showEditProfileDialog(ModpackGridPanel.this.minecraftLauncher, profile);
            }
        });

        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ModpackGridPanel.this.playProfile(profile);
            }
        });

        actions.add(settingsButton);
        actions.add(playButton);

        card.add(title, BorderLayout.WEST);
        card.add(actions, BorderLayout.EAST);
        return card;
    }

    private void playProfile(Profile profile) {
        ProfileManager profileManager = this.minecraftLauncher.getProfileManager();
        profileManager.setSelectedProfile(profile.getName());

        try {
            profileManager.saveProfiles();
        } catch (IOException ex) {
            LOGGER.error("Couldn't save selected profile before launching {}", profile.getName(), ex);
        }

        GameLaunchDispatcher dispatcher = this.minecraftLauncher.getLaunchDispatcher();
        if (dispatcher.isRunningInSameFolder()) {
            int result = JOptionPane.showConfirmDialog(
                    ((SwingUserInterface) this.minecraftLauncher.getUserInterface()).getFrame(),
                    "You already have an instance of Minecraft running. If you launch another one in the same folder, they may clash and corrupt your saves.\n"
                            + "This could cause many issues, in singleplayer or otherwise. We will not be responsible for anything that goes wrong.\n"
                            + "Do you want to start another instance of Minecraft, despite this?\n"
                            + "You may solve this issue by launching the game in a different folder (see profile settings).",
                    "Duplicate instance warning",
                    JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        dispatcher.play();
    }

    @Override
    public void onProfilesRefreshed(ProfileManager manager) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ModpackGridPanel.this.reloadProfiles();
            }
        });
    }
}
