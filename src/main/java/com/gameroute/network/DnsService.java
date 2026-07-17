package com.gameroute.network;

import com.gameroute.utils.CommandRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Measures latency to a small set of well-known public DNS resolvers and can
 * (only when explicitly asked to, after user confirmation in the UI) point a
 * network adapter at the fastest one via {@code netsh}.
 * <p>
 * GameRoute never changes DNS settings on its own initiative.
 */
public class DnsService {

    private static final Logger log = LoggerFactory.getLogger(DnsService.class);

    /** Candidate resolvers, in no particular order; latency decides the winner. */
    public static final Map<String, String> CANDIDATE_RESOLVERS = new LinkedHashMap<>();

    static {
        CANDIDATE_RESOLVERS.put("Cloudflare", "1.1.1.1");
        CANDIDATE_RESOLVERS.put("Google", "8.8.8.8");
        CANDIDATE_RESOLVERS.put("Quad9", "9.9.9.9");
        CANDIDATE_RESOLVERS.put("OpenDNS", "208.67.222.222");
    }

    private final PingService pingService;

    public DnsService(PingService pingService) {
        this.pingService = pingService;
    }

    /** Pings every candidate resolver once each and returns name -> RTT ms (-1 if unreachable). */
    public Map<String, Double> measureAll() {
        Map<String, Double> results = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : CANDIDATE_RESOLVERS.entrySet()) {
            var sample = pingService.ping(entry.getValue());
            results.put(entry.getKey(), sample.success() ? sample.rttMillis() : -1);
        }
        return results;
    }

    public String bestResolverIp(Map<String, Double> measurements) {
        return measurements.entrySet().stream()
                .filter(e -> e.getValue() >= 0)
                .min(Map.Entry.comparingByValue())
                .map(e -> CANDIDATE_RESOLVERS.get(e.getKey()))
                .orElse(null);
    }

    public CommandRunner.Result flushDnsCache() {
        log.info("Flushing DNS resolver cache");
        return CommandRunner.runCmd("ipconfig /flushdns", 15);
    }

    /**
     * Sets the given adapter's primary DNS server. Requires administrator
     * privileges; the caller is responsible for obtaining user confirmation
     * before invoking this.
     */
    public CommandRunner.Result setAdapterDns(String adapterName, String dnsIp) {
        log.info("Setting DNS for adapter '{}' to {}", adapterName, dnsIp);
        String cmd = String.format("netsh interface ip set dns name=\"%s\" static %s primary", adapterName, dnsIp);
        return CommandRunner.runCmd(cmd, 15);
    }

    public CommandRunner.Result resetAdapterDns(String adapterName) {
        log.info("Resetting adapter '{}' to DHCP-assigned DNS", adapterName);
        String cmd = String.format("netsh interface ip set dns name=\"%s\" dhcp", adapterName);
        return CommandRunner.runCmd(cmd, 15);
    }
}
