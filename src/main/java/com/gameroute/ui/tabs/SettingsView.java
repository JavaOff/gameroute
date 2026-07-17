package com.gameroute.ui.tabs;

import com.gameroute.ui.AppServices;
import com.gameroute.ui.components.Animations;
import com.gameroute.ui.components.Dialogs;
import com.gameroute.ui.icons.Icons;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

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
        VBox language = buildLanguageCard(services);
        VBox notifications = buildNotificationsCard(services);

        root.getChildren().addAll(appearance, startup, language, notifications);
        setContent(root);

        Animations.staggeredEntrance(List.of(appearance, startup, language, notifications), 70);
    }

    private HBox sectionTitle(String text, javafx.scene.Node icon) {
        Label title = new Label(text);
        title.getStyleClass().add("card-title");
        HBox row = new HBox(8, icon, title);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox buildAppearanceCard(AppServices services, Stage stage) {
        HBox title = sectionTitle("APPEARANCE", Icons.gear(16, Color.web("#9EA3AE")));

        CheckBox darkMode = new CheckBox("Dark mode");
        darkMode.setSelected(services.config().isDarkMode());
        darkMode.setOnAction(e -> {
            boolean enabled = darkMode.isSelected();
            services.config().setDarkMode(enabled);
            String stylesheet = getClass().getResource("/css/dark.css").toExternalForm();
            if (enabled) {
                if (!stage.getScene().getStylesheets().contains(stylesheet)) {
                    stage.getScene().getStylesheets().add(stylesheet);
                }
            } else {
                stage.getScene().getStylesheets().remove(stylesheet);
            }
        });

        VBox card = new VBox(12, title, darkMode);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    private VBox buildStartupCard(AppServices services) {
        HBox title = sectionTitle("STARTUP", Icons.zap(16, Color.web("#9EA3AE")));

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

    private VBox buildLanguageCard(AppServices services) {
        HBox title = sectionTitle("LANGUAGE", Icons.globe(16, Color.web("#9EA3AE")));

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
        HBox title = sectionTitle("NOTIFICATIONS", Icons.bell(16, Color.web("#9EA3AE")));

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
