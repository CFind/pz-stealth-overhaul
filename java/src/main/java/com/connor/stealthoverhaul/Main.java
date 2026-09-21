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
 * Optional ZombieBuddy entry point. The class must be named {@code Main} and
 * live in {@code javaPkgName} ({@code com.connor.stealthoverhaul}).
 */
public final class Main {
    public static void main(String[] args) {
        DetectionConfig config = DetectionConfig.defaults();
        System.out.println(
                "[StealthOverhaul] Java loaded enabled="
                        + config.enabled
                        + " detectionThreshold="
                        + config.detectionThreshold
                        + " baseGainPerSecond="
                        + config.baseGainPerSecond
                        + " maxEvaluationDelta="
                        + config.maxEvaluationDelta
                        + " visionRange="
                        + config.minimumVisionRange
                        + "-"
                        + config.maximumVisionRange
                        + " rearCutoffDot="
                        + config.rearCutoffDot
                        + " closeRange="
                        + config.closeRange);
        IsoZombieSpottedPatch.bindBookkeepingHandles();
        StealthOverhaulAPI.javaLoaded = true;
    }

    private Main() {}
}
