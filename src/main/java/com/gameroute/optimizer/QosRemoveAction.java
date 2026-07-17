package com.gameroute.optimizer;

import com.gameroute.model.OptimizationActionResult;
import com.gameroute.network.QosService;
import com.gameroute.utils.CommandRunner;

/** Removes the QoS policy {@link QosPolicyAction} creates, undoing that traffic-priority tag. */
public class QosRemoveAction implements OptimizationAction {

    private final QosService qosService;

    public QosRemoveAction(QosService qosService) {
        this.qosService = qosService;
    }

    @Override
    public String getName() {
        return "Remove QoS traffic priority";
    }

    @Override
    public String getDescription() {
        return "Deletes the GameRoute-LoL QoS policy, if one was created.";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public String getWarning() {
        return "Requires an elevated process. Safe to run even if no QoS policy was ever created.";
    }

    @Override
    public OptimizationActionResult execute() {
        CommandRunner.Result result = qosService.removeLeagueQosPolicy();
        return result.success()
                ? OptimizationActionResult.ok(getName(), "QoS policy removed (or was already absent).")
                : OptimizationActionResult.failure(getName(), "PowerShell reported: " + result.output().trim());
    }
}
