package com.gameroute.ui.tabs;

import com.gameroute.config.Constants;
import com.gameroute.ui.components.Animations;
import com.gameroute.ui.icons.Icons;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tails GameRoute's own log file so users (and support requests) don't need
 * to hunt for {@code ~/.gameroute/logs} manually.
 */
public class LogsView extends VBox {

    private static final Path LOG_FILE = Constants.APP_HOME.resolve("logs").resolve("gameroute.log");

    private final TextArea textArea = new TextArea();
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "log-tailer"));

    private long lastLineCount = 0;

    public LogsView() {
        setSpacing(14);
        setPadding(new Insets(24));

        Label title = new Label("APPLICATION LOGS");
        title.getStyleClass().add("card-title");
        HBox titleRow = new HBox(8, Icons.fileText(16, Color.web("#9EA3AE")), title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button openFolder = new Button("Open log folder");
        openFolder.getStyleClass().add("button-ghost");
        openFolder.setOnAction(e -> openLogFolder());

        HBox header = new HBox(12, titleRow, spacer, openFolder);
        header.setAlignment(Pos.CENTER_LEFT);

        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setPrefHeight(600);
        textArea.getStyleClass().add("mono");
        textArea.setStyle("-fx-control-inner-background: #10131A; -fx-text-fill: #F5F5F7;");

        VBox card = new VBox(12, header, textArea);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        getChildren().add(card);
        Animations.fadeInUp(card, 380, 14);

        executor.scheduleAtFixedRate(this::tail, 0, 2, TimeUnit.SECONDS);
    }

    private void tail() {
        if (!Files.exists(LOG_FILE)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(LOG_FILE);
            if (lines.size() <= lastLineCount) {
                return;
            }
            List<String> newLines = lines.subList((int) lastLineCount, lines.size());
            lastLineCount = lines.size();
            StringBuilder appended = new StringBuilder();
            newLines.forEach(l -> appended.append(l).append('\n'));
            Platform.runLater(() -> {
                textArea.appendText(appended.toString());
            });
        } catch (IOException e) {
            // Best-effort: skip this poll, try again next tick.
        }
    }

    private void openLogFolder() {
        try {
            Files.createDirectories(LOG_FILE.getParent());
            new ProcessBuilder("explorer.exe", LOG_FILE.getParent().toString()).start();
        } catch (IOException e) {
            // Non-critical convenience action; nothing to recover from here.
        }
    }
}
