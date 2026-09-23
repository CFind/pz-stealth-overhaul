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

import java.util.Locale;
import zombie.SandboxOptions;
import zombie.config.BooleanConfigOption;
import zombie.config.ConfigOption;
import zombie.config.DoubleConfigOption;
import zombie.config.IntegerConfigOption;
import zombie.core.Core;

/**
 * Reads {@code StealthOverhaul.*} sandbox options into one immutable
 * {@link DetectionConfig}. Bounds match {@code media/sandbox-options.txt}.
 *
 * <p>{@code SandboxOptions.getOptionByName} is
 * {@code SandboxOptions.java:576-578}. Values are clamped here even when the
 * sandbox UI also declares bounds ({@code docs/mod.md}).
 */
final class SandboxConfig {
    private static final String PREFIX = "StealthOverhaul.";

    private static boolean logged;
    private static boolean failedClosed;

    private SandboxConfig() {}

    static void applyIfPresent(AwarenessSystem system) {
        SandboxOptions options = SandboxOptions.instance;
        if (options == null || options.getOptionByName(PREFIX + "Enabled") == null) {
            return;
        }
        DetectionConfig next;
        try {
            next = read(options);
        } catch (IllegalStateException ex) {
            return;
        }
        if (!next.isValid()) {
            if (!failedClosed) {
                failedClosed = true;
                system.setConfig(DetectionConfig.copyDefaults().enabled(false).build());
                System.out.println(
                        "[StealthOverhaul] sandbox config invalid; detection stays off until minimum vision range is at or below maximum");
            }
            return;
        }
        failedClosed = false;
        DetectionConfig current = system.storedConfig();
        if (current != null && current.matches(next)) {
            log(next);
            return;
        }
        system.setConfig(next);
        log(next);
    }

    /**
     * Clamped snapshot from already-read numbers. Unit tests call this
     * without the game jar.
     */
    static DetectionConfig snapshot(
            boolean enabled,
            double detectionThreshold,
            double baseGainPerSecond,
            double maxEvaluationDelta,
            double decayDelaySeconds,
            double decayPerSecond,
            double reacquireThreshold,
            double recordExpirySeconds,
            double minimumVisionRange,
            double maximumVisionRange,
            double closeRange,
            int maximumFloorDifference,
            double rearCutoffDot,
            double peripheralStrength,
            double minimumLightFactor,
            double lightExponent,
            double stationaryFactor,
            double walkingFactor,
            double runningFactor,
            double sneakSkillStrength,
            double inconspicuousFactor,
            double conspicuousFactor,
            double goodSightFactor,
            double poorSightFactor,
            double rainPenaltyStrength,
            double fogPenaltyStrength,
            double eatingVisionFactor) {
        return DetectionConfig.builder()
                .enabled(enabled)
                .detectionThreshold(clamp(detectionThreshold, 0.05, 10.0))
                .baseGainPerSecond(clamp(baseGainPerSecond, 0.0, 5.0))
                .maxEvaluationDelta(clamp(maxEvaluationDelta, 0.01, 2.0))
                .decayDelaySeconds(clamp(decayDelaySeconds, 0.0, 60.0))
                .decayPerSecond(clamp(decayPerSecond, 0.0, 5.0))
                .reacquireThreshold(clamp(reacquireThreshold, 0.05, 10.0))
                .recordExpirySeconds(clamp(recordExpirySeconds, 1.0, 600.0))
                .minimumVisionRange(clamp(minimumVisionRange, 1.0, 60.0))
                .maximumVisionRange(clamp(maximumVisionRange, 1.0, 80.0))
                .closeRange(clamp(closeRange, 0.0, 10.0))
                .maximumFloorDifference(clampInt(maximumFloorDifference, 0, 8))
                .rearCutoffDot(clamp(rearCutoffDot, -1.0, 1.0))
                .peripheralStrength(clamp(peripheralStrength, 0.05, 4.0))
                .minimumLightFactor(clamp(minimumLightFactor, 0.0, 1.0))
                .lightExponent(clamp(lightExponent, 0.05, 8.0))
                .stationaryFactor(clamp(stationaryFactor, 0.0, 5.0))
                .walkingFactor(clamp(walkingFactor, 0.0, 5.0))
                .runningFactor(clamp(runningFactor, 0.0, 8.0))
                .sneakSkillStrength(clamp(sneakSkillStrength, 0.0, 4.0))
                .inconspicuousFactor(clamp(inconspicuousFactor, 0.0, 3.0))
                .conspicuousFactor(clamp(conspicuousFactor, 0.0, 3.0))
                .goodSightFactor(clamp(goodSightFactor, 0.05, 8.0))
                .poorSightFactor(clamp(poorSightFactor, 0.05, 4.0))
                .rainPenaltyStrength(clamp(rainPenaltyStrength, 0.0, 20.0))
                .fogPenaltyStrength(clamp(fogPenaltyStrength, 0.0, 30.0))
                .eatingVisionFactor(clamp(eatingVisionFactor, 0.0, 1.0))
                .build();
    }

    static float clamp(double value, double min, double max) {
        if (Double.isNaN(value) || value == Double.NEGATIVE_INFINITY) {
            return (float) min;
        }
        if (value == Double.POSITIVE_INFINITY || value > max) {
            return (float) max;
        }
        if (value < min) {
            return (float) min;
        }
        return (float) value;
    }

    static int clampInt(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static DetectionConfig read(SandboxOptions options) {
        return snapshot(
                bool(options, "Enabled"),
                number(options, "DetectionThreshold"),
                number(options, "BaseGainPerSecond"),
                number(options, "MaxEvaluationDelta"),
                number(options, "DecayDelaySeconds"),
                number(options, "DecayPerSecond"),
                number(options, "ReacquireThreshold"),
                number(options, "RecordExpirySeconds"),
                number(options, "MinimumVisionRange"),
                number(options, "MaximumVisionRange"),
                number(options, "CloseRange"),
                integer(options, "MaximumFloorDifference"),
                number(options, "RearCutoffDot"),
                number(options, "PeripheralStrength"),
                number(options, "MinimumLightFactor"),
                number(options, "LightExponent"),
                number(options, "StationaryFactor"),
                number(options, "WalkingFactor"),
                number(options, "RunningFactor"),
                number(options, "SneakSkillStrength"),
                number(options, "InconspicuousFactor"),
                number(options, "ConspicuousFactor"),
                number(options, "GoodSightFactor"),
                number(options, "PoorSightFactor"),
                number(options, "RainPenaltyStrength"),
                number(options, "FogPenaltyStrength"),
                number(options, "EatingVisionFactor"));
    }

    private static ConfigOption option(SandboxOptions options, String name) {
        SandboxOptions.SandboxOption sandboxOption = options.getOptionByName(PREFIX + name);
        if (sandboxOption == null) {
            throw new IllegalStateException(name);
        }
        return sandboxOption.asConfigOption();
    }

    private static boolean bool(SandboxOptions options, String name) {
        return ((BooleanConfigOption) option(options, name)).getValue();
    }

    private static double number(SandboxOptions options, String name) {
        return ((DoubleConfigOption) option(options, name)).getValue();
    }

    private static int integer(SandboxOptions options, String name) {
        return ((IntegerConfigOption) option(options, name)).getValue();
    }

    private static void log(DetectionConfig config) {
        boolean first = !logged;
        logged = true;
        if (!first && !debug()) {
            return;
        }
        System.out.println(
                "[StealthOverhaul] sandbox"
                        + " enabled="
                        + config.enabled
                        + " threshold="
                        + num(config.detectionThreshold)
                        + " gain="
                        + num(config.baseGainPerSecond)
                        + " delta="
                        + num(config.maxEvaluationDelta)
                        + " decayDelay="
                        + num(config.decayDelaySeconds)
                        + " decay="
                        + num(config.decayPerSecond)
                        + " reacquire="
                        + num(config.reacquireThreshold)
                        + " expiry="
                        + num(config.recordExpirySeconds)
                        + " vision="
                        + num(config.minimumVisionRange)
                        + "-"
                        + num(config.maximumVisionRange)
                        + " close="
                        + num(config.closeRange)
                        + " floors="
                        + config.maximumFloorDifference
                        + " rear="
                        + num(config.rearCutoffDot)
                        + " peripheral="
                        + num(config.peripheralStrength)
                        + " lightMin="
                        + num(config.minimumLightFactor)
                        + " lightExp="
                        + num(config.lightExponent)
                        + " still="
                        + num(config.stationaryFactor)
                        + " walk="
                        + num(config.walkingFactor)
                        + " run="
                        + num(config.runningFactor)
                        + " sneak="
                        + num(config.sneakSkillStrength)
                        + " inconspicuous="
                        + num(config.inconspicuousFactor)
                        + " conspicuous="
                        + num(config.conspicuousFactor)
                        + " goodSight="
                        + num(config.goodSightFactor)
                        + " poorSight="
                        + num(config.poorSightFactor)
                        + " rain="
                        + num(config.rainPenaltyStrength)
                        + " fog="
                        + num(config.fogPenaltyStrength)
                        + " eating="
                        + num(config.eatingVisionFactor));
    }

    private static String num(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static boolean debug() {
        try {
            Core core = Core.getInstance();
            return core != null && core.getDebug();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
