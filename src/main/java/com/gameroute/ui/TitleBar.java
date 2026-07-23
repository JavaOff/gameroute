package com.gameroute.ui;

import com.gameroute.config.Constants;
import com.gameroute.config.GameCatalog;
import com.gameroute.service.NotificationCenter;
import com.gameroute.service.NotificationCenter.AppNotification;
import com.gameroute.ui.icons.Icons;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Custom borderless-window title bar, Windows 11 style: brand mark, a
 * draggable middle zone (double-click toggles maximize, the OS convention),
 * a quick-jump search box, the live connection indicator and monitored
 * region chip, then user avatar, notification center (with unread badge),
 * theme switcher and settings, ahead of the three window controls.
 */
public class TitleBar extends HBox {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final AppServices services;
    private double dragOffsetX;
    private double dragOffsetY;
    private Button avatarButton;

    public TitleBar(Stage stage, AppServices services, Consumer<String> onNavigate, Runnable onSettingsClick) {
        this.services = services;
        getStyleClass().add("title-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);

        HBox brand = buildBrand();

        HBox dragZone = new HBox(brand);
        dragZone.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(dragZone, Priority.ALWAYS);
        installDrag(dragZone, stage);

        HBox searchBox = buildSearchBox(onNavigate);
        HBox connectionChip = buildConnectionChip();
        HBox regionChip = buildRegionChip();

        Button avatarBtn = buildAvatarButton();
        Button bellBtn = buildBellButton();
        Button themeBtn = buildThemeButton();
        Button settingsBtn = iconButton(Icons.gear(18, Color.web("#F5F5F7")));
        settingsBtn.setOnAction(e -> onSettingsClick.run());

        HBox windowControls = buildWindowControls(stage);

        getChildren().addAll(dragZone, searchBox, spacer(6), connectionChip, regionChip, spacer(6),
                avatarBtn, bellBtn, themeBtn, settingsBtn, spacer(14), windowControls);
    }

    // ---------------------------------------------------------------- search

    private static final class SearchTarget {
        final String label;
        final String sublabel;
        final String pageId;

        SearchTarget(String label, String sublabel, String pageId) {
            this.label = label;
            this.sublabel = sublabel;
            this.pageId = pageId;
        }
    }

    private static final List<SearchTarget> STATIC_TARGETS = List.of(
            new SearchTarget("Dashboard", "Live overview", "dashboard"),
            new SearchTarget("Games", "Supported game library", "games"),
            new SearchTarget("Optimizer", "One-click tuning actions", "optimizer"),
            new SearchTarget("Servers", "Region latency browser", "servers"),
            new SearchTarget("Statistics", "Ping history & trends", "statistics"),
            new SearchTarget("Diagnostics", "Running apps & route analysis", "diagnostics"),
            new SearchTarget("Profiles", "Per-game optimization profiles", "profiles"),
            new SearchTarget("Logs", "Application log", "logs"),
            new SearchTarget("Settings", "Appearance & preferences", "settings")
    );

    private HBox buildSearchBox(Consumer<String> onNavigate) {
        TextField field = new TextField();
        field.setPromptText("Search pages and games...");
        field.getStyleClass().add("titlebar-search-field");
        field.setPrefWidth(240);

        HBox box = new HBox(8, Icons.search(14, Color.web("#B9BEC7")), field);
        box.getStyleClass().add("titlebar-search");
        box.setAlignment(Pos.CENTER_LEFT);

        Popup results = newPopup();
        VBox resultsBox = popupBox();
        results.getContent().add(resultsBox);

        field.textProperty().addListener((obs, old, text) -> {
            resultsBox.getChildren().clear();
            String query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            if (query.isEmpty()) {
                results.hide();
                return;
            }
            List<SearchTarget> matches = new java.util.ArrayList<>();
            for (SearchTarget target : STATIC_TARGETS) {
                if (target.label.toLowerCase(Locale.ROOT).contains(query)) {
                    matches.add(target);
                }
            }
            for (var game : GameCatalog.GAMES) {
                if (game.displayName().toLowerCase(Locale.ROOT).contains(query)) {
                    matches.add(new SearchTarget(game.displayName(), "Open in Profiles", "profiles"));
                }
            }
            if (matches.isEmpty()) {
                Label empty = new Label("No matches");
                empty.getStyleClass().add("card-subtitle");
                resultsBox.getChildren().add(empty);
            } else {
                for (SearchTarget target : matches.subList(0, Math.min(8, matches.size()))) {
                    resultsBox.getChildren().add(searchResultRow(target, field, results, onNavigate));
                }
            }
            if (!results.isShowing()) {
                var bounds = box.localToScreen(box.getBoundsInLocal());
                results.show(box.getScene().getWindow(), bounds.getMinX(), bounds.getMaxY() + 6);
            }
            resultsBox.getStylesheets().setAll(ThemeManager.stylesheets());
        });
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                results.hide();
            }
        });
        return box;
    }

    private HBox searchResultRow(SearchTarget target, TextField field, Popup popup, Consumer<String> onNavigate) {
        Label name = new Label(target.label);
        name.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");
        Label sub = new Label(target.sublabel);
        sub.getStyleClass().add("card-subtitle");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox row = new HBox(8, name, grow, sub);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("notif-item");
        row.setPrefWidth(260);
        row.setCursor(javafx.scene.Cursor.HAND);
        row.setOnMouseClicked(e -> {
            onNavigate.accept(target.pageId);
            field.clear();
            popup.hide();
        });
        return row;
    }

    // ---------------------------------------------------------------- connection + region chips

    private HBox buildConnectionChip() {
        Circle dot = new Circle(4);
        dot.getStyleClass().add("dot-warn");
        Label text = new Label("Connecting");
        text.setStyle("-fx-font-size: 11px; -fx-font-weight: 700;");
        HBox chip = new HBox(6, dot, text);
        chip.getStyleClass().add("chip");
        chip.setAlignment(Pos.CENTER_LEFT);

        services.pingMonitor().addListener((sample, stats) -> javafx.application.Platform.runLater(() -> {
            boolean online = stats.currentMs() >= 0;
            dot.getStyleClass().removeAll("dot-good", "dot-bad", "dot-warn");
            dot.getStyleClass().add(online ? "dot-good" : "dot-bad");
            text.setText(online ? "Connected" : "Connection issues");
        }));
        return chip;
    }

    private HBox buildRegionChip() {
        Label text = new Label(regionLabel());
        text.setStyle("-fx-font-size: 11px; -fx-font-weight: 700;");
        HBox chip = new HBox(6, Icons.globe(12, Color.web("#B9BEC7")), text);
        chip.getStyleClass().add("chip");
        chip.setAlignment(Pos.CENTER_LEFT);

        // No config-change-listener plumbing exists, so poll cheaply -- the
        // preferred region only changes via the Dashboard's region picker.
        Timeline poll = new Timeline(new KeyFrame(Duration.seconds(2), e -> text.setText(regionLabel())));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
        return chip;
    }

    private String regionLabel() {
        try {
            return com.gameroute.model.Region.valueOf(services.config().getPreferredRegion()).getCode();
        } catch (Exception e) {
            return "--";
        }
    }

    private HBox buildBrand() {
        Label mark = new Label("●");
        mark.getStyleClass().add("title-logo-mark");
        mark.setStyle("-fx-font-size: 10px;");
        Label name = new Label("GameRoute");
        name.getStyleClass().add("title-logo");
        HBox brand = new HBox(8, mark, name);
        brand.setAlignment(Pos.CENTER_LEFT);
        return brand;
    }

    // ---------------------------------------------------------------- avatar

    private Button buildAvatarButton() {
        avatarButton = iconButton(avatarVisual());
        avatarButton.setOnAction(e -> showPopup(avatarButton, buildAccountPopupContent()));
        return avatarButton;
    }

    /** Discord's own "blurple" -- used as the ring color when connected but the role has no color set. */
    private static final String DISCORD_BLURPLE = "#5865F2";

    /** The circular avatar graphic: the connected Discord user's real picture, or local initials. */
    private StackPane avatarVisual() {
        return avatarVisual(22, true);
    }

    /**
     * @param size     avatar diameter in pixels
     * @param withRing whether to draw a thin colored ring around it -- the connected user's role
     *                 color if they have one, Discord's own blurple otherwise, or nothing at all
     *                 when not connected (there's no real status to represent yet).
     */
    private StackPane avatarVisual(double size, boolean withRing) {
        var discordUser = services.discordAccountService().currentUser(services.config());
        double radius = size / 2.0;
        StackPane avatar;
        if (discordUser.isPresent()) {
            var urlOpt = services.discordAccountService().avatarUrl(discordUser.get());
            if (urlOpt.isPresent()) {
                ImageView view = new ImageView(new Image(urlOpt.get(), size, size, false, true, true));
                Circle clip = new Circle(radius, radius, radius);
                view.setClip(clip);
                avatar = new StackPane(view);
            } else {
                avatar = new StackPane(discordInitialsLabel(discordUser.get().displayName(), size));
            }
        } else {
            String userName = System.getProperty("user.name", "Player");
            avatar = new StackPane(discordInitialsLabel(userName, size));
        }
        avatar.getStyleClass().add("avatar-circle");
        avatar.setPrefSize(size, size);
        avatar.setMaxSize(size, size);

        if (withRing && discordUser.isPresent()) {
            String ringColorHex = discordUser.get().hasRole() ? discordUser.get().roleColor() : "";
            if (ringColorHex == null || ringColorHex.isBlank()) {
                ringColorHex = DISCORD_BLURPLE;
            }
            double ringRadius = radius + 2;
            Circle ring = new Circle(ringRadius, ringRadius, ringRadius);
            ring.setFill(Color.TRANSPARENT);
            ring.setStroke(Color.web(ringColorHex));
            ring.setStrokeWidth(2);
            StackPane ringed = new StackPane(ring, avatar);
            ringed.setPrefSize(ringRadius * 2, ringRadius * 2);
            ringed.setMaxSize(ringRadius * 2, ringRadius * 2);
            return ringed;
        }
        return avatar;
    }

    private Label discordInitialsLabel(String name, double avatarSize) {
        String initials = name.length() >= 2 ? name.substring(0, 2).toUpperCase(Locale.ROOT) : name.toUpperCase(Locale.ROOT);
        Label label = new Label(initials);
        label.getStyleClass().add("avatar-initials");
        label.setStyle("-fx-font-size: " + Math.round(avatarSize * 0.4) + "px;");
        return label;
    }

    /** Re-reads the connected Discord account (or lack of one) and updates the title bar avatar. */
    public void refreshAvatarVisual() {
        avatarButton.setGraphic(avatarVisual());
    }

    private VBox buildAccountPopupContent() {
        VBox content = popupBox();
        content.setSpacing(14);
        var discordUser = services.discordAccountService().currentUser(services.config());

        if (discordUser.isPresent()) {
            var user = discordUser.get();
            StackPane bigAvatar = avatarVisual(52, true);

            Label title = new Label(user.displayName());
            title.setStyle("-fx-font-weight: 800; -fx-font-size: 16px;");

            VBox nameBlock = new VBox(4, title);
            nameBlock.setAlignment(Pos.CENTER_LEFT);
            if (user.hasDistinctUsername()) {
                Label handle = new Label("@" + user.username());
                handle.getStyleClass().add("card-subtitle");
                nameBlock.getChildren().add(handle);
            }
            if (user.hasRole()) {
                Region gap = new Region();
                gap.setMinHeight(2);
                nameBlock.getChildren().addAll(gap, roleBadge(user.roleName(), user.roleColor()));
            } else {
                Label sub = new Label("Connected via Discord");
                sub.getStyleClass().add("card-subtitle");
                nameBlock.getChildren().add(sub);
            }

            HBox header = new HBox(14, bigAvatar, nameBlock);
            header.setAlignment(Pos.CENTER_LEFT);

            VBox facts = new VBox(4);
            facts.getChildren().add(factRow("On Discord since", formatMonthYear(user.accountCreatedAt())));
            if (user.hasGuildJoinDate()) {
                parseDiscordTimestamp(user.guildJoinedAt())
                        .ifPresent(joined -> facts.getChildren().add(factRow("Member here since", formatMonthYear(joined))));
            }

            Button viewProfile = new Button("View on Discord ↗");
            viewProfile.getStyleClass().add("button-ghost");
            viewProfile.setMaxWidth(Double.MAX_VALUE);
            viewProfile.setOnAction(ev -> {
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://discord.com/users/" + user.id()));
                } catch (Exception e) {
                    // Best-effort -- no browser available or the call was refused; nothing else to do about it.
                }
            });

            Region divider = new Region();
            divider.setStyle("-fx-background-color: -fx-border-subtle;");
            divider.setMinHeight(1);
            divider.setMaxHeight(1);

            Button disconnect = new Button("Disconnect Discord");
            disconnect.getStyleClass().add("button-ghost");
            disconnect.setMaxWidth(Double.MAX_VALUE);
            disconnect.setOnAction(ev -> {
                services.discordAccountService().disconnect(services.config());
                refreshAvatarVisual();
            });
            content.getChildren().addAll(header, facts, divider, viewProfile, disconnect);
        } else {
            String userName = System.getProperty("user.name", "Player");
            Label title = new Label(userName);
            title.setStyle("-fx-font-weight: 800; -fx-font-size: 13px;");
            Label sub = new Label("Local profile · " + Constants.APP_NAME + " " + Constants.APP_VERSION);
            sub.getStyleClass().add("card-subtitle");
            Label note = new Label("Settings and statistics are stored locally in ~/.gameroute. Connecting Discord "
                    + "shares your id, username and avatar with the GameRoute server so its Owner/Administrator/"
                    + "Moderator can see who's currently connected -- that's part of connecting, not a separate "
                    + "setting. Disconnect anytime to stop.");
            note.getStyleClass().add("card-subtitle");
            note.setWrapText(true);
            note.setMaxWidth(220);
            Button connect = new Button("Connect Discord");
            connect.getStyleClass().add("button-ghost");
            connect.setOnAction(ev -> {
                connect.setDisable(true);
                connect.setText("Waiting for browser login...");
                services.discordAccountService().connect(services.config())
                        .thenAccept(user -> javafx.application.Platform.runLater(this::refreshAvatarVisual))
                        .exceptionally(err -> {
                            javafx.application.Platform.runLater(() -> {
                                connect.setDisable(false);
                                connect.setText("Connect Discord");
                            });
                            return null;
                        });
            });
            content.getChildren().addAll(title, sub, note, connect);
        }
        return content;
    }

    /** A small colored pill for the user's Discord server role -- tinted background, colored text, colored dot. */
    private HBox roleBadge(String roleName, String roleColorHex) {
        String colorHex = (roleColorHex != null && !roleColorHex.isBlank()) ? roleColorHex : "#B9BEC7";
        Circle dot = new Circle(3.5);
        dot.setFill(Color.web(colorHex));
        Label label = new Label(roleName);
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: " + colorHex + ";");
        HBox pill = new HBox(6, dot, label);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.setPadding(new Insets(4, 10, 4, 8));
        pill.setStyle("-fx-background-color: derive(" + colorHex + ", -85%); -fx-background-radius: 999;");
        return pill;
    }

    private static final DateTimeFormatter MONTH_YEAR_FMT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    /** "On Discord since" / "Member here since" style row -- a muted label and a bold value. */
    private HBox factRow(String label, String value) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("card-subtitle");
        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-font-size: 11px; -fx-font-weight: 700;");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox row = new HBox(6, labelNode, grow, valueNode);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String formatMonthYear(java.time.Instant instant) {
        return MONTH_YEAR_FMT.format(instant.atZone(ZoneId.systemDefault()));
    }

    /** Discord's {@code joined_at} shape (ISO-8601 with a numeric offset, e.g. {@code 2015-04-26T06:26:56.936000+00:00}). */
    private static Optional<java.time.Instant> parseDiscordTimestamp(String value) {
        try {
            return Optional.of(java.time.OffsetDateTime.parse(value).toInstant());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- notifications

    private Button buildBellButton() {
        NotificationCenter center = services.notificationCenter();

        Label badge = new Label();
        badge.getStyleClass().add("sidebar-badge");
        badge.setVisible(false);
        badge.setManaged(false);
        center.unreadCountProperty().addListener((obs, o, n) -> {
            badge.setText(n.intValue() > 9 ? "9+" : String.valueOf(n.intValue()));
            badge.setVisible(n.intValue() > 0);
        });

        StackPane stack = new StackPane(Icons.bell(18, Color.web("#F5F5F7")), badge);
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        badge.setTranslateX(6);
        badge.setTranslateY(-4);

        Button button = iconButton(stack);
        button.setOnAction(e -> {
            center.markAllRead();
            showPopup(button, buildNotificationList(center));
        });
        return button;
    }

    private VBox buildNotificationList(NotificationCenter center) {
        VBox content = popupBox();
        Label title = new Label("Notifications");
        title.setStyle("-fx-font-weight: 800; -fx-font-size: 13px;");
        content.getChildren().add(title);

        if (center.history().isEmpty()) {
            Label empty = new Label("You're all caught up -- no alerts right now.");
            empty.getStyleClass().add("card-subtitle");
            empty.setWrapText(true);
            content.getChildren().add(empty);
            return content;
        }

        VBox list = new VBox(6);
        int shown = 0;
        for (AppNotification notification : center.history()) {
            if (shown++ >= 12) {
                break;
            }
            Label itemTitle = new Label(notification.title());
            itemTitle.setStyle("-fx-font-weight: 700; -fx-font-size: 11px;");
            Label itemBody = new Label(notification.message());
            itemBody.getStyleClass().add("card-subtitle");
            itemBody.setWrapText(true);
            itemBody.setMaxWidth(240);
            Label time = new Label(TIME_FMT.format(notification.at().atZone(ZoneId.systemDefault())));
            time.getStyleClass().add("notif-time");

            Region grow = new Region();
            HBox.setHgrow(grow, Priority.ALWAYS);
            HBox head = new HBox(6, itemTitle, grow, time);
            head.setAlignment(Pos.CENTER_LEFT);

            VBox item = new VBox(2, head, itemBody);
            item.getStyleClass().add("notif-item");
            list.getChildren().add(item);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(280);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        content.getChildren().add(scroll);
        return content;
    }

    // ---------------------------------------------------------------- theme switch

    private Button buildThemeButton() {
        Button button = iconButton(Icons.palette(18, Color.web("#F5F5F7")));
        button.setOnAction(e -> {
            VBox content = popupBox();
            Label title = new Label("Theme");
            title.setStyle("-fx-font-weight: 800; -fx-font-size: 13px;");
            content.getChildren().add(title);

            Popup popup = newPopup();
            for (ThemeManager.Theme theme : ThemeManager.Theme.values()) {
                content.getChildren().add(themeRow(theme, popup));
            }
            popup.getContent().add(content);
            showPopupInstance(button, popup, content);
        });
        return button;
    }

    private HBox themeRow(ThemeManager.Theme theme, Popup popup) {
        Circle swatch = new Circle(6, theme.accent());
        Label name = new Label(theme.displayName());
        name.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        Label current = new Label(ThemeManager.current() == theme ? "✓" : "");
        current.setStyle("-fx-font-weight: 800;");

        HBox row = new HBox(10, swatch, name, grow, current);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("notif-item");
        row.setCursor(javafx.scene.Cursor.HAND);
        row.setPrefWidth(200);
        row.setOnMouseClicked(e -> {
            ThemeManager.apply(theme);
            services.config().setTheme(theme.name());
            popup.hide();
        });
        return row;
    }

    // ---------------------------------------------------------------- popup plumbing

    private VBox popupBox() {
        VBox content = new VBox(8);
        content.getStyleClass().add("glass-card");
        content.setPadding(new Insets(14));
        content.setMaxWidth(300);
        return content;
    }

    private Popup newPopup() {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        return popup;
    }

    private void showPopup(Region anchor, VBox content) {
        Popup popup = newPopup();
        popup.getContent().add(content);
        showPopupInstance(anchor, popup, content);
    }

    private void showPopupInstance(Region anchor, Popup popup, VBox content) {
        content.getStylesheets().setAll(ThemeManager.stylesheets());
        var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor.getScene().getWindow(), bounds.getMinX() - 180, bounds.getMaxY() + 8);
    }

    private Button iconButton(javafx.scene.Node icon) {
        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().add("window-btn");
        return button;
    }

    private HBox buildWindowControls(Stage stage) {
        Button minimize = iconButton(Icons.minus(14, Color.web("#B9BEC7")));
        minimize.setOnAction(e -> stage.setIconified(true));

        Button maximize = iconButton(Icons.square(13, Color.web("#B9BEC7")));
        maximize.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));

        Button close = iconButton(Icons.close(14, Color.web("#B9BEC7")));
        close.getStyleClass().add("window-btn-close");
        close.setOnAction(e -> stage.hide());

        HBox box = new HBox(4, minimize, maximize, close);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private Region spacer(double width) {
        Region region = new Region();
        region.setMinWidth(width);
        region.setMaxWidth(width);
        return region;
    }

    private void installDrag(Region dragZone, Stage stage) {
        dragZone.setOnMousePressed(e -> {
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
            e.consume();
        });
        dragZone.setOnMouseDragged(e -> {
            if (stage.isMaximized()) {
                return;
            }
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
            e.consume();
        });
        dragZone.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                stage.setMaximized(!stage.isMaximized());
            }
        });
    }
}
