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
 * Sharing your Discord id/username/avatar with the GameRoute server (so the in-app Admin panel,
 * Owner/Administrator/Moderator only, can see who currently has GameRoute connected) is an
 * inherent, disclosed part of connecting a Discord account -- not a separate toggle. This sends a
 * "still connected" heartbeat exactly as long as a Discord account is connected, and stops
 * immediately the moment it's disconnected (see {@link DiscordAccountService#disconnect}); if you
 * don't want this, don't connect Discord (or disconnect it) -- there's no in-between state.
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

    /** Sends one heartbeat immediately (if connected) and starts the repeating loop. Call once at startup. */
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

    /** No-op unless a Discord account is connected; also called directly right after one connects. */
    public void sendHeartbeat(AppConfig config, DiscordAccountService discordAccountService) {
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
