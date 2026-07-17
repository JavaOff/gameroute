package com.gameroute.network;

import com.gameroute.model.TracerouteHop;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the tracert output parser against a real captured Windows
 * {@code tracert} run (English locale), covering: a resolved hostname hop,
 * a fully-timed-out hop, and a partially-timed-out hop.
 */
class TracerouteServiceTest {

    private static final String SAMPLE_OUTPUT = """
            Tracing route to euw1.api.riotgames.com.cdn.cloudflare.net [172.64.146.28]
            over a maximum of 8 hops:

              1     2 ms     1 ms     7 ms  SYNOLOGYROUTER [192.168.0.1]\s
              2    17 ms     9 ms     8 ms  10.11.5.146\s
              3     *        *        *     Request timed out.
              4    83 ms    82 ms    58 ms  10.149.255.1\s
              5   134 ms   107 ms     *     10.254.100.254\s
              6    70 ms    45 ms    23 ms  10.254.101.1\s
              7    59 ms    45 ms    42 ms  tlapnet-194-33.cust.tlapnet.cz [45.153.194.33]\s
              8    67 ms    80 ms    60 ms  ip-213-192-1-5.net.vodafone.cz [213.192.1.5]\s

            Trace complete.
            """;

    private final TracerouteService service = new TracerouteService();

    @Test
    void parsesEveryHop() {
        List<TracerouteHop> hops = service.parse(SAMPLE_OUTPUT);
        assertEquals(8, hops.size());
    }

    @Test
    void parsesResolvedHostnameAndIp() {
        TracerouteHop hop = service.parse(SAMPLE_OUTPUT).get(0);
        assertEquals(1, hop.getHopNumber());
        assertEquals("SYNOLOGYROUTER", hop.getHostname());
        assertEquals("192.168.0.1", hop.getIpAddress());
        assertEquals(2.0, hop.getRtt1());
        assertEquals(1.0, hop.getRtt2());
        assertEquals(7.0, hop.getRtt3());
    }

    @Test
    void parsesBareIpWithoutHostname() {
        TracerouteHop hop = service.parse(SAMPLE_OUTPUT).get(1);
        assertEquals("10.11.5.146", hop.getIpAddress());
        assertNull(hop.getHostname());
    }

    @Test
    void parsesFullyTimedOutHop() {
        TracerouteHop hop = service.parse(SAMPLE_OUTPUT).get(2);
        assertTrue(hop.isTimeout());
        assertNull(hop.getRtt1());
        assertNull(hop.getRtt2());
        assertNull(hop.getRtt3());
    }

    @Test
    void parsesPartiallyTimedOutHop() {
        TracerouteHop hop = service.parse(SAMPLE_OUTPUT).get(4);
        assertEquals(134.0, hop.getRtt1());
        assertEquals(107.0, hop.getRtt2());
        assertNull(hop.getRtt3());
        assertEquals("10.254.100.254", hop.getIpAddress());
        assertTrue(!hop.isTimeout());
    }

    @Test
    void markProblematicHopsFlagsTimeoutsAndLatencyJumps() {
        List<TracerouteHop> hops = service.parse(SAMPLE_OUTPUT);
        new RouteAnalyzer().markProblematicHops(hops);
        assertTrue(hops.get(2).isProblematic()); // fully timed out hop
        assertTrue(!hops.get(0).isProblematic()); // first hop, low latency
    }
}
