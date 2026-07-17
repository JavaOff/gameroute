package com.gameroute.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Persists user-facing settings (theme, autostart, language, notifications,
 * preferred region/adapter) to {@code ~/.gameroute/settings.properties}.
 * Loaded once at startup and flushed to disk on every change.
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private static final String KEY_DARK_MODE = "ui.darkMode";
    private static final String KEY_AUTO_START = "app.autoStart";
    private static final String KEY_START_MINIMIZED = "app.startMinimized";
    private static final String KEY_LANGUAGE = "app.language";
    private static final String KEY_NOTIFICATIONS = "app.notificationsEnabled";
    private static final String KEY_PREFERRED_REGION = "network.preferredRegion";
    private static final String KEY_PREFERRED_ADAPTER = "network.preferredAdapter";

    private final Properties properties = new Properties();

    public AppConfig() {
        load();
    }

    private void load() {
        setDefaultsIfAbsent();
        if (!Files.exists(Constants.SETTINGS_FILE)) {
            save();
            return;
        }
        try (InputStream in = Files.newInputStream(Constants.SETTINGS_FILE)) {
            properties.load(in);
        } catch (IOException e) {
            log.warn("Could not read settings file, using defaults", e);
        }
    }

    private void setDefaultsIfAbsent() {
        properties.putIfAbsent(KEY_DARK_MODE, "true");
        properties.putIfAbsent(KEY_AUTO_START, "false");
        properties.putIfAbsent(KEY_START_MINIMIZED, "false");
        properties.putIfAbsent(KEY_LANGUAGE, "en");
        properties.putIfAbsent(KEY_NOTIFICATIONS, "true");
        properties.putIfAbsent(KEY_PREFERRED_REGION, "EUW");
        properties.putIfAbsent(KEY_PREFERRED_ADAPTER, "");
    }

    public synchronized void save() {
        try {
            Files.createDirectories(Constants.APP_HOME);
            try (OutputStream out = Files.newOutputStream(Constants.SETTINGS_FILE)) {
                properties.store(out, Constants.APP_NAME + " settings");
            }
        } catch (IOException e) {
            log.error("Could not persist settings", e);
        }
    }

    public boolean isDarkMode() {
        return Boolean.parseBoolean(properties.getProperty(KEY_DARK_MODE));
    }

    public void setDarkMode(boolean value) {
        properties.setProperty(KEY_DARK_MODE, String.valueOf(value));
        save();
    }

    public boolean isAutoStart() {
        return Boolean.parseBoolean(properties.getProperty(KEY_AUTO_START));
    }

    public void setAutoStart(boolean value) {
        properties.setProperty(KEY_AUTO_START, String.valueOf(value));
        save();
    }

    public boolean isStartMinimized() {
        return Boolean.parseBoolean(properties.getProperty(KEY_START_MINIMIZED));
    }

    public void setStartMinimized(boolean value) {
        properties.setProperty(KEY_START_MINIMIZED, String.valueOf(value));
        save();
    }

    public String getLanguage() {
        return properties.getProperty(KEY_LANGUAGE);
    }

    public void setLanguage(String value) {
        properties.setProperty(KEY_LANGUAGE, value);
        save();
    }

    public boolean isNotificationsEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_NOTIFICATIONS));
    }

    public void setNotificationsEnabled(boolean value) {
        properties.setProperty(KEY_NOTIFICATIONS, String.valueOf(value));
        save();
    }

    public String getPreferredRegion() {
        return properties.getProperty(KEY_PREFERRED_REGION);
    }

    public void setPreferredRegion(String value) {
        properties.setProperty(KEY_PREFERRED_REGION, value);
        save();
    }

    public String getPreferredAdapter() {
        return properties.getProperty(KEY_PREFERRED_ADAPTER);
    }

    public void setPreferredAdapter(String value) {
        properties.setProperty(KEY_PREFERRED_ADAPTER, value);
        save();
    }
}
