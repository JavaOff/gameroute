package com.gameroute.optimizer;

import com.gameroute.model.OptimizationActionResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * GameRoute never force-closes other applications — silently killing a
 * process the user didn't ask to close is exactly the kind of surprising,
 * hard-to-reverse action this tool avoids. Instead this action is a
 * read-only advisory: it lists commonly bandwidth-heavy background
 * applications it finds running, so the user can close them manually.
 */
public class BackgroundAppsAdvisoryAction implements OptimizationAction {

    private static final List<String> KNOWN_BANDWIDTH_HEAVY = List.of(
            "OneDrive.exe", "Dropbox.exe", "Steam.exe", "EpicGamesLauncher.exe",
            "GalaxyClient.exe", "BackgroundDownloader.exe", "WindowsUpdateBox.exe",
            "Spotify.exe", "battle.net.exe"
    );

    @Override
    public String getName() {
        return "Scan for bandwidth-heavy background apps";
    }

    @Override
    public String getDescription() {
        return "Lists running applications known to use background bandwidth, for you to close manually if desired. "
                + "GameRoute does not close applications on its own.";
    }

    @Override
    public boolean requiresAdmin() {
        return false;
    }

    @Override
    public String getWarning() {
        return "Read-only: this only reports findings, it does not stop any process.";
    }

    @Override
    public OptimizationActionResult execute() {
        List<String> found = ProcessHandle.allProcesses()
                .map(ph -> ph.info().command().orElse(""))
                .filter(cmd -> KNOWN_BANDWIDTH_HEAVY.stream()
                        .anyMatch(known -> cmd.toLowerCase().endsWith(known.toLowerCase())))
                .map(cmd -> cmd.substring(Math.max(0, cmd.lastIndexOf('\\') + 1)))
                .distinct()
                .collect(Collectors.toList());

        String message = found.isEmpty()
                ? "No known bandwidth-heavy background applications detected."
                : "Found running: " + String.join(", ", found) + ". Consider pausing/closing these manually while playing.";
        return OptimizationActionResult.ok(getName(), message);
    }
}
