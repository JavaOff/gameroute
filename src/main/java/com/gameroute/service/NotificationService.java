package com.gameroute.service;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Native system tray integration: lets GameRoute minimize to the tray and
 * raise OS-level toast notifications (e.g. "optimization complete",
 * "packet loss detected"). Falls back to a no-op if the platform has no
 * tray support.
 */
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private TrayIcon trayIcon;
    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Loads the app icon for the tray; falls back to a generated glyph if the resource is ever missing. */
    private Image renderTrayIcon() {
        try (InputStream in = getClass().getResourceAsStream("/icons/gameroute.png")) {
            if (in != null) {
                return ImageIO.read(in);
            }
        } catch (IOException e) {
            log.warn("Could not load bundled tray icon, using fallback glyph", e);
        }
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xFF, 0x3B, 0x30));
        g.fillOval(0, 0, size, size);
        g.setColor(Color.WHITE);
        g.fillOval(size / 4, size / 4, size / 2, size / 2);
        g.dispose();
        return image;
    }

    public void installTrayIcon(Stage stage) {
        if (!SystemTray.isSupported()) {
            log.info("System tray not supported on this platform; minimize-to-tray disabled.");
            return;
        }
        Platform.setImplicitExit(false);
        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image icon = renderTrayIcon();

            PopupMenu menu = new PopupMenu();
            MenuItem show = new MenuItem("Show GameRoute");
            show.addActionListener(e -> Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            }));
            MenuItem exit = new MenuItem("Exit");
            exit.addActionListener(e -> {
                tray.remove(trayIcon);
                Platform.exit();
                System.exit(0);
            });
            menu.add(show);
            menu.addSeparator();
            menu.add(exit);

            trayIcon = new TrayIcon(icon, "GameRoute", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            }));
            tray.add(trayIcon);
        } catch (AWTException e) {
            log.warn("Could not install system tray icon", e);
        }
    }

    public void notify(String title, String message) {
        if (!enabled || trayIcon == null) {
            return;
        }
        trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
    }

    public void warn(String title, String message) {
        if (!enabled || trayIcon == null) {
            return;
        }
        trayIcon.displayMessage(title, message, TrayIcon.MessageType.WARNING);
    }
}
