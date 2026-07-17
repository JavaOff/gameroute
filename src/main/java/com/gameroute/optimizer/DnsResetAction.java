package com.gameroute.optimizer;

import com.gameroute.model.OptimizationActionResult;
import com.gameroute.network.DnsService;
import com.gameroute.utils.CommandRunner;

public class DnsResetAction implements OptimizationAction {

    private final DnsService dnsService;
    private final String adapterName;

    public DnsResetAction(DnsService dnsService, String adapterName) {
        this.dnsService = dnsService;
        this.adapterName = adapterName;
    }

    @Override
    public String getName() {
        return "Reset DNS to automatic";
    }

    @Override
    public String getDescription() {
        return "Restores adapter '" + adapterName + "' to DHCP-assigned DNS servers.";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public String getWarning() {
        return "Requires an elevated process. Any manually configured DNS server on this adapter will be discarded.";
    }

    @Override
    public OptimizationActionResult execute() {
        CommandRunner.Result result = dnsService.resetAdapterDns(adapterName);
        return result.success()
                ? OptimizationActionResult.ok(getName(), "Adapter '" + adapterName + "' reverted to DHCP DNS.")
                : OptimizationActionResult.failure(getName(), "netsh reported: " + result.output().trim());
    }
}
