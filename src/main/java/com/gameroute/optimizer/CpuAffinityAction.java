package com.gameroute.optimizer;

import com.gameroute.model.OptimizationActionResult;
import com.gameroute.utils.CommandRunner;
import com.gameroute.utils.ProcessUtils;

import java.util.Optional;

/**
 * Restricts (or restores) which logical CPU cores the League of Legends
 * process may run on. Useful on CPUs with mixed core types (e.g. Intel
 * P/E-cores) where pinning to performance cores reduces frame-time spikes.
 */
public class CpuAffinityAction implements OptimizationAction {

    private final long affinityMask;

    /**
     * @param affinityMask bitmask of allowed logical cores (bit N = core N); use
     *                      {@code -1} to restore the default (all cores).
     */
    public CpuAffinityAction(long affinityMask) {
        this.affinityMask = affinityMask;
    }

    @Override
    public String getName() {
        return affinityMask == -1 ? "Restore default CPU affinity" : "Set CPU affinity";
    }

    @Override
    public String getDescription() {
        return "Pins the game process to a chosen set of logical CPU cores.";
    }

    @Override
    public boolean requiresAdmin() {
        return false;
    }

    @Override
    public String getWarning() {
        return "Restricting a game to too few cores can reduce performance instead of improving it. "
                + "This setting is not persisted by Windows and resets the next time the game restarts.";
    }

    @Override
    public OptimizationActionResult execute() {
        Optional<ProcessHandle> handle = ProcessUtils.findLeagueProcess();
        if (handle.isEmpty()) {
            return OptimizationActionResult.failure(getName(), "League of Legends is not currently running.");
        }
        long pid = handle.get().pid();
        // "Restore defaults" computes a full mask from the machine's logical core count.
        String script = affinityMask == -1
                ? String.format(
                        "$p = Get-Process -Id %d -ErrorAction Stop; "
                                + "$full = [Math]::Pow(2, [Environment]::ProcessorCount) - 1; "
                                + "$p.ProcessorAffinity = [IntPtr]::new([long]$full)",
                        pid)
                : String.format(
                        "(Get-Process -Id %d -ErrorAction Stop).ProcessorAffinity = [IntPtr]::new(%d)",
                        pid, affinityMask);
        CommandRunner.Result result = CommandRunner.runPowerShell(script, 10);
        return result.success()
                ? OptimizationActionResult.ok(getName(), "Affinity mask 0x" + Long.toHexString(affinityMask) + " applied to PID " + pid + ".")
                : OptimizationActionResult.failure(getName(), "PowerShell reported: " + result.output().trim());
    }
}
