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
 * Per-evaluation exposure snapshot and engine-free factor math.
 *
 * <p>Vanilla spotting categories come from {@code spottedNew} and
 * {@code updateVisionRadius}
 * ({@code characters/IsoZombie.java:2141-2274, 5542-5573}). Curves are
 * independent of the random chance scale.
 *
 * <p>Fields and helpers are public. ByteBuddy inlines spotted advice into
 * {@code IsoZombie}, which cannot see private members here.
 */
public final class DetectionFactors {
    public static final String BLOCKED_NONE = "";
    public static final String BLOCKED_FLOOR = "floor";
    public static final String BLOCKED_RANGE = "range";
    public static final String BLOCKED_REAR = "rear";
    public static final String BLOCKED_LOS = "los";
    public static final String BLOCKED_VEHICLE = "vehicle";
    public static final String BLOCKED_LIGHT = "light";
    public static final String BLOCKED_FACTOR = "factor";

    public static final DetectionFactors UNSCALED = identity();
    public static final DetectionFactors NONE = blocked(BLOCKED_FACTOR);

    public boolean exposed;
    public String blockedReason = BLOCKED_NONE;
    public float distance;
    public float facingDot;
    public float effectiveRange;
    public float distanceFactor = 1.0f;
    public float angleFactor = 1.0f;
    public float lightFactor = 1.0f;
    public float movementFactor = 1.0f;
    public float postureAndSkillFactor = 1.0f;
    public float traitFactor = 1.0f;
    public float zombieSightFactor = 1.0f;
    public float weatherAndActivityFactor = 1.0f;
    public float coverFactor = 1.0f;
    public float clothingFactor = 1.0f;
    public float vehicleFactor = 1.0f;

    public DetectionFactors() {}

    public DetectionFactors reset() {
        exposed = false;
        blockedReason = BLOCKED_NONE;
        distance = 0.0f;
        facingDot = 0.0f;
        effectiveRange = 0.0f;
        distanceFactor = 1.0f;
        angleFactor = 1.0f;
        lightFactor = 1.0f;
        movementFactor = 1.0f;
        postureAndSkillFactor = 1.0f;
        traitFactor = 1.0f;
        zombieSightFactor = 1.0f;
        weatherAndActivityFactor = 1.0f;
        coverFactor = 1.0f;
        clothingFactor = 1.0f;
        vehicleFactor = 1.0f;
        return this;
    }

    public DetectionFactors copyTo(DetectionFactors dest) {
        if (dest == null) {
            return null;
        }
        dest.exposed = exposed;
        dest.blockedReason = blockedReason;
        dest.distance = distance;
        dest.facingDot = facingDot;
        dest.effectiveRange = effectiveRange;
        dest.distanceFactor = distanceFactor;
        dest.angleFactor = angleFactor;
        dest.lightFactor = lightFactor;
        dest.movementFactor = movementFactor;
        dest.postureAndSkillFactor = postureAndSkillFactor;
        dest.traitFactor = traitFactor;
        dest.zombieSightFactor = zombieSightFactor;
        dest.weatherAndActivityFactor = weatherAndActivityFactor;
        dest.coverFactor = coverFactor;
        dest.clothingFactor = clothingFactor;
        dest.vehicleFactor = vehicleFactor;
        return dest;
    }

    public float rawMultiplier() {
        return distanceFactor
                * angleFactor
                * lightFactor
                * movementFactor
                * postureAndSkillFactor
                * traitFactor
                * zombieSightFactor
                * weatherAndActivityFactor
                * coverFactor
                * clothingFactor
                * vehicleFactor;
    }

    public float combinedMultiplier() {
        if (!exposed) {
            return 0.0f;
        }
        return rawMultiplier();
    }

    public static DetectionFactors identity() {
        DetectionFactors factors = new DetectionFactors();
        factors.exposed = true;
        return factors;
    }

    public static DetectionFactors blocked(String reason) {
        DetectionFactors factors = new DetectionFactors();
        factors.block(reason);
        return factors;
    }

    public DetectionFactors block(String reason) {
        exposed = false;
        blockedReason = reason == null ? BLOCKED_FACTOR : reason;
        return this;
    }

    public DetectionFactors markExposed() {
        float combined = rawMultiplier();
        if (!Float.isFinite(combined) || combined <= 0.0f) {
            if (blockedReason == null || blockedReason.isEmpty()) {
                blockedReason = BLOCKED_FACTOR;
            }
            exposed = false;
            return this;
        }
        exposed = true;
        blockedReason = BLOCKED_NONE;
        return this;
    }

    /**
     * Vanilla zeros chance beyond {@code visionRadiusResult}
     * ({@code IsoZombie.java:2145-2146}). Inside range there is no extra
     * spottedNew distance falloff.
     */
    public static float distanceFactor(float distance, float range) {
        if (!Float.isFinite(distance) || !Float.isFinite(range) || range <= 0.0f || distance > range) {
            return 0.0f;
        }
        return 1.0f;
    }

    /**
     * Rear cutoff and close-range override from {@code IsoZombie.java:2149-2167}.
     * Front stays 1.0; {@code peripheralStrength} scales how fast gain falls
     * toward the rear without changing the front.
     */
    public static float angleFactor(float facingDot, float distance, DetectionConfig config) {
        if (config == null) {
            return 0.0f;
        }
        if (distance <= config.closeRange) {
            return 1.0f;
        }
        if (facingDot < config.rearCutoffDot) {
            return 0.0f;
        }
        float span = 1.0f - config.rearCutoffDot;
        if (span <= 0.0f) {
            return facingDot >= 1.0f ? 1.0f : 0.0f;
        }
        float t = clamp((facingDot - config.rearCutoffDot) / span, 0.0f, 1.0f);
        float exponent = 1.0f / config.peripheralStrength;
        return (float) Math.pow(t, exponent);
    }

    /**
     * Target-square light from {@code IsoZombie.java:2107-2115}, shaped by
     * {@code LightExponent} and floored at {@code MinimumLightFactor}.
     */
    public static float lightFactor(float light, DetectionConfig config) {
        if (config == null) {
            return 0.0f;
        }
        float clamped = clamp(light, 0.0f, 1.0f);
        float shaped = (float) Math.pow(clamped, config.lightExponent);
        if (!Float.isFinite(shaped)) {
            shaped = 0.0f;
        }
        return Math.max(shaped, config.minimumLightFactor);
    }

    /**
     * Movement bands from {@code IsoZombie.java:2178-2194}. Vanilla compares
     * last-frame length to 0.5 / 1.0 / 1.5; running uses {@code isRunning()}.
     */
    public static float movementFactor(
            float movementLength,
            boolean running,
            boolean sneaking,
            boolean aiming,
            float distance,
            DetectionConfig config) {
        if (config == null) {
            return 0.0f;
        }
        float factor;
        if (running || movementLength >= 1.5f) {
            factor = config.runningFactor;
        } else if (movementLength >= 0.5f) {
            factor = config.walkingFactor;
        } else {
            factor = config.stationaryFactor;
        }
        if (distance < DetectionConfig.CLOSE_RANGE_ACTIVITY_DISTANCE
                && (running || (!sneaking && !aiming))) {
            factor *= DetectionConfig.CLOSE_RANGE_ACTIVITY_BONUS;
        }
        return factor;
    }

    /**
     * {@code getSneakSpotMod()} applies only while sneaking
     * ({@code IsoZombie.java:2197-2202}). Strength 0 ignores skill; 1 is vanilla.
     */
    public static float postureAndSkillFactor(boolean sneaking, float sneakSpotMod, DetectionConfig config) {
        if (config == null) {
            return 1.0f;
        }
        if (!sneaking) {
            return 1.0f;
        }
        float mod = Float.isFinite(sneakSpotMod) ? sneakSpotMod : 1.0f;
        return 1.0f + (mod - 1.0f) * config.sneakSkillStrength;
    }

    public static float traitFactor(boolean inconspicuous, boolean conspicuous, DetectionConfig config) {
        if (config == null) {
            return 1.0f;
        }
        if (conspicuous) {
            return config.conspicuousFactor;
        }
        if (inconspicuous) {
            return config.inconspicuousFactor;
        }
        return 1.0f;
    }

    /**
     * Sight lore 1 is good, 3 is poor ({@code IsoZombie.java:2222-2228, 5560-5566}).
     */
    public static float zombieSightFactor(int sight, int loreSight, DetectionConfig config) {
        if (config == null) {
            return 1.0f;
        }
        if (sight == 1 || loreSight == 1) {
            return config.goodSightFactor;
        }
        if (sight == 3 || loreSight == 3) {
            return config.poorSightFactor;
        }
        return 1.0f;
    }

    /**
     * Rain and fog shrink range instead of gain. Eating and inactive scale
     * gain ({@code IsoZombie.java:2230-2232, 2272-2274}).
     */
    public static float weatherAndActivityFactor(boolean eating, boolean inactive, DetectionConfig config) {
        if (config == null) {
            return 1.0f;
        }
        float factor = 1.0f;
        if (eating) {
            factor *= config.eatingVisionFactor;
        }
        if (inactive) {
            factor *= DetectionConfig.INACTIVE_FACTOR;
        }
        return factor;
    }

    /**
     * Sneak-near-cover using {@code checkIsNearWall()} like spottedOld
     * ({@code IsoZombie.java:2720-2731}). Bonus {@code <= 1} means no cover.
     */
    public static float coverFactor(boolean sneaking, boolean sameSquare, float sneakTileBonus) {
        if (!sneaking || sameSquare) {
            return 1.0f;
        }
        if (!Float.isFinite(sneakTileBonus) || sneakTileBonus <= 1.0f) {
            return 1.0f;
        }
        return 1.0f / sneakTileBonus;
    }

    /**
     * Vanilla divides chance and radius by {@code getWornItemsVisionModifier()}
     * ({@code IsoZombie.java:2271, 5568}).
     */
    public static float clothingFactor(float wornItemsVisionModifier) {
        if (!Float.isFinite(wornItemsVisionModifier) || wornItemsVisionModifier <= 0.0f) {
            return 1.0f;
        }
        return 1.0f / wornItemsVisionModifier;
    }

    /**
     * Vehicle between zombie and a player on foot
     * ({@code IsoZombie.java:2262-2270}). {@code 0} is a visibility gate.
     */
    public static float vehicleFactor(boolean vehicleBetween, boolean playerInVehicle, float distance) {
        if (playerInVehicle || !vehicleBetween) {
            return 1.0f;
        }
        if (distance < DetectionConfig.VEHICLE_PARTIAL_DISTANCE) {
            return DetectionConfig.VEHICLE_PARTIAL_FACTOR;
        }
        return 0.0f;
    }

    /**
     * {@code updateVisionRadius} ({@code IsoZombie.java:5542-5573}) minus the
     * private method call. Weather, darkness, sight, clothing, and eating
     * shrink range; the result clamps to configured min/max.
     */
    public static float effectiveRange(
            float targetLight,
            float rainIntensity,
            float fogIntensity,
            int sight,
            int loreSight,
            float wornItemsVisionModifier,
            boolean eating,
            DetectionConfig config) {
        if (config == null) {
            return 0.0f;
        }
        float light = clamp(targetLight, 0.0f, 1.0f);
        float darknessPenalty = (1.0f - light) * DetectionConfig.DARKNESS_PENALTY_STRENGTH;
        float rainPenalty = clamp(rainIntensity, 0.0f, 1.0f) * config.rainPenaltyStrength;
        float fogPenalty = clamp(fogIntensity, 0.0f, 1.0f) * config.fogPenaltyStrength;
        float radius = config.maximumVisionRange - Math.max(darknessPenalty, rainPenalty + fogPenalty);
        radius *= zombieSightFactor(sight, loreSight, config);
        radius *= clothingFactor(wornItemsVisionModifier);
        if (eating) {
            radius *= config.eatingVisionFactor;
        }
        return clamp(radius, config.minimumVisionRange, config.maximumVisionRange);
    }

    public static float facingDot(float lookX, float lookY, float dx, float dy) {
        float lookLen = length(lookX, lookY);
        float targetLen = length(dx, dy);
        if (lookLen <= 0.0001f || targetLen <= 0.0001f) {
            return 1.0f;
        }
        return (lookX * dx + lookY * dy) / (lookLen * targetLen);
    }

    public static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
