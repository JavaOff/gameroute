package com.gameroute.service;

import com.gameroute.config.AppConfig;
import com.gameroute.config.Constants;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Login with Discord" via OAuth2 + PKCE (no client secret -- GameRoute is a public desktop
 * app, so a secret embedded in it could never actually stay secret) so the title bar can show
 * your real Discord name, avatar, and your role in the official GameRoute Discord server.
 * Scopes are {@code identify} (username, global display name, avatar hash) and
 * {@code guilds.members.read} (just enough to read your own role IDs in that one server --
 * no email, no friends list, no other servers). Everything after "click Connect" happens in
 * your own browser against Discord's own login page; GameRoute never sees your password.
 */
public class DiscordAccountService {

    private static final Logger log = LoggerFactory.getLogger(DiscordAccountService.class);
    private static final String AUTHORIZE_URL = "https://discord.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://discord.com/api/oauth2/token";
    private static final String REVOKE_URL = "https://discord.com/api/oauth2/token/revoke";
    private static final String ME_URL = "https://discord.com/api/users/@me";
    private static final String MEMBER_URL = ME_URL + "/guilds/" + Constants.DISCORD_GUILD_ID + "/member";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Discord's own snowflake epoch (2015-01-01T00:00:00Z), in Unix millis -- every ID encodes its creation time. */
    private static final long DISCORD_EPOCH_MILLIS = 1_420_070_400_000L;

    public record DiscordUser(String id, String username, String globalName, String avatarHash,
                               String roleId, String roleName, String roleColor, String guildJoinedAt) {
        /** Prefer the display name if the user has set one; falls back to the plain username. */
        public String displayName() {
            return (globalName != null && !globalName.isBlank()) ? globalName : username;
        }

        /** Whether there's a separate @handle worth showing under the display name. */
        public boolean hasDistinctUsername() {
            return username != null && !username.isBlank() && !username.equals(displayName());
        }

        public boolean hasRole() {
            return roleName != null && !roleName.isBlank();
        }

        /** Owner/Administrator/Moderator in the GameRoute server -- gates the in-app Admin panel. */
        public boolean isAdmin() {
            return roleId != null && Constants.DISCORD_ADMIN_ROLE_IDS.contains(roleId);
        }

        public boolean hasGuildJoinDate() {
            return guildJoinedAt != null && !guildJoinedAt.isBlank();
        }

        /**
         * When this Discord *account* was created -- not read from any API, just decoded from the
         * ID itself, since every Discord snowflake embeds its own creation timestamp.
         */
        public java.time.Instant accountCreatedAt() {
            long snowflake = Long.parseLong(id);
            return java.time.Instant.ofEpochMilli((snowflake >> 22) + DISCORD_EPOCH_MILLIS);
        }
    }

    private record GuildRole(String id, String name, int color, int position) {
    }

    private record GuildMember(List<String> roleIds, String joinedAt) {
    }

    public Optional<DiscordUser> currentUser(AppConfig config) {
        String id = config.getDiscordUserId();
        if (id.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new DiscordUser(id, config.getDiscordUsername(), config.getDiscordGlobalName(),
                config.getDiscordAvatarHash(), config.getDiscordRoleId(), config.getDiscordRoleName(),
                config.getDiscordRoleColor(), config.getDiscordGuildJoinedAt()));
    }

    /** {@code https://cdn.discordapp.com/avatars/...} PNG for the given user, or empty if they have no custom avatar. */
    public Optional<String> avatarUrl(DiscordUser user) {
        if (user.avatarHash() == null || user.avatarHash().isBlank()) {
            return Optional.empty();
        }
        return Optional.of("https://cdn.discordapp.com/avatars/" + user.id() + "/" + user.avatarHash() + ".png?size=64");
    }

    /**
     * Opens the system browser to Discord's own authorize page and waits for the redirect back
     * to a short-lived local server. Completes with the connected user, or completes
     * exceptionally if the user closes the tab, denies access, or nothing arrives within 3 minutes.
     */
    public CompletableFuture<DiscordUser> connect(AppConfig config) {
        String verifier = randomUrlSafe(64);
        String challenge = codeChallenge(verifier);
        String state = randomUrlSafe(24);

        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", Constants.DISCORD_OAUTH_REDIRECT_PORT), 0);
        } catch (IOException e) {
            CompletableFuture<DiscordUser> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IOException("Could not open local port " + Constants.DISCORD_OAUTH_REDIRECT_PORT
                    + " for the Discord login redirect -- is something else already using it?", e));
            return failed;
        }
        server.createContext("/callback", exchange -> {
            java.util.Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            String page;
            if (state.equals(params.get("state")) && params.get("code") != null) {
                codeFuture.complete(params.get("code"));
                page = "<html><body style=\"font-family:sans-serif;background:#111;color:#eee;"
                        + "text-align:center;padding-top:80px\"><h2>GameRoute connected</h2>"
                        + "<p>You can close this tab and return to GameRoute.</p></body></html>";
            } else {
                codeFuture.completeExceptionally(new IOException("Discord login was cancelled or denied."));
                page = "<html><body style=\"font-family:sans-serif;background:#111;color:#eee;"
                        + "text-align:center;padding-top:80px\"><h2>Not connected</h2>"
                        + "<p>You can close this tab and return to GameRoute.</p></body></html>";
            }
            byte[] bytes = page.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();

        String authorizeUrl = AUTHORIZE_URL + "?client_id=" + Constants.DISCORD_RPC_CLIENT_ID
                + "&response_type=code"
                + "&redirect_uri=" + encode(Constants.DISCORD_OAUTH_REDIRECT_URI)
                + "&scope=" + encode(Constants.DISCORD_OAUTH_SCOPE)
                + "&code_challenge=" + challenge
                + "&code_challenge_method=S256"
                + "&state=" + state;
        try {
            Desktop.getDesktop().browse(URI.create(authorizeUrl));
        } catch (IOException e) {
            server.stop(0);
            CompletableFuture<DiscordUser> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }

        return codeFuture
                .orTimeout(3, TimeUnit.MINUTES)
                .whenComplete((code, err) -> server.stop(0))
                .thenCompose(code -> exchangeCodeForToken(code, verifier))
                .thenCompose(tokens -> fetchUser(tokens[0]).thenCompose(user ->
                        resolveMembership(tokens[0]).handle((membership, err) -> {
                            // The role/membership lookup is a nice-to-have, not the login itself -- if the user
                            // isn't a member of the GameRoute server, or the lookup fails for any reason (backend
                            // hiccup, etc.), still complete the connect with no role/join-date rather than
                            // failing the whole login over it.
                            if (err != null) {
                                log.debug("Could not resolve Discord role/membership, continuing without it", err);
                            }
                            GuildRole role = membership != null ? membership.role() : null;
                            String joinedAt = membership != null && membership.joinedAt() != null ? membership.joinedAt() : "";
                            String roleId = role != null ? role.id() : "";
                            String roleName = role != null ? role.name() : "";
                            // color 0 means the role has no color set (Discord shows it in the default
                            // text color) -- leave blank rather than rendering that as literal black.
                            String roleColor = role != null && role.color() != 0 ? String.format("#%06X", role.color()) : "";
                            DiscordUser withDetails = new DiscordUser(user.id(), user.username(), user.globalName(),
                                    user.avatarHash(), roleId, roleName, roleColor, joinedAt);
                            config.saveDiscordAccount(withDetails.id(), withDetails.username(), withDetails.globalName(),
                                    withDetails.avatarHash(), tokens[0], tokens[1], withDetails.roleId(), withDetails.roleName(),
                                    withDetails.roleColor(), withDetails.guildJoinedAt());
                            return withDetails;
                        })));
    }

    private record RoleResolution(GuildRole role, String joinedAt) {
    }

    /**
     * The member's highest-position role in the GameRoute Discord server (restricted to
     * {@link Constants#DISCORD_DISPLAYABLE_ROLE_IDS} -- the server also has bot/utility/permission
     * roles that a member can hold without it meaning anything about their actual standing, so
     * those are never candidates here even if their raw position would otherwise outrank a real
     * role like Owner), plus when they joined that server. Role is {@code null} if they hold none
     * of the displayable roles, or aren't a member of that server at all.
     */
    private CompletableFuture<RoleResolution> resolveMembership(String accessToken) {
        return fetchMember(accessToken).thenCompose(member -> {
            if (member.roleIds().isEmpty()) {
                return CompletableFuture.completedFuture(new RoleResolution(null, member.joinedAt()));
            }
            return fetchGuildRoles().thenApply(guildRoles -> {
                GuildRole top = guildRoles.stream()
                        .filter(r -> member.roleIds().contains(r.id()))
                        .filter(r -> Constants.DISCORD_DISPLAYABLE_ROLE_IDS.contains(r.id()))
                        .max(java.util.Comparator.comparingInt(GuildRole::position))
                        .orElse(null);
                return new RoleResolution(top, member.joinedAt());
            });
        });
    }

    /** The user's role IDs and join date in the GameRoute server, or an empty/blank pair if they aren't a member. */
    private CompletableFuture<GuildMember> fetchMember(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MEMBER_URL))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() == 404) {
                return new GuildMember(List.of(), ""); // not a member of the GameRoute server
            }
            if (response.statusCode() != 200) {
                throw new RuntimeException("Could not fetch guild membership: HTTP " + response.statusCode());
            }
            String json = response.body();
            return new GuildMember(stringArrayField(json, "roles"), field(json, "joined_at"));
        });
    }

    /** All roles defined in the GameRoute server, resolved via GameRoute's own backend (needs a bot token). */
    private CompletableFuture<List<GuildRole>> fetchGuildRoles() {
        String url = Constants.DISCORD_GUILD_ROLES_ENDPOINT + "?guildId=" + Constants.DISCORD_GUILD_ID;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new RuntimeException("Could not fetch guild role catalog: HTTP " + response.statusCode());
            }
            return parseGuildRoles(response.body());
        });
    }

    private static List<GuildRole> parseGuildRoles(String json) {
        List<GuildRole> roles = new ArrayList<>();
        Matcher m = Pattern.compile("\\{\\s*\"id\"\\s*:\\s*\"(\\d+)\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]*)\"\\s*,"
                + "\\s*\"color\"\\s*:\\s*(\\d+)\\s*,\\s*\"position\"\\s*:\\s*(-?\\d+)\\s*}").matcher(json);
        while (m.find()) {
            roles.add(new GuildRole(m.group(1), m.group(2), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))));
        }
        return roles;
    }

    /** Parses a {@code "key":["a","b"]} JSON string array (Discord's member.roles shape) without a JSON library. */
    private static List<String> stringArrayField(String json, String key) {
        Matcher outer = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^]]*)]").matcher(json);
        if (!outer.find()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Matcher inner = Pattern.compile("\"([^\"]*)\"").matcher(outer.group(1));
        while (inner.find()) {
            values.add(inner.group(1));
        }
        return values;
    }

    /** Best-effort server-side revoke, then always clears the locally stored account regardless. */
    public void disconnect(AppConfig config) {
        String token = config.getDiscordAccessToken();
        config.clearDiscordAccount();
        if (token.isBlank()) {
            return;
        }
        String body = "token=" + encode(token) + "&client_id=" + encode(Constants.DISCORD_RPC_CLIENT_ID);
        HttpRequest request = HttpRequest.newBuilder(URI.create(REVOKE_URL))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(e -> {
                    log.debug("Discord token revoke failed (already disconnected locally): {}", e.getMessage());
                    return null;
                });
    }

    /** @return {@code [accessToken, refreshToken]} */
    private CompletableFuture<String[]> exchangeCodeForToken(String code, String verifier) {
        String body = "client_id=" + encode(Constants.DISCORD_RPC_CLIENT_ID)
                + "&grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(Constants.DISCORD_OAUTH_REDIRECT_URI)
                + "&code_verifier=" + encode(verifier);
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new RuntimeException("Discord token exchange failed: HTTP " + response.statusCode() + " " + response.body());
            }
            String json = response.body();
            return new String[]{field(json, "access_token"), field(json, "refresh_token")};
        });
    }

    private CompletableFuture<DiscordUser> fetchUser(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ME_URL))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new RuntimeException("Could not fetch Discord profile: HTTP " + response.statusCode());
            }
            String json = response.body();
            return new DiscordUser(field(json, "id"), field(json, "username"), field(json, "global_name"),
                    field(json, "avatar"), "", "", "", "");
        });
    }

    private static String field(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static java.util.Map<String, String> parseQuery(String rawQuery) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        if (rawQuery == null) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String randomUrlSafe(int bytes) {
        byte[] raw = new byte[bytes];
        new SecureRandom().nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String codeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
