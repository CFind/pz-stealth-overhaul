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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import me.zed_0xff.zombie_buddy.Patch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwarenessSystemTest {
    private static final float TICK = 0.10f;
    private static final float HOUR = 1.0f / 3600.0f;

    @Test
    void namedTypesAreConstructable() {
        assertDoesNotThrow(
                () -> {
                    new DetectionConfig();
                    new DetectionFactors();
                    new AwarenessRecord();
                    new AwarenessSystem();
                    new StealthOverhaulAPI();
                    new IsoZombieSpottedPatch();
                });
    }

    @Test
    void spottedPatchIsAdviceOnIsoZombieDispatcher() {
        Patch patch = IsoZombieSpottedPatch.class.getAnnotation(Patch.class);
        assertNotNull(patch);
        assertEquals("zombie.characters.IsoZombie", patch.className());
        assertEquals("spotted", patch.methodName());
        assertTrue(patch.isAdvice());
    }

    @Test
    void spottedEnterCanSkipAndRewriteForced() throws Exception {
        Method enter = IsoZombieSpottedPatch.class.getDeclaredMethod(
                "enter", Object.class, Object.class, boolean.class);
        Patch.OnEnter onEnter = enter.getAnnotation(Patch.OnEnter.class);
        assertNotNull(onEnter);
        assertTrue(onEnter.skipOn());

        Patch.Argument forced = argumentAnnotation(enter, 2);
        assertNotNull(forced);
        assertEquals(1, forced.value());
        assertFalse(forced.readOnly());

        Method exit = IsoZombieSpottedPatch.class.getDeclaredMethod("exit", Object.class, Object.class);
        assertNotNull(exit.getAnnotation(Patch.OnExit.class));
    }

    @Test
    void spottedAdviceMembersArePublic() {
        for (Field field : IsoZombieSpottedPatch.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            int modifiers = field.getModifiers();
            assertTrue(Modifier.isPublic(modifiers), field.getName() + " must be public for ByteBuddy inlining");
            assertTrue(Modifier.isStatic(modifiers), field.getName());
        }
        for (Method method : IsoZombieSpottedPatch.class.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            int modifiers = method.getModifiers();
            assertTrue(Modifier.isPublic(modifiers), method.getName() + " must be public for ByteBuddy inlining");
            assertTrue(Modifier.isStatic(modifiers), method.getName());
        }
    }

    @Test
    void defaultConfigIsValidMilestone3Baseline() {
        DetectionConfig config = DetectionConfig.defaults();
        assertTrue(config.enabled);
        assertTrue(config.isValid());
        assertEquals(1.0f, config.detectionThreshold);
        assertEquals(0.35f, config.baseGainPerSecond);
        assertEquals(0.10f, config.maxEvaluationDelta);
        assertEquals(10.0f, config.minimumVisionRange);
        assertEquals(20.0f, config.maximumVisionRange);
        assertEquals(-0.4f, config.rearCutoffDot);
        assertEquals(0.5f, config.closeRange);
        assertEquals(1.75f, config.goodSightFactor);
        assertEquals(0.35f, config.poorSightFactor);
    }

    @Test
    void invalidThresholdFailsClosed() {
        DetectionConfig invalid = new DetectionConfig(
                true, 0.0f, 0.35f, 0.10f, 0.50f, 0.25f, 1.0f, 30.0f);
        assertFalse(invalid.isValid());
    }

    @Test
    void gainReachesThresholdInExpectedTicks() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        int ticksToThreshold = ticksUntilPromote(system, record);
        // 1.0 / (0.35 * 0.10) = 28.57, so the 29th capped tick promotes.
        assertEquals(29, ticksToThreshold);
        assertEquals(1.0f, record.getAwareness(), 1.0e-5f);
        assertEquals(AwarenessState.SUSPICIOUS, record.getState());
    }

    @Test
    void stallCannotInstantDetect() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        boolean promote = system.evaluate(record, 10.0f, 0.0, true, false);
        assertFalse(promote);
        assertEquals(0.35f * 0.10f, record.getAwareness(), 1.0e-5f);
    }

    @Test
    void sameWorldAgeDoesNotDoubleGain() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        system.evaluate(record, TICK, 1.0, true, false);
        float afterFirst = record.getAwareness();
        system.evaluate(record, TICK, 1.0, true, false);
        assertEquals(afterFirst, record.getAwareness(), 0.0f);
    }

    @Test
    void decayAppliesAfterDelayWhenExposureGaps() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        system.evaluate(record, TICK, 0.0, true, false);
        float afterGain = record.getAwareness();
        assertEquals(0.35f * TICK, afterGain, 1.0e-5f);

        // 0.5s delay + 0.12s decay at 0.25/s = 0.03 removed.
        double laterHours = 0.62 / 3600.0;
        boolean promote = system.evaluate(record, TICK, laterHours, false, false);
        assertFalse(promote);
        assertEquals(afterGain - 0.03f, record.getAwareness(), 1.0e-4f);
    }

    @Test
    void lazyDecayIsIdempotentAtTheSameWorldAge() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        system.evaluate(record, TICK, 0.0, true, false);
        double laterHours = 0.62 / 3600.0;
        system.refreshForRead(record, laterHours);
        float once = record.getAwareness();
        system.refreshForRead(record, laterHours);
        assertEquals(once, record.getAwareness(), 0.0f);
        assertEquals(AwarenessState.SUSPICIOUS, record.getState());
    }

    @Test
    void refreshForReadDecaysWithoutCreatingPairs() {
        AwarenessSystem system = new AwarenessSystem();
        Object zombie = new Object();
        Object player = new Object();
        AwarenessRecord record = system.getOrCreate(zombie, player);
        system.evaluate(record, TICK, 0.0, true, false);
        system.refreshForRead(record, 10.0 * HOUR);
        assertEquals(0.0f, record.getAwareness(), 0.0f);
        assertEquals(AwarenessState.UNAWARE, record.getState());
        assertEquals(1, system.pairCount());
        assertSame(record, system.get(zombie, player));
    }

    @Test
    void alreadyTargetedPairPromotesEvenBelowThreshold() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        assertTrue(system.evaluate(record, TICK, 0.0, true, true));
        assertTrue(record.getAwareness() < 1.0f);
    }

    @Test
    void alreadyTargetedPairWithoutExposureDoesNotPromote() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        assertFalse(system.evaluate(record, TICK, 0.0, false, true));
        assertEquals(0.0f, record.getAwareness(), 0.0f);
    }

    @Test
    void markAcceptedSetsDetected() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        system.markAccepted(record, true);
        assertTrue(record.isLastAcceptedTarget());
        assertEquals(AwarenessState.DETECTED, record.getState());
    }

    @Test
    void rejectedPromoteDoesNotClearAnExistingDetection() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        system.markAccepted(record, true);
        system.markAccepted(record, false);
        assertTrue(record.isLastAcceptedTarget());
        assertEquals(AwarenessState.DETECTED, record.getState());
    }

    @Test
    void fullAwarenessWithoutExposureDoesNotPromote() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        record.awareness = 1.0f;
        record.awarenessAtExposure = 1.0f;
        record.lastExposureHours = 0.0;
        record.state = AwarenessState.SUSPICIOUS;
        assertFalse(system.evaluate(record, TICK, 0.0, false, false));
        assertEquals(1.0f, record.getAwareness(), 1.0e-5f);
        assertEquals(AwarenessState.SUSPICIOUS, record.getState());
    }

    @Test
    void detectedPairStaysDetectedWhileVanillaKeepsTheTarget() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        system.markAccepted(record, true);
        record.awareness = 1.0f;
        record.awarenessAtExposure = 1.0f;
        record.lastExposureHours = 0.0;

        boolean promote = system.evaluate(record, TICK, 10.0 * HOUR, false, true);
        assertFalse(promote);
        assertEquals(0.0f, record.getAwareness(), 1.0e-4f);
        assertEquals(AwarenessState.DETECTED, record.getState());
        assertTrue(record.isLastAcceptedTarget());

        system.refreshForRead(record, 10.0 * HOUR);
        assertEquals(AwarenessState.DETECTED, record.getState());
    }

    @Test
    void detectedPairDropsOnlyAfterVanillaDropsTheTarget() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        system.markAccepted(record, true);
        record.awareness = 1.0f;
        record.awarenessAtExposure = 1.0f;
        record.lastExposureHours = 0.0;

        boolean promote = system.evaluate(record, TICK, 10.0 * HOUR, false, false);
        assertFalse(promote);
        assertEquals(0.0f, record.getAwareness(), 1.0e-4f);
        assertEquals(AwarenessState.UNAWARE, record.getState());
        assertFalse(record.isLastAcceptedTarget());
    }

    @Test
    void returnToExposureRefillsWithoutChangingAnExistingTarget() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        system.markAccepted(record, true);
        record.awareness = 1.0f;
        record.awarenessAtExposure = 1.0f;
        record.lastExposureHours = 0.0;
        system.evaluate(record, TICK, 10.0 * HOUR, false, true);

        boolean promote = system.evaluate(record, TICK, 10.0 * HOUR + TICK * HOUR, true, true);
        assertTrue(promote);
        assertEquals(AwarenessState.DETECTED, record.getState());
        assertTrue(record.getAwareness() < 1.0f);
    }

    @Test
    void staleRecordsArePurged() {
        AwarenessSystem system = new AwarenessSystem();
        Object zombie = new Object();
        Object player = new Object();
        AwarenessRecord record = system.getOrCreate(zombie, player);
        system.evaluate(record, TICK, 0.0, true, false);
        assertEquals(1, system.pairCount());
        system.purgeStale(31.0 * HOUR);
        assertEquals(0, system.pairCount());
        assertNull(system.get(zombie, player));
    }

    @Test
    void pairIdentityIsByReference() {
        AwarenessSystem system = new AwarenessSystem();
        Object zombie = new Object();
        Object playerA = new Object();
        Object playerB = new Object();
        AwarenessRecord first = system.getOrCreate(zombie, playerA);
        AwarenessRecord second = system.getOrCreate(zombie, playerA);
        AwarenessRecord other = system.getOrCreate(zombie, playerB);
        assertSame(first, second);
        assertFalse(first == other);
        assertEquals(2, system.pairCount());
    }

    @Test
    void halfGainMultiplierTakesTwiceAsManyTicks() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        DetectionFactors half = DetectionFactors.identity();
        half.distanceFactor = 0.5f;
        double hoursPerTick = TICK / 3600.0;
        int ticks = -1;
        for (int tick = 1; tick <= 80; tick++) {
            if (system.evaluate(record, TICK, tick * hoursPerTick, half, false)) {
                ticks = tick;
                break;
            }
        }
        // 1.0 / (0.35 * 0.5 * 0.10) = 57.14, so the 58th capped tick promotes.
        assertEquals(58, ticks);
    }

    @Test
    void blockedFactorsDoNotGain() {
        AwarenessSystem system = new AwarenessSystem();
        AwarenessRecord record = system.getOrCreate(new Object(), new Object());
        boolean promote = system.evaluate(record, TICK, 0.0, DetectionFactors.NONE, false);
        assertFalse(promote);
        assertEquals(0.0f, record.getAwareness(), 0.0f);
        assertFalse(record.lastExposed);
    }

    private static int ticksUntilPromote(AwarenessSystem system, AwarenessRecord record) {
        double hoursPerTick = TICK / 3600.0;
        for (int tick = 1; tick <= 40; tick++) {
            if (system.evaluate(record, TICK, tick * hoursPerTick, true, false)) {
                return tick;
            }
        }
        return -1;
    }

    private static Patch.Argument argumentAnnotation(Method method, int parameterIndex) {
        for (Annotation annotation : method.getParameterAnnotations()[parameterIndex]) {
            if (annotation instanceof Patch.Argument) {
                return (Patch.Argument) annotation;
            }
        }
        return null;
    }
}
