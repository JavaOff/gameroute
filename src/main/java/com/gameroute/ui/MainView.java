package com.gameroute.ui;

import com.gameroute.model.OptimizationActionResult;
import com.gameroute.monitor.ProcessDiagnosticsMonitor.AppUsage;
import com.gameroute.service.NotificationCenter;
import com.gameroute.service.ProfileStore;
import com.gameroute.ui.components.Animations;
import com.gameroute.ui.components.ToastHost;
import com.gameroute.ui.icons.Icons;
import com.gameroute.ui.tabs.AdminView;
import com.gameroute.ui.tabs.DashboardView;
import com.gameroute.ui.tabs.DiagnosticsView;
import com.gameroute.ui.tabs.FpsPerformanceView;
import com.gameroute.ui.tabs.GamesView;
import com.gameroute.ui.tabs.LogsView;
import com.gameroute.ui.tabs.OptimizerView;
import com.gameroute.ui.tabs.ProfilesView;
import com.gameroute.ui.tabs.ServersView;
import com.gameroute.ui.tabs.SettingsView;
import com.gameroute.ui.tabs.StatisticsView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application shell: custom title bar on top, hover-expanding navigation
 * rail on the left, the active page filling the center (crossfaded on
 * switch) with a toast-notification overlay on top, and a live status strip
 * on the bottom. This is the node given the rounded-corner/shadow treatment
 * in {@link com.gameroute.Main}; window chrome (drag, resize, min/max/close)
 * lives in {@link TitleBar} / {@link WindowResizer}.
 */
public class MainView extends BorderPane {

    private static final double LOSS_ALERT_THRESHOLD_PERCENT = 5.0;
    private static final long ALERT_COOLDOWN_SECONDS = 300;

    private final Map<String, Node> pages = new LinkedHashMap<>();
    private final StackPane content = new StackPane();
    private final TitleBar titleBar;
    private final StatusBar statusBar;
    private final Sidebar sidebar;

    private final ProfileStore profileStore = new ProfileStore();

    private Instant lastLossAlert = Instant.EPOCH;
    private Instant lastDownloadAlert = Instant.EPOCH;
    private String lastDetectedGameId;

    public MainView(AppServices services, Stage stage) {
        getStyleClass().add("app-shell");

        pages.put("dashboard", new DashboardView(services, () -> selectPage("optimizer")));
        pages.put("games", new GamesView(services, () -> selectPage("profiles")));
        pages.put("fps-performance", new FpsPerformanceView(services));
        pages.put("optimizer", new OptimizerView(services));
        pages.put("servers", new ServersView(services));
        pages.put("statistics", new StatisticsView(services));
        pages.put("diagnostics", new DiagnosticsView(services));
        pages.put("profiles", new ProfilesView(services));
        pages.put("logs", new LogsView());
        pages.put("settings", new SettingsView(services, stage));

        // Owner/Administrator/Moderator only -- checked once at startup (a Discord connect/role
        // change made mid-session needs a restart to show or hide this, same as the rest of the
        // nav rail, which is built once here and has no add/remove-entry mechanism of its own).
        boolean isAdmin = services.discordAccountService().currentUser(services.config())
                .map(com.gameroute.service.DiscordAccountService.DiscordUser::isAdmin)
                .orElse(false);
        if (isAdmin) {
            pages.put("admin", new AdminView(services));
        }

        List<Sidebar.NavEntry> navEntries = new java.util.ArrayList<>(List.of(
                new Sidebar.NavEntry("dashboard", "Dashboard", Icons::home),
                new Sidebar.NavEntry("games", "Games", Icons::gamepad),
                new Sidebar.NavEntry("fps-performance", "FPS Performance", Icons::activity),
                new Sidebar.NavEntry("optimizer", "Optimizer", Icons::zap),
                new Sidebar.NavEntry("servers", "Servers", Icons::globe),
                new Sidebar.NavEntry("statistics", "Statistics", Icons::barChart),
                new Sidebar.NavEntry("diagnostics", "Diagnostics", Icons::satelliteDish),
                new Sidebar.NavEntry("profiles", "Profiles", Icons::userCircle),
                new Sidebar.NavEntry("logs", "Logs", Icons::fileText),
                new Sidebar.NavEntry("settings", "Settings", Icons::gear)
        ));
        if (isAdmin) {
            navEntries.add(new Sidebar.NavEntry("admin", "Admin", Icons::shieldCheck));
        }
        List<Sidebar.NavEntry> entries = List.copyOf(navEntries);

        sidebar = new Sidebar(entries, this::selectPage, () -> selectPage("optimizer"));
        titleBar = new TitleBar(stage, services, this::selectPage, () -> selectPage("settings"));
        statusBar = new StatusBar(services);

        ToastHost toastHost = new ToastHost();
        toastHost.setPickOnBounds(false);
        services.notificationCenter().setToastHandler(toastHost::show);

        StackPane centerStack = new StackPane(content, toastHost);

        setTop(titleBar);
        setLeft(sidebar);
        setCenter(centerStack);
        setBottom(statusBar);

        selectPage("dashboard");
        wireAlerts(services);
    }

    /**
     * App-wide event -> notification wiring. Lives here (not in individual
     * tabs) so alerts fire no matter which page is visible.
     */
    private void wireAlerts(AppServices services) {
        NotificationCenter center = services.notificationCenter();

        services.gameProcessMonitor().addListener(status -> {
            String gameId = status.running() ? status.game().id() : null;
            boolean justDetected = gameId != null && !gameId.equals(lastDetectedGameId);
            if (justDetected) {
                center.info(status.game().displayName() + " detected",
                        "Optimization profile is ready in the Profiles tab.");
                if (services.config().isAutoOptimizeEnabled()) {
                    autoOptimize(services, status);
                }
            } else if (gameId == null && lastDetectedGameId != null) {
                center.info("Game closed", "GameRoute keeps monitoring your connection.");
            }
            lastDetectedGameId = gameId;
        });

        services.pingMonitor().addListener((sample, stats) -> {
            if (stats.sampleCount() > 30
                    && stats.packetLossPercent() > LOSS_ALERT_THRESHOLD_PERCENT
                    && Instant.now().isAfter(lastLossAlert.plusSeconds(ALERT_COOLDOWN_SECONDS))) {
                lastLossAlert = Instant.now();
                center.warning("High packet loss",
                        String.format("%.1f%% of probes are being dropped. Check the Diagnostics tab to find the hop.",
                                stats.packetLossPercent()));
            }
        });

        services.processDiagnosticsMonitor().addListener(usages -> {
            Set<String> downloading = new HashSet<>();
            for (AppUsage usage : usages) {
                if (usage.possiblyDownloading()) {
                    downloading.add(usage.app().displayName());
                }
            }
            Platform.runLater(() -> sidebar.setBadge("diagnostics", downloading.size()));
            if (!downloading.isEmpty()
                    && Instant.now().isAfter(lastDownloadAlert.plusSeconds(ALERT_COOLDOWN_SECONDS))) {
                lastDownloadAlert = Instant.now();
                center.warning("Heavy download detected",
                        String.join(", ", downloading) + " may be saturating your bandwidth while you play.");
            }
        });
    }

    /**
     * Runs the detected game's saved profile with no confirmation dialog --
     * only reached when the user has explicitly opted in via the
     * confirm-on-enable checkbox in Settings. Called on the
     * GameProcessMonitor's own background poll thread (never the FX
     * thread), so the PowerShell calls this triggers don't stall anything.
     */
    private void autoOptimize(AppServices services, com.gameroute.model.GameStatus status) {
        var game = status.game();
        var profile = profileStore.profileFor(game.id());
        var actions = services.optimizerService().profileSequence(game, profile, services.config().getPreferredAdapter());
        List<OptimizationActionResult> results = services.optimizerService().runAll(actions);
        boolean allOk = results.stream().allMatch(OptimizationActionResult::success);
        services.notificationCenter().push(
                allOk ? NotificationCenter.Type.SUCCESS : NotificationCenter.Type.WARNING,
                allOk ? "Auto-optimized " + game.displayName() : "Auto-optimize finished with issues",
                allOk ? results.size() + " steps applied automatically -- turn this off in Settings."
                        : "Some steps failed -- check the Optimizer action log and Statistics history.");
    }

    private void selectPage(String id) {
        Node page = pages.get(id);
        if (page != null) {
            Animations.crossFadePage(content, page);
        }
    }

    /** Called after a Discord account connects/disconnects outside the title bar's own popup (e.g. the first-run prompt). */
    public void refreshAvatar() {
        titleBar.refreshAvatarVisual();
    }

    /** Toggles the rounded-corner/shadow "floating card" look off while maximized (a full-screen window shouldn't look like it has margins). */
    public void applyMaximizedStyle(boolean maximized) {
        setStyleState(this, "app-shell-square", maximized);
        setStyleState(titleBar, "title-bar-square", maximized);
        setStyleState(statusBar, "status-bar-square", maximized);
    }

    private void setStyleState(Node node, String styleClass, boolean present) {
        if (present) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }
}
