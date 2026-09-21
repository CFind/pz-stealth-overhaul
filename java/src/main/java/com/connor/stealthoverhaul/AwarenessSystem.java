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

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Owns awareness records and the deterministic update/decay loop. Engine-free
 * so JVM tests can drive it with dummy pair identities.
 */
public final class AwarenessSystem {
    public enum SpotAction {
        PASS_THROUGH,
        SKIP,
        PROMOTE
    }

    private static final AwarenessSystem INSTANCE = new AwarenessSystem();
    private static final double HOURS_TO_SECONDS = 3600.0;
    private static final int PURGE_INTERVAL = 64;

    private final IdentityHashMap<Object, IdentityHashMap<Object, AwarenessRecord>> records =
            new IdentityHashMap<Object, IdentityHashMap<Object, AwarenessRecord>>();
    private DetectionConfig config;
    private int evaluations;
    public int configRevision;

    public AwarenessSystem() {
        this(DetectionConfig.defaults());
    }

    public AwarenessSystem(DetectionConfig config) {
        this.config = config == null ? DetectionConfig.defaults() : config;
    }

    public static AwarenessSystem getInstance() {
        return INSTANCE;
    }

    public DetectionConfig getConfig() {
        return config;
    }

    public void setConfig(DetectionConfig config) {
        this.config = config == null ? DetectionConfig.defaults() : config;
        configRevision++;
    }

    public int getConfigRevision() {
        return configRevision;
    }

    public void clear() {
        records.clear();
    }

    public AwarenessRecord get(Object zombie, Object player) {
        if (zombie == null || player == null) {
            return null;
        }
        IdentityHashMap<Object, AwarenessRecord> byPlayer = records.get(zombie);
        if (byPlayer == null) {
            return null;
        }
        return byPlayer.get(player);
    }

    public AwarenessRecord getOrCreate(Object zombie, Object player) {
        if (zombie == null || player == null) {
            return null;
        }
        IdentityHashMap<Object, AwarenessRecord> byPlayer = records.get(zombie);
        if (byPlayer == null) {
            byPlayer = new IdentityHashMap<Object, AwarenessRecord>();
            records.put(zombie, byPlayer);
        }
        AwarenessRecord record = byPlayer.get(player);
        if (record == null) {
            record = new AwarenessRecord();
            byPlayer.put(player, record);
        }
        return record;
    }

    /**
     * Apply lazy decay and optional fixed-rate gain.
     *
     * @param simDeltaSeconds tick delta from
     *     {@code GameTime.getMultipliedSecondsSinceLastUpdate()}
     *     ({@code GameTime.java:211-213})
     * @param worldAgeHours timestamp from {@code GameTime.getWorldAgeHours()}
     *     ({@code GameTime.java:1184-1193})
     * @return {@code true} when this pair should be promoted
     */
    public boolean evaluate(
            AwarenessRecord record,
            float simDeltaSeconds,
            double worldAgeHours,
            boolean exposed,
            boolean vanillaHasTarget) {
        return evaluate(
                record,
                simDeltaSeconds,
                worldAgeHours,
                exposed ? DetectionFactors.UNSCALED : DetectionFactors.NONE,
                vanillaHasTarget);
    }

    public boolean evaluate(
            AwarenessRecord record,
            float simDeltaSeconds,
            double worldAgeHours,
            DetectionFactors factors,
            boolean vanillaHasTarget) {
        if (record == null) {
            return false;
        }
        DetectionConfig active = config;
        record.copyFactors(factors);
        boolean sameTick = !Double.isNaN(record.lastEvaluationHours)
                && record.lastEvaluationHours == worldAgeHours;
        if (!sameTick) {
            applyLazyDecay(record, worldAgeHours, active);
            float multiplier = factors == null ? 0.0f : factors.combinedMultiplier();
            if (multiplier > 0.0f) {
                float delta = capDelta(simDeltaSeconds, active);
                if (delta > 0.0f && active.baseGainPerSecond > 0.0f) {
                    record.awareness = clamp(
                            record.awareness + active.baseGainPerSecond * multiplier * delta,
                            0.0f,
                            active.detectionThreshold);
                    record.lastExposureHours = worldAgeHours;
                    record.awarenessAtExposure = record.awareness;
                }
            }
            record.lastEvaluationHours = worldAgeHours;
            updateStateAfterEvaluation(record, active, vanillaHasTarget);
            evaluations++;
            if ((evaluations & (PURGE_INTERVAL - 1)) == 0) {
                purgeStale(worldAgeHours);
            }
        }
        boolean exposedNow = factors != null && factors.combinedMultiplier() > 0.0f;
        return shouldPromote(record, active, vanillaHasTarget, exposedNow);
    }

    /**
     * A rejected promote must not clear an acceptance vanilla already holds.
     * Demotion happens in {@link #updateStateAfterEvaluation} after the live
     * target is gone and awareness has fallen below the reacquire threshold.
     */
    public void markAccepted(AwarenessRecord record, boolean accepted) {
        if (record == null || !accepted) {
            return;
        }
        record.lastAcceptedTarget = true;
        record.state = AwarenessState.DETECTED;
    }

    public void purgeStale(double worldAgeHours) {
        float expiry = config.recordExpirySeconds;
        Iterator<Map.Entry<Object, IdentityHashMap<Object, AwarenessRecord>>> zombieIt =
                records.entrySet().iterator();
        while (zombieIt.hasNext()) {
            Map.Entry<Object, IdentityHashMap<Object, AwarenessRecord>> zombieEntry = zombieIt.next();
            Iterator<Map.Entry<Object, AwarenessRecord>> playerIt =
                    zombieEntry.getValue().entrySet().iterator();
            while (playerIt.hasNext()) {
                AwarenessRecord record = playerIt.next().getValue();
                if (isExpired(record, worldAgeHours, expiry)) {
                    playerIt.remove();
                }
            }
            if (zombieEntry.getValue().isEmpty()) {
                zombieIt.remove();
            }
        }
    }

    public int pairCount() {
        int count = 0;
        for (IdentityHashMap<Object, AwarenessRecord> byPlayer : records.values()) {
            count += byPlayer.size();
        }
        return count;
    }

    public static float capDelta(float simDeltaSeconds, DetectionConfig config) {
        if (!Float.isFinite(simDeltaSeconds) || simDeltaSeconds <= 0.0f) {
            return 0.0f;
        }
        return Math.min(simDeltaSeconds, config.maxEvaluationDelta);
    }

    /**
     * Apply timestamp-based decay without requiring a spotting call. Safe to
     * invoke from Lua getters: remaining awareness is computed from the
     * exposure snapshot, so repeated reads at the same world age do not
     * over-decay.
     */
    /**
     * Decay the stored value for a Lua read. Does not clear {@code DETECTED}:
     * only a spotting evaluation that observes vanilla without this target can
     * do that, and only after awareness falls below the reacquire threshold.
     */
    public void refreshForRead(AwarenessRecord record, double worldAgeHours) {
        if (record == null || Double.isNaN(worldAgeHours)) {
            return;
        }
        applyLazyDecay(record, worldAgeHours, config);
        if (record.state == AwarenessState.DETECTED) {
            return;
        }
        updateStateAfterEvaluation(record, config, false);
    }

    public static void applyLazyDecay(AwarenessRecord record, double worldAgeHours, DetectionConfig config) {
        if (Double.isNaN(record.lastExposureHours) || config.decayPerSecond <= 0.0f) {
            return;
        }
        float idleSeconds = (float) ((worldAgeHours - record.lastExposureHours) * HOURS_TO_SECONDS);
        if (idleSeconds <= config.decayDelaySeconds) {
            return;
        }
        float decayTime = idleSeconds - config.decayDelaySeconds;
        record.awareness = clamp(
                record.awarenessAtExposure - decayTime * config.decayPerSecond,
                0.0f,
                config.detectionThreshold);
    }

    /**
     * Promote only while the player is actually exposed. High awareness alone
     * must not force another vanilla spot after they leave the cone; that
     * kept pursuit and then let the meter start a second fill.
     */
    public static boolean shouldPromote(
            AwarenessRecord record, DetectionConfig config, boolean vanillaHasTarget, boolean exposed) {
        if (!exposed) {
            return false;
        }
        if (vanillaHasTarget) {
            return true;
        }
        float threshold = record.state == AwarenessState.DETECTED
                ? config.reacquireThreshold
                : config.detectionThreshold;
        return record.awareness >= threshold;
    }

    /**
     * Vanilla pursuit and the awareness meter are separate. While vanilla still
     * has this player, the pair stays {@code DETECTED} even if the stored
     * value has decayed. Reacquisition starts only after the target is gone
     * and awareness is below {@code reacquireThreshold}.
     */
    public static void updateStateAfterEvaluation(
            AwarenessRecord record, DetectionConfig config, boolean vanillaHasTarget) {
        if (vanillaHasTarget) {
            record.state = AwarenessState.DETECTED;
            record.lastAcceptedTarget = true;
            return;
        }
        if (record.state == AwarenessState.DETECTED) {
            if (record.awareness < config.reacquireThreshold) {
                record.state = record.awareness <= 0.0f ? AwarenessState.UNAWARE : AwarenessState.SUSPICIOUS;
                record.lastAcceptedTarget = false;
            }
            return;
        }
        if (record.awareness <= 0.0f) {
            record.state = AwarenessState.UNAWARE;
        } else {
            record.state = AwarenessState.SUSPICIOUS;
        }
    }

    public static boolean isExpired(AwarenessRecord record, double worldAgeHours, float expirySeconds) {
        if (Double.isNaN(record.lastEvaluationHours)) {
            return false;
        }
        float idleSeconds = (float) ((worldAgeHours - record.lastEvaluationHours) * HOURS_TO_SECONDS);
        return idleSeconds > expirySeconds;
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
