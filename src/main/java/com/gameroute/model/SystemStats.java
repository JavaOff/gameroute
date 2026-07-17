package com.gameroute.model;

import java.time.Instant;

/**
 * Point-in-time snapshot of host system resource usage.
 */
public record SystemStats(Instant timestamp, double cpuLoadPercent, long usedRamMb, long totalRamMb,
                           double uploadKbps, double downloadKbps) {

    public double ramUsagePercent() {
        return totalRamMb == 0 ? 0 : (usedRamMb * 100.0) / totalRamMb;
    }
}
