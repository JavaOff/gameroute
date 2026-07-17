package com.gameroute.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs external commands (ping, tracert, netsh, powershell, ...) with a hard
 * timeout and captures combined stdout/stderr. This is the single choke point
 * through which GameRoute talks to the OS, which keeps process-handling
 * concerns (timeouts, encoding, stream draining) out of every caller.
 */
public final class CommandRunner {

    private static final Logger log = LoggerFactory.getLogger(CommandRunner.class);

    private CommandRunner() {
    }

    public record Result(int exitCode, String output, boolean timedOut) {
        public boolean success() {
            return !timedOut && exitCode == 0;
        }
    }

    private static final Charset CONSOLE_CHARSET = resolveConsoleCharset();

    private static Charset resolveConsoleCharset() {
        try {
            return Charset.forName(System.getProperty("native.encoding", "IBM850"));
        } catch (Exception e) {
            return Charset.defaultCharset();
        }
    }

    public static Result run(List<String> command, long timeoutSeconds) {
        log.debug("Executing: {}", String.join(" ", command));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        StringBuilder output = new StringBuilder();
        try {
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), CONSOLE_CHARSET))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Result(-1, output.toString(), true);
            }
            return new Result(process.exitValue(), output.toString(), false);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Command failed: {}", String.join(" ", command), e);
            return new Result(-1, e.getMessage() == null ? "" : e.getMessage(), false);
        }
    }

    public static Result runCmd(String command, long timeoutSeconds) {
        return run(List.of("cmd", "/c", command), timeoutSeconds);
    }

    public static Result runPowerShell(String script, long timeoutSeconds) {
        return run(List.of("powershell", "-NoProfile", "-NonInteractive", "-Command", script), timeoutSeconds);
    }
}
