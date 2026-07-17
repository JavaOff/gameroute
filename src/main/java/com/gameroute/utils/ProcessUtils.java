package com.gameroute.utils;

import java.util.List;
import java.util.Optional;

/**
 * Helpers built on {@link ProcessHandle} for locating and controlling the
 * League of Legends game process without any native code or elevated access.
 */
public final class ProcessUtils {

    /** Executable names, in preference order, that indicate the game is running. */
    public static final List<String> LEAGUE_PROCESS_NAMES = List.of(
            "League of Legends.exe",
            "LeagueClientUx.exe",
            "LeagueClient.exe"
    );

    private ProcessUtils() {
    }

    public static Optional<ProcessHandle> findProcessByName(String name) {
        return ProcessHandle.allProcesses()
                .filter(ph -> ph.info().command()
                        .map(cmd -> cmd.toLowerCase().endsWith(name.toLowerCase()))
                        .orElse(false))
                .findFirst();
    }

    public static Optional<ProcessHandle> findLeagueProcess() {
        for (String name : LEAGUE_PROCESS_NAMES) {
            Optional<ProcessHandle> found = findProcessByName(name);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** Directory containing the executable, or empty if unavailable/inaccessible. */
    public static Optional<String> installDirectory(ProcessHandle handle) {
        return handle.info().command().map(cmd -> {
            int sepIndex = Math.max(cmd.lastIndexOf('\\'), cmd.lastIndexOf('/'));
            return sepIndex > 0 ? cmd.substring(0, sepIndex) : null;
        });
    }
}
