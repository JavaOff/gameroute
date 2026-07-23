package com.gameroute.ui.tabs;

import com.gameroute.config.Constants;
import com.gameroute.service.UpdateService;
import com.gameroute.ui.AppServices;
import com.gameroute.ui.ThemeManager;
import com.gameroute.ui.components.Animations;
import com.gameroute.ui.components.Dialogs;
import com.gameroute.ui.icons.Icons;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * User-facing preferences: theme, autostart, start-minimized, language and
 * notifications. Anything that touches Windows configuration (autostart)
 * asks for confirmation before applying, same as the Optimizer tab.
 */
public class SettingsView extends ScrollPane {

    public SettingsView(AppServices services, Stage stage) {
        setFitToWidth(true);
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));

        VBox appearance = buildAppearanceCard(services, stage);
        VBox startup = buildStartupCard(services);
        VBox optimization = buildOptimizationCard(services);
        VBox updates = buildUpdatesCard(services, stage);
        VBox language = buildLanguageCard(services);
        VBox notifications = buildNotificationsCard(services);
        VBox discordPresence = buildDiscordPresenceCard(services);
        VBox privacy = buildPrivacyCard(services);
        VBox bugReport = buildBugReportCard(services);

        root.getChildren().addAll(appearance, startup, optimization, updates, language, notifications, discordPresence, privacy, bugReport);
        setContent(root);

        Animations.staggeredEntrance(
                List.of(appearance, startup, optimization, updates, language, notifications, discordPresence, privacy, bugReport), 70);
    }

    private HBox sectionTitle(String text, javafx.scene.Node icon) {
        Label title = new Label(text);
        title.getStyleClass().add("card-title");
        HBox row = new HBox(8, icon, title);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox buildAppearanceCard(AppServices services, Stage stage) {
        HBox title = sectionTitle("APPEARANCE", Icons.palette(16, Color.web("#B9BEC7")));

        Label label = new Label("Theme");
        label.getStyleClass().add("card-subtitle");

        HBox swatches = new HBox(10);
        swatches.setAlignment(Pos.CENTER_LEFT);
        for (ThemeManager.Theme theme : ThemeManager.Theme.values()) {
            swatches.getChildren().add(themeSwatch(services, theme, swatches));
        }

        Label note = new Label("Also switchable any time from the palette icon in the title bar.");
        note.getStyleClass().add("card-subtitle");

        VBox card = new VBox(12, title, label, swatches, note);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    /** A clickable theme preview tile: accent dot on the theme's card color, highlighted when active. */
    private VBox themeSwatch(AppServices services, ThemeManager.Theme theme, HBox allSwatches) {
        javafx.scene.shape.Circle accentDot = new javafx.scene.shape.Circle(9, theme.accent());
        StackPane preview = new StackPane(accentDot);
        preview.setPrefSize(64, 40);
        preview.setMaxSize(64, 40);
        preview.setStyle("-fx-background-color: " + (theme == ThemeManager.Theme.OLED_BLACK ? "#000000" : "#151922")
                + "; -fx-background-radius: 10;");

        Label name = new Label(theme.displayName());
        name.setStyle("-fx-font-size: 10px; -fx-font-weight: 700;");
        name.getStyleClass().add("card-subtitle");

        VBox tile = new VBox(6, preview, name);
        tile.setAlignment(Pos.CENTER);
        tile.setPadding(new javafx.geometry.Insets(8));
        tile.setCursor(javafx.scene.Cursor.HAND);
        tile.setUserData(theme);
        updateSwatchBorder(tile, ThemeManager.current() == theme);

        tile.setOnMouseClicked(e -> {
            ThemeManager.apply(theme);
            services.config().setTheme(theme.name());
            for (javafx.scene.Node node : allSwatches.getChildren()) {
                if (node instanceof VBox other) {
                    updateSwatchBorder(other, other.getUserData() == theme);
                }
            }
        });
        return tile;
    }

    private void updateSwatchBorder(VBox tile, boolean active) {
        tile.setStyle("-fx-background-radius: 12; -fx-border-radius: 12; -fx-border-width: "
                + (active ? "2" : "1") + "; -fx-border-color: "
                + (active ? "-fx-red" : "-fx-border-subtle") + ";");
    }

    private VBox buildStartupCard(AppServices services) {
        HBox title = sectionTitle("STARTUP", Icons.zap(16, Color.web("#B9BEC7")));

        CheckBox autoStart = new CheckBox("Start GameRoute with Windows");
        autoStart.setSelected(services.config().isAutoStart());

        CheckBox startMinimized = new CheckBox("Start minimized to tray");
        startMinimized.setSelected(services.config().isStartMinimized());
        startMinimized.setOnAction(e -> services.config().setStartMinimized(startMinimized.isSelected()));

        autoStart.setOnAction(e -> {
            boolean enable = autoStart.isSelected();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setHeaderText((enable ? "Enable" : "Disable") + " auto-start");
            confirm.setContentText(enable
                    ? "This adds GameRoute to your Windows user startup entries (HKCU Run key). It only affects your user account."
                    : "This removes GameRoute from your Windows user startup entries (HKCU Run key).");
            Dialogs.themed(confirm);
            Optional<ButtonType> choice = confirm.showAndWait();
            if (choice.isEmpty() || choice.get() != ButtonType.OK) {
                autoStart.setSelected(!enable); // revert the checkbox
                return;
            }
            boolean success = enable
                    ? services.autoStartService().enable(currentLaunchCommand())
                    : services.autoStartService().disable();
            services.config().setAutoStart(success && enable);
            if (!success) {
                Alert error = new Alert(Alert.AlertType.ERROR, "Could not update the Windows startup entry. Check the Logs tab for details.");
                Dialogs.themed(error);
                error.showAndWait();
                autoStart.setSelected(!enable);
            }
        });

        VBox card = new VBox(12, title, autoStart, startMinimized);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    private VBox buildOptimizationCard(AppServices services) {
        HBox title = sectionTitle("OPTIMIZATION", Icons.shieldCheck(16, Color.web("#B9BEC7")));

        CheckBox autoOptimize = new CheckBox("Automatically optimize when a supported game starts");
        autoOptimize.setSelected(services.config().isAutoOptimizeEnabled());

        Label note = new Label("Applies that game's saved profile (priority, DNS, QoS) the moment GameRoute "
                + "detects it running -- the same steps Quick Optimize runs, just without asking each time. "
                + "Edit what each game's profile actually does on the Profiles tab.");
        note.getStyleClass().add("card-subtitle");
        note.setWrapText(true);

        autoOptimize.setOnAction(e -> {
            boolean enable = autoOptimize.isSelected();
            if (!enable) {
                services.config().setAutoOptimizeEnabled(false);
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setHeaderText("Enable automatic optimization");
            confirm.setContentText("From now on, whenever GameRoute detects a supported game has just started, "
                    + "it will immediately apply that game's saved profile (process priority, DNS resolver, QoS "
                    + "tagging) without a confirmation dialog -- same as clicking Quick Optimize yourself, just "
                    + "automatic. You'll still get a notification each time it runs. Turn this checkbox off here "
                    + "at any time to go back to manual.");
            Dialogs.themed(confirm);
            Optional<ButtonType> choice = confirm.showAndWait();
            if (choice.isEmpty() || choice.get() != ButtonType.OK) {
                autoOptimize.setSelected(false);
                return;
            }
            services.config().setAutoOptimizeEnabled(true);
        });

        VBox card = new VBox(12, title, autoOptimize, note);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    private VBox buildUpdatesCard(AppServices services, Stage stage) {
        HBox title = sectionTitle("UPDATES", Icons.refresh(16, Color.web("#B9BEC7")));

        Label versionLabel = new Label("Current version: " + Constants.APP_VERSION);
        versionLabel.getStyleClass().add("card-subtitle");

        CheckBox autoCheck = new CheckBox("Automatically check for updates on startup");
        autoCheck.setSelected(services.config().isAutoCheckForUpdatesEnabled());
        autoCheck.setOnAction(e -> services.config().setAutoCheckForUpdatesEnabled(autoCheck.isSelected()));

        Label status = new Label("Not checked yet this session.");
        status.getStyleClass().add("card-subtitle");
        status.setWrapText(true);

        Button checkNow = new Button("Check Now");
        checkNow.getStyleClass().add("button-ghost");

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox checkRow = new HBox(10, status, grow, checkNow);
        checkRow.setAlignment(Pos.CENTER_LEFT);

        VBox actionArea = new VBox(10);

        checkNow.setOnAction(e -> {
            checkNow.setDisable(true);
            status.setText("Checking...");
            actionArea.getChildren().clear();
            services.updateService().checkForUpdate().thenAccept(maybeUpdate -> Platform.runLater(() -> {
                checkNow.setDisable(false);
                if (maybeUpdate.isEmpty()) {
                    status.setText("You're up to date (v" + Constants.APP_VERSION + ").");
                    return;
                }
                UpdateService.UpdateInfo update = maybeUpdate.get();
                status.setText("Update available: v" + update.version());
                actionArea.getChildren().add(buildUpdateAvailablePanel(services, stage, update));
            }));
        });

        VBox card = new VBox(12, title, versionLabel, autoCheck, checkRow, actionArea);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    /** The "here's what's new, do you want it" panel shown once a newer release is found. */
    private VBox buildUpdateAvailablePanel(AppServices services, Stage stage, UpdateService.UpdateInfo update) {
        Label notes = new Label(update.notes() == null || update.notes().isBlank()
                ? "No release notes provided." : update.notes());
        notes.getStyleClass().add("card-subtitle");
        notes.setWrapText(true);
        notes.setMaxHeight(120);

        Button viewNotes = new Button("View on GitHub");
        viewNotes.getStyleClass().add("button-ghost");
        viewNotes.setDisable(update.releasePageUrl() == null);
        viewNotes.setOnAction(e -> openInBrowser(update.releasePageUrl()));

        Button skip = new Button("Skip This Version");
        skip.getStyleClass().add("button-ghost");

        Button install = new Button("Download && Install");
        install.getStyleClass().add("btn-glow-red");

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox buttons = new HBox(10, viewNotes, grow, skip, install);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(10, notes, buttons);
        panel.setPadding(new Insets(12));
        panel.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 12;"
                + "-fx-border-color: -fx-border-subtle; -fx-border-radius: 12; -fx-border-width: 1;");

        skip.setOnAction(e -> {
            services.config().setSkippedUpdateVersion(update.version());
            panel.setVisible(false);
            panel.setManaged(false);
        });

        install.setOnAction(e -> downloadAndInstall(services, stage, update, install));
        return panel;
    }

    private void downloadAndInstall(AppServices services, Stage stage, UpdateService.UpdateInfo update, Button install) {
        install.setDisable(true);
        install.setText("Downloading...");
        Path dest = Path.of(System.getProperty("java.io.tmpdir"), "GameRouteSetup-" + update.version() + ".exe");
        services.updateService().downloadInstaller(update.downloadUrl(), dest).thenAccept(ok -> Platform.runLater(() -> {
            if (!ok) {
                install.setDisable(false);
                install.setText("Download && Install");
                Alert error = new Alert(Alert.AlertType.ERROR,
                        "Could not download the installer. Check your connection and try again, or download it "
                                + "manually from the GitHub release page.");
                Dialogs.themed(error);
                error.showAndWait();
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setHeaderText("Install GameRoute " + update.version() + "?");
            confirm.setContentText("GameRoute will close now so the installer can replace it. Your settings, "
                    + "statistics and profiles are stored separately and are not affected. The installer window "
                    + "will guide you through the rest.");
            Dialogs.themed(confirm);
            Optional<ButtonType> choice = confirm.showAndWait();
            if (choice.isEmpty() || choice.get() != ButtonType.OK) {
                install.setDisable(false);
                install.setText("Download && Install");
                return;
            }
            try {
                new ProcessBuilder(dest.toString()).start();
            } catch (Exception ex) {
                Alert error = new Alert(Alert.AlertType.ERROR,
                        "Downloaded the installer but could not launch it. Open it manually from: " + dest);
                Dialogs.themed(error);
                error.showAndWait();
                install.setDisable(false);
                install.setText("Download && Install");
                return;
            }
            stage.hide();
            Platform.exit();
        }));
    }

    private void openInBrowser(String url) {
        if (url == null) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {
            // no default browser association or headless env -- nothing sensible to fall back to here
        }
    }

    private VBox buildLanguageCard(AppServices services) {
        HBox title = sectionTitle("LANGUAGE", Icons.globe(16, Color.web("#B9BEC7")));

        ComboBox<String> language = new ComboBox<>();
        language.getItems().addAll("en", "de");
        language.setValue(services.config().getLanguage());
        language.setOnAction(e -> services.config().setLanguage(language.getValue()));

        Label note = new Label("Restart GameRoute for language changes to take effect.");
        note.getStyleClass().add("card-subtitle");

        VBox row = new VBox(8, language, note);
        VBox card = new VBox(12, title, row);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    private VBox buildNotificationsCard(AppServices services) {
        HBox title = sectionTitle("NOTIFICATIONS", Icons.bell(16, Color.web("#B9BEC7")));

        CheckBox notifications = new CheckBox("Show system tray notifications (optimization results, packet loss alerts)");
        notifications.setSelected(services.config().isNotificationsEnabled());
        notifications.setOnAction(e -> {
            boolean enabled = notifications.isSelected();
            services.config().setNotificationsEnabled(enabled);
            services.notificationService().setEnabled(enabled);
        });

        VBox card = new VBox(12, title, notifications);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    private VBox buildDiscordPresenceCard(AppServices services) {
        HBox title = sectionTitle("DISCORD RICH PRESENCE", Icons.activity(16, Color.web("#B9BEC7")));

        CheckBox presence = new CheckBox("Show GameRoute as a Discord activity while it's running");
        presence.setSelected(services.config().isDiscordPresenceEnabled());

        Label note = new Label("Talks directly to your own local Discord client over the same private connection "
                + "Spotify and VS Code use -- nothing is sent to GameRoute's servers. Shows \"GameRoute\" plus "
                + "whichever supported game it currently detects (or \"Idle\" if none), with a \"Visit Website\" "
                + "button others can click. This can't hide or replace any other app's own Discord activity -- "
                + "Discord only lets each app control its own entry.");
        note.getStyleClass().add("card-subtitle");
        note.setWrapText(true);

        presence.setOnAction(e -> {
            boolean enable = presence.isSelected();
            services.config().setDiscordPresenceEnabled(enable);
            services.discordPresenceService().setEnabled(enable);
        });

        VBox card = new VBox(12, title, presence, note);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    private VBox buildPrivacyCard(AppServices services) {
        HBox title = sectionTitle("PRIVACY", Icons.shieldCheck(16, Color.web("#B9BEC7")));

        CheckBox telemetry = new CheckBox("Send an anonymous usage ping (helps the developer see how many people use GameRoute)");
        telemetry.setSelected(services.config().isTelemetryEnabled());

        Label note = new Label("Off by default. When enabled, GameRoute sends a random install id (generated once on "
                + "this PC, not derived from any hardware or personal identifier) and the app version -- nothing "
                + "else -- roughly every " + Constants.TELEMETRY_HEARTBEAT_INTERVAL_MINUTES + " minutes while "
                + "GameRoute is running, so the developer can see roughly how many people have it open right now. "
                + "No IP logging on our side beyond what any web request naturally exposes, no crash reports, no usage patterns.");
        note.getStyleClass().add("card-subtitle");
        note.setWrapText(true);

        telemetry.setOnAction(e -> {
            boolean enable = telemetry.isSelected();
            if (!enable) {
                services.config().setTelemetryEnabled(false);
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setHeaderText("Enable anonymous usage ping?");
            confirm.setContentText("GameRoute will periodically send a random install id and its version number "
                    + "to help the developer see a rough count of how many people use it. No personal data, no "
                    + "hardware identifiers, no usage patterns -- just that one id and a version string. Turn this "
                    + "checkbox off at any time to stop.");
            Dialogs.themed(confirm);
            Optional<ButtonType> choice = confirm.showAndWait();
            if (choice.isEmpty() || choice.get() != ButtonType.OK) {
                telemetry.setSelected(false);
                return;
            }
            services.config().setTelemetryEnabled(true);
            services.telemetryService().sendHeartbeat(services.config());
        });

        CheckBox shareDiscord = new CheckBox("Share my connected Discord identity with server admins");
        shareDiscord.setSelected(services.config().isShareDiscordWithAdminsEnabled());

        boolean isServerStaff = services.discordAccountService().currentUser(services.config())
                .map(com.gameroute.service.DiscordAccountService.DiscordUser::isAdmin)
                .orElse(false);

        Label shareDiscordNote = new Label();
        shareDiscordNote.getStyleClass().add("card-subtitle");
        shareDiscordNote.setWrapText(true);
        if (isServerStaff) {
            shareDiscord.setSelected(true);
            shareDiscord.setDisable(true);
            shareDiscordNote.setText("Locked on because your connected Discord account holds a server staff role "
                    + "(Owner/Administrator/Moderator) -- staff visibility to each other in the Admin panel is a "
                    + "policy for that role, not an individual choice. Disconnect Discord (or lose that role) to "
                    + "stop sharing. Regular members keep full control over this toggle.");
        } else {
            shareDiscordNote.setText("Off by default, and separate from the usage ping above. When enabled "
                    + "(and a Discord account is connected), GameRoute periodically shares your Discord id, username "
                    + "and avatar so the GameRoute server's Owner/Administrator/Moderator can see who currently has "
                    + "GameRoute connected, in an in-app Admin panel only they can open. Nothing else about you or "
                    + "your usage is shared this way.");
        }

        shareDiscord.setOnAction(e -> {
            boolean enable = shareDiscord.isSelected();
            if (!enable) {
                services.config().setShareDiscordWithAdminsEnabled(false);
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setHeaderText("Share your Discord identity with server admins?");
            confirm.setContentText("Your Discord id, username and avatar will be periodically shared so the "
                    + "GameRoute Discord server's Owner/Administrator/Moderator can see who currently has GameRoute "
                    + "connected, in an in-app Admin panel. This requires a connected Discord account and only takes "
                    + "effect while one is connected. Turn this checkbox off at any time to stop.");
            Dialogs.themed(confirm);
            Optional<ButtonType> choice = confirm.showAndWait();
            if (choice.isEmpty() || choice.get() != ButtonType.OK) {
                shareDiscord.setSelected(false);
                return;
            }
            services.config().setShareDiscordWithAdminsEnabled(true);
            services.discordIdentitySharingService().sendHeartbeat(services.config(), services.discordAccountService());
        });

        VBox card = new VBox(12, title, telemetry, note, shareDiscord, shareDiscordNote);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    /**
     * User-initiated only -- nothing is ever sent unless someone writes a description here and
     * presses Send themselves. Always includes the app version, OS and a recent log tail, since a
     * report with no diagnostic context isn't actually actionable; tied to your Discord identity
     * if connected, otherwise your anonymous install id, so it can show up in your own history if
     * you ever connect Discord later.
     */
    private VBox buildBugReportCard(AppServices services) {
        HBox title = sectionTitle("REPORT A PROBLEM", Icons.wrench(16, Color.web("#B9BEC7")));

        TextArea description = new TextArea();
        description.setPromptText("What went wrong? The more detail, the easier it is to fix.");
        description.setWrapText(true);
        description.setPrefRowCount(4);

        Label note = new Label("Sends your description along with the app version, OS and a recent slice of the "
                + "local log file -- nothing else, and only when you press Send.");
        note.getStyleClass().add("card-subtitle");
        note.setWrapText(true);

        Button send = new Button("Send Report");
        send.getStyleClass().add("btn-glow-red");
        Label status = new Label();
        status.getStyleClass().add("card-subtitle");

        send.setOnAction(e -> {
            String text = description.getText() == null ? "" : description.getText().trim();
            if (text.isEmpty()) {
                status.setText("Write a description first.");
                return;
            }
            send.setDisable(true);
            status.setText("Sending...");
            services.bugReportService().submit(services.config(), services.discordAccountService(), text)
                    .whenComplete((v, err) -> Platform.runLater(() -> {
                        send.setDisable(false);
                        if (err != null) {
                            status.setText("Could not send -- check your connection and try again.");
                        } else {
                            status.setText("Sent, thank you.");
                            description.clear();
                        }
                    }));
        });

        HBox actionRow = new HBox(12, send, status);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, title, description, note, actionRow);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    /**
     * Full command line (executable + arguments) to re-launch GameRoute the
     * same way it's currently running. {@code ProcessHandle.Info.command()}
     * alone only returns the bare executable path (e.g. "java.exe") with no
     * {@code -jar gameroute.jar} argument, which would register a startup
     * entry that launches a no-op JVM instead of the app -- {@code commandLine()}
     * includes the arguments too.
     */
    private String currentLaunchCommand() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        return info.commandLine().orElseGet(() -> info.command().orElse("javaw -jar gameroute.jar"));
    }
}
