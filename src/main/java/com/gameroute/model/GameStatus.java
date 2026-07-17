package com.gameroute.model;

/**
 * Snapshot of League of Legends process detection state.
 */
public record GameStatus(boolean running, String processName, long pid,
                          String installPath, String region) {

    public static GameStatus notRunning() {
        return new GameStatus(false, null, -1, null, null);
    }
}
