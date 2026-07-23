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
    private static final String KEY_THEME = "ui.theme";
    private static final String KEY_AUTO_START = "app.autoStart";
    private static final String KEY_START_MINIMIZED = "app.startMinimized";
    private static final String KEY_LANGUAGE = "app.language";
    private static final String KEY_NOTIFICATIONS = "app.notificationsEnabled";
    private static final String KEY_PREFERRED_REGION = "network.preferredRegion";
    private static final String KEY_PREFERRED_ADAPTER = "network.preferredAdapter";
    private static final String KEY_AUTO_OPTIMIZE = "app.autoOptimize";
    private static final String KEY_AUTO_CHECK_UPDATES = "app.autoCheckUpdates";
    private static final String KEY_SKIPPED_UPDATE_VERSION = "app.skippedUpdateVersion";
    private static final String KEY_TELEMETRY_ENABLED = "app.telemetryEnabled";
    private static final String KEY_TELEMETRY_PROMPT_SHOWN = "app.telemetryPromptShown";
    private static final String KEY_INSTALL_ID = "app.installId";
    private static final String KEY_DISCORD_PRESENCE_ENABLED = "app.discordPresenceEnabled";
    private static final String KEY_DISCORD_ACCOUNT_PROMPT_SHOWN = "app.discordAccountPromptShown";
    private static final String KEY_DISCORD_USER_ID = "discordAccount.id";
    private static final String KEY_DISCORD_USERNAME = "discordAccount.username";
    private static final String KEY_DISCORD_GLOBAL_NAME = "discordAccount.globalName";
    private static final String KEY_DISCORD_AVATAR_HASH = "discordAccount.avatarHash";
    private static final String KEY_DISCORD_ACCESS_TOKEN = "discordAccount.accessToken";
    private static final String KEY_DISCORD_REFRESH_TOKEN = "discordAccount.refreshToken";
    private static final String KEY_DISCORD_ROLE_ID = "discordAccount.roleId";
    private static final String KEY_DISCORD_ROLE_NAME = "discordAccount.roleName";
    private static final String KEY_DISCORD_ROLE_COLOR = "discordAccount.roleColor";
    private static final String KEY_DISCORD_GUILD_JOINED_AT = "discordAccount.guildJoinedAt";

    private final Properties properties = new Properties();

    public AppConfig() {
        load();
    }

    private void load() {
        setDefaultsIfAbsent();
        if (Files.exists(Constants.SETTINGS_FILE)) {
            try (InputStream in = Files.newInputStream(Constants.SETTINGS_FILE)) {
                properties.load(in);
            } catch (IOException e) {
                log.warn("Could not read settings file, using defaults", e);
            }
        }
        // Always flush: on a genuinely first run this persists the defaults, and on a
        // settings file predating a newer key (e.g. install ID) it persists the
        // freshly-generated default for that key instead of silently regenerating a
        // new random value (a new "install") on every future launch.
        save();
    }

    private void setDefaultsIfAbsent() {
        properties.putIfAbsent(KEY_DARK_MODE, "true");
        properties.putIfAbsent(KEY_THEME, "ULTRA_DARK");
        properties.putIfAbsent(KEY_AUTO_START, "false");
        properties.putIfAbsent(KEY_START_MINIMIZED, "false");
        properties.putIfAbsent(KEY_LANGUAGE, "en");
        properties.putIfAbsent(KEY_NOTIFICATIONS, "true");
        properties.putIfAbsent(KEY_PREFERRED_REGION, "EUW");
        properties.putIfAbsent(KEY_PREFERRED_ADAPTER, "");
        properties.putIfAbsent(KEY_AUTO_OPTIMIZE, "false");
        properties.putIfAbsent(KEY_AUTO_CHECK_UPDATES, "true");
        properties.putIfAbsent(KEY_SKIPPED_UPDATE_VERSION, "");
        properties.putIfAbsent(KEY_TELEMETRY_ENABLED, "false");
        properties.putIfAbsent(KEY_TELEMETRY_PROMPT_SHOWN, "false");
        // On by default (unlike telemetry): a Discord Rich Presence entry is immediately
        // visible on the user's own profile the moment it appears, so there's no hidden
        // data collection to consent to -- just an easy off-switch in Settings.
        properties.putIfAbsent(KEY_DISCORD_PRESENCE_ENABLED, "true");
        properties.putIfAbsent(KEY_DISCORD_ACCOUNT_PROMPT_SHOWN, "false");
        // Generated once, on first run, so a telemetry ping (when the user opts in) can be
        // counted as "one install" without carrying any hardware or personal identifier.
        properties.putIfAbsent(KEY_INSTALL_ID, java.util.UUID.randomUUID().toString());
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

    public String getTheme() {
        return properties.getProperty(KEY_THEME, "ULTRA_DARK");
    }

    public void setTheme(String value) {
        properties.setProperty(KEY_THEME, value);
        save();
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

    /** When enabled, GameRoute silently applies a detected game's saved profile the moment it starts, without a per-launch confirmation. */
    public boolean isAutoOptimizeEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_AUTO_OPTIMIZE));
    }

    public void setAutoOptimizeEnabled(boolean value) {
        properties.setProperty(KEY_AUTO_OPTIMIZE, String.valueOf(value));
        save();
    }

    public boolean isAutoCheckForUpdatesEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_AUTO_CHECK_UPDATES));
    }

    public void setAutoCheckForUpdatesEnabled(boolean value) {
        properties.setProperty(KEY_AUTO_CHECK_UPDATES, String.valueOf(value));
        save();
    }

    /** A version the user chose "Skip this version" on -- so it doesn't nag again until a newer one ships. */
    public String getSkippedUpdateVersion() {
        return properties.getProperty(KEY_SKIPPED_UPDATE_VERSION, "");
    }

    public void setSkippedUpdateVersion(String value) {
        properties.setProperty(KEY_SKIPPED_UPDATE_VERSION, value);
        save();
    }

    /** Off by default. When enabled, {@code TelemetryService} sends an anonymous install-id heartbeat. */
    public boolean isTelemetryEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_TELEMETRY_ENABLED));
    }

    public void setTelemetryEnabled(boolean value) {
        properties.setProperty(KEY_TELEMETRY_ENABLED, String.valueOf(value));
        save();
    }

    /** Whether the one-time first-run "help improve GameRoute?" prompt has already been shown. */
    public boolean hasShownTelemetryPrompt() {
        return Boolean.parseBoolean(properties.getProperty(KEY_TELEMETRY_PROMPT_SHOWN));
    }

    public void setTelemetryPromptShown(boolean value) {
        properties.setProperty(KEY_TELEMETRY_PROMPT_SHOWN, String.valueOf(value));
        save();
    }

    /** A random UUID generated once on first run -- not derived from any hardware or personal identifier. */
    public String getInstallId() {
        return properties.getProperty(KEY_INSTALL_ID);
    }


    /** On by default; talks only to the user's own local Discord client, never a server. */
    public boolean isDiscordPresenceEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_DISCORD_PRESENCE_ENABLED));
    }

    public void setDiscordPresenceEnabled(boolean value) {
        properties.setProperty(KEY_DISCORD_PRESENCE_ENABLED, String.valueOf(value));
        save();
    }

    public boolean hasShownDiscordAccountPrompt() {
        return Boolean.parseBoolean(properties.getProperty(KEY_DISCORD_ACCOUNT_PROMPT_SHOWN));
    }

    public void setDiscordAccountPromptShown(boolean value) {
        properties.setProperty(KEY_DISCORD_ACCOUNT_PROMPT_SHOWN, String.valueOf(value));
        save();
    }

    /** Empty when no Discord account is connected. */
    public String getDiscordUserId() {
        return properties.getProperty(KEY_DISCORD_USER_ID, "");
    }

    public String getDiscordUsername() {
        return properties.getProperty(KEY_DISCORD_USERNAME, "");
    }

    public String getDiscordGlobalName() {
        return properties.getProperty(KEY_DISCORD_GLOBAL_NAME, "");
    }

    public String getDiscordAvatarHash() {
        return properties.getProperty(KEY_DISCORD_AVATAR_HASH, "");
    }

    public String getDiscordAccessToken() {
        return properties.getProperty(KEY_DISCORD_ACCESS_TOKEN, "");
    }

    public String getDiscordRefreshToken() {
        return properties.getProperty(KEY_DISCORD_REFRESH_TOKEN, "");
    }

    /** ID of the highest role the user holds in the GameRoute Discord server, or blank if none/not a member. */
    public String getDiscordRoleId() {
        return properties.getProperty(KEY_DISCORD_ROLE_ID, "");
    }

    /** Highest role the user holds in the GameRoute Discord server, or blank if none/not a member. */
    public String getDiscordRoleName() {
        return properties.getProperty(KEY_DISCORD_ROLE_NAME, "");
    }

    /** That role's Discord color as {@code #RRGGBB}, or blank if the role has no color (or none is known). */
    public String getDiscordRoleColor() {
        return properties.getProperty(KEY_DISCORD_ROLE_COLOR, "");
    }

    /** ISO-8601 timestamp of when the user joined the GameRoute Discord server, or blank if unknown. */
    public String getDiscordGuildJoinedAt() {
        return properties.getProperty(KEY_DISCORD_GUILD_JOINED_AT, "");
    }

    public void saveDiscordAccount(String id, String username, String globalName, String avatarHash,
                                    String accessToken, String refreshToken, String roleId, String roleName,
                                    String roleColor, String guildJoinedAt) {
        properties.setProperty(KEY_DISCORD_USER_ID, id == null ? "" : id);
        properties.setProperty(KEY_DISCORD_USERNAME, username == null ? "" : username);
        properties.setProperty(KEY_DISCORD_GLOBAL_NAME, globalName == null ? "" : globalName);
        properties.setProperty(KEY_DISCORD_AVATAR_HASH, avatarHash == null ? "" : avatarHash);
        properties.setProperty(KEY_DISCORD_ACCESS_TOKEN, accessToken == null ? "" : accessToken);
        properties.setProperty(KEY_DISCORD_REFRESH_TOKEN, refreshToken == null ? "" : refreshToken);
        properties.setProperty(KEY_DISCORD_ROLE_ID, roleId == null ? "" : roleId);
        properties.setProperty(KEY_DISCORD_ROLE_NAME, roleName == null ? "" : roleName);
        properties.setProperty(KEY_DISCORD_ROLE_COLOR, roleColor == null ? "" : roleColor);
        properties.setProperty(KEY_DISCORD_GUILD_JOINED_AT, guildJoinedAt == null ? "" : guildJoinedAt);
        save();
    }

    public void clearDiscordAccount() {
        saveDiscordAccount("", "", "", "", "", "", "", "", "", "");
    }
}
