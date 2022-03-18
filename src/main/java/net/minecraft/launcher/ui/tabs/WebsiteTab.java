package net.minecraft.launcher.ui.tabs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.IntrospectionException;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import javax.swing.JPanel;
import net.minecraft.launcher.Launcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WebsiteTab
extends JPanel {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = LogManager.getLogger();
    //private final Browser browser = this.selectBrowser();
    private final Launcher minecraftLauncher;

    public WebsiteTab(Launcher minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
        this.setLayout(new BorderLayout());
        //this.add(this.browser.getComponent(), "Center");
        //this.browser.resize(this.getSize());
        this.addComponentListener(new ComponentAdapter(){

            @Override
            public void componentResized(ComponentEvent e) {
                //WebsiteTab.this.browser.resize(e.getComponent().getSize());
            }
        });
    }

    public Launcher getMinecraftLauncher() {
        return this.minecraftLauncher;
    }

    public static void addToSystemClassLoader(File file) throws IntrospectionException {
        if (ClassLoader.getSystemClassLoader() instanceof URLClassLoader) {
            URLClassLoader classLoader = (URLClassLoader)ClassLoader.getSystemClassLoader();
            try {
                Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                method.setAccessible(true);
                method.invoke(classLoader, file.toURI().toURL());
            }
            catch (Throwable t) {
                LOGGER.warn("Couldn't add " + file + " to system classloader", t);
            }
        }
    }

    public boolean hasJFX() {
        try {
            this.getClass().getClassLoader().loadClass("javafx.embed.swing.JFXPanel");
            return true;
        }
        catch (ClassNotFoundException e) {
            return false;
        }
    }

}

