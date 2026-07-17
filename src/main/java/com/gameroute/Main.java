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
import com.gameroute.monitor.SystemMonitor;
import com.gameroute.optimizer.OptimizerService;
import com.gameroute.service.AutoStartService;
import com.gameroute.service.CsvExportService;
import com.gameroute.service.NotificationService;
import com.gameroute.service.StatisticsService;
import com.gameroute.ui.AppServices;
import com.gameroute.ui.MainView;
import com.gameroute.ui.WindowResizer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;

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
        OptimizerService optimizerService = new OptimizerService(dnsService, qosService);
        AutoStartService autoStartService = new AutoStartService();

        gameProcessMonitor = new GameProcessMonitor();
        systemMonitor = new SystemMonitor();
        pingMonitor = new PingMonitor(pingService);
        statisticsService = new StatisticsService();
        CsvExportService csvExportService = new CsvExportService(statisticsService);
        NotificationService notificationService = new NotificationService();
        notificationService.setEnabled(config.isNotificationsEnabled());

        AppServices services = new AppServices(
                config, pingService, tracerouteService, dnsService, networkInterfaceService,
                qosService, routeAnalyzer, optimizerService, autoStartService,
                gameProcessMonitor, systemMonitor, pingMonitor, statisticsService,
                csvExportService, notificationService);

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
        scene.getStylesheets().add(getClass().getResource("/css/dark.css").toExternalForm());

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

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownServices));
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
        if (statisticsService != null) statisticsService.close();
    }
}
