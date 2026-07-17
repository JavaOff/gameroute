package com.gameroute.ui.components;

import javafx.scene.control.Alert;

/**
 * Applies GameRoute's dark stylesheet to an {@link Alert}. Alerts open in
 * their own separate Stage/Scene with JavaFX's default light theme
 * underneath, so without this every dialog renders with light text on a
 * light background regardless of the main window's theme -- easy to forget
 * on any single call site, which is why every {@code new Alert(...)} in the
 * app should be passed through here instead of styling itself.
 */
public final class Dialogs {

    private Dialogs() {
    }

    public static Alert themed(Alert alert) {
        alert.getDialogPane().getStylesheets().add(
                Dialogs.class.getResource("/css/dark.css").toExternalForm());
        return alert;
    }
}
