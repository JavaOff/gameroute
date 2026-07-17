package com.gameroute.ui.components;

import com.gameroute.ui.icons.Icons;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * One row of the Optimizer checklist: a circular status marker (empty ->
 * checked, with a little pop animation), the optimization's name, and a
 * trailing status label ("Ready", "Running...", "Done", "Failed").
 */
public class ChecklistItem extends HBox {

    private final Circle marker = new Circle(9);
    private final StackPane markerStack;
    private final Label statusLabel = new Label("Ready");

    public ChecklistItem(String name, boolean requiresAdmin) {
        setSpacing(14);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(10, 4, 10, 4));

        marker.setFill(Color.TRANSPARENT);
        marker.setStroke(Color.web("#9EA3AE"));
        marker.setStrokeWidth(1.6);
        markerStack = new StackPane(marker);
        markerStack.setPrefSize(20, 20);

        Label nameLabel = new Label(name + (requiresAdmin ? "  ·  admin" : ""));
        nameLabel.setStyle("-fx-font-weight: 700; -fx-font-size: 13px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusLabel.getStyleClass().add("card-subtitle");

        getChildren().addAll(markerStack, nameLabel, spacer, statusLabel);
    }

    public void setRunning() {
        statusLabel.setText("Running...");
        statusLabel.setStyle("-fx-text-fill: #FFD60A;");
        marker.setStroke(Color.web("#FFD60A"));
    }

    public void setDone(boolean success) {
        if (success) {
            statusLabel.setText("Done");
            statusLabel.setStyle("-fx-text-fill: #30D158;");
            marker.setFill(Color.web("#30D158"));
            marker.setStroke(Color.web("#30D158"));
            Node check = Icons.check(12, Color.BLACK);
            markerStack.getChildren().setAll(marker, check);
            pop(markerStack);
        } else {
            statusLabel.setText("Failed");
            statusLabel.setStyle("-fx-text-fill: #FF3B30;");
            marker.setFill(Color.TRANSPARENT);
            marker.setStroke(Color.web("#FF3B30"));
            markerStack.getChildren().setAll(marker, Icons.close(12, Color.web("#FF3B30")));
            pop(markerStack);
        }
    }

    public void reset() {
        statusLabel.setText("Ready");
        statusLabel.setStyle("");
        marker.setFill(Color.TRANSPARENT);
        marker.setStroke(Color.web("#9EA3AE"));
        markerStack.getChildren().setAll(marker);
    }

    private void pop(Node node) {
        node.setScaleX(0.5);
        node.setScaleY(0.5);
        ScaleTransition scale = new ScaleTransition(Duration.millis(320), node);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);
        scale.play();
    }
}
