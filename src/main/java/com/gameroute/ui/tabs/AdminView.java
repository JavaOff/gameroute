package com.gameroute.ui.tabs;

import com.gameroute.config.Constants;
import com.gameroute.ui.AppServices;
import com.gameroute.ui.ThemeManager;
import com.gameroute.ui.components.Animations;
import com.gameroute.utils.MiniJson;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final DateTimeFormatter REPORT_DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

    private final Label onlineNowValue = new Label("...");
    private final Label downloadsValue = new Label("...");
    private final VBox connectedUsersList = new VBox(8);
    private final Label connectedUsersStatus = new Label("Loading...");
    private final VBox bugReportUsersList = new VBox(8);
    private final Label bugReportUsersStatus = new Label("Loading...");

    private record ConnectedUser(String id, long lastSeen, String username, String globalName, String avatarHash) {
        String displayName() {
            return (globalName != null && !globalName.isBlank()) ? globalName : username;
        }
    }

    private record BugReportUserSummary(String userId, String username, String globalName, String avatarHash,
                                         boolean isDiscordUser, int reportCount, long latestSubmittedAt) {
        String displayName() {
            if (globalName != null && !globalName.isBlank()) {
                return globalName;
            }
            if (username != null && !username.isBlank()) {
                return username;
            }
            return isDiscordUser ? userId : "Anonymous install";
        }
    }

    private record BugReport(String id, String userId, boolean isDiscordUser, String username, String globalName,
                              String avatarHash, String version, String os, String description, String logs,
                              long submittedAt, String status, String notes) {
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

        Label bugReportsTitle = new Label("BUG REPORTS");
        bugReportsTitle.getStyleClass().add("card-title");
        bugReportUsersStatus.getStyleClass().add("card-subtitle");
        VBox bugReportsCard = new VBox(12, bugReportsTitle, bugReportUsersStatus, bugReportUsersList);
        bugReportsCard.getStyleClass().addAll("glass-card", "glass-card-hover");

        getChildren().addAll(statsCard, connectedCard, bugReportsCard);
        Animations.fadeInUp(statsCard, 380, 14);
        Animations.fadeInUp(connectedCard, 380, 20);
        Animations.fadeInUp(bugReportsCard, 380, 26);

        refresh.setOnAction(e -> {
            loadStats();
            loadConnectedUsers(services);
            loadBugReportUsers(services);
        });
        loadStats();
        loadConnectedUsers(services);
        loadBugReportUsers(services);
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

    // ---------------------------------------------------------------- bug reports

    private void loadBugReportUsers(AppServices services) {
        bugReportUsersStatus.setText("Loading...");
        bugReportUsersStatus.setManaged(true);
        bugReportUsersStatus.setVisible(true);
        bugReportUsersList.getChildren().clear();

        String accessToken = services.config().getDiscordAccessToken();
        if (accessToken.isBlank()) {
            bugReportUsersStatus.setText("Connect your own Discord account to load this list.");
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(Constants.BUG_REPORTS_ENDPOINT))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        Platform.runLater(() -> bugReportUsersStatus.setText("Could not load (HTTP " + response.statusCode() + ")."));
                        return;
                    }
                    List<BugReportUserSummary> users = parseBugReportUsers(response.body());
                    Platform.runLater(() -> renderBugReportUsers(services, users));
                })
                .exceptionally(err -> {
                    Platform.runLater(() -> bugReportUsersStatus.setText("Could not reach the server."));
                    return null;
                });
    }

    private void renderBugReportUsers(AppServices services, List<BugReportUserSummary> users) {
        bugReportUsersList.getChildren().clear();
        if (users.isEmpty()) {
            bugReportUsersStatus.setText("No reports yet.");
            bugReportUsersStatus.setManaged(true);
            bugReportUsersStatus.setVisible(true);
            return;
        }
        bugReportUsersStatus.setManaged(false);
        bugReportUsersStatus.setVisible(false);
        for (BugReportUserSummary user : users) {
            bugReportUsersList.getChildren().add(bugReportUserRow(services, user));
        }
    }

    private HBox bugReportUserRow(AppServices services, BugReportUserSummary user) {
        StackPane avatar = smallAvatar(user.userId(), user.avatarHash(), user.displayName(), 28);

        Label name = new Label(user.displayName());
        name.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;");

        Label countBadge = new Label(user.reportCount() + (user.reportCount() == 1 ? " report" : " reports"));
        countBadge.getStyleClass().add("card-subtitle");

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        Label latest = new Label(relativeTime(user.latestSubmittedAt()));
        latest.getStyleClass().add("card-subtitle");

        Button view = new Button("View");
        view.getStyleClass().add("button-ghost");
        view.setOnAction(e -> openReportsDialog(services, user, view));

        HBox row = new HBox(10, avatar, name, countBadge, grow, latest, view);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private StackPane smallAvatar(String id, String avatarHash, String displayName, double size) {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("avatar-circle");
        avatar.setPrefSize(size, size);
        avatar.setMaxSize(size, size);
        if (avatarHash != null && !avatarHash.isBlank()) {
            ImageView view = new ImageView(new Image(CDN_AVATAR_BASE + id + "/" + avatarHash + ".png?size=64",
                    size, size, false, true, true));
            Circle clip = new Circle(size / 2, size / 2, size / 2);
            view.setClip(clip);
            avatar.getChildren().add(view);
        } else {
            String base = (displayName == null || displayName.isBlank()) ? "?" : displayName;
            String initials = base.length() >= 2 ? base.substring(0, 2).toUpperCase(Locale.ROOT) : base.toUpperCase(Locale.ROOT);
            Label label = new Label(initials);
            label.getStyleClass().add("avatar-initials");
            label.setStyle("-fx-font-size: 11px;");
            avatar.getChildren().add(label);
        }
        return avatar;
    }

    /** Opens a modal window with every report from this one user, newest first, each editable (status/notes). */
    private void openReportsDialog(AppServices services, BugReportUserSummary user, Node anchor) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        Window owner = anchor.getScene().getWindow();
        dialog.initOwner(owner);
        dialog.setTitle(user.displayName() + " -- bug reports");

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));
        Label loading = new Label("Loading reports...");
        loading.getStyleClass().add("card-subtitle");
        content.getChildren().add(loading);

        content.setStyle("-fx-background-color: #0F1013;");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(580, 660);
        scroll.setStyle("-fx-background-color: #0F1013;");

        Scene scene = new Scene(scroll);
        scene.setFill(javafx.scene.paint.Color.web("#0F1013"));
        scene.getStylesheets().setAll(ThemeManager.stylesheets());
        dialog.setScene(scene);
        dialog.show();

        String accessToken = services.config().getDiscordAccessToken();
        String url = Constants.BUG_REPORTS_ENDPOINT + "?userId=" + URLEncoder.encode(user.userId(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        Platform.runLater(() -> content.getChildren().setAll(
                                new Label("Could not load (HTTP " + response.statusCode() + ").")));
                        return;
                    }
                    List<BugReport> reports = parseBugReports(response.body());
                    Platform.runLater(() -> {
                        content.getChildren().clear();
                        if (reports.isEmpty()) {
                            content.getChildren().add(new Label("No reports found."));
                        } else {
                            for (BugReport report : reports) {
                                content.getChildren().add(reportCard(services, report));
                            }
                        }
                    });
                })
                .exceptionally(err -> {
                    Platform.runLater(() -> content.getChildren().setAll(new Label("Could not reach the server.")));
                    return null;
                });
    }

    private VBox reportCard(AppServices services, BugReport report) {
        Label dateLabel = new Label(REPORT_DATE_FMT.format(Instant.ofEpochSecond(report.submittedAt()).atZone(ZoneId.systemDefault())));
        dateLabel.setStyle("-fx-font-weight: 700; -fx-font-size: 12px;");

        Label meta = new Label("v" + (report.version().isBlank() ? "?" : report.version())
                + " -- " + (report.os().isBlank() ? "unknown OS" : report.os()));
        meta.getStyleClass().add("card-subtitle");

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        HBox header = new HBox(10, dateLabel, grow, meta);
        header.setAlignment(Pos.CENTER_LEFT);

        Label descTitle = new Label("DESCRIPTION");
        descTitle.getStyleClass().add("card-title");
        TextArea description = new TextArea(report.description());
        description.setEditable(false);
        description.setWrapText(true);
        description.setPrefRowCount(3);

        Label logsTitle = new Label("LOGS");
        logsTitle.getStyleClass().add("card-title");
        TextArea logs = new TextArea(report.logs().isBlank() ? "(no logs attached)" : report.logs());
        logs.setEditable(false);
        logs.setWrapText(false);
        logs.setPrefRowCount(6);
        logs.getStyleClass().add("mono");

        Label statusTitle = new Label("STATUS");
        statusTitle.getStyleClass().add("card-title");
        ComboBox<String> status = new ComboBox<>();
        status.getItems().addAll("Open", "In Progress", "Fixed", "Closed");
        status.setValue(report.status().isBlank() ? "Open" : report.status());

        Label notesTitle = new Label("INTERNAL NOTES");
        notesTitle.getStyleClass().add("card-title");
        TextArea notes = new TextArea(report.notes());
        notes.setWrapText(true);
        notes.setPrefRowCount(2);
        notes.setPromptText("Only visible to server staff.");

        Button save = new Button("Save");
        save.getStyleClass().add("button-ghost");
        Label saveStatus = new Label();
        saveStatus.getStyleClass().add("card-subtitle");
        save.setOnAction(e -> {
            save.setDisable(true);
            saveStatus.setText("Saving...");
            updateReport(services, report.id(), status.getValue(), notes.getText())
                    .whenComplete((v, err) -> Platform.runLater(() -> {
                        save.setDisable(false);
                        saveStatus.setText(err != null ? "Could not save." : "Saved.");
                    }));
        });
        HBox saveRow = new HBox(10, save, saveStatus);
        saveRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, header, descTitle, description, logsTitle, logs, statusTitle, status, notesTitle, notes, saveRow);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 12;"
                + "-fx-border-color: -fx-border-subtle; -fx-border-radius: 12; -fx-border-width: 1;");
        return card;
    }

    private java.util.concurrent.CompletableFuture<Void> updateReport(AppServices services, String reportId, String status, String notes) {
        String accessToken = services.config().getDiscordAccessToken();
        String body = "{\"reportId\":\"" + escapeJson(reportId) + "\",\"status\":\"" + escapeJson(status)
                + "\",\"notes\":\"" + escapeJson(notes) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(Constants.BUG_REPORTS_ENDPOINT))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("HTTP " + response.statusCode());
                    }
                });
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    @SuppressWarnings("unchecked")
    private static List<BugReportUserSummary> parseBugReportUsers(String json) {
        Map<String, Object> root = MiniJson.parseObject(json);
        Object usersObj = root.get("users");
        List<BugReportUserSummary> result = new ArrayList<>();
        if (usersObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> user = (Map<String, Object>) m;
                    result.add(new BugReportUserSummary(
                            str(user, "userId"), str(user, "username"), str(user, "globalName"), str(user, "avatarHash"),
                            bool(user, "isDiscordUser"), (int) num(user, "reportCount"), (long) num(user, "latestSubmittedAt")));
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<BugReport> parseBugReports(String json) {
        Map<String, Object> root = MiniJson.parseObject(json);
        Object reportsObj = root.get("reports");
        List<BugReport> result = new ArrayList<>();
        if (reportsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> r = (Map<String, Object>) m;
                    result.add(new BugReport(
                            str(r, "id"), str(r, "userId"), bool(r, "isDiscordUser"), str(r, "username"), str(r, "globalName"),
                            str(r, "avatarHash"), str(r, "version"), str(r, "os"), str(r, "description"), str(r, "logs"),
                            (long) num(r, "submittedAt"), str(r, "status"), str(r, "notes")));
                }
            }
        }
        return result;
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static boolean bool(Map<String, Object> map, String key) {
        return Boolean.TRUE.equals(map.get(key));
    }

    private static double num(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}
