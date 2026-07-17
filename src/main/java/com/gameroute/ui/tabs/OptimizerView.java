package com.gameroute.ui.tabs;

import com.gameroute.model.OptimizationActionResult;
import com.gameroute.network.NetworkInterfaceService;
import com.gameroute.optimizer.OptimizationAction;
import com.gameroute.ui.AppServices;
import com.gameroute.ui.components.Animations;
import com.gameroute.ui.components.ChecklistItem;
import com.gameroute.ui.components.Dialogs;
import com.gameroute.ui.icons.Icons;
import com.gameroute.utils.OsUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The Optimizer tab: a one-click "Optimize for League of Legends" checklist
 * plus every individual action broken out for manual control. No action ever
 * runs without an explicit confirmation dialog naming exactly what it will
 * change on the system.
 */
public class OptimizerView extends ScrollPane {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "optimizer-actions"));

    private final ListView<String> resultsLog = new ListView<>();
    private final ComboBox<String> adapterBox = new ComboBox<>();
    private final List<ChecklistItem> checklistItems = new ArrayList<>();
    private final List<ChecklistItem> revertChecklistItems = new ArrayList<>();

    public OptimizerView(AppServices services) {
        setFitToWidth(true);
        VBox root = new VBox(18);
        root.setPadding(new Insets(24));

        List<NetworkInterfaceService.AdapterInfo> adapters = services.networkInterfaceService().listAdapters();
        adapters.forEach(a -> adapterBox.getItems().add(a.name()));
        if (!adapterBox.getItems().isEmpty()) {
            String preferred = services.config().getPreferredAdapter();
            adapterBox.setValue(adapterBox.getItems().contains(preferred) ? preferred : adapterBox.getItems().get(0));
        }
        adapterBox.setOnAction(e -> services.config().setPreferredAdapter(adapterBox.getValue()));

        VBox oneClickCard = buildOneClickCard(services);
        VBox disableCard = buildDisableCard(services);
        VBox adapterCard = buildAdapterSelector();
        VBox individualCard = buildIndividualActionsCard(services);
        VBox resultsCard = buildResultsCard();

        root.getChildren().addAll(oneClickCard, disableCard, adapterCard, individualCard, resultsCard);
        setContent(root);

        Animations.staggeredEntrance(List.of(oneClickCard, disableCard, adapterCard, individualCard, resultsCard), 70);
    }

    private VBox buildOneClickCard(AppServices services) {
        Label title = new Label("OPTIMIZE FOR LEAGUE OF LEGENDS");
        title.getStyleClass().add("card-title");

        Label description = new Label(
                "Raises the game's process priority, switches to the fastest DNS resolver, flushes the DNS cache, "
                        + "scans for bandwidth-heavy background apps, tags game traffic for QoS priority and "
                        + "normalizes TCP auto-tuning. Every step below is logged, in order.");
        description.setWrapText(true);
        description.getStyleClass().add("card-subtitle");

        VBox checklist = new VBox(2);
        for (OptimizationAction action : services.optimizerService().oneClickSequence(adapterOrPlaceholder())) {
            ChecklistItem item = new ChecklistItem(displayName(action.getName()), action.requiresAdmin());
            checklistItems.add(item);
            checklist.getChildren().add(item);
        }

        Button runAll = new Button("OPTIMIZE NOW");
        runAll.getStyleClass().add("btn-glow-red");
        runAll.setMaxWidth(Double.MAX_VALUE);
        runAll.setGraphic(Icons.zap(16, Color.WHITE));
        Animations.hoverScale(runAll, 1.02);
        runAll.setOnAction(e -> confirmAndRun(runAll, "Optimize for League of Legends",
                "This will: raise the game's process priority, switch adapter '" + adapterOrPlaceholder()
                        + "' to the fastest available DNS server, flush the DNS cache, scan (read-only) for "
                        + "background apps, create a QoS traffic-priority policy and normalize TCP auto-tuning.",
                () -> services.optimizerService().oneClickSequence(adapterOrPlaceholder()), services, checklistItems));

        VBox card = new VBox(14, title, description, checklist, runAll);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    /** Maps action names to the shorter marketing-style labels used in the checklist. */
    private String displayName(String actionName) {
        String lower = actionName.toLowerCase();
        if (lower.contains("qos")) return lower.contains("remove") ? "Remove QoS Priority" : "QoS Traffic Priority";
        if (lower.contains("normal") && lower.contains("priority")) return "Restore Normal Priority";
        if (lower.contains("priority")) return "High Process Priority";
        if (lower.contains("fastest dns")) return "Best DNS Resolver";
        if (lower.contains("reset dns") || lower.contains("automatic")) return "Reset DNS to Automatic";
        if (lower.contains("flush")) return "Flush DNS Cache";
        if (lower.contains("background")) return "Background App Scan";
        if (lower.contains("tcp")) return "TCP Auto-Tuning";
        return actionName;
    }

    private VBox buildDisableCard(AppServices services) {
        Label title = new Label("DISABLE OPTIMIZATIONS");
        title.getStyleClass().add("card-title");

        Label description = new Label(
                "Undoes what \"Optimize Now\" changed: restores normal process priority, reverts DNS to automatic, "
                        + "and removes the QoS traffic-priority policy. Safe to run even if you never ran Optimize Now.");
        description.setWrapText(true);
        description.getStyleClass().add("card-subtitle");

        VBox checklist = new VBox(2);
        for (OptimizationAction action : services.optimizerService().revertSequence(adapterOrPlaceholder())) {
            ChecklistItem item = new ChecklistItem(displayName(action.getName()), action.requiresAdmin());
            revertChecklistItems.add(item);
            checklist.getChildren().add(item);
        }

        Button disableAll = new Button("DISABLE OPTIMIZATIONS");
        disableAll.getStyleClass().add("button-ghost");
        disableAll.setMaxWidth(Double.MAX_VALUE);
        Animations.hoverScale(disableAll, 1.02);
        disableAll.setOnAction(e -> confirmAndRun(disableAll, "Disable Optimizations",
                "This will: set the game's process priority back to Normal, reset adapter '" + adapterOrPlaceholder()
                        + "' to automatic (DHCP) DNS, and remove the QoS traffic-priority policy.",
                () -> services.optimizerService().revertSequence(adapterOrPlaceholder()), services, revertChecklistItems));

        VBox card = new VBox(14, title, description, checklist, disableAll);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    private VBox buildAdapterSelector() {
        Label title = new Label("NETWORK ADAPTER");
        title.getStyleClass().add("card-title");
        Label label = new Label("Used for DNS / renew actions:");
        label.getStyleClass().add("card-subtitle");
        HBox row = new HBox(10, label, adapterBox);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(10, title, row);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    private VBox buildIndividualActionsCard(AppServices services) {
        Label title = new Label("INDIVIDUAL ACTIONS");
        title.getStyleClass().add("card-title");

        VBox list = new VBox(10);
        for (OptimizationAction action : services.optimizerService().individualActions(adapterOrPlaceholder())) {
            list.getChildren().add(buildActionRow(action, services));
        }

        VBox card = new VBox(12, title, list);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    private HBox buildActionRow(OptimizationAction action, AppServices services) {
        VBox text = new VBox(2);
        Label name = new Label(action.getName());
        name.setStyle("-fx-font-weight: 700;");
        Label desc = new Label(action.getDescription() + (action.requiresAdmin() ? "  (requires Administrator)" : ""));
        desc.getStyleClass().add("card-subtitle");
        desc.setWrapText(true);
        text.getChildren().addAll(name, desc);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button run = new Button("Run");
        run.getStyleClass().add("button-ghost");
        run.setOnAction(e -> confirmAndRun(run, action.getName(), action.getWarning(),
                () -> List.of(action), services, List.of()));

        HBox row = new HBox(14, text, spacer, run);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox buildResultsCard() {
        Label title = new Label("ACTION LOG");
        title.getStyleClass().add("card-title");
        resultsLog.setPrefHeight(220);
        VBox card = new VBox(10, title, resultsLog);
        card.getStyleClass().addAll("glass-card", "glass-card-hover");
        return card;
    }

    private String adapterOrPlaceholder() {
        return adapterBox.getValue() != null ? adapterBox.getValue() : "";
    }

    private void confirmAndRun(Button trigger, String title, String warning,
                                java.util.function.Supplier<List<OptimizationAction>> actionsSupplier,
                                AppServices services, List<ChecklistItem> itemsToAnimate) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm: " + title);
        alert.setHeaderText(title);
        String adminNote = !OsUtils.isRunningAsAdmin()
                ? "\n\nNote: GameRoute is not running as Administrator. Actions that require elevation will fail until you restart it as admin."
                : "";
        alert.setContentText(warning + adminNote);
        alert.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        Dialogs.themed(alert);

        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            return;
        }

        trigger.setDisable(true);
        itemsToAnimate.forEach(ChecklistItem::reset);
        List<OptimizationAction> actions = actionsSupplier.get();
        runSequentially(actions, itemsToAnimate, 0, services, trigger);
    }

    /** Runs actions one at a time so the checklist can visibly step through Running -> Done/Failed per item. */
    private void runSequentially(List<OptimizationAction> actions, List<ChecklistItem> items, int index,
                                  AppServices services, Button trigger) {
        if (index >= actions.size()) {
            trigger.setDisable(false);
            return;
        }
        if (index < items.size()) {
            items.get(index).setRunning();
        }
        CompletableFuture
                .supplyAsync(() -> services.optimizerService().runAll(List.of(actions.get(index))), executor)
                .thenAccept(results -> Platform.runLater(() -> {
                    logResults(results);
                    if (index < items.size() && !results.isEmpty()) {
                        items.get(index).setDone(results.get(0).success());
                    }
                    runSequentially(actions, items, index + 1, services, trigger);
                }));
    }

    private void logResults(List<OptimizationActionResult> results) {
        for (OptimizationActionResult result : results) {
            String time = TIME_FMT.format(result.timestamp().atZone(java.time.ZoneId.systemDefault()));
            String status = result.success() ? "OK" : "FAILED";
            resultsLog.getItems().add(0, String.format("[%s] %s - %s: %s", time, status, result.actionName(), result.message()));
        }
    }
}
