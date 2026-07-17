package com.gameroute.monitor;

import com.gameroute.config.Constants;
import com.gameroute.model.GameStatus;
import com.gameroute.utils.ProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Polls for a running League of Legends process and, best-effort, its
 * configured server region.
 * <p>
 * Region detection reads the {@code Region=} key from the game's own
 * {@code Config/game.cfg} next to the detected executable — no network
 * calls or reverse engineering of Riot's client API are involved, so it
 * only works once the client has actually connected at least once.
 */
public class GameProcessMonitor {

    private static final Logger log = LoggerFactory.getLogger(GameProcessMonitor.class);

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "game-process-monitor"));

    public GameStatus detect() {
        Optional<ProcessHandle> process = ProcessUtils.findLeagueProcess();
        if (process.isEmpty()) {
            return GameStatus.notRunning();
        }
        ProcessHandle handle = process.get();
        String command = handle.info().command().orElse("unknown");
        String region = ProcessUtils.installDirectory(handle)
                .map(this::readRegionFromConfig)
                .orElse(null);
        return new GameStatus(true, command.substring(Math.max(0, command.lastIndexOf('\\') + 1)),
                handle.pid(), ProcessUtils.installDirectory(handle).orElse(null), region);
    }

    private String readRegionFromConfig(String installDir) {
        Path candidate = Path.of(installDir, Constants.LEAGUE_CONFIG_RELATIVE_PATH);
        // The client executable often lives one level below the install root
        // (e.g. "<Root>/Game/League of Legends.exe"), so also check the parent.
        if (!Files.exists(candidate)) {
            candidate = Path.of(installDir).getParent() != null
                    ? Path.of(installDir).getParent().resolve(Constants.LEAGUE_CONFIG_RELATIVE_PATH)
                    : candidate;
        }
        if (!Files.exists(candidate)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(candidate);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.regionMatches(true, 0, "Region=", 0, 7)) {
                    return trimmed.substring(7).trim();
                }
            }
        } catch (IOException e) {
            log.debug("Could not read game.cfg at {}", candidate, e);
        }
        return null;
    }

    /** Starts polling on a background thread; the listener is invoked on that thread. */
    public void start(Consumer<GameStatus> listener) {
        executor.scheduleAtFixedRate(() -> {
            try {
                listener.accept(detect());
            } catch (Exception e) {
                log.error("Game process detection failed", e);
            }
        }, 0, Constants.GAME_DETECTION_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }
}
