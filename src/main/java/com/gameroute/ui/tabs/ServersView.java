package com.gameroute.ui.tabs;

import com.gameroute.config.ServerDatabase;
import com.gameroute.model.PingSample;
import com.gameroute.model.ServerInfo;
import com.gameroute.ui.AppServices;
import com.gameroute.ui.components.Animations;
import com.gameroute.ui.icons.Icons;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Riot server database with live-measured latency, so the user can see at a
 * glance which regions are closest/fastest to reach from their connection.
 */
public class ServersView extends VBox {

    private final ObservableList<ServerInfo> servers = FXCollections.observableArrayList(ServerDatabase.allServers());
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "server-latency-probe"));

    public ServersView(AppServices services) {
        setSpacing(16);
        setPadding(new Insets(24));

        Label title = new Label("RIOT SERVER REGIONS");
        title.getStyleClass().add("card-title");
        HBox titleRow = new HBox(8, Icons.globe(20, Color.web("#FF3B30")), title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Button refresh = new Button("Test All Servers");
        refresh.getStyleClass().add("btn-glow-red");
        refresh.setOnAction(e -> measureAll(services, refresh));
        Animations.hoverScale(refresh, 1.03);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(16, titleRow, spacer, refresh);
        header.setAlignment(Pos.CENTER_LEFT);

        Label disclaimer = new Label(
                "Pings Riot's public developer API gateway per platform. That gateway is Cloudflare-fronted, "
                        + "so results estimate your distance to Riot's nearest edge network, not the exact live game "
                        + "server -- treat them as a relative comparison between regions, not an exact prediction.");
        disclaimer.getStyleClass().add("card-subtitle");
        disclaimer.setWrapText(true);

        TableView<ServerInfo> table = buildTable();

        VBox card = new VBox(14, header, disclaimer, table);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        getChildren().add(card);
        Animations.fadeInUp(card, 380, 14);

        measureAll(services, refresh);
        executor.scheduleAtFixedRate(() -> measureAll(services, null), 60, 60, TimeUnit.SECONDS);
    }

    private TableView<ServerInfo> buildTable() {
        TableView<ServerInfo> table = new TableView<>(servers);
        table.setPrefHeight(480);

        TableColumn<ServerInfo, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getCode()));

        TableColumn<ServerInfo, String> name = new TableColumn<>("Region");
        name.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getDisplayName()));
        name.setPrefWidth(220);

        TableColumn<ServerInfo, String> host = new TableColumn<>("Host");
        host.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getHost()));
        host.setPrefWidth(220);

        TableColumn<ServerInfo, Number> latency = new TableColumn<>("Latency");
        latency.setCellValueFactory(data -> data.getValue().latencyMsProperty());
        latency.setCellFactory(latencyCellFactory());

        TableColumn<ServerInfo, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(data -> data.getValue().statusProperty());
        status.setCellFactory(col -> statusCell());

        table.getColumns().addAll(code, name, host, latency, status);
        return table;
    }

    private Callback<TableColumn<ServerInfo, Number>, TableCell<ServerInfo, Number>> latencyCellFactory() {
        return column -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                double ms = value.doubleValue();
                getStyleClass().add("mono");
                if (ms < 0) {
                    setText("--");
                    setStyle("-fx-text-fill: #9EA3AE;");
                } else {
                    setText(String.format("%.0f ms", ms));
                    setStyle(ms < 60 ? "-fx-text-fill: #30D158; -fx-font-weight: 700;"
                            : ms < 120 ? "-fx-text-fill: #FFD60A; -fx-font-weight: 700;"
                            : "-fx-text-fill: #FF3B30; -fx-font-weight: 700;");
                }
            }
        };
    }

    private TableCell<ServerInfo, String> statusCell() {
        return new TableCell<>() {
            private final Label pill = new Label();

            @Override
            protected void updateItem(String statusText, boolean empty) {
                super.updateItem(statusText, empty);
                if (empty || statusText == null) {
                    setGraphic(null);
                    return;
                }
                pill.getStyleClass().removeAll("pill-good", "pill-warn", "pill-bad");
                pill.getStyleClass().add("pill");
                pill.setText(statusText);
                if ("Reachable".equals(statusText)) {
                    pill.getStyleClass().add("pill-good");
                } else if ("Unreachable".equals(statusText)) {
                    pill.getStyleClass().add("pill-bad");
                } else {
                    pill.getStyleClass().add("pill-warn");
                }
                setGraphic(pill);
            }
        };
    }

    private void measureAll(AppServices services, Button triggerButtonOrNull) {
        if (triggerButtonOrNull != null) {
            triggerButtonOrNull.setDisable(true);
        }
        executor.submit(() -> {
            for (ServerInfo server : servers) {
                PingSample sample = services.pingService().ping(server.getHost());
                double value = sample.success() ? sample.rttMillis() : -1;
                Platform.runLater(() -> server.setLatencyMs(value));
            }
            if (triggerButtonOrNull != null) {
                Platform.runLater(() -> triggerButtonOrNull.setDisable(false));
            }
        });
    }
}
