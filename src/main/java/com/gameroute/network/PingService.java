package com.gameroute.network;

import com.gameroute.config.Constants;
import com.gameroute.model.PingSample;
import com.gameroute.utils.CommandRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Issues single ICMP echo probes using the platform {@code ping} tool.
 * <p>
 * Java's {@link java.net.InetAddress#isReachable} is unreliable on Windows
 * (it silently falls back to a TCP probe unless the process is elevated),
 * so GameRoute shells out to {@code ping} the same way any diagnostics tool
 * would and parses the reported round-trip time from its output.
 */
public class PingService {

    private static final Logger log = LoggerFactory.getLogger(PingService.class);

    // Matches "time=23ms" / "time<1ms" (English Windows locale)
    private static final Pattern TIME_PATTERN = Pattern.compile("time[=<]([0-9]+)ms");

    /**
     * Sends one ping to {@code host}.
     *
     * @param host         hostname or IP address to probe
     * @param timeoutMs    per-probe timeout in milliseconds
     * @param sourceIpOrNull optional local interface address to bind the probe to
     */
    public PingSample ping(String host, int timeoutMs, String sourceIpOrNull) {
        Instant now = Instant.now();
        List<String> command = new ArrayList<>(List.of("ping", "-n", "1", "-w", String.valueOf(timeoutMs)));
        if (sourceIpOrNull != null && !sourceIpOrNull.isBlank()) {
            command.add("-S");
            command.add(sourceIpOrNull);
        }
        command.add(host);

        CommandRunner.Result result = CommandRunner.run(command, Math.max(2, timeoutMs / 1000 + 1));
        if (result.timedOut()) {
            return PingSample.timeout(now);
        }
        Matcher matcher = TIME_PATTERN.matcher(result.output());
        if (matcher.find()) {
            double rtt = Double.parseDouble(matcher.group(1));
            return PingSample.of(now, rtt);
        }
        log.debug("Ping to {} produced no parseable reply:\n{}", host, result.output());
        return PingSample.timeout(now);
    }

    public PingSample ping(String host) {
        return ping(host, Constants.PING_TIMEOUT_MS, null);
    }
}
