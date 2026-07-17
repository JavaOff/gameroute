package com.gameroute.optimizer;

import com.gameroute.model.OptimizationActionResult;
import com.gameroute.network.QosService;
import com.gameroute.utils.CommandRunner;

public class TcpTuningAction implements OptimizationAction {

    private final QosService qosService;
    private final String level;

    public TcpTuningAction(QosService qosService, String level) {
        this.qosService = qosService;
        this.level = level;
    }

    @Override
    public String getName() {
        return "Set TCP auto-tuning: " + level;
    }

    @Override
    public String getDescription() {
        return "Adjusts Windows' TCP receive-window auto-tuning level (netsh interface tcp set global autotuninglevel=" + level + ").";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public String getWarning() {
        return "Applies machine-wide, to every TCP connection, not just League of Legends. "
                + "'normal' is the Windows default; only choose 'restricted'/'highlyrestricted' if you suspect "
                + "a router on your path mishandles large receive windows.";
    }

    @Override
    public OptimizationActionResult execute() {
        CommandRunner.Result result = qosService.setTcpAutoTuning(level);
        return result.success()
                ? OptimizationActionResult.ok(getName(), "TCP autotuninglevel set to " + level + ".")
                : OptimizationActionResult.failure(getName(), "netsh reported: " + result.output().trim());
    }
}
