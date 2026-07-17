package com.gameroute.ui.components;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.List;

/**
 * Small, reusable motion toolkit. JavaFX CSS has no {@code transition}/
 * keyframe support (unlike web CSS), so every hover-scale, fade-in, glow
 * pulse and ripple in GameRoute's UI is driven from here rather than from
 * the stylesheet -- this class is the single place that owns "how things
 * move" so every view feels consistent.
 */
public final class Animations {

    private Animations() {
    }

    /** Gentle scale-up on hover, back to normal on exit -- used on cards and nav items. */
    public static void hoverScale(Node node, double targetScale) {
        node.setOnMouseEntered(e -> scaleTo(node, targetScale, 160));
        node.setOnMouseExited(e -> scaleTo(node, 1.0, 160));
    }

    private static void scaleTo(Node node, double scale, int millis) {
        ScaleTransition t = new ScaleTransition(Duration.millis(millis), node);
        t.setToX(scale);
        t.setToY(scale);
        t.setInterpolator(Interpolator.EASE_BOTH);
        t.play();
    }

    /** Simple fade-in from transparent, used for freshly-built cards/pages. */
    public static void fadeIn(Node node, int millis) {
        node.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    /** Fade + gentle rise-in, the entrance used for dashboard cards. */
    public static void fadeInUp(Node node, int millis, double fromOffsetY) {
        node.setOpacity(0);
        node.setTranslateY(fromOffsetY);
        FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition rise = new TranslateTransition(Duration.millis(millis), node);
        rise.setFromY(fromOffsetY);
        rise.setToY(0);
        rise.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fade, rise).play();
    }

    /** Plays {@link #fadeInUp} on each node with a small staggered delay, for a cascading reveal. */
    public static void staggeredEntrance(List<? extends Node> nodes, int staggerMillis) {
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            node.setOpacity(0);
            PauseTransition delay = new PauseTransition(Duration.millis((long) i * staggerMillis));
            delay.setOnFinished(e -> fadeInUp(node, 380, 14));
            delay.play();
        }
    }

    /**
     * Crossfades {@code newNode} in over {@code oldNode} inside a single-child
     * container, used for the sidebar's page switches so nothing just pops in.
     */
    public static void crossFadePage(Pane container, Node newNode) {
        newNode.setOpacity(0);
        newNode.setTranslateY(8);
        container.getChildren().setAll(newNode);
        FadeTransition fade = new FadeTransition(Duration.millis(220), newNode);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition rise = new TranslateTransition(Duration.millis(220), newNode);
        rise.setFromY(8);
        rise.setToY(0);
        rise.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fade, rise).play();
    }

    /** A slow, endless breathing glow -- used behind the big "Optimize Now" buttons. */
    public static Timeline glowPulse(DropShadow shadow, double minRadius, double maxRadius) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(shadow.radiusProperty(), minRadius, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.seconds(1.6), new KeyValue(shadow.radiusProperty(), maxRadius, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.seconds(3.2), new KeyValue(shadow.radiusProperty(), minRadius, Interpolator.EASE_BOTH))
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        return timeline;
    }

    /**
     * Wraps a control in a clipping {@link StackPane} and adds a Material-style
     * ripple on click: a translucent circle grows from the click point and
     * fades out. Returns the wrapper -- add that to the layout instead of the
     * original node.
     */
    public static StackPane withRipple(Region content, Color rippleColor) {
        StackPane wrapper = new StackPane(content);
        wrapper.setPickOnBounds(true);
        content.setOnMousePressed(e -> {
            Circle ripple = new Circle(4, rippleColor);
            ripple.setOpacity(0.5);
            ripple.setMouseTransparent(true);
            ripple.setCenterX(e.getX());
            ripple.setCenterY(e.getY());
            wrapper.getChildren().add(ripple);

            double maxRadius = Math.max(content.getWidth(), content.getHeight());
            ScaleTransition grow = new ScaleTransition(Duration.millis(420), ripple);
            grow.setToX(maxRadius / 4.0);
            grow.setToY(maxRadius / 4.0);
            FadeTransition fade = new FadeTransition(Duration.millis(420), ripple);
            fade.setFromValue(0.5);
            fade.setToValue(0);
            SequentialTransition sequence = new SequentialTransition(new ParallelTransition(grow, fade));
            sequence.setOnFinished(evt -> wrapper.getChildren().remove(ripple));
            sequence.play();
        });
        return wrapper;
    }

    /** A soft red glow drop shadow, the app's signature accent effect. */
    public static DropShadow redGlow(double radius) {
        DropShadow shadow = new DropShadow(BlurType.GAUSSIAN, Color.rgb(255, 59, 48, 0.6), radius, 0.35, 0, 0);
        return shadow;
    }
}
