package com.gameroute.optimizer;

import com.gameroute.model.OptimizationActionResult;
import com.gameroute.network.DnsService;
import com.gameroute.utils.CommandRunner;

import java.util.Map;

/**
 * Measures a handful of public DNS resolvers and switches the given adapter
 * to whichever answered fastest. Reverting to DHCP-assigned DNS is always
 * available via {@link DnsResetAction}.
 */
public class DnsOptimizeAction implements OptimizationAction {

    private final DnsService dnsService;
    private final String adapterName;

    public DnsOptimizeAction(DnsService dnsService, String adapterName) {
        this.dnsService = dnsService;
        this.adapterName = adapterName;
    }

    @Override
    public String getName() {
        return "Select fastest DNS resolver";
    }

    @Override
    public String getDescription() {
        return "Pings Cloudflare, Google, Quad9 and OpenDNS and sets adapter '" + adapterName
                + "' to use whichever responded fastest.";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public String getWarning() {
        return "Changes the DNS server for network adapter '" + adapterName + "'. Requires an elevated process. "
                + "Use 'Reset DNS to automatic' to revert.";
    }

    @Override
    public OptimizationActionResult execute() {
        Map<String, Double> measurements = dnsService.measureAll();
        String bestIp = dnsService.bestResolverIp(measurements);
        if (bestIp == null) {
            return OptimizationActionResult.failure(getName(), "None of the candidate DNS resolvers responded.");
        }
        CommandRunner.Result result = dnsService.setAdapterDns(adapterName, bestIp);
        String summary = measurements.entrySet().stream()
                .map(e -> e.getKey() + "=" + (e.getValue() < 0 ? "timeout" : e.getValue() + "ms"))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return result.success()
                ? OptimizationActionResult.ok(getName(), "Set DNS to " + bestIp + " (" + summary + ")")
                : OptimizationActionResult.failure(getName(), "netsh reported: " + result.output().trim()
                        + " (measurements: " + summary + ")");
    }
}
