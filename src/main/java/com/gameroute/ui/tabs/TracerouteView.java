package com.gameroute.ui.tabs;

import com.gameroute.model.Region;
import com.gameroute.model.TracerouteHop;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Runs and visualizes a hop-by-hop traceroute to the selected Riot region,
 * highlighting hops that look like the source of a latency problem and
 * flagging when the path itself changes between runs.
 */
public class TracerouteView extends VBox {

    private final ObservableList<TracerouteHop> hops = FXCollections.observableArrayList();
    private final Label routeChangeBanner = new Label();
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "traceroute-runner"));

    public TracerouteView(AppServices services) {
        setSpacing(16);
        setPadding(new Insets(24));

        Label title = new Label("ROUTE ANALYZER");
        title.getStyleClass().add("card-title");
        HBox titleRow = new HBox(8, Icons.satelliteDish(20, Color.web("#FF3B30")), title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Region> regionBox = new ComboBox<>();
        regionBox.getItems().addAll(Region.values());
        regionBox.setValue(Region.valueOf(services.config().getPreferredRegion()));

        Button run = new Button("Run Traceroute");
        run.getStyleClass().add("btn-glow-red");
        run.setOnAction(e -> runTraceroute(services, regionBox.getValue(), run));
        Animations.hoverScale(run, 1.03);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        HBox header = new HBox(16, titleRow, spacer, regionBox, run);
        header.setAlignment(Pos.CENTER_LEFT);

        Label disclaimer = new Label(
                "Traces the route to Riot's public developer API gateway for this platform (Cloudflare-fronted), "
                        + "as a proxy for the general network path -- not necessarily the exact route live game traffic takes.");
        disclaimer.getStyleClass().add("card-subtitle");
        disclaimer.setWrapText(true);

        routeChangeBanner.getStyleClass().addAll("pill", "pill-bad");
        routeChangeBanner.setVisible(false);
        routeChangeBanner.setManaged(false);

        TableView<TracerouteHop> table = buildTable();

        VBox card = new VBox(14, header, disclaimer, routeChangeBanner, table);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        getChildren().add(card);

        Animations.fadeInUp(card, 380, 14);
    }

    private TableView<TracerouteHop> buildTable() {
        TableView<TracerouteHop> table = new TableView<>(hops);
        table.setPrefHeight(520);

        TableColumn<TracerouteHop, Number> hop = new TableColumn<>("Hop");
        hop.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getHopNumber()));
        hop.setPrefWidth(50);

        TableColumn<TracerouteHop, String> hostname = new TableColumn<>("Hostname");
        hostname.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getHostname() != null ? d.getValue().getHostname() : ""));
        hostname.setPrefWidth(210);

        TableColumn<TracerouteHop, String> ip = new TableColumn<>("IP Address");
        ip.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getIpAddress()));
        ip.setPrefWidth(140);

        TableColumn<TracerouteHop, String> rtt1 = rttColumn("RTT 1", TracerouteHop::getRtt1);
        TableColumn<TracerouteHop, String> rtt2 = rttColumn("RTT 2", TracerouteHop::getRtt2);
        TableColumn<TracerouteHop, String> rtt3 = rttColumn("RTT 3", TracerouteHop::getRtt3);

        TableColumn<TracerouteHop, String> avg = new TableColumn<>("Average");
        avg.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().isTimeout() ? "*" : String.format("%.0f ms", d.getValue().getAverageRtt())));

        TableColumn<TracerouteHop, TracerouteHop> status = new TableColumn<>("Status");
        status.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        status.setCellFactory(col -> statusCell());
        status.setPrefWidth(110);

        table.getColumns().addAll(List.of(hop, hostname, ip, rtt1, rtt2, rtt3, avg, status));

        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(TracerouteHop item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("problematic-hop");
                if (!empty && item != null && item.isProblematic()) {
                    getStyleClass().add("problematic-hop");
                }
            }
        });
        return table;
    }

    private TableCell<TracerouteHop, TracerouteHop> statusCell() {
        return new TableCell<>() {
            private final Label pill = new Label();

            @Override
            protected void updateItem(TracerouteHop hop, boolean empty) {
                super.updateItem(hop, empty);
                if (empty || hop == null) {
                    setGraphic(null);
                    return;
                }
                pill.getStyleClass().removeAll("pill-good", "pill-warn", "pill-bad");
                pill.getStyleClass().add("pill");
                if (hop.isTimeout()) {
                    pill.setText("Timeout");
                    pill.getStyleClass().add("pill-bad");
                } else if (hop.isProblematic()) {
                    pill.setText("Slow");
                    pill.getStyleClass().add("pill-warn");
                } else {
                    pill.setText("OK");
                    pill.getStyleClass().add("pill-good");
                }
                setGraphic(pill);
            }
        };
    }

    private TableColumn<TracerouteHop, String> rttColumn(String title, java.util.function.Function<TracerouteHop, Double> extractor) {
        TableColumn<TracerouteHop, String> column = new TableColumn<>(title);
        column.setCellValueFactory(d -> {
            Double value = extractor.apply(d.getValue());
            return new ReadOnlyObjectWrapper<>(value == null ? "*" : String.format("%.0f ms", value));
        });
        return column;
    }

    private void runTraceroute(AppServices services, Region region, Button trigger) {
        trigger.setDisable(true);
        executor.submit(() -> {
            List<TracerouteHop> result = services.tracerouteService().traceroute(region.getHost());
            services.routeAnalyzer().markProblematicHops(result);
            boolean changed = services.routeAnalyzer().detectRouteChange(result);
            Platform.runLater(() -> {
                hops.setAll(result);
                routeChangeBanner.setText("Route to " + region.getCode() + " has changed since the last traceroute");
                routeChangeBanner.setVisible(changed);
                routeChangeBanner.setManaged(changed);
                trigger.setDisable(false);
            });
        });
    }
}
