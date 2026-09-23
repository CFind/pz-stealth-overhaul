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

class SandboxConfigTest {
    @Test
    void clampPullsValuesIntoTheDeclaredRange() {
        assertEquals(0.05f, SandboxConfig.clamp(Double.NaN, 0.05, 10.0), 0.0f);
        assertEquals(0.05f, SandboxConfig.clamp(Double.NEGATIVE_INFINITY, 0.05, 10.0), 0.0f);
        assertEquals(10.0f, SandboxConfig.clamp(Double.POSITIVE_INFINITY, 0.05, 10.0), 0.0f);
        assertEquals(0.05f, SandboxConfig.clamp(0.0, 0.05, 10.0), 0.0f);
        assertEquals(10.0f, SandboxConfig.clamp(40.0, 0.05, 10.0), 0.0f);
        assertEquals(1.0f, SandboxConfig.clamp(1.0, 0.05, 10.0), 0.0f);
        assertEquals(0, SandboxConfig.clampInt(-3, 0, 8));
        assertEquals(8, SandboxConfig.clampInt(12, 0, 8));
    }

    @Test
    void proposedBaselinesMatchTheJavaDefaults() {
        DetectionConfig config = SandboxConfig.snapshot(
                true,
                1.0,
                0.35,
                0.10,
                0.50,
                0.25,
                1.0,
                30.0,
                10.0,
                20.0,
                0.5,
                1,
                -0.4,
                1.0,
                0.0,
                1.0,
                0.8,
                1.0,
                2.0,
                1.0,
                0.8,
                1.2,
                1.75,
                0.35,
                2.5,
                7.0,
                0.5);
        assertTrue(config.matches(DetectionConfig.defaults()));
        assertTrue(config.isValid());
    }

    @Test
    void invertedVisionRangeFailsClosed() {
        DetectionConfig config = SandboxConfig.snapshot(
                true,
                1.0,
                0.35,
                0.10,
                0.50,
                0.25,
                1.0,
                30.0,
                40.0,
                20.0,
                0.5,
                1,
                -0.4,
                1.0,
                0.0,
                1.0,
                0.8,
                1.0,
                2.0,
                1.0,
                0.8,
                1.2,
                1.75,
                0.35,
                2.5,
                7.0,
                0.5);
        assertFalse(config.isValid());
    }
}
