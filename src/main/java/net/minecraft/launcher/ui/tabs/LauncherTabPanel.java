package net.minecraft.launcher.ui.tabs;

import java.awt.Component;
import java.io.IOException;

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.plaf.DimensionUIResource;

import net.minecraft.launcher.Launcher;
import net.minecraft.launcher.LauncherConstants;
import net.minecraft.launcher.ui.tabs.ConsoleTab;
import net.minecraft.launcher.ui.tabs.CrashReportTab;
import net.minecraft.launcher.ui.tabs.ProfileListTab;
import net.minecraft.launcher.ui.tabs.WebsiteTab;

public class LauncherTabPanel
extends JTabbedPane {
    private final Launcher minecraftLauncher;
    private final WebsiteTab blog;
    private final ConsoleTab console;
    private CrashReportTab crashReportTab;

    public LauncherTabPanel(Launcher minecraftLauncher) {
        super(1);
        this.minecraftLauncher = minecraftLauncher;
        this.blog = new WebsiteTab(minecraftLauncher);
        this.console = new ConsoleTab(minecraftLauncher);
        this.createInterface();
    }

    protected void createInterface() {
    	
    	JEditorPane jep = new JEditorPane();
    	jep.setEditable(false);   

    	try {
    	  jep.setPage(LauncherConstants.URL_WEBSITE);
    	}catch (IOException e) {
    	  jep.setContentType("text/html");
    	  jep.setText("<html>Could not load</html>");
    	} 
    	
    	
    	JScrollPane scrollPane = new JScrollPane(jep);     
    	JFrame f = new JFrame("Test HTML");
    	f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    	f.getContentPane().add(scrollPane);
    	//f.setPreferredSize(new DimensionUIResource(800,600));
    	f.setVisible(true);
    	
        //this.addTab("News", this.blog);
    	this.addTab("News", scrollPane);
        this.addTab("Launcher Log", this.console);
        //this.addTab("Profile Editor", new ProfileListTab(this.minecraftLauncher));
    }

    public Launcher getMinecraftLauncher() {
        return this.minecraftLauncher;
    }

    public WebsiteTab getBlog() {
        return this.blog;
    }

    public ConsoleTab getConsole() {
        return this.console;
    }

    public void showConsole() {
        this.setSelectedComponent(this.console);
    }

    public void setCrashReport(CrashReportTab newTab) {
        if (this.crashReportTab != null) {
            this.removeTab(this.crashReportTab);
        }
        this.crashReportTab = newTab;
        this.addTab("Crash Report", this.crashReportTab);
        this.setSelectedComponent(newTab);
    }

    protected void removeTab(Component tab) {
        for (int i = 0; i < this.getTabCount(); ++i) {
            if (this.getTabComponentAt(i) != tab) continue;
            this.removeTabAt(i);
            break;
        }
    }

    public void removeTab(String name) {
        int index = this.indexOfTab(name);
        if (index > -1) {
            this.removeTabAt(index);
        }
    }
}

