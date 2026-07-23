package com.gameroute.service;

import com.gameroute.config.AppConfig;
import com.gameroute.config.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Sends a bug report only when the user writes one and presses "Send Report" themselves
 * (Settings > Report a Problem) -- nothing here ever runs on a schedule or in the background.
 * Always includes the app version, OS, and a recent tail of the local log file, since a report
 * with no diagnostic context isn't actually useful to act on; tied to the user's Discord identity
 * if connected, otherwise their anonymous install id, so the Admin panel can group a person's
 * reports into a history either way.
 */
public class BugReportService {

    private static final Logger log = LoggerFactory.getLogger(BugReportService.class);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public CompletableFuture<Void> submit(AppConfig config, DiscordAccountService discordAccountService, String description) {
        Optional<DiscordAccountService.DiscordUser> discordUser = discordAccountService.currentUser(config);
        String userId = discordUser.map(DiscordAccountService.DiscordUser::id).orElse("anon:" + config.getInstallId());
        boolean isDiscordUser = discordUser.isPresent();
        String username = discordUser.map(DiscordAccountService.DiscordUser::username).orElse("");
        String globalName = discordUser.map(DiscordAccountService.DiscordUser::globalName).orElse("");
        String avatarHash = discordUser.map(DiscordAccountService.DiscordUser::avatarHash).orElse("");

        String body = "{\"userId\":\"" + escape(userId) + "\",\"isDiscordUser\":" + isDiscordUser
                + ",\"username\":\"" + escape(username) + "\",\"globalName\":\"" + escape(globalName)
                + "\",\"avatarHash\":\"" + escape(avatarHash) + "\",\"version\":\"" + escape(Constants.APP_VERSION)
                + "\",\"os\":\"" + escape(System.getProperty("os.name", "") + " " + System.getProperty("os.version", ""))
                + "\",\"description\":\"" + escape(description) + "\",\"logs\":\"" + escape(recentLogTail()) + "\"}";

        HttpRequest request = HttpRequest.newBuilder(URI.create(Constants.BUG_REPORT_SUBMIT_ENDPOINT))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() / 100 != 2) {
                        throw new RuntimeException("Bug report submission failed: HTTP " + response.statusCode());
                    }
                });
    }

    /** The most recent {@link Constants#BUG_REPORT_LOG_TAIL_CHARS} characters of the local log file, or empty if unreadable. */
    private static String recentLogTail() {
        try {
            java.nio.file.Path logFile = Constants.APP_HOME.resolve("logs").resolve("gameroute.log");
            if (!Files.exists(logFile)) {
                return "";
            }
            String content = Files.readString(logFile);
            int start = Math.max(0, content.length() - Constants.BUG_REPORT_LOG_TAIL_CHARS);
            return content.substring(start);
        } catch (IOException e) {
            log.debug("Could not read log file for bug report", e);
            return "";
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
