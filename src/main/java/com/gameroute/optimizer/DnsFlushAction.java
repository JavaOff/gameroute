package com.gameroute.optimizer;

import com.gameroute.model.OptimizationActionResult;
import com.gameroute.network.DnsService;
import com.gameroute.utils.CommandRunner;

public class DnsFlushAction implements OptimizationAction {

    private final DnsService dnsService;

    public DnsFlushAction(DnsService dnsService) {
        this.dnsService = dnsService;
    }

    @Override
    public String getName() {
        return "Flush DNS cache";
    }

    @Override
    public String getDescription() {
        return "Clears cached DNS lookups (ipconfig /flushdns) so stale, slow-resolving records don't linger.";
    }

    @Override
    public boolean requiresAdmin() {
        return false;
    }

    @Override
    public String getWarning() {
        return "The very next DNS lookups after flushing will take slightly longer while the cache repopulates.";
    }

    @Override
    public OptimizationActionResult execute() {
        CommandRunner.Result result = dnsService.flushDnsCache();
        return result.success()
                ? OptimizationActionResult.ok(getName(), "DNS resolver cache cleared.")
                : OptimizationActionResult.failure(getName(), "ipconfig reported: " + result.output().trim());
    }
}
