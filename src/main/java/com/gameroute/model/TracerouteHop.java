package com.gameroute.model;

/**
 * A single hop reported by a traceroute run. Any of the three RTT samples
 * may be {@code null} if that probe timed out.
 */
public class TracerouteHop {

    private final int hopNumber;
    private final String ipAddress;
    private final String hostname;
    private final Double rtt1;
    private final Double rtt2;
    private final Double rtt3;
    private boolean problematic;

    public TracerouteHop(int hopNumber, String ipAddress, String hostname,
                          Double rtt1, Double rtt2, Double rtt3) {
        this.hopNumber = hopNumber;
        this.ipAddress = ipAddress;
        this.hostname = hostname;
        this.rtt1 = rtt1;
        this.rtt2 = rtt2;
        this.rtt3 = rtt3;
    }

    public int getHopNumber() {
        return hopNumber;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getHostname() {
        return hostname;
    }

    public Double getRtt1() {
        return rtt1;
    }

    public Double getRtt2() {
        return rtt2;
    }

    public Double getRtt3() {
        return rtt3;
    }

    /** Average of whichever probes succeeded, or -1 if the hop never replied. */
    public double getAverageRtt() {
        double sum = 0;
        int count = 0;
        for (Double v : new Double[]{rtt1, rtt2, rtt3}) {
            if (v != null) {
                sum += v;
                count++;
            }
        }
        return count == 0 ? -1 : sum / count;
    }

    public boolean isTimeout() {
        return rtt1 == null && rtt2 == null && rtt3 == null;
    }

    public boolean isProblematic() {
        return problematic;
    }

    public void setProblematic(boolean problematic) {
        this.problematic = problematic;
    }
}
