package com.gameroute.ui;

import com.gameroute.config.Constants;
import com.gameroute.ui.icons.Icons;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.stage.Stage;

/**
 * Custom borderless-window title bar: brand mark, a draggable middle zone
 * (also toggles maximize on double-click, the standard OS convention), and
 * the profile / notifications / settings icon buttons plus the three window
 * controls on the right.
 */
public class TitleBar extends HBox {

    private double dragOffsetX;
    private double dragOffsetY;

    public TitleBar(Stage stage, Runnable onSettingsClick) {
        getStyleClass().add("title-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);

        HBox brand = buildBrand();

        HBox dragZone = new HBox(brand);
        dragZone.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(dragZone, Priority.ALWAYS);
        installDrag(dragZone, stage);

        Button profileBtn = iconButton(Icons.userCircle(18, Color.web("#F5F5F7")));
        profileBtn.setOnAction(e -> showPopup(profileBtn,
                Constants.APP_NAME + " " + Constants.APP_VERSION, "Unofficial companion app for League of Legends."));

        Button bellBtn = iconButton(withNotificationDot(Icons.bell(18, Color.web("#F5F5F7"))));
        bellBtn.setOnAction(e -> showPopup(bellBtn, "Notifications", "You're all caught up -- no alerts right now."));

        Button settingsBtn = iconButton(Icons.gear(18, Color.web("#F5F5F7")));
        settingsBtn.setOnAction(e -> onSettingsClick.run());

        HBox windowControls = buildWindowControls(stage);

        getChildren().addAll(dragZone, profileBtn, bellBtn, settingsBtn, spacer(14), windowControls);
    }

    private HBox buildBrand() {
        Label mark = new Label("●");
        mark.getStyleClass().add("title-logo-mark");
        mark.setStyle("-fx-font-size: 10px;");
        Label name = new Label("GameRoute");
        name.getStyleClass().add("title-logo");
        HBox brand = new HBox(8, mark, name);
        brand.setAlignment(Pos.CENTER_LEFT);
        return brand;
    }

    private Region withNotificationDot(javafx.scene.Node icon) {
        Circle dot = new Circle(3);
        dot.getStyleClass().add("notif-dot");
        StackPane stack = new StackPane(icon, dot);
        StackPane.setAlignment(dot, Pos.TOP_RIGHT);
        dot.setTranslateX(-2);
        dot.setTranslateY(2);
        return stack;
    }

    private Button iconButton(javafx.scene.Node icon) {
        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().add("window-btn");
        return button;
    }

    private void showPopup(Region anchor, String title, String body) {
        Popup popup = new Popup();
        popup.setAutoHide(true);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: 800; -fx-font-size: 13px; -fx-text-fill: -fx-text-primary;");
        Label bodyLabel = new Label(body);
        bodyLabel.getStyleClass().add("card-subtitle");
        bodyLabel.setWrapText(true);
        bodyLabel.setMaxWidth(220);

        VBox content = new VBox(6, titleLabel, bodyLabel);
        content.getStyleClass().add("glass-card");
        content.setPadding(new Insets(14));
        content.setMaxWidth(240);
        content.getStylesheets().add(getClass().getResource("/css/dark.css").toExternalForm());

        popup.getContent().add(content);
        var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor.getScene().getWindow(), bounds.getMinX() - 90, bounds.getMaxY() + 8);
    }

    private HBox buildWindowControls(Stage stage) {
        Button minimize = iconButton(Icons.minus(14, Color.web("#9EA3AE")));
        minimize.setOnAction(e -> stage.setIconified(true));

        Button maximize = iconButton(Icons.square(13, Color.web("#9EA3AE")));
        maximize.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));

        Button close = iconButton(Icons.close(14, Color.web("#9EA3AE")));
        close.getStyleClass().add("window-btn-close");
        close.setOnAction(e -> stage.hide());

        HBox box = new HBox(4, minimize, maximize, close);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private Region spacer(double width) {
        Region region = new Region();
        region.setMinWidth(width);
        region.setMaxWidth(width);
        return region;
    }

    private void installDrag(Region dragZone, Stage stage) {
        dragZone.setOnMousePressed(e -> {
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
            e.consume();
        });
        dragZone.setOnMouseDragged(e -> {
            if (stage.isMaximized()) {
                return;
            }
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
            e.consume();
        });
        dragZone.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                stage.setMaximized(!stage.isMaximized());
            }
        });
    }
}
