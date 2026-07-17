package com.gameroute.ui.tabs;

import com.gameroute.model.DailyStatistics;
import com.gameroute.ui.AppServices;
import com.gameroute.ui.components.Animations;
import com.gameroute.ui.components.Dialogs;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Historical ping/jitter/packet-loss figures, aggregated per day from the
 * persisted CSV history, with a one-click CSV export for external analysis.
 */
public class StatisticsView extends VBox {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private final BarChart<String, Number> chart;
    private final TableView<DailyStatistics> table = new TableView<>();

    public StatisticsView(AppServices services) {
        setSpacing(16);
        setPadding(new Insets(24));

        Label title = new Label("PING HISTORY");
        title.getStyleClass().add("card-title");

        ToggleGroup range = new ToggleGroup();
        ToggleButton day1 = segmentButton("24H", range);
        ToggleButton day7 = segmentButton("7D", range);
        ToggleButton day30 = segmentButton("30D", range);
        day7.setSelected(true);
        HBox segmented = new HBox(2, day1, day7, day30);
        segmented.getStyleClass().add("segmented-group");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("button-ghost");
        Button export = new Button("Export CSV");
        export.getStyleClass().add("btn-glow-red");

        HBox header = new HBox(16, title, segmented, spacer, refresh, export);
        header.setAlignment(Pos.CENTER_LEFT);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Average ping (ms)");
        chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(260);
        chart.setCategoryGap(30);
        chart.setBarGap(4);

        buildTable();

        VBox card = new VBox(14, header, chart, table);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        getChildren().add(card);
        Animations.fadeInUp(card, 380, 14);

        Runnable reload = () -> reload(services, day30.isSelected() ? 30 : day7.isSelected() ? 7 : 1);
        day1.setOnAction(e -> reload.run());
        day7.setOnAction(e -> reload.run());
        day30.setOnAction(e -> reload.run());
        refresh.setOnAction(e -> reload.run());
        export.setOnAction(e -> exportCsv(services, export.getScene().getWindow()));

        reload.run();
    }

    private ToggleButton segmentButton(String text, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        return button;
    }

    private void buildTable() {
        table.setPrefHeight(260);

        TableColumn<DailyStatistics, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().date().toString()));

        TableColumn<DailyStatistics, String> avg = new TableColumn<>("Avg Ping");
        avg.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(String.format("%.0f ms", d.getValue().avgPingMs())));

        TableColumn<DailyStatistics, String> minMax = new TableColumn<>("Min / Max");
        minMax.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                String.format("%.0f / %.0f ms", d.getValue().minPingMs(), d.getValue().maxPingMs())));

        TableColumn<DailyStatistics, String> jitter = new TableColumn<>("Jitter");
        jitter.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(String.format("%.1f ms", d.getValue().avgJitterMs())));

        TableColumn<DailyStatistics, String> loss = new TableColumn<>("Packet Loss");
        loss.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(String.format("%.1f%%", d.getValue().packetLossPercent())));

        TableColumn<DailyStatistics, Number> samples = new TableColumn<>("Samples");
        samples.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().sampleCount()));

        table.getColumns().addAll(List.of(date, avg, minMax, jitter, loss, samples));
    }

    private void reload(AppServices services, int days) {
        List<DailyStatistics> stats = services.statisticsService().dailyStatistics(days);
        table.getItems().setAll(stats);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (DailyStatistics stat : stats) {
            series.getData().add(new XYChart.Data<>(DATE_FMT.format(stat.date()), stat.avgPingMs()));
        }
        chart.getData().setAll(series);
    }

    private void exportCsv(AppServices services, Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export ping history CSV");
        chooser.setInitialFileName("gameroute-ping-history.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File destination = chooser.showSaveDialog(owner);
        if (destination == null) {
            return;
        }
        boolean ok = services.csvExportService().exportTo(destination.toPath());
        Alert alert = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
        alert.setHeaderText(ok ? "Export complete" : "Export failed");
        alert.setContentText(ok ? "Saved to " + destination.getAbsolutePath() : "Could not write the CSV file. Check the logs for details.");
        Dialogs.themed(alert);
        alert.showAndWait();
    }
}
