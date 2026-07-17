package com.gameroute.ui.icons;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Small, dependency-free vector icon set. JavaFX has no built-in loader for
 * arbitrary multi-element SVG documents (only {@link SVGPath}, which is a
 * single path's worth of data), so icons here are either hand-authored path
 * data in a thin-stroke idiom, or composed from a couple of primitive shapes
 * ({@link Circle}, {@link Arc}, ...) where a single path would be
 * impractical (globe, trophy, wifi). Every icon is authored on a 24x24 grid
 * and scaled to the caller-supplied size, so they stay crisp at any
 * resolution -- no bitmap assets involved.
 * <p>
 * Every factory method returns a fixed {@code size x size} node: a scale
 * transform on a shape does not change its reported layout bounds, so
 * without the fixed-size wrapper every icon would visually shrink/grow but
 * still reserve its original 24x24 footprint in surrounding layouts.
 */
public final class Icons {

    private Icons() {
    }

    private static Node box(Node visual, double size) {
        StackPane pane = new StackPane(visual);
        pane.setPrefSize(size, size);
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);
        return pane;
    }

    private static Node strokePath(String data, double size, Paint color, double strokeWidth) {
        SVGPath path = new SVGPath();
        path.setContent(data);
        path.setFill(Color.TRANSPARENT);
        path.setStroke(color);
        path.setStrokeWidth(strokeWidth);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        double scale = size / 24.0;
        path.setScaleX(scale);
        path.setScaleY(scale);
        return box(path, size);
    }

    public static Node home(double size, Paint color) {
        return strokePath("M4 12 L12 5 L20 12 M6 11 V19 H10 V14 H14 V19 H18 V11", size, color, 1.8);
    }

    public static Node zap(double size, Paint color) {
        SVGPath path = new SVGPath();
        path.setContent("M13 3 L5 14 H11 L10 21 L19 10 H13 Z");
        path.setFill(color);
        path.setStroke(null);
        double scale = size / 24.0;
        path.setScaleX(scale);
        path.setScaleY(scale);
        return box(path, size);
    }

    public static Node globe(double size, Paint color) {
        double r = size / 2.0 - 1;
        Circle outer = new Circle(r);
        outer.setFill(Color.TRANSPARENT);
        outer.setStroke(color);
        outer.setStrokeWidth(1.6);

        Ellipse meridian = new Ellipse(r * 0.45, r);
        meridian.setFill(Color.TRANSPARENT);
        meridian.setStroke(color);
        meridian.setStrokeWidth(1.4);

        Line equator = new Line(-r, 0, r, 0);
        equator.setStroke(color);
        equator.setStrokeWidth(1.4);

        Group group = new Group(outer, meridian, equator);
        return box(group, size);
    }

    public static Node satelliteDish(double size, Paint color) {
        double s = size / 24.0;
        Arc dish = new Arc(12, 15, 8, 8, 20, 140);
        dish.setType(ArcType.OPEN);
        dish.setFill(Color.TRANSPARENT);
        dish.setStroke(color);
        dish.setStrokeWidth(1.8);
        Line pole = new Line(12, 15, 12, 21);
        pole.setStroke(color);
        pole.setStrokeWidth(1.8);
        Circle dot = new Circle(12, 6, 1.6);
        dot.setFill(color);
        Group g = new Group(dish, pole, dot);
        g.setScaleX(s);
        g.setScaleY(s);
        return box(g, size);
    }

    public static Node barChart(double size, Paint color) {
        double s = size / 24.0;
        Rectangle bar1 = bar(4, 14, 4, 6, color);
        Rectangle bar2 = bar(10, 9, 4, 11, color);
        Rectangle bar3 = bar(16, 4, 4, 16, color);
        Group g = new Group(bar1, bar2, bar3);
        g.setScaleX(s);
        g.setScaleY(s);
        return box(g, size);
    }

    private static Rectangle bar(double x, double y, double w, double h, Paint color) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.setArcWidth(2);
        r.setArcHeight(2);
        r.setFill(color);
        return r;
    }

    public static Node gear(double size, Paint color) {
        return strokePath(
                "M12 8 A4 4 0 1 1 12 16 A4 4 0 1 1 12 8 "
                        + "M12 2 V5 M12 19 V22 M2 12 H5 M19 12 H22 "
                        + "M4.9 4.9 L7 7 M17 17 L19.1 19.1 M19.1 4.9 L17 7 M7 17 L4.9 19.1",
                size, color, 1.7);
    }

    public static Node fileText(double size, Paint color) {
        return strokePath("M6 2 H15 L18 5 V22 H6 Z M14 2 V6 H18 M9 12 H15 M9 16 H15 M9 8 H11", size, color, 1.6);
    }

    public static Node bell(double size, Paint color) {
        return strokePath("M12 3 C9 3 7 5 7 9 V13 L5 16 H19 L17 13 V9 C17 5 15 3 12 3 Z M10 19 A2 2 0 0 0 14 19",
                size, color, 1.6);
    }

    public static Node userCircle(double size, Paint color) {
        double r = size / 2.0 - 1;
        Circle outer = new Circle(r);
        outer.setFill(Color.TRANSPARENT);
        outer.setStroke(color);
        outer.setStrokeWidth(1.6);
        Circle head = new Circle(0, -r * 0.28, r * 0.32);
        head.setFill(Color.TRANSPARENT);
        head.setStroke(color);
        head.setStrokeWidth(1.5);
        Arc shoulders = new Arc(0, r * 0.55, r * 0.62, r * 0.5, 20, 140);
        shoulders.setType(ArcType.OPEN);
        shoulders.setFill(Color.TRANSPARENT);
        shoulders.setStroke(color);
        shoulders.setStrokeWidth(1.5);
        Group g = new Group(outer, head, shoulders);
        return box(g, size);
    }

    public static Node shieldCheck(double size, Paint color) {
        return strokePath("M12 2 L20 5 V11 C20 16 17 20 12 22 C7 20 4 16 4 11 V5 Z M8.5 12 L11 14.5 L15.5 9.5",
                size, color, 1.7);
    }

    public static Node trophy(double size, Paint color) {
        return strokePath(
                "M7 4 H17 V9 C17 12 15 14 12 14 C9 14 7 12 7 9 Z "
                        + "M7 5 H3 V7 C3 9 5 10 7 10 M17 5 H21 V7 C21 9 19 10 17 10 "
                        + "M12 14 V18 M8 21 H16 M9 18 H15",
                size, color, 1.6);
    }

    public static Node wifi(double size, Paint color) {
        SVGPath path = new SVGPath();
        path.setContent("M2 8.5 C7 3.8 17 3.8 22 8.5 M5.5 12.3 C9 9 15 9 18.5 12.3 M9 16 C10.9 14.2 13.1 14.2 15 16");
        path.setFill(Color.TRANSPARENT);
        path.setStroke(color);
        path.setStrokeWidth(1.8);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        double scale = size / 24.0;
        path.setScaleX(scale);
        path.setScaleY(scale);
        Circle dot = new Circle(1.3);
        dot.setFill(color);
        dot.setTranslateX(0);
        dot.setTranslateY(19.0 * scale - 12 * scale);
        Group g = new Group(path, dot);
        return box(g, size);
    }

    public static Node minus(double size, Paint color) {
        return strokePath("M5 12 H19", size, color, 1.6);
    }

    public static Node square(double size, Paint color) {
        return strokePath("M6 6 H18 V18 H6 Z", size, color, 1.5);
    }

    public static Node close(double size, Paint color) {
        return strokePath("M6 6 L18 18 M18 6 L6 18", size, color, 1.7);
    }

    public static Node check(double size, Paint color) {
        return strokePath("M5 13 L10 18 L19 6", size, color, 2.2);
    }

    public static Node chevronRight(double size, Paint color) {
        return strokePath("M9 5 L16 12 L9 19", size, color, 1.8);
    }

    public static Node arrowUp(double size, Paint color) {
        return strokePath("M12 20 V5 M6 11 L12 5 L18 11", size, color, 1.9);
    }

    public static Node arrowDown(double size, Paint color) {
        return strokePath("M12 4 V19 M6 13 L12 19 L18 13", size, color, 1.9);
    }

    public static Node cpu(double size, Paint color) {
        return strokePath(
                "M8 8 H16 V16 H8 Z M10 3 V8 M14 3 V8 M10 16 V21 M14 16 V21 "
                        + "M3 10 H8 M3 14 H8 M16 10 H21 M16 14 H21",
                size, color, 1.6);
    }

    public static Node memory(double size, Paint color) {
        return strokePath(
                "M4 8 H20 V17 H4 Z M7 8 V5 M11 8 V5 M15 8 V5 M17 8 V5",
                size, color, 1.6);
    }
}
