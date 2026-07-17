package com.gameroute.optimizer;

import com.gameroute.model.OptimizationActionResult;
import com.gameroute.utils.CommandRunner;

/**
 * Cycles a single network adapter (disable, then re-enable) to force it to
 * renegotiate its link and DHCP lease. Scoped to one adapter rather than
 * a global {@code ipconfig /release}+{@code /renew}, which would briefly
 * drop every network connection on the machine.
 */
public class NetworkAdapterRenewAction implements OptimizationAction {

    private final String adapterName;

    public NetworkAdapterRenewAction(String adapterName) {
        this.adapterName = adapterName;
    }

    @Override
    public String getName() {
        return "Renew network adapter";
    }

    @Override
    public String getDescription() {
        return "Disables and re-enables adapter '" + adapterName + "' to force a fresh DHCP lease and link renegotiation.";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public String getWarning() {
        return "This will briefly disconnect adapter '" + adapterName + "' (usually 2-5 seconds). "
                + "Any in-progress downloads or calls on that adapter will interrupt.";
    }

    @Override
    public OptimizationActionResult execute() {
        CommandRunner.Result disable = CommandRunner.runCmd(
                String.format("netsh interface set interface \"%s\" admin=disable", adapterName), 15);
        if (!disable.success()) {
            return OptimizationActionResult.failure(getName(), "Could not disable adapter: " + disable.output().trim());
        }
        CommandRunner.Result enable = CommandRunner.runCmd(
                String.format("netsh interface set interface \"%s\" admin=enable", adapterName), 15);
        return enable.success()
                ? OptimizationActionResult.ok(getName(), "Adapter '" + adapterName + "' cycled successfully.")
                : OptimizationActionResult.failure(getName(), "Adapter disabled but re-enable failed: "
                        + enable.output().trim() + " -- re-enable it manually via Network Connections.");
    }
}
