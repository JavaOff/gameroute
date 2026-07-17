package com.gameroute.ui;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.stage.Stage;

/**
 * Adds edge/corner drag-to-resize to an undecorated {@link Stage}.
 * <p>
 * A {@code StageStyle.TRANSPARENT} window (used here so GameRoute can draw
 * its own rounded corners and shadow) gets none of the OS's native resize
 * handling, so this replaces it: any pixel within {@link #MARGIN} of the
 * root node's edge becomes a resize handle with the matching cursor.
 */
public final class WindowResizer {

    private static final double MARGIN = 6;
    private static final double MIN_WIDTH = 980;
    private static final double MIN_HEIGHT = 640;

    private double dragStartX, dragStartY, startWidth, startHeight, startStageX, startStageY;
    private boolean resizingLeft, resizingRight, resizingTop, resizingBottom;

    public static void attach(Node root, Stage stage) {
        new WindowResizer().install(root, stage);
    }

    private void install(Node root, Stage stage) {
        root.setOnMouseMoved(e -> {
            if (stage.isMaximized()) {
                root.setCursor(Cursor.DEFAULT);
                return;
            }
            root.setCursor(cursorFor(e.getX(), e.getY(), root.getBoundsInLocal().getWidth(), root.getBoundsInLocal().getHeight()));
        });

        root.setOnMousePressed(e -> {
            if (stage.isMaximized()) {
                return;
            }
            double w = root.getBoundsInLocal().getWidth();
            double h = root.getBoundsInLocal().getHeight();
            resizingLeft = e.getX() < MARGIN;
            resizingRight = e.getX() > w - MARGIN;
            resizingTop = e.getY() < MARGIN;
            resizingBottom = e.getY() > h - MARGIN;
            dragStartX = e.getScreenX();
            dragStartY = e.getScreenY();
            startWidth = stage.getWidth();
            startHeight = stage.getHeight();
            startStageX = stage.getX();
            startStageY = stage.getY();
        });

        root.setOnMouseDragged(e -> {
            if (stage.isMaximized() || !(resizingLeft || resizingRight || resizingTop || resizingBottom)) {
                return;
            }
            double dx = e.getScreenX() - dragStartX;
            double dy = e.getScreenY() - dragStartY;

            if (resizingRight) {
                stage.setWidth(Math.max(MIN_WIDTH, startWidth + dx));
            } else if (resizingLeft) {
                double newWidth = Math.max(MIN_WIDTH, startWidth - dx);
                stage.setX(startStageX + (startWidth - newWidth));
                stage.setWidth(newWidth);
            }

            if (resizingBottom) {
                stage.setHeight(Math.max(MIN_HEIGHT, startHeight + dy));
            } else if (resizingTop) {
                double newHeight = Math.max(MIN_HEIGHT, startHeight - dy);
                stage.setY(startStageY + (startHeight - newHeight));
                stage.setHeight(newHeight);
            }
        });

        root.setOnMouseReleased(e -> {
            resizingLeft = resizingRight = resizingTop = resizingBottom = false;
        });
    }

    private Cursor cursorFor(double x, double y, double w, double h) {
        boolean left = x < MARGIN;
        boolean right = x > w - MARGIN;
        boolean top = y < MARGIN;
        boolean bottom = y > h - MARGIN;

        if (left && top) return Cursor.NW_RESIZE;
        if (right && top) return Cursor.NE_RESIZE;
        if (left && bottom) return Cursor.SW_RESIZE;
        if (right && bottom) return Cursor.SE_RESIZE;
        if (left) return Cursor.W_RESIZE;
        if (right) return Cursor.E_RESIZE;
        if (top) return Cursor.N_RESIZE;
        if (bottom) return Cursor.S_RESIZE;
        return Cursor.DEFAULT;
    }
}
