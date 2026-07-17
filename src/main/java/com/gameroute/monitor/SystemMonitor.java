package com.gameroute.monitor;

import com.gameroute.config.Constants;
import com.gameroute.model.SystemStats;
import com.gameroute.network.NetworkInterfaceService;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Samples host CPU load, RAM usage and aggregate network throughput once
 * per second using OSHI, which reads OS performance counters directly
 * (no elevated privileges required).
 */
public class SystemMonitor {

    private static final Logger log = LoggerFactory.getLogger(SystemMonitor.class);

    private final SystemInfo systemInfo = new SystemInfo();
    private final CentralProcessor processor = systemInfo.getHardware().getProcessor();
    private final NetworkInterfaceService networkInterfaceService = new NetworkInterfaceService();

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "system-monitor"));

    private long[] previousTicks = processor.getSystemCpuLoadTicks();
    private NetworkInterfaceService.ByteCounters previousBytes = networkInterfaceService.totalBytes();
    private long previousSampleAt = System.nanoTime();

    public SystemStats sample() {
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(previousTicks) * 100.0;
        previousTicks = processor.getSystemCpuLoadTicks();

        GlobalMemory memory = systemInfo.getHardware().getMemory();
        long totalMb = memory.getTotal() / (1024 * 1024);
        long usedMb = (memory.getTotal() - memory.getAvailable()) / (1024 * 1024);

        NetworkInterfaceService.ByteCounters current = networkInterfaceService.totalBytes();
        long now = System.nanoTime();
        double elapsedSeconds = Math.max(0.001, (now - previousSampleAt) / 1_000_000_000.0);
        double uploadKbps = ((current.bytesSent() - previousBytes.bytesSent()) / 1024.0) / elapsedSeconds;
        double downloadKbps = ((current.bytesReceived() - previousBytes.bytesReceived()) / 1024.0) / elapsedSeconds;
        previousBytes = current;
        previousSampleAt = now;

        return new SystemStats(Instant.now(), Math.max(0, cpuLoad), Math.max(0, usedMb), totalMb,
                Math.max(0, uploadKbps), Math.max(0, downloadKbps));
    }

    public void start(Consumer<SystemStats> listener) {
        executor.scheduleAtFixedRate(() -> {
            try {
                listener.accept(sample());
            } catch (Exception e) {
                log.error("System sampling failed", e);
            }
        }, 1, Constants.SYSTEM_MONITOR_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }
}
