package com.gameroute.network;

import com.gameroute.config.Constants;
import com.gameroute.model.TracerouteHop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Analyzes a sequence of {@link TracerouteHop}s to flag likely trouble spots
 * and to detect when the path to a server has changed between two runs
 * (which often correlates with an ISP/backbone re-route and a latency shift).
 */
public class RouteAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(RouteAnalyzer.class);

    private List<TracerouteHop> lastRoute;

    /** Marks hops whose RTT jumps well above the previous hop, or that never replied. */
    public List<TracerouteHop> markProblematicHops(List<TracerouteHop> hops) {
        double previousAvg = 0;
        for (TracerouteHop hop : hops) {
            double avg = hop.getAverageRtt();
            boolean timeout = hop.isTimeout();
            boolean jump = !timeout && previousAvg > 0
                    && (avg - previousAvg) > Constants.HOP_LATENCY_JUMP_THRESHOLD_MS;
            hop.setProblematic(timeout || jump);
            if (!timeout) {
                previousAvg = avg;
            }
        }
        return hops;
    }

    /**
     * Compares the given route against the last one analyzed for the same
     * target and reports whether the sequence of hop IPs changed.
     */
    public boolean detectRouteChange(List<TracerouteHop> newRoute) {
        boolean changed = lastRoute != null && !sameIpSequence(lastRoute, newRoute);
        if (changed) {
            log.info("Route change detected: {} hops -> {} hops", lastRoute.size(), newRoute.size());
        }
        lastRoute = newRoute;
        return changed;
    }

    private boolean sameIpSequence(List<TracerouteHop> a, List<TracerouteHop> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i).getIpAddress(), b.get(i).getIpAddress())) {
                return false;
            }
        }
        return true;
    }

    public void reset() {
        lastRoute = null;
    }
}
