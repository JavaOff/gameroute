package com.gameroute.service;

import com.gameroute.utils.CommandRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers/unregisters GameRoute in the current user's Run key
 * ({@code HKCU\...\Run}) so it can optionally start with Windows. Scoped to
 * the current user (no admin rights needed) and only ever changed from the
 * Settings tab after the user flips the "Auto start" toggle.
 */
public class AutoStartService {

    private static final Logger log = LoggerFactory.getLogger(AutoStartService.class);
    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "GameRoute";

    public boolean enable(String jarOrExePath) {
        log.info("Registering auto-start entry: {}", jarOrExePath);
        String cmd = String.format("reg add \"%s\" /v %s /t REG_SZ /d \"%s\" /f", RUN_KEY, VALUE_NAME, jarOrExePath);
        return CommandRunner.runCmd(cmd, 10).success();
    }

    public boolean disable() {
        log.info("Removing auto-start entry");
        String cmd = String.format("reg delete \"%s\" /v %s /f", RUN_KEY, VALUE_NAME);
        CommandRunner.Result result = CommandRunner.runCmd(cmd, 10);
        // Deleting a value that doesn't exist returns non-zero; treat that as success too.
        return result.success() || result.output().toLowerCase().contains("unable to find");
    }

    public boolean isEnabled() {
        String cmd = String.format("reg query \"%s\" /v %s", RUN_KEY, VALUE_NAME);
        return CommandRunner.runCmd(cmd, 10).success();
    }
}
