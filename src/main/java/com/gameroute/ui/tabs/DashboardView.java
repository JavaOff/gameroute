package com.gameroute.ui.tabs;

import com.gameroute.charts.GlowLineChart;
import com.gameroute.config.Constants;
import com.gameroute.config.ServerDatabase;
import com.gameroute.model.GameStatus;
import com.gameroute.model.PingSample;
import com.gameroute.model.PingStats;
import com.gameroute.model.Region;
import com.gameroute.model.ServerInfo;
import com.gameroute.model.SystemStats;
import com.gameroute.ui.AppServices;
import com.gameroute.ui.components.Animations;
import com.gameroute.ui.icons.Icons;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The showcase "live overview" page: League of Legends detection, an
 * at-a-glance health check, CPU/RAM/network mini-graphs, the big ping
 * readout with its own history graph, a best-region leaderboard, and
 * upload/download throughput.
 */
public class DashboardView extends ScrollPane {

    private static final Color RED = Color.web("#FF3B30");
    private static final Color SUCCESS = Color.web("#30D158");
    private static final Color WARNING = Color.web("#FFD60A");

    private final GlowLineChart pingChart = new GlowLineChart(Constants.PING_HISTORY_WINDOW, RED, true);
    private final GlowLineChart uploadChart = new GlowLineChart(120, Color.web("#4FA8FF"), true);
    private final GlowLineChart downloadChart = new GlowLineChart(120, SUCCESS, true);
    private final GlowLineChart cpuSpark = new GlowLineChart(60, RED, true);
    private final GlowLineChart ramSpark = new GlowLineChart(60, Color.web("#4FA8FF"), true);
    private final GlowLineChart netSpark = new GlowLineChart(60, SUCCESS, true);

    private final Label gameStatusDotHolder = new Label();
    private final Circle gameStatusDot = new Circle(5);
    private final Label gameStatusText = new Label("Detecting...");
    private final Label gameRegionText = new Label("--");
    private final Label systemStatusHeadline = new Label("Checking connection...");
    private final Label systemStatusSubline = new Label("Gathering telemetry");
    private final Node systemStatusIcon;

    private final Label pingHuge = new Label("--");
    private final Label pingAvg = new Label("-- ms");
    private final Label pingMin = new Label("-- ms");
    private final Label pingMax = new Label("-- ms");
    private final Label pingLoss = new Label("--%");
    private final Label pingJitter = new Label("-- ms");

    private final Label cpuValue = new Label("--%");
    private final Label ramValue = new Label("--%");
    private final Label netValue = new Label("-- KB/s");

    private final Label uploadValue = new Label("-- KB/s");
    private final Label downloadValue = new Label("-- KB/s");

    private final Label bestServerName = new Label("--");
    private final Label bestServerLatency = new Label("-- ms");
    private final VBox leaderboardRows = new VBox(8);

    private final ObservableList<ServerInfo> serverBoard = FXCollections.observableArrayList(ServerDatabase.allServers());
    private final ScheduledExecutorService boardExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "dashboard-region-sweep"));

    public DashboardView(AppServices services) {
        setFitToWidth(true);
        getStyleClass().add("edge-to-edge");

        systemStatusIcon = Icons.shieldCheck(28, SUCCESS);

        VBox root = new VBox(18);
        root.setPadding(new Insets(24));

        HBox regionBar = buildRegionBar(services);

        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(18);
        for (int i = 0; i < 6; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 6);
            grid.getColumnConstraints().add(cc);
        }

        Node leagueCard = buildLeagueCard();
        Node systemStatusCard = buildSystemStatusCard();
        Node cpuCard = buildMiniMetricCard("CPU", cpuValue, cpuSpark, Icons.cpu(20, RED));
        Node ramCard = buildMiniMetricCard("RAM", ramValue, ramSpark, Icons.memory(20, Color.web("#4FA8FF")));
        Node netCard = buildMiniMetricCard("NETWORK", netValue, netSpark, Icons.wifi(20, SUCCESS));
        Node pingCard = buildPingCard();
        Node bestServerCard = buildBestServerCard();
        Node networkThroughputCard = buildNetworkThroughputCard();

        grid.add(leagueCard, 0, 0, 4, 1);
        grid.add(systemStatusCard, 4, 0, 2, 1);
        grid.add(cpuCard, 0, 1, 2, 1);
        grid.add(ramCard, 2, 1, 2, 1);
        grid.add(netCard, 4, 1, 2, 1);
        grid.add(pingCard, 0, 2, 4, 1);
        grid.add(bestServerCard, 4, 2, 2, 1);
        grid.add(networkThroughputCard, 0, 3, 6, 1);

        root.getChildren().addAll(regionBar, grid);
        setContent(root);

        Animations.staggeredEntrance(List.of(leagueCard, systemStatusCard, cpuCard, ramCard, netCard,
                pingCard, bestServerCard, networkThroughputCard), 60);

        wireMonitors(services);
        wireGameDetection(services);
        startRegionLeaderboard(services);
    }

    // ---------------------------------------------------------------- region bar

    private HBox buildRegionBar(AppServices services) {
        Label label = new Label("Monitoring region:");
        label.getStyleClass().add("card-subtitle");
        ComboBox<Region> regionBox = new ComboBox<>();
        regionBox.getItems().addAll(Region.values());
        String preferred = services.config().getPreferredRegion();
        regionBox.setValue(Region.valueOf(preferred));
        services.pingMonitor().setTarget(regionBox.getValue().getHost());

        regionBox.setOnAction(e -> {
            Region selected = regionBox.getValue();
            services.config().setPreferredRegion(selected.name());
            services.pingMonitor().setTarget(selected.getHost());
            pingChart.clear();
        });

        Label note = new Label("pings Riot's public API gateway for this platform -- see the Servers tab for details");
        note.getStyleClass().add("card-subtitle");

        HBox bar = new HBox(10, label, regionBox, note);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ---------------------------------------------------------------- top cards

    private Node buildLeagueCard() {
        Node logo = Icons.zap(26, RED);
        Label title = new Label("LEAGUE OF LEGENDS");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: -fx-text-secondary;");
        HBox titleRow = new HBox(10, logo, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        gameStatusDot.getStyleClass().add("dot-bad");
        HBox statusRow = new HBox(8, gameStatusDot, gameStatusText);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        gameStatusText.setStyle("-fx-font-size: 22px; -fx-font-weight: 800;");

        Label regionLabel = new Label("Region");
        regionLabel.getStyleClass().add("card-subtitle");
        gameRegionText.setStyle("-fx-font-size: 16px; -fx-font-weight: 700;");
        VBox regionBlock = new VBox(2, regionLabel, gameRegionText);

        javafx.scene.layout.Region hSpacer = new javafx.scene.layout.Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);

        HBox bottomRow = new HBox(24, statusRow, hSpacer, regionBlock);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(16, titleRow, bottomRow);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        Animations.hoverScale(card, 1.01);
        return card;
    }

    private Node buildSystemStatusCard() {
        StackPane iconStack = new StackPane(systemStatusIcon);
        RotateTransition idlePulse = new RotateTransition(Duration.seconds(4), systemStatusIcon);

        systemStatusHeadline.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");
        systemStatusHeadline.setWrapText(true);
        systemStatusSubline.getStyleClass().add("card-subtitle");
        systemStatusSubline.setWrapText(true);
        VBox textBlock = new VBox(3, systemStatusHeadline, systemStatusSubline);
        textBlock.setMaxWidth(260);

        HBox row = new HBox(14, iconStack, textBlock);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(row);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        Animations.hoverScale(card, 1.01);
        return card;
    }

    private Node buildMiniMetricCard(String title, Label valueLabel, GlowLineChart spark, Node icon) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        HBox titleRow = new HBox(8, icon, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        valueLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: 800;");

        spark.setPrefHeight(46);
        spark.setFixedRange(0, 100);

        VBox card = new VBox(10, titleRow, valueLabel, spark);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        Animations.hoverScale(card, 1.02);
        return card;
    }

    // ---------------------------------------------------------------- ping card

    private Node buildPingCard() {
        Label title = new Label("LIVE PING");
        title.getStyleClass().add("card-title");

        Label unit = new Label("ms");
        unit.getStyleClass().add("metric-unit");
        pingHuge.getStyleClass().add("metric-huge");
        HBox hugeRow = new HBox(8, pingHuge, unit);
        hugeRow.setAlignment(Pos.BASELINE_LEFT);

        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(28);
        statsGrid.getColumnConstraints().addAll(col(), col(), col(), col(), col());
        statsGrid.add(statBlock("AVERAGE", pingAvg), 0, 0);
        statsGrid.add(statBlock("MINIMUM", pingMin), 1, 0);
        statsGrid.add(statBlock("MAXIMUM", pingMax), 2, 0);
        statsGrid.add(statBlock("PACKET LOSS", pingLoss), 3, 0);
        statsGrid.add(statBlock("JITTER", pingJitter), 4, 0);

        pingChart.setPrefHeight(140);

        VBox card = new VBox(14, title, hugeRow, statsGrid, pingChart);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        Animations.hoverScale(card, 1.005);
        return card;
    }

    private ColumnConstraints col() {
        ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(20);
        return cc;
    }

    private VBox statBlock(String label, Label value) {
        Label l = new Label(label);
        l.getStyleClass().add("card-title");
        value.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");
        value.getStyleClass().add("mono");
        return new VBox(4, l, value);
    }

    // ---------------------------------------------------------------- best server

    private Node buildBestServerCard() {
        Node trophy = Icons.trophy(24, Color.web("#FFD60A"));
        Label title = new Label("BEST SERVER");
        title.getStyleClass().add("card-title");
        HBox titleRow = new HBox(8, trophy, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        bestServerName.setStyle("-fx-font-size: 20px; -fx-font-weight: 800;");
        HBox nameRow = new HBox(8, bestServerName, Icons.wifi(16, SUCCESS));
        nameRow.setAlignment(Pos.CENTER_LEFT);

        bestServerLatency.getStyleClass().addAll("metric-good", "mono");
        bestServerLatency.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");

        leaderboardRows.setPadding(new Insets(6, 0, 0, 0));

        VBox card = new VBox(12, titleRow, nameRow, bestServerLatency, leaderboardRows);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        Animations.hoverScale(card, 1.01);
        return card;
    }

    private HBox leaderboardRow(ServerInfo info, boolean isBest) {
        Label name = new Label(info.getDisplayName());
        name.setStyle(isBest ? "-fx-font-weight: 800;" : "-fx-text-fill: -fx-text-secondary;");
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label latency = new Label(info.getLatencyMs() < 0 ? "--" : String.format("%.0f ms", info.getLatencyMs()));
        latency.getStyleClass().add("mono");
        latency.setStyle("-fx-text-fill: -fx-text-secondary; -fx-font-size: 12px;");
        HBox row = new HBox(8, name, spacer, latency);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ---------------------------------------------------------------- network throughput

    private Node buildNetworkThroughputCard() {
        Label title = new Label("NETWORK THROUGHPUT");
        title.getStyleClass().add("card-title");

        Node upIcon = Icons.arrowUp(16, Color.web("#4FA8FF"));
        Label upLabel = new Label("Upload");
        upLabel.getStyleClass().add("card-subtitle");
        HBox upHeader = new HBox(6, upIcon, upLabel);
        upHeader.setAlignment(Pos.CENTER_LEFT);
        uploadValue.setStyle("-fx-font-size: 18px; -fx-font-weight: 800;");
        uploadChart.setPrefHeight(90);
        VBox upBlock = new VBox(6, upHeader, uploadValue, uploadChart);
        HBox.setHgrow(upBlock, Priority.ALWAYS);

        Node downIcon = Icons.arrowDown(16, SUCCESS);
        Label downLabel = new Label("Download");
        downLabel.getStyleClass().add("card-subtitle");
        HBox downHeader = new HBox(6, downIcon, downLabel);
        downHeader.setAlignment(Pos.CENTER_LEFT);
        downloadValue.setStyle("-fx-font-size: 18px; -fx-font-weight: 800;");
        downloadChart.setPrefHeight(90);
        VBox downBlock = new VBox(6, downHeader, downloadValue, downloadChart);
        HBox.setHgrow(downBlock, Priority.ALWAYS);

        HBox charts = new HBox(24, upBlock, downBlock);

        VBox card = new VBox(14, title, charts);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        Animations.hoverScale(card, 1.005);
        return card;
    }

    // ---------------------------------------------------------------- wiring

    private void wireMonitors(AppServices services) {
        services.pingMonitor().start((sample, stats) -> Platform.runLater(() -> {
            updatePingCards(stats);
            pingChart.addSample(sample.success() ? sample.rttMillis() : -1);
            services.statisticsService().record(sample);
        }));

        services.systemMonitor().start(stats -> Platform.runLater(() -> updateSystemCards(stats)));
    }

    private void wireGameDetection(AppServices services) {
        services.gameProcessMonitor().start(status -> Platform.runLater(() -> updateGameCards(status)));
    }

    private void updateGameCards(GameStatus status) {
        gameStatusDot.getStyleClass().removeAll("dot-good", "dot-bad");
        if (status.running()) {
            gameStatusDot.getStyleClass().add("dot-good");
            gameStatusText.setText("Running");
            gameStatusText.setStyle("-fx-font-size: 22px; -fx-font-weight: 800; -fx-text-fill: -fx-success;");
            gameRegionText.setText(status.region() != null ? status.region() : "Unknown");
            systemStatusHeadline.setText("Game is running");
            systemStatusSubline.setText("Everything optimal");
        } else {
            gameStatusDot.getStyleClass().add("dot-bad");
            gameStatusText.setText("Not running");
            gameStatusText.setStyle("-fx-font-size: 22px; -fx-font-weight: 800; -fx-text-fill: -fx-text-secondary;");
            gameRegionText.setText("--");
            systemStatusHeadline.setText("Standing by");
            systemStatusSubline.setText("League not detected -- monitoring stays active");
        }
    }

    private void updatePingCards(PingStats stats) {
        pingHuge.setText(stats.currentMs() < 0 ? "--" : String.format("%.0f", stats.currentMs()));
        pingHuge.getStyleClass().removeAll("metric-good", "metric-warn", "metric-bad");
        pingHuge.getStyleClass().add(toneClass(stats.currentMs()));

        pingAvg.setText(String.format("%.0f ms", stats.averageMs()));
        pingMin.setText(String.format("%.0f ms", stats.minMs()));
        pingMax.setText(String.format("%.0f ms", stats.maxMs()));
        pingLoss.setText(String.format("%.1f%%", stats.packetLossPercent()));
        pingJitter.setText(String.format("%.1f ms", stats.jitterMs()));
    }

    private String toneClass(double ms) {
        if (ms < 0) return "metric-bad";
        if (ms < 60) return "metric-good";
        if (ms < 120) return "metric-warn";
        return "metric-bad";
    }

    private void updateSystemCards(SystemStats stats) {
        cpuValue.setText(String.format("%.0f%%", stats.cpuLoadPercent()));
        cpuSpark.addSample(stats.cpuLoadPercent());

        ramValue.setText(String.format("%.0f%%", stats.ramUsagePercent()));
        ramSpark.addSample(stats.ramUsagePercent());

        double totalKbps = stats.uploadKbps() + stats.downloadKbps();
        netValue.setText(formatRate(totalKbps));
        netSpark.addSample(Math.min(100, totalKbps / 20.0));

        uploadValue.setText(formatRate(stats.uploadKbps()));
        uploadChart.addSample(stats.uploadKbps());

        downloadValue.setText(formatRate(stats.downloadKbps()));
        downloadChart.addSample(stats.downloadKbps());
    }

    private String formatRate(double kbps) {
        return kbps >= 1024 ? String.format("%.1f MB/s", kbps / 1024.0) : String.format("%.0f KB/s", kbps);
    }

    private void startRegionLeaderboard(AppServices services) {
        Runnable sweep = () -> {
            for (ServerInfo server : serverBoard) {
                PingSample sample = services.pingService().ping(server.getHost());
                double value = sample.success() ? sample.rttMillis() : -1;
                Platform.runLater(() -> server.setLatencyMs(value));
            }
            Platform.runLater(this::refreshLeaderboard);
        };
        boardExecutor.submit(sweep);
        boardExecutor.scheduleAtFixedRate(sweep, 45, 45, TimeUnit.SECONDS);
    }

    private void refreshLeaderboard() {
        List<ServerInfo> ranked = new ArrayList<>(serverBoard);
        ranked.removeIf(s -> s.getLatencyMs() < 0);
        ranked.sort(Comparator.comparingDouble(ServerInfo::getLatencyMs));

        if (ranked.isEmpty()) {
            return;
        }
        ServerInfo best = ranked.get(0);
        bestServerName.setText(best.getDisplayName());
        bestServerLatency.setText(String.format("%.0f ms", best.getLatencyMs()));

        leaderboardRows.getChildren().clear();
        int shown = Math.min(4, ranked.size() - 1);
        for (int i = 1; i <= shown; i++) {
            leaderboardRows.getChildren().add(leaderboardRow(ranked.get(i), false));
        }
    }
}
