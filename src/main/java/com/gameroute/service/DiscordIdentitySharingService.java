package com.gameroute.service;

import com.gameroute.config.AppConfig;
import com.gameroute.config.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Separate, explicit opt-in from {@link TelemetryService} (which never carries identity): while
 * enabled AND a Discord account is connected, sends a "still connected" heartbeat with that
 * account's id/username/avatar, so the in-app Admin panel (Owner/Administrator/Moderator only)
 * can show who currently has GameRoute connected to Discord. Off by default; only ever sends
 * anything once the user has explicitly turned it on in Settings.
 */
public class DiscordIdentitySharingService {

    private static final Logger log = LoggerFactory.getLogger(DiscordIdentitySharingService.class);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "discord-identity-share");
        thread.setDaemon(true);
        return thread;
    });

    /** Sends one heartbeat immediately (if enabled and connected) and starts the repeating loop. Call once at startup. */
    public void start(AppConfig config, DiscordAccountService discordAccountService) {
        sendHeartbeat(config, discordAccountService);
        scheduler.scheduleAtFixedRate(() -> sendHeartbeat(config, discordAccountService),
                Constants.DISCORD_PRESENCE_HEARTBEAT_INTERVAL_MINUTES,
                Constants.DISCORD_PRESENCE_HEARTBEAT_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    /** No-op unless both enabled and a Discord account is connected; also called directly right after either changes. */
    public void sendHeartbeat(AppConfig config, DiscordAccountService discordAccountService) {
        if (!config.isShareDiscordWithAdminsEnabled()) {
            return;
        }
        var userOpt = discordAccountService.currentUser(config);
        if (userOpt.isEmpty()) {
            return;
        }
        var user = userOpt.get();
        String body = "{\"discordId\":\"" + user.id() + "\",\"username\":\"" + escape(user.username())
                + "\",\"globalName\":\"" + escape(user.globalName()) + "\",\"avatarHash\":\"" + escape(user.avatarHash()) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(Constants.DISCORD_PRESENCE_ENDPOINT))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(e -> {
                    log.debug("Discord presence heartbeat failed: {}", e.getMessage());
                    return null;
                });
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
