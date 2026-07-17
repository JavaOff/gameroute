package com.gameroute.ui;

import com.gameroute.ui.icons.Icons;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Bottom app-wide status strip: connectivity indicator plus a visual
 * countdown. The countdown is informational only -- GameRoute never applies
 * an optimization automatically or without confirmation (see the Optimizer
 * tab), so this does not silently run anything; it simply signals the app
 * is alive and gives a sense of cadence, matching the "auto optimization
 * timer" element from the design spec.
 */
public class StatusBar extends HBox {

    private static final int AUTO_CHECK_INTERVAL_SECONDS = 300;

    private final Label countdownLabel = new Label();
    private int secondsRemaining = AUTO_CHECK_INTERVAL_SECONDS;

    public StatusBar() {
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);

        javafx.scene.Node shield = Icons.shieldCheck(15, Color.web("#30D158"));

        Label connected = new Label("Connected");
        connected.getStyleClass().add("status-bar-text");
        connected.setStyle("-fx-text-fill: #F5F5F7; -fx-font-weight: 700;");

        Label operational = new Label("· Everything operational");
        operational.getStyleClass().add("status-bar-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        countdownLabel.getStyleClass().addAll("status-bar-text", "mono");
        updateCountdownLabel();

        getChildren().addAll(shield, connected, operational, spacer, countdownLabel);

        startCountdown();
    }

    private void startCountdown() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secondsRemaining--;
            if (secondsRemaining <= 0) {
                secondsRemaining = AUTO_CHECK_INTERVAL_SECONDS;
            }
            updateCountdownLabel();
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void updateCountdownLabel() {
        int minutes = secondsRemaining / 60;
        int seconds = secondsRemaining % 60;
        countdownLabel.setText(String.format("Next auto-check in %d:%02d", minutes, seconds));
    }
}
