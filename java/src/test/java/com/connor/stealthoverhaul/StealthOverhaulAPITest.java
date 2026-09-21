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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import me.zed_0xff.zombie_buddy.Exposer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StealthOverhaulAPITest {
    @BeforeEach
    void resetSingleton() {
        StealthOverhaulAPI.javaLoaded = false;
        IsoZombieSpottedPatch.spottingAdviceFailed = false;
        IsoZombieSpottedPatch.loggedFailure = false;
        AwarenessSystem.getInstance().clear();
        AwarenessSystem.getInstance().setConfig(DetectionConfig.defaults());
    }

    @AfterEach
    void clearLoadedFlag() {
        StealthOverhaulAPI.javaLoaded = false;
        IsoZombieSpottedPatch.spottingAdviceFailed = false;
        AwarenessSystem.getInstance().clear();
        AwarenessSystem.getInstance().setConfig(DetectionConfig.defaults());
    }

    @Test
    void luaClassAnnotationUsesBlankNameForSimpleGlobal() {
        Exposer.LuaClass luaClass = StealthOverhaulAPI.class.getAnnotation(Exposer.LuaClass.class);
        assertNotNull(luaClass);
        assertEquals("", luaClass.name());
    }

    @Test
    void publicApiMethodsAreStatic() {
        for (Method method : StealthOverhaulAPI.class.getDeclaredMethods()) {
            if (method.isSynthetic() || !Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            assertTrue(Modifier.isStatic(method.getModifiers()), method.getName());
        }
    }

    @Test
    void inactiveUntilJavaLoaded() {
        assertFalse(StealthOverhaulAPI.isPatchActive());
        assertEquals("java not loaded", StealthOverhaulAPI.explainPatchStatus());
        assertEquals(0.0f, StealthOverhaulAPI.getDetectionThreshold());
        assertEquals(0, StealthOverhaulAPI.getConfigRevision());
    }

    @Test
    void activeAfterJavaLoadedWithValidConfig() {
        StealthOverhaulAPI.javaLoaded = true;
        assertTrue(StealthOverhaulAPI.isPatchActive());
        assertEquals(1.0f, StealthOverhaulAPI.getDetectionThreshold());
        assertTrue(StealthOverhaulAPI.explainPatchStatus().startsWith("active"));
    }

    @Test
    void adviceFailureDisablesPatch() {
        StealthOverhaulAPI.javaLoaded = true;
        IsoZombieSpottedPatch.spottingAdviceFailed = true;
        assertFalse(StealthOverhaulAPI.isPatchActive());
        assertEquals(
                "spotted() advice failed; vanilla spotting continues",
                StealthOverhaulAPI.explainPatchStatus());
    }

    @Test
    void invalidConfigFailsClosed() {
        StealthOverhaulAPI.javaLoaded = true;
        AwarenessSystem.getInstance()
                .setConfig(new DetectionConfig(true, 0.0f, 0.35f, 0.10f, 0.50f, 0.25f, 1.0f, 30.0f));
        assertFalse(StealthOverhaulAPI.isPatchActive());
        assertEquals("invalid config", StealthOverhaulAPI.explainPatchStatus());
    }

    @Test
    void pairGettersAreZeroWithoutARecord() {
        StealthOverhaulAPI.javaLoaded = true;
        Object zombie = new Object();
        Object player = new Object();
        assertEquals(0.0f, StealthOverhaulAPI.getAwareness(zombie, player));
        assertEquals(StealthOverhaulAPI.STATE_UNAWARE, StealthOverhaulAPI.getAwarenessState(zombie, player));
        assertEquals(0.0f, StealthOverhaulAPI.getEffectiveRange(zombie, player));
        assertEquals(0.0f, StealthOverhaulAPI.getExposureRate(zombie, player));
        assertEquals("", StealthOverhaulAPI.getLastBlockedReason(zombie, player));
        assertFalse(StealthOverhaulAPI.wasLastExposed(zombie, player));
    }

    @Test
    void pairGettersReadExistingRecordWithoutCreating() {
        StealthOverhaulAPI.javaLoaded = true;
        Object zombie = new Object();
        Object player = new Object();
        AwarenessRecord record = AwarenessSystem.getInstance().getOrCreate(zombie, player);
        record.awareness = 0.42f;
        record.state = AwarenessState.SUSPICIOUS;
        record.lastEffectiveRange = 14.5f;
        record.lastGainMultiplier = 0.5f;
        record.lastDistance = 3.0f;
        record.lastFacingDot = 0.9f;
        record.lastExposed = true;
        record.lastBlockedReason = DetectionFactors.BLOCKED_NONE;

        assertEquals(0.42f, StealthOverhaulAPI.getAwareness(zombie, player), 1.0e-5f);
        assertEquals(StealthOverhaulAPI.STATE_SUSPICIOUS, StealthOverhaulAPI.getAwarenessState(zombie, player));
        assertEquals(14.5f, StealthOverhaulAPI.getEffectiveRange(zombie, player), 1.0e-5f);
        assertEquals(0.5f * 0.35f, StealthOverhaulAPI.getExposureRate(zombie, player), 1.0e-5f);
        assertEquals(0.5f, StealthOverhaulAPI.getLastGainMultiplier(zombie, player), 1.0e-5f);
        assertEquals(3.0f, StealthOverhaulAPI.getLastDistance(zombie, player), 1.0e-5f);
        assertEquals(0.9f, StealthOverhaulAPI.getLastFacingDot(zombie, player), 1.0e-5f);
        assertTrue(StealthOverhaulAPI.wasLastExposed(zombie, player));
        assertEquals(1, AwarenessSystem.getInstance().pairCount());
    }

    @Test
    void inactivePairGettersHideAuthoritativeState() {
        Object zombie = new Object();
        Object player = new Object();
        AwarenessRecord record = AwarenessSystem.getInstance().getOrCreate(zombie, player);
        record.awareness = 0.9f;
        record.state = AwarenessState.DETECTED;
        assertFalse(StealthOverhaulAPI.isPatchActive());
        assertEquals(0.0f, StealthOverhaulAPI.getAwareness(zombie, player));
        assertEquals(StealthOverhaulAPI.STATE_UNAWARE, StealthOverhaulAPI.getAwarenessState(zombie, player));
    }

    @Test
    void awarenessStateOrdinalsMatchApiConstants() {
        assertEquals(StealthOverhaulAPI.STATE_UNAWARE, AwarenessState.UNAWARE.ordinal());
        assertEquals(StealthOverhaulAPI.STATE_SUSPICIOUS, AwarenessState.SUSPICIOUS.ordinal());
        assertEquals(StealthOverhaulAPI.STATE_DETECTED, AwarenessState.DETECTED.ordinal());
    }

    @Test
    void configRevisionIncrementsOnReplace() {
        StealthOverhaulAPI.javaLoaded = true;
        int before = StealthOverhaulAPI.getConfigRevision();
        AwarenessSystem.getInstance().setConfig(DetectionConfig.defaults());
        assertEquals(before + 1, StealthOverhaulAPI.getConfigRevision());
    }

    @Test
    void angleFactorUsesConfigWithoutCreatingRecords() {
        StealthOverhaulAPI.javaLoaded = true;
        DetectionConfig config = DetectionConfig.defaults();
        assertEquals(
                DetectionFactors.angleFactor(1.0f, 10.0f, config),
                StealthOverhaulAPI.angleFactor(1.0f, 10.0f),
                1.0e-5f);
        assertEquals(0.0f, StealthOverhaulAPI.angleFactor(-0.41f, 2.0f), 0.0f);
        assertEquals(0, AwarenessSystem.getInstance().pairCount());
    }

    @Test
    void angleFactorIsZeroWhenInactive() {
        assertEquals(0.0f, StealthOverhaulAPI.angleFactor(1.0f, 10.0f), 0.0f);
    }

    @Test
    void getPotentialRangeDoesNotCreateRecords() {
        StealthOverhaulAPI.javaLoaded = true;
        Object zombie = new Object();
        Object player = new Object();
        assertEquals(0.0f, StealthOverhaulAPI.getPotentialRange(zombie, player), 0.0f);
        assertEquals(0, AwarenessSystem.getInstance().pairCount());
        assertNull(AwarenessSystem.getInstance().get(zombie, player));
    }

    @Test
    void pairGettersSkipDecayWhenExposureTimestampIsUnset() {
        StealthOverhaulAPI.javaLoaded = true;
        Object zombie = new Object();
        Object player = new Object();
        AwarenessRecord record = AwarenessSystem.getInstance().getOrCreate(zombie, player);
        record.awareness = 0.42f;
        record.state = AwarenessState.SUSPICIOUS;
        assertTrue(Double.isNaN(record.lastExposureHours));
        assertEquals(0.42f, StealthOverhaulAPI.getAwareness(zombie, player), 1.0e-5f);
        assertEquals(StealthOverhaulAPI.STATE_SUSPICIOUS, StealthOverhaulAPI.getAwarenessState(zombie, player));
        assertEquals(1, AwarenessSystem.getInstance().pairCount());
    }
}
