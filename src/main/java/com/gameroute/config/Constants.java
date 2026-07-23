package com.gameroute.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Application-wide constants: file locations, timing intervals and tunable
 * thresholds. Centralized here so behavior can be adjusted without hunting
 * through the codebase.
 */
public final class Constants {

    private Constants() {
    }

    public static final String APP_NAME = "GameRoute";
    public static final String APP_VERSION = loadVersion();

    /**
     * Reads the real build version from {@code version.properties}, filtered
     * from {@code ${project.version}} by Maven at build time (see the pom's
     * resources-filtered directory) -- so the auto-updater's "am I current?"
     * check always compares against what actually got built, never a
     * hand-typed constant that can silently drift from a release tag.
     */
    private static String loadVersion() {
        try (InputStream in = Constants.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String version = props.getProperty("version");
                if (version != null && !version.isBlank() && !version.startsWith("${")) {
                    return version;
                }
            }
        } catch (IOException ignored) {
            // fall through to the dev-time default below
        }
        return "1.0.0-dev";
    }

    public static final Path APP_HOME = Paths.get(System.getProperty("user.home"), ".gameroute");
    public static final Path SETTINGS_FILE = APP_HOME.resolve("settings.properties");
    public static final Path STATS_DIR = APP_HOME.resolve("stats");
    public static final Path PING_HISTORY_CSV = STATS_DIR.resolve("ping-history.csv");
    public static final Path OPTIMIZATION_HISTORY_LOG = STATS_DIR.resolve("optimization-history.log");

    public static final int PING_INTERVAL_SECONDS = 1;
    public static final int PING_TIMEOUT_MS = 1000;
    public static final int PING_HISTORY_WINDOW = 300; // 5 minutes of 1s samples, kept in memory for charts/jitter/loss

    public static final int TRACEROUTE_MAX_HOPS = 30;
    public static final int TRACEROUTE_TIMEOUT_MS = 1000;

    public static final int SYSTEM_MONITOR_INTERVAL_SECONDS = 1;
    public static final int GAME_DETECTION_INTERVAL_SECONDS = 5;
    public static final int SERVER_LATENCY_REFRESH_SECONDS = 60;

    /** A hop is flagged "problematic" when its RTT jumps this many ms above the previous hop's average. */
    public static final double HOP_LATENCY_JUMP_THRESHOLD_MS = 30.0;

    public static final String LEAGUE_CONFIG_RELATIVE_PATH = "Config/game.cfg";

    /** The live marketing site; also linked as a button on the Discord Rich Presence card. */
    public static final String WEBSITE_URL = "https://gamerouteweb.vercel.app";
    /** Opt-in only (see {@code TelemetryService}); update if the site's production domain changes. */
    public static final String TELEMETRY_ENDPOINT = WEBSITE_URL + "/api/telemetry";
    /** How often the opt-in heartbeat repeats while GameRoute is running -- keep in sync with the "online now" window read server-side. */
    public static final int TELEMETRY_HEARTBEAT_INTERVAL_MINUTES = 5;

    /**
     * Separate opt-in from the anonymous telemetry above -- POSTs the connected Discord identity
     * (id/username/avatar) so Owner/Administrator/Moderator can see who has GameRoute connected
     * in the in-app Admin panel. Reading this list back requires the caller's own Discord OAuth
     * token, which the server independently re-verifies against the GameRoute guild's roles --
     * see website/api/discord-presence.js.
     */
    public static final String DISCORD_PRESENCE_ENDPOINT = WEBSITE_URL + "/api/discord-presence";
    public static final int DISCORD_PRESENCE_HEARTBEAT_INTERVAL_MINUTES = 5;

    /**
     * Discord Application ID for Rich Presence, from the same Discord
     * Developer Portal application already used for the Discord bot (Application
     * > General Information > Application ID). Left blank until set -- with no
     * ID configured, {@code DiscordPresenceService} never attempts to connect.
     */
    public static final String DISCORD_RPC_CLIENT_ID = "1528300009945169970";
    /** Must exactly match an uploaded key under that application's Rich Presence > Art Assets tab. */
    public static final String DISCORD_RPC_LARGE_IMAGE_KEY = "gameroute_logo";
    /** How often to retry connecting to Discord's local IPC pipe when not yet connected. */
    public static final int DISCORD_RPC_RECONNECT_SECONDS = 15;

    /**
     * OAuth2 "Login with Discord" (identify scope only -- username/avatar, nothing else) so the
     * title bar can show your real Discord profile instead of a generic local one. Reuses the
     * same Application as Rich Presence; must have this exact redirect URI registered under its
     * OAuth2 settings, and be marked a "Public Client" (no client secret) since GameRoute is a
     * distributed desktop app -- embedding a secret in it would let anyone extract and reuse it.
     */
    public static final int DISCORD_OAUTH_REDIRECT_PORT = 47115;
    public static final String DISCORD_OAUTH_REDIRECT_URI = "http://127.0.0.1:" + DISCORD_OAUTH_REDIRECT_PORT + "/callback";
    /** {@code guilds.members.read} lets GameRoute show the user's own role in the GameRoute Discord server below. */
    public static final String DISCORD_OAUTH_SCOPE = "identify guilds.members.read";

    /** The official GameRoute Discord server -- a connected user's highest role there is shown next to their name. */
    public static final String DISCORD_GUILD_ID = "1305524197874995401";
    /** Server-side proxy (needs a bot token to resolve role IDs to names/colors -- see website/api/discord-guild-roles.js). */
    public static final String DISCORD_GUILD_ROLES_ENDPOINT = WEBSITE_URL + "/api/discord-guild-roles";
    /**
     * Only these roles from the GameRoute server are ever shown as "your role" in the title bar --
     * the server also has bot/utility/moderation-tool roles (a bot's own role, a blanket "*"
     * permission role, Ticket Tool, Security, etc.) that would be meaningless or misleading to
     * surface here even if a user happens to hold one. Keyed by role ID (from
     * https://discord.com/developers -> the server's role list) rather than name, since names
     * (with emoji prefixes) can change without the ID changing.
     */
    public static final java.util.Set<String> DISCORD_DISPLAYABLE_ROLE_IDS = java.util.Set.of(
            "1398953662872682567", // Owner
            "1398953666047643769", // Administrator
            "1528383809144492202", // Moderator
            "1398953666827911243", // Support
            "1528762645690323104", // Bug Hunter
            "1398953647588773888", // Server Booster
            "1363867921008431246"  // Member
    );
    /** Only these roles can see the in-app Admin panel -- aggregate usage stats only, nothing about individual users. */
    public static final java.util.Set<String> DISCORD_ADMIN_ROLE_IDS = java.util.Set.of(
            "1398953662872682567", // Owner
            "1398953666047643769", // Administrator
            "1528383809144492202"  // Moderator
    );
    /** Same public, unauthenticated aggregate numbers as the Discord bot's {@code /userstats} command. */
    public static final String STATS_ENDPOINT = WEBSITE_URL + "/api/stats";

    /**
     * User-initiated only -- nothing is ever sent here without the user writing a description and
     * pressing "Send Report" themselves (see Settings > Report a Problem). Tied to their Discord
     * id if connected, otherwise their anonymous install id, so the Admin panel can group a
     * person's reports together.
     */
    public static final String BUG_REPORT_SUBMIT_ENDPOINT = WEBSITE_URL + "/api/bug-report-submit";
    /** Admin-only (role re-verified server-side, same pattern as discord-presence.js) list/detail/update. */
    public static final String BUG_REPORTS_ENDPOINT = WEBSITE_URL + "/api/bug-reports";
    /** How many characters of the tail of gameroute.log to attach to a report -- enough context, not the whole history. */
    public static final int BUG_REPORT_LOG_TAIL_CHARS = 20_000;
}
