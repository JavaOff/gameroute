package com.gameroute.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Reusable "stat tile" used across the Dashboard: a title, a large value and
 * an optional subtitle. Value color can be swapped between good/warn/bad to
 * give an at-a-glance read on the metric.
 */
public class StatCard extends VBox {

    private final Label titleLabel = new Label();
    private final Label valueLabel = new Label();
    private final Label subtitleLabel = new Label();

    public enum Tone { NEUTRAL, GOOD, WARN, BAD }

    public StatCard(String title) {
        getStyleClass().add("card");
        setSpacing(6);
        setAlignment(Pos.TOP_LEFT);

        titleLabel.getStyleClass().add("card-title");
        titleLabel.setText(title.toUpperCase());

        valueLabel.getStyleClass().add("card-value");
        subtitleLabel.getStyleClass().add("card-subtitle");

        getChildren().addAll(titleLabel, valueLabel, subtitleLabel);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    public void setSubtitle(String subtitle) {
        subtitleLabel.setText(subtitle);
    }

    public void setTone(Tone tone) {
        valueLabel.getStyleClass().removeAll("card-value-good", "card-value-warn", "card-value-bad");
        switch (tone) {
            case GOOD -> valueLabel.getStyleClass().add("card-value-good");
            case WARN -> valueLabel.getStyleClass().add("card-value-warn");
            case BAD -> valueLabel.getStyleClass().add("card-value-bad");
            default -> { /* neutral: no extra class */ }
        }
    }
}
