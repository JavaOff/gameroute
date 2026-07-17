package com.gameroute.model;

/**
 * Rolling aggregate computed over the current ping history window.
 */
public record PingStats(double currentMs, double averageMs, double minMs, double maxMs,
                         double jitterMs, double packetLossPercent, int sampleCount) {

    public static PingStats empty() {
        return new PingStats(0, 0, 0, 0, 0, 0, 0);
    }
}
