package com.gameroute.ui;

import com.gameroute.config.Constants;
import com.gameroute.ui.components.Animations;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Left navigation rail: icon + label rows with a glowing red indicator that
 * slides to track the active page, and the big "Optimize Now" shortcut
 * pinned to the bottom.
 */
public class Sidebar extends VBox {

    private static final double ITEM_HEIGHT = 46;
    private static final Color INACTIVE_COLOR = Color.web("#9EA3AE");
    private static final Color ACTIVE_COLOR = Color.web("#F5F5F7");

    public record NavEntry(String id, String label, BiFunction<Double, Color, javafx.scene.Node> icon) {
    }

    private final List<Label> itemLabels = new ArrayList<>();
    private final List<NavEntry> entries;
    private final Rectangle indicator = new Rectangle(4, ITEM_HEIGHT - 14);
    private int activeIndex = 0;

    public Sidebar(List<NavEntry> entries, Consumer<String> onSelect, Runnable onOptimizeNow) {
        this.entries = entries;
        getStyleClass().add("sidebar");
        setPrefWidth(250);
        setMinWidth(250);
        setMaxWidth(250);
        setPadding(new Insets(18, 14, 18, 14));
        setSpacing(4);

        indicator.getStyleClass().add("nav-indicator");
        indicator.setArcWidth(4);
        indicator.setArcHeight(4);

        Pane navArea = buildNavArea(entries, onSelect);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        javafx.scene.control.Button optimizeButton = buildOptimizeButton(onOptimizeNow);

        Label version = new Label("v" + Constants.APP_VERSION);
        version.getStyleClass().add("sidebar-version");
        HBox footer = new HBox(version);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(10, 4, 0, 4));

        getChildren().addAll(navArea, spacer, optimizeButton, footer);

        selectIndex(0, false);
    }

    private Pane buildNavArea(List<NavEntry> entries, Consumer<String> onSelect) {
        Pane pane = new Pane();
        pane.setPrefHeight(entries.size() * (ITEM_HEIGHT + 4));

        VBox list = new VBox(4);
        list.setPrefWidth(222);

        for (int i = 0; i < entries.size(); i++) {
            NavEntry entry = entries.get(i);
            Label item = buildNavItem(entry);
            int index = i;
            item.setOnMouseClicked(e -> {
                selectIndex(index, true);
                onSelect.accept(entry.id());
            });
            Animations.hoverScale(item, 1.02);
            itemLabels.add(item);
            list.getChildren().add(item);
        }

        pane.getChildren().addAll(list, indicator);
        indicator.setLayoutX(-14);
        return pane;
    }

    private Label buildNavItem(NavEntry entry) {
        Label label = new Label(entry.label());
        label.setGraphic(entry.icon().apply(18.0, INACTIVE_COLOR));
        label.setGraphicTextGap(14);
        label.getStyleClass().addAll("nav-item", "nav-label");
        label.setPrefWidth(222);
        label.setPrefHeight(ITEM_HEIGHT);
        label.setAlignment(Pos.CENTER_LEFT);
        return label;
    }

    private void selectIndex(int index, boolean animate) {
        Label previous = itemLabels.get(activeIndex);
        previous.getStyleClass().removeAll("nav-item-active", "nav-label-active");
        previous.setGraphic(entries.get(activeIndex).icon().apply(18.0, INACTIVE_COLOR));

        activeIndex = index;
        Label active = itemLabels.get(index);
        active.getStyleClass().addAll("nav-item-active", "nav-label-active");
        active.setGraphic(entries.get(index).icon().apply(18.0, ACTIVE_COLOR));

        double targetY = index * (ITEM_HEIGHT + 4) + 7;
        if (animate) {
            TranslateTransition slide = new TranslateTransition(Duration.millis(260), indicator);
            slide.setToY(targetY - indicator.getLayoutY());
            slide.setInterpolator(Interpolator.EASE_BOTH);
            slide.play();
        } else {
            indicator.setLayoutY(targetY);
        }
    }

    private javafx.scene.control.Button buildOptimizeButton(Runnable onOptimizeNow) {
        javafx.scene.control.Button button = new javafx.scene.control.Button("OPTIMIZE NOW");
        button.getStyleClass().add("btn-optimize-sidebar");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> onOptimizeNow.run());
        Animations.hoverScale(button, 1.03);
        var shadow = Animations.redGlow(20);
        button.setEffect(shadow);
        Animations.glowPulse(shadow, 14, 26);
        return button;
    }
}
