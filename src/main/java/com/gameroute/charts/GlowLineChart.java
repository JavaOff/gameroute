package com.gameroute.charts;

import javafx.animation.Interpolator;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A neon-glow line chart drawn on a {@link Canvas}, replacing JavaFX's
 * built-in {@code LineChart} (which is expensive to restyle for a bloom/glow
 * look, and animates each point individually rather than the whole path).
 * <p>
 * The glow is approximated by stroking the same path several times with
 * increasing width and decreasing opacity -- a standard, cheap substitute
 * for a true blur/bloom post-process, which JavaFX's software Canvas doesn't
 * support directly. New data reveals with a left-to-right wipe animation
 * rather than popping in.
 */
public class GlowLineChart extends Region {

    private final Canvas canvas = new Canvas();
    private final Deque<Double> samples = new ArrayDeque<>();
    private final int windowSize;
    private final Color lineColor;
    private final boolean areaFill;

    private final DoubleProperty revealProgress = new SimpleDoubleProperty(1);
    private Double fixedMin;
    private Double fixedMax;

    public GlowLineChart(int windowSize, Color lineColor, boolean areaFill) {
        this.windowSize = windowSize;
        this.lineColor = lineColor;
        this.areaFill = areaFill;
        getChildren().add(canvas);
        setMinHeight(60);
        widthProperty().addListener((obs, o, n) -> requestRedraw());
        heightProperty().addListener((obs, o, n) -> requestRedraw());
        revealProgress.addListener((obs, o, n) -> redraw());
    }

    public void setFixedRange(double min, double max) {
        this.fixedMin = min;
        this.fixedMax = max;
    }

    /** Adds one sample; pass a negative value to represent a dropped/timed-out probe (rendered as a gap). */
    public void addSample(double value) {
        Runnable action = () -> {
            samples.addLast(value);
            while (samples.size() > windowSize) {
                samples.removeFirst();
            }
            redraw();
        };
        runOnFx(action);
    }

    /** Replaces the whole dataset at once (used for historical/aggregated views) and plays the reveal wipe. */
    public void setSamples(List<Double> values) {
        runOnFx(() -> {
            samples.clear();
            samples.addAll(values);
            playReveal();
        });
    }

    public void clear() {
        runOnFx(() -> {
            samples.clear();
            redraw();
        });
    }

    private void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private void playReveal() {
        revealProgress.set(0);
        Timeline timeline = new Timeline();
        timeline.getKeyFrames().add(new javafx.animation.KeyFrame(
                javafx.util.Duration.millis(650),
                new javafx.animation.KeyValue(revealProgress, 1, Interpolator.EASE_OUT)));
        timeline.play();
    }

    private void requestRedraw() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        redraw();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        canvas.setWidth(w);
        canvas.setHeight(h);
        canvas.relocate(0, 0);
        redraw();
    }

    private void redraw() {
        double w = getWidth();
        double h = getHeight();
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        if (w <= 0 || h <= 0 || samples.size() < 2) {
            return;
        }

        List<Double> data = List.copyOf(samples);
        double min = fixedMin != null ? fixedMin : data.stream().filter(v -> v >= 0).mapToDouble(Double::doubleValue).min().orElse(0);
        double max = fixedMax != null ? fixedMax : data.stream().filter(v -> v >= 0).mapToDouble(Double::doubleValue).max().orElse(1);
        if (max - min < 1e-6) {
            max = min + 1;
        }
        double padding = (max - min) * 0.15;
        double effectiveMin = min - padding;
        double effectiveMax = max + padding;

        int n = data.size();
        double stepX = w / (n - 1);
        double visibleWidth = w * revealProgress.get();

        gc.save();
        gc.beginPath();
        gc.rect(0, 0, visibleWidth, h);
        gc.clip();

        // Area fill under the line, fading to transparent.
        if (areaFill) {
            gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 0.28)),
                    new Stop(1, Color.color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 0.0))));
            gc.beginPath();
            boolean started = false;
            for (int i = 0; i < n; i++) {
                double x = i * stepX;
                double v = data.get(i);
                if (v < 0) {
                    continue;
                }
                double y = h - ((v - effectiveMin) / (effectiveMax - effectiveMin)) * h;
                if (!started) {
                    gc.moveTo(x, h);
                    gc.lineTo(x, y);
                    started = true;
                } else {
                    gc.lineTo(x, y);
                }
            }
            if (started) {
                gc.lineTo((n - 1) * stepX, h);
                gc.closePath();
                gc.fill();
            }
        }

        // Glow passes: same path, wider + fainter each time.
        double[] glowWidths = {7, 5, 3};
        double[] glowAlphas = {0.10, 0.18, 0.28};
        for (int pass = 0; pass < glowWidths.length; pass++) {
            gc.setStroke(Color.color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), glowAlphas[pass]));
            gc.setLineWidth(glowWidths[pass]);
            strokeSeries(gc, data, stepX, h, effectiveMin, effectiveMax);
        }

        // Crisp core line on top.
        gc.setStroke(lineColor);
        gc.setLineWidth(2.0);
        strokeSeries(gc, data, stepX, h, effectiveMin, effectiveMax);

        gc.restore();
    }

    private void strokeSeries(GraphicsContext gc, List<Double> data, double stepX, double h, double min, double max) {
        gc.beginPath();
        boolean started = false;
        for (int i = 0; i < data.size(); i++) {
            double v = data.get(i);
            double x = i * stepX;
            if (v < 0) {
                started = false; // gap: lift the pen for a dropped/timed-out sample
                continue;
            }
            double y = h - ((v - min) / (max - min)) * h;
            if (!started) {
                gc.moveTo(x, y);
                started = true;
            } else {
                gc.lineTo(x, y);
            }
        }
        gc.stroke();
    }
}
