package com.gameroute.optimizer;

import com.gameroute.model.OptimizationActionResult;

/**
 * A single, independently applicable optimization step. Every implementation
 * must be safe to run repeatedly and must never be invoked by anything other
 * than an explicit user action — the UI layer is responsible for presenting
 * {@link #getWarning()} in a confirmation dialog before calling {@link #execute()}.
 */
public interface OptimizationAction {

    String getName();

    String getDescription();

    /** True if this action needs an elevated (Administrator) process to succeed. */
    boolean requiresAdmin();

    /** Risk/behavior disclosure shown to the user before they confirm. */
    String getWarning();

    OptimizationActionResult execute();
}
