package com.gameroute.model;

import java.time.Instant;

/**
 * Outcome of a single {@link com.gameroute.optimizer.OptimizationAction} run,
 * shown to the user so every change GameRoute makes stays auditable.
 */
public record OptimizationActionResult(String actionName, boolean success, String message,
                                        Instant timestamp) {

    public static OptimizationActionResult ok(String actionName, String message) {
        return new OptimizationActionResult(actionName, true, message, Instant.now());
    }

    public static OptimizationActionResult failure(String actionName, String message) {
        return new OptimizationActionResult(actionName, false, message, Instant.now());
    }
}
