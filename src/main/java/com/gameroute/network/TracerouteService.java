package com.gameroute.network;

import com.gameroute.config.Constants;
import com.gameroute.model.TracerouteHop;
import com.gameroute.utils.CommandRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the platform {@code tracert} tool and parses its output into a list
 * of {@link TracerouteHop}s. Windows-only; there is no portable Java API for
 * ICMP TTL-based route discovery.
 */
public class TracerouteService {

    private static final Logger log = LoggerFactory.getLogger(TracerouteService.class);

    private static final Pattern HOP_START = Pattern.compile("^\\s*(\\d+)\\s+(.*)$");
    private static final Pattern RTT_TOKEN = Pattern.compile("^(\\*|<?\\d+)\\s*(ms)?\\s*");

    public List<TracerouteHop> traceroute(String host) {
        return traceroute(host, Constants.TRACEROUTE_MAX_HOPS, Constants.TRACEROUTE_TIMEOUT_MS);
    }

    public List<TracerouteHop> traceroute(String host, int maxHops, int timeoutMs) {
        List<String> command = List.of("tracert", "-h", String.valueOf(maxHops),
                "-w", String.valueOf(timeoutMs), host);
        // Generous overall timeout: worst case every hop times out on all 3 probes.
        long budget = (long) Math.max(10.0, maxHops * 3 * (timeoutMs / 1000.0 + 1) + 5);
        CommandRunner.Result result = CommandRunner.run(command, budget);
        if (result.timedOut()) {
            log.warn("Traceroute to {} exceeded time budget of {}s", host, budget);
        }
        return parse(result.output());
    }

    List<TracerouteHop> parse(String tracertOutput) {
        List<TracerouteHop> hops = new ArrayList<>();
        for (String rawLine : tracertOutput.lines().toList()) {
            Matcher hopMatcher = HOP_START.matcher(rawLine);
            if (!hopMatcher.matches()) {
                continue;
            }
            int hopNumber;
            try {
                hopNumber = Integer.parseInt(hopMatcher.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            String rest = hopMatcher.group(2).trim();
            Double[] rtts = new Double[3];
            for (int i = 0; i < 3; i++) {
                Matcher tokenMatcher = RTT_TOKEN.matcher(rest);
                if (!tokenMatcher.lookingAt()) {
                    rtts = null;
                    break;
                }
                String token = tokenMatcher.group(1);
                rtts[i] = token.equals("*") ? null : Double.parseDouble(token.replace("<", ""));
                rest = rest.substring(tokenMatcher.end()).trim();
            }
            if (rtts == null) {
                continue; // not a data line (banner/header text)
            }

            String ip;
            String hostname = null;
            if (rest.toLowerCase().startsWith("request timed out")) {
                ip = "*";
            } else if (rest.contains("[") && rest.contains("]")) {
                hostname = rest.substring(0, rest.indexOf('[')).trim();
                ip = rest.substring(rest.indexOf('[') + 1, rest.indexOf(']')).trim();
            } else {
                ip = rest.trim();
            }

            hops.add(new TracerouteHop(hopNumber, ip, hostname, rtts[0], rtts[1], rtts[2]));
        }
        return hops;
    }
}
