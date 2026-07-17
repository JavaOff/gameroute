package com.gameroute.model;

/**
 * Riot Games platform regions and the ping target used to estimate latency
 * to each.
 * <p>
 * Riot does not publish a stable, per-region list of ICMP-pingable game
 * server hostnames (their support docs explicitly stopped listing server IP
 * ranges after infrastructure moves). The only per-platform hostnames Riot
 * documents and guarantees to keep working are its
 * <a href="https://developer.riotgames.com">Developer Portal</a> API
 * gateways, {@code <platform>.api.riotgames.com}, verified reachable for
 * every region below. They are Cloudflare-fronted for DDoS protection, so
 * measured latency reflects the round trip to Riot's nearest edge
 * network location for that platform — correlated with, but not identical
 * to, live in-game server latency. Treat these numbers as a relative,
 * order-of-magnitude estimate for troubleshooting, not an exact prediction.
 */
public enum Region {

    EUW("EUW", "Europe West", "euw1.api.riotgames.com"),
    EUNE("EUNE", "Europe Nordic & East", "eun1.api.riotgames.com"),
    NA("NA", "North America", "na1.api.riotgames.com"),
    KR("KR", "Korea", "kr.api.riotgames.com"),
    BR("BR", "Brazil", "br1.api.riotgames.com"),
    LAN("LAN", "Latin America North", "la1.api.riotgames.com"),
    LAS("LAS", "Latin America South", "la2.api.riotgames.com"),
    OCE("OCE", "Oceania", "oc1.api.riotgames.com"),
    JP("JP", "Japan", "jp1.api.riotgames.com"),
    TR("TR", "Turkey", "tr1.api.riotgames.com"),
    RU("RU", "Russia", "ru.api.riotgames.com");

    private final String code;
    private final String displayName;
    private final String host;

    Region(String code, String displayName, String host) {
        this.code = code;
        this.displayName = displayName;
        this.host = host;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getHost() {
        return host;
    }
}
