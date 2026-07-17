package com.gameroute.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * UI-bindable row combining a Riot {@link Region} with its most recently
 * measured latency, for display in the Servers tab.
 */
public class ServerInfo {

    private final Region region;
    private final SimpleStringProperty status = new SimpleStringProperty("Not tested");
    private final SimpleDoubleProperty latencyMs = new SimpleDoubleProperty(-1);

    public ServerInfo(Region region) {
        this.region = region;
    }

    public Region getRegion() {
        return region;
    }

    public String getCode() {
        return region.getCode();
    }

    public String getDisplayName() {
        return region.getDisplayName();
    }

    public String getHost() {
        return region.getHost();
    }

    public double getLatencyMs() {
        return latencyMs.get();
    }

    public void setLatencyMs(double value) {
        latencyMs.set(value);
        status.set(value < 0 ? "Unreachable" : "Reachable");
    }

    public SimpleDoubleProperty latencyMsProperty() {
        return latencyMs;
    }

    public String getStatus() {
        return status.get();
    }

    public SimpleStringProperty statusProperty() {
        return status;
    }
}
