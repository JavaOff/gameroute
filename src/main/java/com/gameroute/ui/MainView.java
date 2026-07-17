package com.gameroute.ui;

import com.gameroute.ui.components.Animations;
import com.gameroute.ui.icons.Icons;
import com.gameroute.ui.tabs.DashboardView;
import com.gameroute.ui.tabs.LogsView;
import com.gameroute.ui.tabs.OptimizerView;
import com.gameroute.ui.tabs.ServersView;
import com.gameroute.ui.tabs.SettingsView;
import com.gameroute.ui.tabs.StatisticsView;
import com.gameroute.ui.tabs.TracerouteView;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application shell: custom title bar on top, navigation rail on the left,
 * the active page filling the center (crossfaded on switch), and a status
 * strip on the bottom. This is the node given the rounded-corner/shadow
 * treatment in {@link com.gameroute.Main}; everything about window chrome
 * (drag, resize, min/max/close) lives in {@link TitleBar} / {@link WindowResizer}.
 */
public class MainView extends BorderPane {

    private final Map<String, Node> pages = new LinkedHashMap<>();
    private final StackPane content = new StackPane();
    private final TitleBar titleBar;
    private final StatusBar statusBar;

    public MainView(AppServices services, Stage stage) {
        getStyleClass().add("app-shell");

        pages.put("dashboard", new DashboardView(services));
        pages.put("optimizer", new OptimizerView(services));
        pages.put("servers", new ServersView(services));
        pages.put("traceroute", new TracerouteView(services));
        pages.put("statistics", new StatisticsView(services));
        pages.put("settings", new SettingsView(services, stage));
        pages.put("logs", new LogsView());

        List<Sidebar.NavEntry> entries = List.of(
                new Sidebar.NavEntry("dashboard", "Dashboard", Icons::home),
                new Sidebar.NavEntry("optimizer", "Optimizer", Icons::zap),
                new Sidebar.NavEntry("servers", "Servers", Icons::globe),
                new Sidebar.NavEntry("traceroute", "Traceroute", Icons::satelliteDish),
                new Sidebar.NavEntry("statistics", "Statistics", Icons::barChart),
                new Sidebar.NavEntry("settings", "Settings", Icons::gear),
                new Sidebar.NavEntry("logs", "Logs", Icons::fileText)
        );

        Sidebar sidebar = new Sidebar(entries, this::selectPage, () -> selectPage("optimizer"));
        titleBar = new TitleBar(stage, () -> selectPage("settings"));
        statusBar = new StatusBar();

        setTop(titleBar);
        setLeft(sidebar);
        setCenter(content);
        setBottom(statusBar);

        selectPage("dashboard");
    }

    private void selectPage(String id) {
        Node page = pages.get(id);
        if (page != null) {
            Animations.crossFadePage(content, page);
        }
    }

    /** Toggles the rounded-corner/shadow "floating card" look off while maximized (a full-screen window shouldn't look like it has margins). */
    public void applyMaximizedStyle(boolean maximized) {
        setStyleState(this, "app-shell-square", maximized);
        setStyleState(titleBar, "title-bar-square", maximized);
        setStyleState(statusBar, "status-bar-square", maximized);
    }

    private void setStyleState(Node node, String styleClass, boolean present) {
        if (present) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }
}
