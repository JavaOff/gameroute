package com.gameroute.model;

import java.time.LocalDate;

/**
 * Aggregated ping/jitter/packet-loss figures for a single calendar day,
 * as persisted by {@link com.gameroute.service.StatisticsService}.
 */
public record DailyStatistics(LocalDate date, double avgPingMs, double minPingMs, double maxPingMs,
                               double avgJitterMs, double packetLossPercent, int sampleCount) {
}
