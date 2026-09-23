/*
 * Stealth Overhaul
 * Copyright (C) 2026 Connor
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.connor.stealthoverhaul;

/**
 * One zombie/player pair's awareness state. Created lazily when spotting
 * evaluation begins. Identity is the map key, not a field here.
 *
 * <p>Fields are public. ZombieBuddy inlines spotted advice into
 * {@code IsoZombie}, which cannot see private members of this class.
 */
public final class AwarenessRecord {
    public float awareness;
    public float awarenessAtExposure;
    public AwarenessState state = AwarenessState.UNAWARE;
    public double lastEvaluationHours = Double.NaN;
    public double lastExposureHours = Double.NaN;
    public boolean lastAcceptedTarget;
    public boolean loggedFirstSkip;
    public boolean loggedPromote;
    public boolean loggedFirstReject;
    public boolean lastExposed;
    public float lastGainMultiplier;
    public float lastDistance;
    public float lastFacingDot;
    public float lastEffectiveRange;
    public float lastCoverFactor = -1.0f;
    public String lastBlockedReason = DetectionFactors.BLOCKED_NONE;

    public float getAwareness() {
        return awareness;
    }

    public AwarenessState getState() {
        return state;
    }

    public boolean isLastAcceptedTarget() {
        return lastAcceptedTarget;
    }

    public void copyFactors(DetectionFactors factors) {
        if (factors == null) {
            lastExposed = false;
            lastGainMultiplier = 0.0f;
            lastDistance = 0.0f;
            lastFacingDot = 0.0f;
            lastEffectiveRange = 0.0f;
            lastCoverFactor = -1.0f;
            lastBlockedReason = DetectionFactors.BLOCKED_FACTOR;
            return;
        }
        lastExposed = factors.exposed;
        lastGainMultiplier = factors.combinedMultiplier();
        lastDistance = factors.distance;
        lastFacingDot = factors.facingDot;
        lastEffectiveRange = factors.effectiveRange;
        lastCoverFactor = factors.coverEvaluated ? factors.coverFactor : -1.0f;
        lastBlockedReason = factors.blockedReason == null ? DetectionFactors.BLOCKED_NONE : factors.blockedReason;
    }
}
