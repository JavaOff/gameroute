package com.gameroute.network;

import com.gameroute.utils.CommandRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps the Windows QoS ("qWave") and global TCP tuning surfaces that are
 * reachable from PowerShell/netsh without any native interop.
 * <p>
 * These are real, documented Windows features (DSCP tagging via
 * {@code New-NetQosPolicy}, TCP auto-tuning via {@code netsh}) but they:
 * <ul>
 *     <li>require an elevated (Administrator) process to take effect,</li>
 *     <li>only mark/shape traffic at the OS level — Windows QoS does not
 *         guarantee bandwidth on the public internet path, only on the
 *         local network segment / a QoS-aware router, and</li>
 *     <li>are never applied without an explicit user confirmation in the UI.</li>
 * </ul>
 */
public class QosService {

    private static final Logger log = LoggerFactory.getLogger(QosService.class);
    private static final String POLICY_NAME = "GameRoute-LoL";

    /**
     * Creates a per-application QoS policy that DSCP-tags traffic from the
     * given executable as Expedited Forwarding (46), the standard low-latency
     * class used for interactive/voice traffic.
     */
    public CommandRunner.Result createLeagueQosPolicy(String exeName) {
        String script = String.format(
                "New-NetQosPolicy -Name '%s' -AppPathNameMatchCondition '%s' -DSCPAction 46 -NetworkProfile All -PolicyStore ActiveStore -ErrorAction Stop",
                POLICY_NAME, exeName);
        log.info("Creating QoS policy for {}", exeName);
        return CommandRunner.runPowerShell(script, 15);
    }

    public CommandRunner.Result removeLeagueQosPolicy() {
        String script = String.format(
                "Remove-NetQosPolicy -Name '%s' -PolicyStore ActiveStore -Confirm:$false -ErrorAction SilentlyContinue", POLICY_NAME);
        log.info("Removing QoS policy {}", POLICY_NAME);
        return CommandRunner.runPowerShell(script, 15);
    }

    public boolean policyExists() {
        String script = String.format("Get-NetQosPolicy -Name '%s' -ErrorAction SilentlyContinue", POLICY_NAME);
        CommandRunner.Result result = CommandRunner.runPowerShell(script, 10);
        return result.success() && result.output().contains(POLICY_NAME);
    }

    /**
     * Sets the global TCP auto-tuning level. "normal" is the Windows default
     * and suits most connections; "highlyrestricted" can help on links with
     * flaky routers that mishandle large receive windows.
     */
    public CommandRunner.Result setTcpAutoTuning(String level) {
        log.info("Setting TCP autotuninglevel={}", level);
        return CommandRunner.runCmd("netsh interface tcp set global autotuninglevel=" + level, 10);
    }

    public CommandRunner.Result showTcpGlobalSettings() {
        return CommandRunner.runCmd("netsh interface tcp show global", 10);
    }
}
