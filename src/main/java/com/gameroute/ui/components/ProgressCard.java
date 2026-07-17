package com.gameroute.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

/**
 * Stat tile variant with a progress bar underneath the value, used for
 * percentage-style metrics like CPU load and RAM usage.
 */
public class ProgressCard extends VBox {

    private final Label titleLabel = new Label();
    private final Label valueLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar(0);

    public ProgressCard(String title) {
        getStyleClass().add("card");
        setSpacing(8);
        setAlignment(Pos.TOP_LEFT);

        titleLabel.getStyleClass().add("card-title");
        titleLabel.setText(title.toUpperCase());

        valueLabel.getStyleClass().add("card-value");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(10);

        getChildren().addAll(titleLabel, valueLabel, progressBar);
    }

    public void setPercent(double percent) {
        double clamped = Math.max(0, Math.min(100, percent));
        valueLabel.setText(String.format("%.0f%%", clamped));
        progressBar.setProgress(clamped / 100.0);
    }

    public void setValueText(String text) {
        valueLabel.setText(text);
    }
}
