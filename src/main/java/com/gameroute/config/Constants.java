package com.gameroute.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application-wide constants: file locations, timing intervals and tunable
 * thresholds. Centralized here so behavior can be adjusted without hunting
 * through the codebase.
 */
public final class Constants {

    private Constants() {
    }

    public static final String APP_NAME = "GameRoute";
    public static final String APP_VERSION = "1.0.0";

    public static final Path APP_HOME = Paths.get(System.getProperty("user.home"), ".gameroute");
    public static final Path SETTINGS_FILE = APP_HOME.resolve("settings.properties");
    public static final Path STATS_DIR = APP_HOME.resolve("stats");
    public static final Path PING_HISTORY_CSV = STATS_DIR.resolve("ping-history.csv");

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
}
