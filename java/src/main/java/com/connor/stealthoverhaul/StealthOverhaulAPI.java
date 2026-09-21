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

import me.zed_0xff.zombie_buddy.Exposer;
import zombie.GameTime;

/**
 * Read-only Java surface exposed to Lua. Must stay in
 * {@code com.connor.stealthoverhaul} so ZombieBuddy can discover it from
 * {@code javaPkgName}.
 *
 * <p>{@code @Exposer.LuaClass} registers this type with Kahlua
 * ({@code Exposer.exposeAnnotatedClasses} in ZombieBuddy). The annotation
 * name is left blank on purpose: ZombieBuddy then exposes the Java simple
 * name into {@code LuaManager.env}, so Lua calls
 * {@code StealthOverhaulAPI.isPatchActive()}. Setting
 * {@code name = "StealthOverhaulAPI"} does not end with
 * {@code "." + simpleName}, and {@code exposeClassNow} then {@code rawset}s
 * that leaf to {@code null}, wiping the global.
 *
 * <p>Pair lookups use {@link AwarenessSystem#get} and never allocate.
 */
@Exposer.LuaClass
public final class StealthOverhaulAPI {
    public static final int STATE_UNAWARE = 0;
    public static final int STATE_SUSPICIOUS = 1;
    public static final int STATE_DETECTED = 2;

    public static boolean javaLoaded;

    public StealthOverhaulAPI() {}

    public static boolean isPatchActive() {
        if (!javaLoaded || IsoZombieSpottedPatch.spottingAdviceFailed) {
            return false;
        }
        DetectionConfig config = AwarenessSystem.getInstance().getConfig();
        return config != null && config.enabled && config.isValid();
    }

    public static String explainPatchStatus() {
        if (!javaLoaded) {
            return "java not loaded";
        }
        if (IsoZombieSpottedPatch.spottingAdviceFailed) {
            return "spotted() advice failed; vanilla spotting continues";
        }
        DetectionConfig config = AwarenessSystem.getInstance().getConfig();
        if (config == null || !config.enabled) {
            return "disabled";
        }
        if (!config.isValid()) {
            return "invalid config";
        }
        return "active threshold="
                + config.detectionThreshold
                + " revision="
                + AwarenessSystem.getInstance().getConfigRevision()
                + " calls="
                + IsoZombieSpottedPatch.callCount;
    }

    public static float getAwareness(Object zombie, Object player) {
        AwarenessRecord record = recordIfActive(zombie, player);
        return record == null ? 0.0f : record.awareness;
    }

    public static int getAwarenessState(Object zombie, Object player) {
        AwarenessRecord record = recordIfActive(zombie, player);
        if (record == null || record.state == null) {
            return STATE_UNAWARE;
        }
        return record.state.ordinal();
    }

    public static float getEffectiveRange(Object zombie, Object player) {
        AwarenessRecord record = recordIfActive(zombie, player);
        return record == null ? 0.0f : record.lastEffectiveRange;
    }

    /**
     * Fresh potential vision radius. Does not create a pair record.
     */
    public static float getPotentialRange(Object zombie, Object player) {
        if (!isPatchActive()) {
            return 0.0f;
        }
        try {
            return ExposureEvaluator.potentialRange(
                    zombie, player, AwarenessSystem.getInstance().getConfig());
        } catch (Throwable ignored) {
            return 0.0f;
        }
    }

    /**
     * Authoritative angle curve. Lua must not reimplement this.
     */
    public static float angleFactor(float facingDot, float distance) {
        if (!isPatchActive()) {
            return 0.0f;
        }
        DetectionConfig config = AwarenessSystem.getInstance().getConfig();
        if (config == null) {
            return 0.0f;
        }
        return DetectionFactors.angleFactor(facingDot, distance, config);
    }

    public static float getExposureRate(Object zombie, Object player) {
        AwarenessRecord record = recordIfActive(zombie, player);
        if (record == null) {
            return 0.0f;
        }
        DetectionConfig config = AwarenessSystem.getInstance().getConfig();
        if (config == null) {
            return 0.0f;
        }
        return record.lastGainMultiplier * config.baseGainPerSecond;
    }

    public static float getDetectionThreshold() {
        if (!isPatchActive()) {
            return 0.0f;
        }
        return AwarenessSystem.getInstance().getConfig().detectionThreshold;
    }

    public static int getConfigRevision() {
        if (!isPatchActive()) {
            return 0;
        }
        return AwarenessSystem.getInstance().getConfigRevision();
    }

    public static float getLastGainMultiplier(Object zombie, Object player) {
        AwarenessRecord record = recordIfActive(zombie, player);
        return record == null ? 0.0f : record.lastGainMultiplier;
    }

    public static float getLastDistance(Object zombie, Object player) {
        AwarenessRecord record = recordIfActive(zombie, player);
        return record == null ? 0.0f : record.lastDistance;
    }

    public static float getLastFacingDot(Object zombie, Object player) {
        AwarenessRecord record = recordIfActive(zombie, player);
        return record == null ? 0.0f : record.lastFacingDot;
    }

    public static String getLastBlockedReason(Object zombie, Object player) {
        AwarenessRecord record = recordIfActive(zombie, player);
        if (record == null || record.lastBlockedReason == null) {
            return DetectionFactors.BLOCKED_NONE;
        }
        return record.lastBlockedReason;
    }

    public static boolean wasLastExposed(Object zombie, Object player) {
        AwarenessRecord record = recordIfActive(zombie, player);
        return record != null && record.lastExposed;
    }

    private static AwarenessRecord recordIfActive(Object zombie, Object player) {
        if (!isPatchActive()) {
            return null;
        }
        AwarenessRecord record = AwarenessSystem.getInstance().get(zombie, player);
        if (record != null) {
            AwarenessSystem.getInstance().refreshForRead(record, currentWorldAgeHours());
        }
        return record;
    }

    /**
     * {@code GameTime} is absent from JVM unit tests. Missing clock must not
     * create records or throw into Lua.
     */
    public static double currentWorldAgeHours() {
        try {
            GameTime gameTime = GameTime.getInstance();
            if (gameTime == null) {
                return Double.NaN;
            }
            return gameTime.getWorldAgeHours();
        } catch (Throwable ignored) {
            return Double.NaN;
        }
    }
}
