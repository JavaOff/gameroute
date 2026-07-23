package com.gameroute.ui.tabs;

import com.gameroute.config.Constants;
import com.gameroute.ui.AppServices;
import com.gameroute.ui.components.Animations;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Only reachable by Owner/Administrator/Moderator in the GameRoute Discord server (gated in
 * {@link com.gameroute.ui.MainView}, which omits this page's nav entry entirely for anyone
 * else). Shows aggregate usage numbers (same as the Discord bot's {@code /userstats}) plus a
 * "who's connected" list -- built only from users who explicitly opted into the separate
 * "Share my Discord identity with server admins" toggle in Settings (off by default). The
 * backend independently re-verifies the caller's own role via Discord's API before returning
 * that list, so this stays admin-only even though the app's source is public.
 */
public class AdminView extends VBox {

    private static final String CDN_AVATAR_BASE = "https://cdn.discordapp.com/avatars/";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final Label onlineNowValue = new Label("...");
    private final Label downloadsValue = new Label("...");
    private final VBox connectedUsersList = new VBox(8);
    private final Label connectedUsersStatus = new Label("Loading...");

    private record ConnectedUser(String id, long lastSeen, String username, String globalName, String avatarHash) {
        String displayName() {
            return (globalName != null && !globalName.isBlank()) ? globalName : username;
        }
    }

    public AdminView(AppServices services) {
        setSpacing(16);
        setPadding(new Insets(24));

        Label title = new Label("ADMIN");
        title.getStyleClass().add("card-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("button-ghost");

        HBox header = new HBox(16, title, spacer, refresh);
        header.setAlignment(Pos.CENTER_LEFT);

        Label note = new Label("Aggregate numbers below are always anonymous. The \"connected users\" list includes "
                + "server staff (Owner/Administrator/Moderator, on by policy for that role) plus any regular member "
                + "who explicitly opted in via Settings > Privacy > \"Share my connected Discord identity with "
                + "server admins\" -- everyone else stays invisible here.");
        note.getStyleClass().add("card-subtitle");
        note.setWrapText(true);

        HBox statsRow = new HBox(18, statTile("ONLINE RIGHT NOW", onlineNowValue), statTile("DOWNLOADS (ALL-TIME)", downloadsValue));

        VBox statsCard = new VBox(14, header, note, statsRow);
        statsCard.getStyleClass().addAll("glass-card", "glass-card-hover");

        Label connectedTitle = new Label("CONNECTED USERS");
        connectedTitle.getStyleClass().add("card-title");
        connectedUsersStatus.getStyleClass().add("card-subtitle");
        VBox connectedCard = new VBox(12, connectedTitle, connectedUsersStatus, connectedUsersList);
        connectedCard.getStyleClass().addAll("glass-card", "glass-card-hover");

        getChildren().addAll(statsCard, connectedCard);
        Animations.fadeInUp(statsCard, 380, 14);
        Animations.fadeInUp(connectedCard, 380, 20);

        refresh.setOnAction(e -> {
            loadStats();
            loadConnectedUsers(services);
        });
        loadStats();
        loadConnectedUsers(services);
    }

    private VBox statTile(String label, Label value) {
        Label l = new Label(label);
        l.getStyleClass().add("card-title");
        value.setStyle("-fx-font-size: 26px; -fx-font-weight: 800;");
        value.getStyleClass().add("mono");
        VBox tile = new VBox(4, l, value);
        tile.setPadding(new Insets(10, 18, 10, 18));
        tile.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 12;"
                + "-fx-border-color: -fx-border-subtle; -fx-border-radius: 12; -fx-border-width: 1;");
        HBox.setHgrow(tile, Priority.ALWAYS);
        return tile;
    }

    private void loadStats() {
        onlineNowValue.setText("...");
        downloadsValue.setText("...");
        HttpRequest request = HttpRequest.newBuilder(URI.create(Constants.STATS_ENDPOINT))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    String body = response.statusCode() == 200 ? response.body() : "";
                    String onlineNow = numberField(body, "onlineNow");
                    String downloads = numberField(body, "downloads");
                    Platform.runLater(() -> {
                        onlineNowValue.setText(onlineNow != null ? onlineNow : "unavailable");
                        downloadsValue.setText(downloads != null ? downloads : "unavailable");
                    });
                })
                .exceptionally(err -> {
                    Platform.runLater(() -> {
                        onlineNowValue.setText("unavailable");
                        downloadsValue.setText("unavailable");
                    });
                    return null;
                });
    }

    private void loadConnectedUsers(AppServices services) {
        connectedUsersStatus.setText("Loading...");
        connectedUsersStatus.setManaged(true);
        connectedUsersStatus.setVisible(true);
        connectedUsersList.getChildren().clear();

        String accessToken = services.config().getDiscordAccessToken();
        if (accessToken.isBlank()) {
            connectedUsersStatus.setText("Connect your own Discord account to load this list.");
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(Constants.DISCORD_PRESENCE_ENDPOINT))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        Platform.runLater(() -> connectedUsersStatus.setText("Could not load (HTTP " + response.statusCode() + ")."));
                        return;
                    }
                    List<ConnectedUser> users = parseConnectedUsers(response.body());
                    Platform.runLater(() -> renderConnectedUsers(users));
                })
                .exceptionally(err -> {
                    Platform.runLater(() -> connectedUsersStatus.setText("Could not reach the server."));
                    return null;
                });
    }

    private void renderConnectedUsers(List<ConnectedUser> users) {
        connectedUsersList.getChildren().clear();
        if (users.isEmpty()) {
            connectedUsersStatus.setText("Nobody has opted in right now.");
            connectedUsersStatus.setManaged(true);
            connectedUsersStatus.setVisible(true);
            return;
        }
        connectedUsersStatus.setManaged(false);
        connectedUsersStatus.setVisible(false);
        for (ConnectedUser user : users) {
            connectedUsersList.getChildren().add(connectedUserRow(user));
        }
    }

    private HBox connectedUserRow(ConnectedUser user) {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("avatar-circle");
        avatar.setPrefSize(28, 28);
        avatar.setMaxSize(28, 28);
        if (!user.avatarHash().isBlank()) {
            ImageView view = new ImageView(new Image(CDN_AVATAR_BASE + user.id() + "/" + user.avatarHash() + ".png?size=64",
                    28, 28, false, true, true));
            Circle clip = new Circle(14, 14, 14);
            view.setClip(clip);
            avatar.getChildren().add(view);
        } else {
            Label initials = new Label(user.displayName().length() >= 2
                    ? user.displayName().substring(0, 2).toUpperCase(Locale.ROOT)
                    : user.displayName().toUpperCase(Locale.ROOT));
            initials.getStyleClass().add("avatar-initials");
            initials.setStyle("-fx-font-size: 11px;");
            avatar.getChildren().add(initials);
        }

        Label name = new Label(user.displayName());
        name.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;");

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        Label lastSeen = new Label(relativeTime(user.lastSeen()));
        lastSeen.getStyleClass().add("card-subtitle");

        HBox row = new HBox(10, avatar, name, grow, lastSeen);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static String relativeTime(long epochSeconds) {
        long secondsAgo = Instant.now().getEpochSecond() - epochSeconds;
        if (secondsAgo < 90) {
            return "just now";
        }
        long minutesAgo = secondsAgo / 60;
        return minutesAgo + " min ago";
    }

    /** @return the field's numeric value, or {@code null} if missing/JSON {@code null} (backend couldn't determine it). */
    private static String numberField(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+|null)").matcher(json);
        if (!m.find() || "null".equals(m.group(1))) {
            return null;
        }
        return m.group(1);
    }

    /** Parses the {@code {"users":[{"id":"...","lastSeen":123,"username":"...","globalName":"...","avatarHash":"..."}]}} shape. */
    private static List<ConnectedUser> parseConnectedUsers(String json) {
        List<ConnectedUser> users = new ArrayList<>();
        Matcher m = Pattern.compile("\\{\\s*\"id\"\\s*:\\s*\"(\\d+)\"\\s*,\\s*\"lastSeen\"\\s*:\\s*(\\d+)\\s*,"
                + "\\s*\"username\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"globalName\"\\s*:\\s*\"([^\"]*)\"\\s*,"
                + "\\s*\"avatarHash\"\\s*:\\s*\"([^\"]*)\"\\s*}").matcher(json);
        while (m.find()) {
            users.add(new ConnectedUser(m.group(1), Long.parseLong(m.group(2)), m.group(3), m.group(4), m.group(5)));
        }
        return users;
    }
}
