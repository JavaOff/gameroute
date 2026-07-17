package com.gameroute.optimizer;

import com.gameroute.model.OptimizationActionResult;
import com.gameroute.network.DnsService;
import com.gameroute.network.QosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and runs the "Optimize for League of Legends" one-click sequence,
 * and exposes the individual {@link OptimizationAction}s for the Optimizer
 * tab's per-action buttons. Every action is only ever executed after the UI
 * has obtained explicit user confirmation.
 */
public class OptimizerService {

    private static final Logger log = LoggerFactory.getLogger(OptimizerService.class);
    private static final String LEAGUE_EXE = "League of Legends.exe";

    private final DnsService dnsService;
    private final QosService qosService;

    public OptimizerService(DnsService dnsService, QosService qosService) {
        this.dnsService = dnsService;
        this.qosService = qosService;
    }

    /**
     * The ordered set of actions the "Optimize for League of Legends" button
     * runs -- six steps, each independently visible in the Optimizer tab's
     * checklist. QoS and TCP tuning require an elevated process; if GameRoute
     * isn't running as Administrator those two simply report a clear failure
     * rather than being skipped silently.
     */
    public List<OptimizationAction> oneClickSequence(String adapterName) {
        List<OptimizationAction> actions = new ArrayList<>();
        actions.add(new ProcessPriorityAction());
        actions.add(new DnsOptimizeAction(dnsService, adapterName));
        actions.add(new DnsFlushAction(dnsService));
        actions.add(new BackgroundAppsAdvisoryAction());
        actions.add(new QosPolicyAction(qosService, LEAGUE_EXE));
        actions.add(new TcpTuningAction(qosService, "normal"));
        return actions;
    }

    public List<OptimizationAction> individualActions(String adapterName) {
        List<OptimizationAction> actions = new ArrayList<>(oneClickSequence(adapterName));
        actions.add(new CpuAffinityAction(-1));
        actions.add(new NetworkAdapterRenewAction(adapterName));
        actions.add(new DnsResetAction(dnsService, adapterName));
        actions.add(new QosRemoveAction(qosService));
        return actions;
    }

    /**
     * Undoes what {@link #oneClickSequence} changes: restores normal process
     * priority, reverts DNS to automatic, and removes the QoS policy. The
     * background-app scan and DNS flush have nothing persistent to undo, and
     * "normal" TCP auto-tuning is already Windows' own default, so neither is
     * repeated here.
     */
    public List<OptimizationAction> revertSequence(String adapterName) {
        List<OptimizationAction> actions = new ArrayList<>();
        actions.add(new ProcessPriorityAction("Normal"));
        actions.add(new DnsResetAction(dnsService, adapterName));
        actions.add(new QosRemoveAction(qosService));
        return actions;
    }

    public List<OptimizationActionResult> runAll(List<OptimizationAction> actions) {
        List<OptimizationActionResult> results = new ArrayList<>();
        for (OptimizationAction action : actions) {
            log.info("Running optimization action: {}", action.getName());
            try {
                results.add(action.execute());
            } catch (Exception e) {
                log.error("Optimization action '{}' threw an exception", action.getName(), e);
                results.add(OptimizationActionResult.failure(action.getName(), "Unexpected error: " + e.getMessage()));
            }
        }
        return results;
    }
}
