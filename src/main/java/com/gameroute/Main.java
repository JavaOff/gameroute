package com.gameroute;

import com.gameroute.config.AppConfig;
import com.gameroute.config.Constants;
import com.gameroute.network.DnsService;
import com.gameroute.network.NetworkInterfaceService;
import com.gameroute.network.PingService;
import com.gameroute.network.QosService;
import com.gameroute.network.RouteAnalyzer;
import com.gameroute.network.TracerouteService;
import com.gameroute.monitor.GameProcessMonitor;
import com.gameroute.monitor.PingMonitor;
import com.gameroute.monitor.ProcessDiagnosticsMonitor;
import com.gameroute.monitor.SystemMonitor;
import com.gameroute.network.PublicIpService;
import com.gameroute.optimizer.OptimizerService;
import com.gameroute.service.AutoStartService;
import com.gameroute.service.CsvExportService;
import com.gameroute.service.DesktopShortcutScanner;
import com.gameroute.service.DiscordAccountService;
import com.gameroute.service.DiscordIdentitySharingService;
import com.gameroute.service.DiscordPresenceService;
import com.gameroute.service.GameIconStore;
import com.gameroute.service.GameServerPingService;
import com.gameroute.service.NotificationCenter;
import com.gameroute.service.NotificationService;
import com.gameroute.service.StatisticsService;
import com.gameroute.service.TelemetryService;
import com.gameroute.service.UpdateService;
import com.gameroute.ui.AppServices;
import com.gameroute.ui.MainView;
import com.gameroute.ui.ThemeManager;
import com.gameroute.ui.WindowResizer;
import com.gameroute.ui.components.Dialogs;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.Optional;

/**
 * Application entry point: wires every service/monitor together (simple
 * manual dependency injection — no DI framework needed for an app this size)
 * and hands them to {@link MainView}.
 */
public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private GameProcessMonitor gameProcessMonitor;
    private SystemMonitor systemMonitor;
    private PingMonitor pingMonitor;
    private StatisticsService statisticsService;
    private ProcessDiagnosticsMonitor processDiagnosticsMonitor;
    private DesktopShortcutScanner desktopShortcutScanner;
    private TelemetryService telemetryService;
    private DiscordPresenceService discordPresenceService;
    private DiscordIdentitySharingService discordIdentitySharingService;
    private GameServerPingService gameServerPingService;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Files.createDirectories(Constants.APP_HOME);
        log.info("Starting {} {}", Constants.APP_NAME, Constants.APP_VERSION);

        AppConfig config = new AppConfig();

        PingService pingService = new PingService();
        TracerouteService tracerouteService = new TracerouteService();
        DnsService dnsService = new DnsService(pingService);
        NetworkInterfaceService networkInterfaceService = new NetworkInterfaceService();
        QosService qosService = new QosService();
        RouteAnalyzer routeAnalyzer = new RouteAnalyzer();
        gameServerPingService = new GameServerPingService();
        OptimizerService optimizerService = new OptimizerService(dnsService, qosService, gameServerPingService);
        AutoStartService autoStartService = new AutoStartService();

        gameProcessMonitor = new GameProcessMonitor();
        systemMonitor = new SystemMonitor();
        pingMonitor = new PingMonitor(pingService);
        statisticsService = new StatisticsService();
        CsvExportService csvExportService = new CsvExportService(statisticsService);
        NotificationService notificationService = new NotificationService();
        notificationService.setEnabled(config.isNotificationsEnabled());
        NotificationCenter notificationCenter = new NotificationCenter(notificationService);
        processDiagnosticsMonitor = new ProcessDiagnosticsMonitor();
        PublicIpService publicIpService = new PublicIpService();
        publicIpService.refresh();

        GameIconStore gameIconStore = new GameIconStore();
        desktopShortcutScanner = new DesktopShortcutScanner(gameIconStore);
        desktopShortcutScanner.scanAsync();
        UpdateService updateService = new UpdateService();
        telemetryService = new TelemetryService();
        discordPresenceService = new DiscordPresenceService();
        discordIdentitySharingService = new DiscordIdentitySharingService();
        DiscordAccountService discordAccountService = new DiscordAccountService();

        AppServices services = new AppServices(
                config, pingService, tracerouteService, dnsService, networkInterfaceService,
                qosService, routeAnalyzer, optimizerService, autoStartService,
                gameProcessMonitor, systemMonitor, pingMonitor, statisticsService,
                csvExportService, notificationService, notificationCenter,
                processDiagnosticsMonitor, publicIpService, gameIconStore, desktopShortcutScanner,
                updateService, telemetryService, discordPresenceService,
                discordAccountService, discordIdentitySharingService, gameServerPingService);

        if (config.isAutoCheckForUpdatesEnabled()) {
            checkForUpdateOnStartup(services);
        }
        telemetryService.start(config);
        discordIdentitySharingService.start(config, discordAccountService);
        discordPresenceService.start(config.isDiscordPresenceEnabled());
        discordPresenceService.updateGame(gameProcessMonitor.detect().game());
        gameProcessMonitor.addListener(status -> discordPresenceService.updateGame(status.game()));
        gameServerPingService.start(gameProcessMonitor);

        // Belt-and-braces: grab the real icon straight from a game's own exe
        // the first time it's seen running, in case the Desktop scan above
        // found no matching shortcut for it.
        gameProcessMonitor.addListener(status -> {
            if (status.running() && status.installPath() != null) {
                String exePath = java.nio.file.Path.of(status.installPath(), status.processName()).toString();
                desktopShortcutScanner.extractFromRunningGame(status.game().id(), exePath);
            }
        });

        MainView mainView = new MainView(services, stage);

        // A transparent, undecorated stage is how the app draws its own rounded
        // corners and window controls -- but that also throws away the OS's
        // native drop shadow. SHADOW_MARGIN of transparent padding around the
        // visible card gives the DropShadow effect room to bleed outward
        // instead of getting clipped at the scene edge.
        double shadowMargin = 18;
        DropShadow windowShadow = new DropShadow(32, Color.rgb(0, 0, 0, 0.55));
        windowShadow.setOffsetY(10);
        mainView.setEffect(windowShadow);

        StackPane sceneRoot = new StackPane(mainView);
        sceneRoot.setPadding(new Insets(shadowMargin));
        sceneRoot.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(sceneRoot, 1180 + shadowMargin * 2, 780 + shadowMargin * 2);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.init(scene, ThemeManager.fromName(config.getTheme()));

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle(Constants.APP_NAME + " " + Constants.APP_VERSION);
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/gameroute.png")));
        stage.setScene(scene);
        stage.setMinWidth(980);
        stage.setMinHeight(640);

        WindowResizer.attach(sceneRoot, stage);

        stage.maximizedProperty().addListener((obs, wasMaximized, isMaximized) -> {
            sceneRoot.setPadding(isMaximized ? Insets.EMPTY : new Insets(shadowMargin));
            mainView.setEffect(isMaximized ? null : windowShadow);
            mainView.applyMaximizedStyle(isMaximized);
        });

        notificationService.installTrayIcon(stage);

        stage.setOnCloseRequest(event -> {
            event.consume();
            stage.hide();
        });

        if (config.isStartMinimized()) {
            stage.setIconified(true);
        }
        stage.show();

        if (!config.hasShownTelemetryPrompt()) {
            maybeShowTelemetryPrompt(services, stage);
        }
        // Temporarily disabled: the Discord application is currently under a Discord-side
        // quarantine (unrelated to this feature's code) which would make every login attempt
        // fail right now. Re-enable this call once the quarantine appeal is resolved -- the
        // "Connect Discord" button in the profile popup still works for anyone who wants to try.
        // if (!config.hasShownDiscordAccountPrompt()) {
        //     maybeShowDiscordAccountPrompt(services, stage, mainView);
        // }

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownServices));
    }

    /**
     * One-time, first-run consent dialog: OK enables the anonymous usage ping,
     * Cancel (or closing the dialog) leaves it off -- either way this is only
     * ever asked once. Lower-friction than requiring a trip to Settings, but
     * still a real choice, not a silent default-on.
     */
    private void maybeShowTelemetryPrompt(AppServices services, Stage stage) {
        AppConfig config = services.config();
        Alert prompt = new Alert(Alert.AlertType.CONFIRMATION);
        prompt.initOwner(stage);
        prompt.setHeaderText("Help improve GameRoute?");
        prompt.setContentText("GameRoute can send a random install id (not tied to any personal or hardware "
                + "identifier) and its version number every few minutes while it's running, so the developer can "
                + "see roughly how many people use it. Nothing else is ever sent -- no crash reports, no usage "
                + "patterns. You can change this anytime in Settings > Privacy.");
        Dialogs.themed(prompt);
        Optional<ButtonType> choice = prompt.showAndWait();
        boolean enable = choice.isPresent() && choice.get() == ButtonType.OK;
        config.setTelemetryEnabled(enable);
        config.setTelemetryPromptShown(true);
        if (enable) {
            services.telemetryService().sendHeartbeat(config);
        }
    }

    /**
     * One-time, first-run prompt offering to replace the generic "Local profile" name in the
     * title bar with the user's real Discord name/avatar. OK opens the system browser to
     * Discord's own login page (GameRoute never sees the password); Cancel leaves it as a local
     * profile, and either way this is only ever asked once -- reachable again anytime via the
     * profile popup itself.
     */
    private void maybeShowDiscordAccountPrompt(AppServices services, Stage stage, MainView mainView) {
        AppConfig config = services.config();
        Alert prompt = new Alert(Alert.AlertType.CONFIRMATION);
        prompt.initOwner(stage);
        prompt.setHeaderText("Connect your Discord account?");
        prompt.setContentText("GameRoute can show your real Discord name and avatar instead of a generic local "
                + "profile. This opens Discord's own login page in your browser -- GameRoute never sees your "
                + "password, and only ever reads your username and avatar (the \"identify\" scope), nothing more. "
                + "You can connect or disconnect anytime from the profile popup in the title bar.");
        Dialogs.themed(prompt);
        Optional<ButtonType> choice = prompt.showAndWait();
        config.setDiscordAccountPromptShown(true);
        if (choice.isPresent() && choice.get() == ButtonType.OK) {
            services.discordAccountService().connect(config)
                    .thenAccept(user -> Platform.runLater(mainView::refreshAvatar))
                    .exceptionally(e -> {
                        log.warn("Discord account connect failed: {}", e.getMessage());
                        Platform.runLater(() -> services.notificationCenter().warning("Discord connect failed",
                                "Could not connect your Discord account. You can try again from the profile popup."));
                        return null;
                    });
        }
    }

    /** Fires a toast + tray notification if a newer release is found and the user hasn't skipped that version. */
    private void checkForUpdateOnStartup(AppServices services) {
        services.updateService().checkForUpdate().thenAccept(maybeUpdate -> maybeUpdate.ifPresent(update -> {
            if (update.version().equals(services.config().getSkippedUpdateVersion())) {
                return;
            }
            Platform.runLater(() -> services.notificationCenter().info(
                    "GameRoute " + update.version() + " is available",
                    "Open Settings to review what's new and install it."));
        }));
    }

    @Override
    public void stop() {
        shutdownServices();
        Platform.exit();
    }

    private void shutdownServices() {
        log.info("Shutting down {}", Constants.APP_NAME);
        if (gameProcessMonitor != null) gameProcessMonitor.stop();
        if (systemMonitor != null) systemMonitor.stop();
        if (pingMonitor != null) pingMonitor.stop();
        if (processDiagnosticsMonitor != null) processDiagnosticsMonitor.stop();
        if (desktopShortcutScanner != null) desktopShortcutScanner.stop();
        if (statisticsService != null) statisticsService.close();
        if (telemetryService != null) telemetryService.stop();
        if (discordPresenceService != null) discordPresenceService.stop();
        if (discordIdentitySharingService != null) discordIdentitySharingService.stop();
        if (gameServerPingService != null) gameServerPingService.stop();
    }
}
