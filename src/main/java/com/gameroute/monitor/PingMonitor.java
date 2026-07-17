package com.gameroute.monitor;

import com.gameroute.config.Constants;
import com.gameroute.model.PingSample;
import com.gameroute.model.PingStats;
import com.gameroute.network.PingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Pings a target host once per second and maintains a rolling window used to
 * derive average/min/max/jitter/packet-loss for the dashboard and statistics
 * views. Thread-safe for the single-writer/single-scheduler usage pattern
 * GameRoute follows (one monitor instance drives the UI updates).
 */
public class PingMonitor {

    private static final Logger log = LoggerFactory.getLogger(PingMonitor.class);

    private final PingService pingService;
    private final Deque<PingSample> history = new ArrayDeque<>();
    private final AtomicReference<String> target = new AtomicReference<>();
    private final AtomicReference<String> sourceAdapterIp = new AtomicReference<>();

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "ping-monitor"));

    public PingMonitor(PingService pingService) {
        this.pingService = pingService;
    }

    public void setTarget(String host) {
        target.set(host);
        synchronized (history) {
            history.clear();
        }
    }

    public void setSourceAdapterIp(String ip) {
        sourceAdapterIp.set(ip);
    }

    public void start(BiConsumer<PingSample, PingStats> listener) {
        executor.scheduleAtFixedRate(() -> {
            String host = target.get();
            if (host == null || host.isBlank()) {
                return;
            }
            try {
                PingSample sample = pingService.ping(host, Constants.PING_TIMEOUT_MS, sourceAdapterIp.get());
                PingStats stats;
                synchronized (history) {
                    history.addLast(sample);
                    while (history.size() > Constants.PING_HISTORY_WINDOW) {
                        history.removeFirst();
                    }
                    stats = computeStats(sample, history);
                }
                listener.accept(sample, stats);
            } catch (Exception e) {
                log.error("Ping monitoring iteration failed", e);
            }
        }, 0, Constants.PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private PingStats computeStats(PingSample current, Deque<PingSample> window) {
        List<PingSample> snapshot = List.copyOf(window);
        long successCount = snapshot.stream().filter(PingSample::success).count();
        double avg = snapshot.stream().filter(PingSample::success).mapToDouble(PingSample::rttMillis).average().orElse(0);
        double min = snapshot.stream().filter(PingSample::success).mapToDouble(PingSample::rttMillis).min().orElse(0);
        double max = snapshot.stream().filter(PingSample::success).mapToDouble(PingSample::rttMillis).max().orElse(0);
        double jitter = computeJitter(snapshot);
        double lossPercent = snapshot.isEmpty() ? 0 : 100.0 * (snapshot.size() - successCount) / snapshot.size();
        return new PingStats(current.success() ? current.rttMillis() : -1, avg, min, max, jitter, lossPercent, snapshot.size());
    }

    /** Mean absolute deviation between consecutive successful samples — a standard, simple jitter estimate. */
    private double computeJitter(List<PingSample> samples) {
        double sum = 0;
        int count = 0;
        Double previous = null;
        for (PingSample sample : samples) {
            if (!sample.success()) {
                continue;
            }
            if (previous != null) {
                sum += Math.abs(sample.rttMillis() - previous);
                count++;
            }
            previous = sample.rttMillis();
        }
        return count == 0 ? 0 : sum / count;
    }

    public void stop() {
        executor.shutdownNow();
    }
}
