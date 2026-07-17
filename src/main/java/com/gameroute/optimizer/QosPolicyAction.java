package com.gameroute.optimizer;

import com.gameroute.model.OptimizationActionResult;
import com.gameroute.network.QosService;
import com.gameroute.utils.CommandRunner;

public class QosPolicyAction implements OptimizationAction {

    private final QosService qosService;
    private final String exeName;

    public QosPolicyAction(QosService qosService, String exeName) {
        this.qosService = qosService;
        this.exeName = exeName;
    }

    @Override
    public String getName() {
        return "Apply QoS traffic priority";
    }

    @Override
    public String getDescription() {
        return "Creates a Windows QoS policy that DSCP-tags traffic from '" + exeName
                + "' as low-latency (Expedited Forwarding).";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public String getWarning() {
        return "Requires an elevated process. Windows QoS only influences how your own machine and "
                + "QoS-aware routers on your local network prioritize this traffic — it has no effect on the "
                + "public internet path to Riot's servers.";
    }

    @Override
    public OptimizationActionResult execute() {
        CommandRunner.Result result = qosService.createLeagueQosPolicy(exeName);
        return result.success()
                ? OptimizationActionResult.ok(getName(), "QoS policy created for " + exeName + ".")
                : OptimizationActionResult.failure(getName(), "PowerShell reported: " + result.output().trim());
    }
}
