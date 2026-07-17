package com.gameroute.network;

import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

import java.util.List;
import java.util.Optional;

/**
 * Enumerates host network adapters (via OSHI) so the user can pick which one
 * GameRoute should prefer for ping/traceroute probes and DNS changes.
 */
public class NetworkInterfaceService {

    private final SystemInfo systemInfo = new SystemInfo();

    public record AdapterInfo(String name, String displayName, String ipv4, boolean up, long speedMbps) {
    }

    public List<AdapterInfo> listAdapters() {
        return systemInfo.getHardware().getNetworkIFs().stream()
                .filter(nif -> nif.getIPv4addr().length > 0)
                .map(nif -> new AdapterInfo(
                        nif.getName(),
                        nif.getDisplayName(),
                        nif.getIPv4addr().length > 0 ? nif.getIPv4addr()[0] : "",
                        nif.isKnownVmMacAddr() ? false : nif.getIfOperStatus() == NetworkIF.IfOperStatus.UP,
                        nif.getSpeed() / 1_000_000L))
                .toList();
    }

    public Optional<NetworkIF> findByName(String name) {
        return systemInfo.getHardware().getNetworkIFs().stream()
                .filter(nif -> nif.getName().equals(name))
                .findFirst();
    }

    /** Total bytes sent/received across all adapters, for throughput delta calculations. */
    public record ByteCounters(long bytesSent, long bytesReceived) {
    }

    public ByteCounters totalBytes() {
        long sent = 0;
        long received = 0;
        for (NetworkIF nif : systemInfo.getHardware().getNetworkIFs()) {
            nif.updateAttributes();
            sent += nif.getBytesSent();
            received += nif.getBytesRecv();
        }
        return new ByteCounters(sent, received);
    }
}
