package com.gameroute.model;

import java.time.Instant;

/**
 * A single ping measurement taken at a point in time.
 * A failed/timed-out probe is represented with {@code success = false}
 * and {@code rttMillis = -1}.
 */
public record PingSample(Instant timestamp, double rttMillis, boolean success) {

    public static PingSample timeout(Instant timestamp) {
        return new PingSample(timestamp, -1, false);
    }

    public static PingSample of(Instant timestamp, double rttMillis) {
        return new PingSample(timestamp, rttMillis, true);
    }
}
