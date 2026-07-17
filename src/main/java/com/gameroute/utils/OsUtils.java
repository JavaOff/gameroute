package com.gameroute.utils;

/**
 * Small helpers for platform detection. GameRoute's optimizer features are
 * Windows-only (they shell out to {@code ping}, {@code tracert}, {@code netsh}
 * and PowerShell); on other platforms those actions are disabled rather than
 * silently failing.
 */
public final class OsUtils {

    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase();

    private OsUtils() {
    }

    public static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    /** Best-effort check for an elevated (Administrator) process token. */
    public static boolean isRunningAsAdmin() {
        if (!isWindows()) {
            return false;
        }
        try {
            // "net session" only succeeds without error when the process holds
            // administrator privileges; a well known, dependency-free probe.
            Process p = new ProcessBuilder("cmd", "/c", "net session >nul 2>&1")
                    .redirectErrorStream(true)
                    .start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
