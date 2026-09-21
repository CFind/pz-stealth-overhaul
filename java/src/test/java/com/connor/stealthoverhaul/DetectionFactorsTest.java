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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectionFactorsTest {
    private static final DetectionConfig CONFIG = DetectionConfig.defaults();
    private static final float EPS = 1.0e-5f;

    @Test
    void identityCombinedMultiplierIsOne() {
        assertTrue(DetectionFactors.UNSCALED.exposed);
        assertEquals(1.0f, DetectionFactors.UNSCALED.combinedMultiplier(), EPS);
    }

    @Test
    void blockedCombinedMultiplierIsZero() {
        assertFalse(DetectionFactors.NONE.exposed);
        assertEquals(0.0f, DetectionFactors.NONE.combinedMultiplier(), 0.0f);
    }

    @Test
    void distanceOutsideRangeIsZero() {
        assertEquals(0.0f, DetectionFactors.distanceFactor(21.0f, 20.0f), 0.0f);
        assertEquals(1.0f, DetectionFactors.distanceFactor(20.0f, 20.0f), 0.0f);
        assertEquals(1.0f, DetectionFactors.distanceFactor(0.0f, 20.0f), 0.0f);
    }

    @Test
    void rearCutoffBlocksBeyondCloseRange() {
        assertEquals(0.0f, DetectionFactors.angleFactor(-0.41f, 2.0f, CONFIG), 0.0f);
        assertEquals(1.0f, DetectionFactors.angleFactor(-1.0f, 0.4f, CONFIG), 0.0f);
        assertEquals(1.0f, DetectionFactors.angleFactor(1.0f, 10.0f, CONFIG), EPS);
    }

    @Test
    void peripheralStrengthDoesNotChangeFrontGain() {
        DetectionConfig wide = DetectionConfig.copyDefaults().peripheralStrength(2.0f).build();
        DetectionConfig narrow = DetectionConfig.copyDefaults().peripheralStrength(0.5f).build();
        assertEquals(1.0f, DetectionFactors.angleFactor(1.0f, 8.0f, wide), EPS);
        assertEquals(1.0f, DetectionFactors.angleFactor(1.0f, 8.0f, narrow), EPS);
        float sideDot = 0.0f;
        float wideSide = DetectionFactors.angleFactor(sideDot, 8.0f, wide);
        float narrowSide = DetectionFactors.angleFactor(sideDot, 8.0f, narrow);
        assertTrue(wideSide > narrowSide);
        assertTrue(wideSide < 1.0f);
        assertTrue(narrowSide > 0.0f);
    }

    @Test
    void lightExponentAndMinimumShapeDarkness() {
        assertEquals(0.0f, DetectionFactors.lightFactor(0.0f, CONFIG), 0.0f);
        assertEquals(1.0f, DetectionFactors.lightFactor(1.0f, CONFIG), EPS);
        DetectionConfig floor = DetectionConfig.copyDefaults().minimumLightFactor(0.2f).build();
        assertEquals(0.2f, DetectionFactors.lightFactor(0.0f, floor), EPS);
        DetectionConfig steep = DetectionConfig.copyDefaults().lightExponent(2.0f).build();
        assertEquals(0.25f, DetectionFactors.lightFactor(0.5f, steep), EPS);
    }

    @Test
    void movementUsesVanillaLengthBandsAndCloseBonus() {
        float walkFar = DetectionFactors.movementFactor(0.5f, false, false, false, 10.0f, CONFIG);
        assertEquals(1.0f, walkFar, EPS);
        float runFar = DetectionFactors.movementFactor(1.5f, true, false, false, 10.0f, CONFIG);
        assertEquals(2.0f, runFar, EPS);
        float stillClose = DetectionFactors.movementFactor(0.0f, false, false, false, 4.0f, CONFIG);
        assertEquals(0.8f * 3.0f, stillClose, EPS);
        float sneakClose = DetectionFactors.movementFactor(0.0f, false, true, false, 4.0f, CONFIG);
        assertEquals(0.8f, sneakClose, EPS);
    }

    @Test
    void sneakSkillStrengthScalesOnlyWhileSneaking() {
        assertEquals(1.0f, DetectionFactors.postureAndSkillFactor(false, 0.4f, CONFIG), EPS);
        assertEquals(0.4f, DetectionFactors.postureAndSkillFactor(true, 0.4f, CONFIG), EPS);
        DetectionConfig ignoreSkill = DetectionConfig.copyDefaults().sneakSkillStrength(0.0f).build();
        assertEquals(1.0f, DetectionFactors.postureAndSkillFactor(true, 0.4f, ignoreSkill), EPS);
    }

    @Test
    void traitsMatchDocumentedBaselines() {
        assertEquals(1.0f, DetectionFactors.traitFactor(false, false, CONFIG), EPS);
        assertEquals(0.8f, DetectionFactors.traitFactor(true, false, CONFIG), EPS);
        assertEquals(1.2f, DetectionFactors.traitFactor(false, true, CONFIG), EPS);
        assertEquals(1.2f, DetectionFactors.traitFactor(true, true, CONFIG), EPS);
    }

    @Test
    void zombieSightUsesGoodAndPoorLore() {
        assertEquals(1.75f, DetectionFactors.zombieSightFactor(1, 2, CONFIG), EPS);
        assertEquals(1.75f, DetectionFactors.zombieSightFactor(2, 1, CONFIG), EPS);
        assertEquals(0.35f, DetectionFactors.zombieSightFactor(3, 2, CONFIG), EPS);
        assertEquals(1.0f, DetectionFactors.zombieSightFactor(2, 2, CONFIG), EPS);
    }

    @Test
    void eatingAndInactiveScaleActivity() {
        assertEquals(1.0f, DetectionFactors.weatherAndActivityFactor(false, false, CONFIG), EPS);
        assertEquals(0.5f, DetectionFactors.weatherAndActivityFactor(true, false, CONFIG), EPS);
        assertEquals(0.25f, DetectionFactors.weatherAndActivityFactor(false, true, CONFIG), EPS);
        assertEquals(0.125f, DetectionFactors.weatherAndActivityFactor(true, true, CONFIG), EPS);
    }

    @Test
    void coverOnlyAppliesWhenSneakingOffSquareWithBonus() {
        assertEquals(1.0f, DetectionFactors.coverFactor(false, false, 6.0f), EPS);
        assertEquals(1.0f, DetectionFactors.coverFactor(true, true, 6.0f), EPS);
        assertEquals(1.0f, DetectionFactors.coverFactor(true, false, 1.0f), EPS);
        assertEquals(1.0f / 6.0f, DetectionFactors.coverFactor(true, false, 6.0f), EPS);
    }

    @Test
    void clothingInvertsWornVisionModifier() {
        assertEquals(1.0f, DetectionFactors.clothingFactor(1.0f), EPS);
        assertEquals(0.5f, DetectionFactors.clothingFactor(2.0f), EPS);
        assertEquals(1.0f, DetectionFactors.clothingFactor(0.0f), EPS);
    }

    @Test
    void vehicleBlocksBeyondPartialDistance() {
        assertEquals(1.0f, DetectionFactors.vehicleFactor(false, false, 8.0f), EPS);
        assertEquals(1.0f, DetectionFactors.vehicleFactor(true, true, 8.0f), EPS);
        assertEquals(0.5f, DetectionFactors.vehicleFactor(true, false, 1.0f), EPS);
        assertEquals(0.0f, DetectionFactors.vehicleFactor(true, false, 2.0f), EPS);
    }

    @Test
    void effectiveRangeClampsAfterWeatherAndSight() {
        float daylight = DetectionFactors.effectiveRange(1.0f, 0.0f, 0.0f, 2, 2, 1.0f, false, CONFIG);
        assertEquals(20.0f, daylight, EPS);
        float dark = DetectionFactors.effectiveRange(0.0f, 0.0f, 0.0f, 2, 2, 1.0f, false, CONFIG);
        assertEquals(15.0f, dark, EPS);
        float poorDark = DetectionFactors.effectiveRange(0.0f, 0.0f, 0.0f, 3, 2, 1.0f, false, CONFIG);
        assertEquals(10.0f, poorDark, EPS);
        float fog = DetectionFactors.effectiveRange(1.0f, 0.0f, 1.0f, 2, 2, 1.0f, false, CONFIG);
        assertEquals(13.0f, fog, EPS);
    }

    @Test
    void facingDotIsOneWhenLookingAtTarget() {
        assertEquals(1.0f, DetectionFactors.facingDot(1.0f, 0.0f, 4.0f, 0.0f), EPS);
        assertEquals(-1.0f, DetectionFactors.facingDot(1.0f, 0.0f, -4.0f, 0.0f), EPS);
        assertEquals(0.0f, DetectionFactors.facingDot(1.0f, 0.0f, 0.0f, 4.0f), EPS);
    }

    @Test
    void invertedVisionRangeFailsClosed() {
        DetectionConfig invalid = DetectionConfig.copyDefaults()
                .minimumVisionRange(20.0f)
                .maximumVisionRange(10.0f)
                .build();
        assertFalse(invalid.isValid());
    }
}
