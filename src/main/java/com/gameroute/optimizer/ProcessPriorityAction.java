package com.gameroute.optimizer;

import com.gameroute.model.OptimizationActionResult;
import com.gameroute.utils.CommandRunner;
import com.gameroute.utils.ProcessUtils;

import java.util.Optional;

/**
 * Sets the League of Legends process priority. Uses PowerShell's
 * {@code Process.PriorityClass} property rather than the deprecated
 * {@code wmic} tool. Defaults to raising it to {@code High}; construct with
 * {@code "Normal"} to undo that (see {@link OptimizerService#revertSequence}).
 */
public class ProcessPriorityAction implements OptimizationAction {

    private final String priorityClass;

    public ProcessPriorityAction() {
        this("High");
    }

    public ProcessPriorityAction(String priorityClass) {
        this.priorityClass = priorityClass;
    }

    @Override
    public String getName() {
        return priorityClass.equals("High")
                ? "Raise League of Legends process priority"
                : "Restore normal process priority";
    }

    @Override
    public String getDescription() {
        return priorityClass.equals("High")
                ? "Sets the game process to 'High' priority so the OS scheduler favors it over background tasks."
                : "Sets the game process back to Windows' default 'Normal' priority.";
    }

    @Override
    public boolean requiresAdmin() {
        return false; // changing your own process's priority class does not require elevation
    }

    @Override
    public String getWarning() {
        return priorityClass.equals("High")
                ? "Setting a process to High priority can occasionally starve other applications of CPU time on low-core-count machines."
                : "Returns the game process to the standard priority every other application runs at.";
    }

    @Override
    public OptimizationActionResult execute() {
        Optional<ProcessHandle> handle = ProcessUtils.findLeagueProcess();
        if (handle.isEmpty()) {
            return OptimizationActionResult.failure(getName(), "League of Legends is not currently running.");
        }
        long pid = handle.get().pid();
        String script = String.format(
                "(Get-Process -Id %d -ErrorAction Stop).PriorityClass = '%s'", pid, priorityClass);
        CommandRunner.Result result = CommandRunner.runPowerShell(script, 10);
        return result.success()
                ? OptimizationActionResult.ok(getName(), "Priority set to " + priorityClass + " for PID " + pid + ".")
                : OptimizationActionResult.failure(getName(), "PowerShell reported: " + result.output().trim());
    }
}
