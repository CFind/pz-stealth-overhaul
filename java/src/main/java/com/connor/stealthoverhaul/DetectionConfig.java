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
 * Immutable detection configuration snapshot. Defaults match
 * {@code docs/mod.md}; sandbox wiring is a later milestone.
 *
 * <p>Fields stay public. ZombieBuddy inlines spotted advice into
 * {@code IsoZombie}, which cannot see private members of this package.
 */
public final class DetectionConfig {
    public static final float DARKNESS_PENALTY_STRENGTH = 5.0f;
    public static final float INACTIVE_FACTOR = 0.25f;
    public static final float CLOSE_RANGE_ACTIVITY_DISTANCE = 5.0f;
    public static final float CLOSE_RANGE_ACTIVITY_BONUS = 3.0f;
    public static final float VEHICLE_PARTIAL_DISTANCE = 1.5f;
    public static final float VEHICLE_PARTIAL_FACTOR = 0.5f;

    private static final DetectionConfig DEFAULTS = new Builder().build();

    public final boolean enabled;
    public final float detectionThreshold;
    public final float baseGainPerSecond;
    public final float maxEvaluationDelta;
    public final float decayDelaySeconds;
    public final float decayPerSecond;
    public final float reacquireThreshold;
    public final float recordExpirySeconds;
    public final float minimumVisionRange;
    public final float maximumVisionRange;
    public final float closeRange;
    public final int maximumFloorDifference;
    public final float rearCutoffDot;
    public final float peripheralStrength;
    public final float minimumLightFactor;
    public final float lightExponent;
    public final float stationaryFactor;
    public final float walkingFactor;
    public final float runningFactor;
    public final float sneakSkillStrength;
    public final float inconspicuousFactor;
    public final float conspicuousFactor;
    public final float goodSightFactor;
    public final float poorSightFactor;
    public final float rainPenaltyStrength;
    public final float fogPenaltyStrength;
    public final float eatingVisionFactor;

    public DetectionConfig() {
        this(DEFAULTS);
    }

    public DetectionConfig(DetectionConfig other) {
        this.enabled = other.enabled;
        this.detectionThreshold = other.detectionThreshold;
        this.baseGainPerSecond = other.baseGainPerSecond;
        this.maxEvaluationDelta = other.maxEvaluationDelta;
        this.decayDelaySeconds = other.decayDelaySeconds;
        this.decayPerSecond = other.decayPerSecond;
        this.reacquireThreshold = other.reacquireThreshold;
        this.recordExpirySeconds = other.recordExpirySeconds;
        this.minimumVisionRange = other.minimumVisionRange;
        this.maximumVisionRange = other.maximumVisionRange;
        this.closeRange = other.closeRange;
        this.maximumFloorDifference = other.maximumFloorDifference;
        this.rearCutoffDot = other.rearCutoffDot;
        this.peripheralStrength = other.peripheralStrength;
        this.minimumLightFactor = other.minimumLightFactor;
        this.lightExponent = other.lightExponent;
        this.stationaryFactor = other.stationaryFactor;
        this.walkingFactor = other.walkingFactor;
        this.runningFactor = other.runningFactor;
        this.sneakSkillStrength = other.sneakSkillStrength;
        this.inconspicuousFactor = other.inconspicuousFactor;
        this.conspicuousFactor = other.conspicuousFactor;
        this.goodSightFactor = other.goodSightFactor;
        this.poorSightFactor = other.poorSightFactor;
        this.rainPenaltyStrength = other.rainPenaltyStrength;
        this.fogPenaltyStrength = other.fogPenaltyStrength;
        this.eatingVisionFactor = other.eatingVisionFactor;
    }

    public DetectionConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.detectionThreshold = builder.detectionThreshold;
        this.baseGainPerSecond = builder.baseGainPerSecond;
        this.maxEvaluationDelta = builder.maxEvaluationDelta;
        this.decayDelaySeconds = builder.decayDelaySeconds;
        this.decayPerSecond = builder.decayPerSecond;
        this.reacquireThreshold = builder.reacquireThreshold;
        this.recordExpirySeconds = builder.recordExpirySeconds;
        this.minimumVisionRange = builder.minimumVisionRange;
        this.maximumVisionRange = builder.maximumVisionRange;
        this.closeRange = builder.closeRange;
        this.maximumFloorDifference = builder.maximumFloorDifference;
        this.rearCutoffDot = builder.rearCutoffDot;
        this.peripheralStrength = builder.peripheralStrength;
        this.minimumLightFactor = builder.minimumLightFactor;
        this.lightExponent = builder.lightExponent;
        this.stationaryFactor = builder.stationaryFactor;
        this.walkingFactor = builder.walkingFactor;
        this.runningFactor = builder.runningFactor;
        this.sneakSkillStrength = builder.sneakSkillStrength;
        this.inconspicuousFactor = builder.inconspicuousFactor;
        this.conspicuousFactor = builder.conspicuousFactor;
        this.goodSightFactor = builder.goodSightFactor;
        this.poorSightFactor = builder.poorSightFactor;
        this.rainPenaltyStrength = builder.rainPenaltyStrength;
        this.fogPenaltyStrength = builder.fogPenaltyStrength;
        this.eatingVisionFactor = builder.eatingVisionFactor;
    }

    /**
     * Milestone 3 eight-field constructor. Remaining factor fields use
     * documented defaults so existing tests stay valid.
     */
    public DetectionConfig(
            boolean enabled,
            float detectionThreshold,
            float baseGainPerSecond,
            float maxEvaluationDelta,
            float decayDelaySeconds,
            float decayPerSecond,
            float reacquireThreshold,
            float recordExpirySeconds) {
        this(copyDefaults()
                .enabled(enabled)
                .detectionThreshold(detectionThreshold)
                .baseGainPerSecond(baseGainPerSecond)
                .maxEvaluationDelta(maxEvaluationDelta)
                .decayDelaySeconds(decayDelaySeconds)
                .decayPerSecond(decayPerSecond)
                .reacquireThreshold(reacquireThreshold)
                .recordExpirySeconds(recordExpirySeconds));
    }

    public static DetectionConfig defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder copyDefaults() {
        return new Builder();
    }

    /**
     * Fail closed: invalid numbers must not gate spotting.
     */
    public boolean isValid() {
        return isPositiveFinite(detectionThreshold)
                && isNonNegativeFinite(baseGainPerSecond)
                && isPositiveFinite(maxEvaluationDelta)
                && isNonNegativeFinite(decayDelaySeconds)
                && isNonNegativeFinite(decayPerSecond)
                && isPositiveFinite(reacquireThreshold)
                && isPositiveFinite(recordExpirySeconds)
                && isPositiveFinite(minimumVisionRange)
                && isPositiveFinite(maximumVisionRange)
                && minimumVisionRange <= maximumVisionRange
                && isNonNegativeFinite(closeRange)
                && maximumFloorDifference >= 0
                && Float.isFinite(rearCutoffDot)
                && rearCutoffDot >= -1.0f
                && rearCutoffDot <= 1.0f
                && isPositiveFinite(peripheralStrength)
                && isNonNegativeFinite(minimumLightFactor)
                && isPositiveFinite(lightExponent)
                && isNonNegativeFinite(stationaryFactor)
                && isNonNegativeFinite(walkingFactor)
                && isNonNegativeFinite(runningFactor)
                && isNonNegativeFinite(sneakSkillStrength)
                && isNonNegativeFinite(inconspicuousFactor)
                && isNonNegativeFinite(conspicuousFactor)
                && isPositiveFinite(goodSightFactor)
                && isPositiveFinite(poorSightFactor)
                && isNonNegativeFinite(rainPenaltyStrength)
                && isNonNegativeFinite(fogPenaltyStrength)
                && isNonNegativeFinite(eatingVisionFactor);
    }

    public static boolean isPositiveFinite(float value) {
        return value > 0.0f && Float.isFinite(value);
    }

    public static boolean isNonNegativeFinite(float value) {
        return value >= 0.0f && Float.isFinite(value);
    }

    /**
     * Mutable builder. Fields and setters are public so patched game code
     * never hits an access check on this type.
     */
    public static final class Builder {
        public boolean enabled = true;
        public float detectionThreshold = 1.0f;
        public float baseGainPerSecond = 0.35f;
        public float maxEvaluationDelta = 0.10f;
        public float decayDelaySeconds = 0.50f;
        public float decayPerSecond = 0.25f;
        public float reacquireThreshold = 1.0f;
        public float recordExpirySeconds = 30.0f;
        public float minimumVisionRange = 10.0f;
        public float maximumVisionRange = 20.0f;
        public float closeRange = 0.5f;
        public int maximumFloorDifference = 1;
        public float rearCutoffDot = -0.4f;
        public float peripheralStrength = 1.0f;
        public float minimumLightFactor = 0.0f;
        public float lightExponent = 1.0f;
        public float stationaryFactor = 0.8f;
        public float walkingFactor = 1.0f;
        public float runningFactor = 2.0f;
        public float sneakSkillStrength = 1.0f;
        public float inconspicuousFactor = 0.8f;
        public float conspicuousFactor = 1.2f;
        public float goodSightFactor = 1.75f;
        public float poorSightFactor = 0.35f;
        public float rainPenaltyStrength = 2.5f;
        public float fogPenaltyStrength = 7.0f;
        public float eatingVisionFactor = 0.5f;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder detectionThreshold(float detectionThreshold) {
            this.detectionThreshold = detectionThreshold;
            return this;
        }

        public Builder baseGainPerSecond(float baseGainPerSecond) {
            this.baseGainPerSecond = baseGainPerSecond;
            return this;
        }

        public Builder maxEvaluationDelta(float maxEvaluationDelta) {
            this.maxEvaluationDelta = maxEvaluationDelta;
            return this;
        }

        public Builder decayDelaySeconds(float decayDelaySeconds) {
            this.decayDelaySeconds = decayDelaySeconds;
            return this;
        }

        public Builder decayPerSecond(float decayPerSecond) {
            this.decayPerSecond = decayPerSecond;
            return this;
        }

        public Builder reacquireThreshold(float reacquireThreshold) {
            this.reacquireThreshold = reacquireThreshold;
            return this;
        }

        public Builder recordExpirySeconds(float recordExpirySeconds) {
            this.recordExpirySeconds = recordExpirySeconds;
            return this;
        }

        public Builder minimumVisionRange(float minimumVisionRange) {
            this.minimumVisionRange = minimumVisionRange;
            return this;
        }

        public Builder maximumVisionRange(float maximumVisionRange) {
            this.maximumVisionRange = maximumVisionRange;
            return this;
        }

        public Builder closeRange(float closeRange) {
            this.closeRange = closeRange;
            return this;
        }

        public Builder maximumFloorDifference(int maximumFloorDifference) {
            this.maximumFloorDifference = maximumFloorDifference;
            return this;
        }

        public Builder rearCutoffDot(float rearCutoffDot) {
            this.rearCutoffDot = rearCutoffDot;
            return this;
        }

        public Builder peripheralStrength(float peripheralStrength) {
            this.peripheralStrength = peripheralStrength;
            return this;
        }

        public Builder minimumLightFactor(float minimumLightFactor) {
            this.minimumLightFactor = minimumLightFactor;
            return this;
        }

        public Builder lightExponent(float lightExponent) {
            this.lightExponent = lightExponent;
            return this;
        }

        public Builder stationaryFactor(float stationaryFactor) {
            this.stationaryFactor = stationaryFactor;
            return this;
        }

        public Builder walkingFactor(float walkingFactor) {
            this.walkingFactor = walkingFactor;
            return this;
        }

        public Builder runningFactor(float runningFactor) {
            this.runningFactor = runningFactor;
            return this;
        }

        public Builder sneakSkillStrength(float sneakSkillStrength) {
            this.sneakSkillStrength = sneakSkillStrength;
            return this;
        }

        public Builder inconspicuousFactor(float inconspicuousFactor) {
            this.inconspicuousFactor = inconspicuousFactor;
            return this;
        }

        public Builder conspicuousFactor(float conspicuousFactor) {
            this.conspicuousFactor = conspicuousFactor;
            return this;
        }

        public Builder goodSightFactor(float goodSightFactor) {
            this.goodSightFactor = goodSightFactor;
            return this;
        }

        public Builder poorSightFactor(float poorSightFactor) {
            this.poorSightFactor = poorSightFactor;
            return this;
        }

        public Builder rainPenaltyStrength(float rainPenaltyStrength) {
            this.rainPenaltyStrength = rainPenaltyStrength;
            return this;
        }

        public Builder fogPenaltyStrength(float fogPenaltyStrength) {
            this.fogPenaltyStrength = fogPenaltyStrength;
            return this;
        }

        public Builder eatingVisionFactor(float eatingVisionFactor) {
            this.eatingVisionFactor = eatingVisionFactor;
            return this;
        }

        public DetectionConfig build() {
            return new DetectionConfig(this);
        }
    }
}
