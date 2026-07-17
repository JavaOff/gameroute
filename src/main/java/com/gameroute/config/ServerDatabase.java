package com.gameroute.config;

import com.gameroute.model.Region;
import com.gameroute.model.ServerInfo;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Static registry of Riot Games platform regions GameRoute can measure
 * latency against. Backed by {@link Region}; wrapped as {@link ServerInfo}
 * rows for direct use in the Servers tab's table.
 */
public final class ServerDatabase {

    private ServerDatabase() {
    }

    public static List<ServerInfo> allServers() {
        return List.of(Region.values()).stream()
                .map(ServerInfo::new)
                .collect(Collectors.toList());
    }
}
