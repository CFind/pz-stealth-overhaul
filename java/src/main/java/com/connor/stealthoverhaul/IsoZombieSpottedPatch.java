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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import me.zed_0xff.zombie_buddy.Patch;
import zombie.GameTime;
import zombie.SandboxOptions;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.characters.NetworkPlayerAI;
import zombie.iso.IsoGridSquare;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.network.GameClient;
import zombie.network.GameServer;

/**
 * Advice on {@code IsoZombie.spotted(IsoMovingObject, boolean)}
 * ({@code characters/IsoZombie.java:2898-2903}).
 *
 * <p>Must remain in {@code com.connor.stealthoverhaul}. ZombieBuddy only
 * registers {@code @Patch} classes whose package exactly equals
 * {@code javaPkgName} ({@code transformers/Pipeline.java}
 * {@code transformParsedJar()}).
 *
 * <p>Compiled against the installed {@code ZombieBuddy.jar}, whose
 * {@code @Patch} type is {@code me.zed_0xff.zombie_buddy.Patch}.
 *
 * <p>Milestone 3 gates ordinary spotting. Milestone 4 restores the
 * {@code !bForced} bookkeeping that vanilla skips on a promoted call
 * ({@code characters/IsoZombie.java:2325-2350, 2790-2814}). Milestone 5
 * replaces the fixed-rate gain with LOS, facing, range, light, movement,
 * stance, lore, weather, cover, clothing, eating, and vehicle factors.
 *
 * <p>Every field and method on this class must be public. ZombieBuddy
 * inlines {@code enter}/{@code exit} into {@code IsoZombie.spotted}, so
 * that class cannot see private or package-private members here.
 */
@Patch(className = "zombie.characters.IsoZombie", methodName = "spotted")
public final class IsoZombieSpottedPatch {
    public static final String LOG_PREFIX = "[StealthOverhaul]";
    public static final long LOG_INTERVAL_NS = 1_000_000_000L;
    public static final ThreadLocal<AwarenessRecord> PROMOTED = new ThreadLocal<AwarenessRecord>();

    public static long callCount;
    public static long lastSkipLogNanos;
    public static long lastRejectLogNanos;
    public static boolean loggedFailure;
    public static boolean spottingAdviceFailed;
    public static boolean bookkeepingBound;
    public static MethodHandle setBonusSpotTime;
    public static MethodHandle setCanSeeTarget;

    /**
     * Inlined into {@code IsoZombie}. Do not touch this class's fields here;
     * assign {@code bForced} in this method so ByteBuddy copies it back.
     */
    @Patch.OnEnter(skipOn = true)
    public static boolean enter(
            @Patch.This Object zombie,
            @Patch.Argument(0) Object other,
            @Patch.Argument(value = 1, readOnly = false) boolean bForced) {
        AwarenessSystem.SpotAction action = decide(zombie, other, bForced);
        if (action == AwarenessSystem.SpotAction.PROMOTE) {
            bForced = true;
            return false;
        }
        return action == AwarenessSystem.SpotAction.SKIP;
    }

    @Patch.OnExit
    public static void exit(@Patch.This Object zombie, @Patch.Argument(0) Object other) {
        onExit(zombie, other);
    }

    public static AwarenessSystem.SpotAction decide(Object zombie, Object other, boolean incomingForced) {
        PROMOTED.remove();
        try {
            return decideUnchecked(zombie, other, incomingForced);
        } catch (Throwable t) {
            spottingAdviceFailed = true;
            logFailure(t);
            return AwarenessSystem.SpotAction.PASS_THROUGH;
        }
    }

    public static void onExit(Object zombie, Object other) {
        AwarenessRecord record = PROMOTED.get();
        PROMOTED.remove();
        if (record == null) {
            return;
        }
        try {
            if (!(zombie instanceof IsoZombie) || !(other instanceof IsoPlayer)) {
                return;
            }
            IsoZombie isoZombie = (IsoZombie) zombie;
            IsoPlayer player = (IsoPlayer) other;
            boolean accepted = isoZombie.getTarget() == player;
            boolean firstAccept = accepted && !record.lastAcceptedTarget;
            AwarenessSystem.getInstance().markAccepted(record, accepted);
            if (accepted) {
                restoreBookkeeping(isoZombie);
            }
            if (firstAccept) {
                System.out.println(
                        LOG_PREFIX
                                + " DETECTED zombie="
                                + System.identityHashCode(isoZombie)
                                + " player="
                                + System.identityHashCode(player)
                                + " awareness="
                                + formatAwareness(record.awareness)
                                + " accepted=true");
            } else if (!accepted) {
                logRejected(isoZombie, player, record);
            }
        } catch (Throwable t) {
            logFailure(t);
        }
    }

    /**
     * Vanilla only writes these on a random (non-forced) success. A
     * mod-promoted {@code bForced=true} call therefore leaves
     * {@code isTargetLocationKnown()} false ({@code IsoZombie.java:5286-5291}),
     * which aborts lunges ({@code LungeState.java:74-78}) while still
     * allowing path-to-last-seen-tile follow.
     *
     * <p>{@code timeSinceSeenFlesh} is public. {@code bonusSpotTime} and
     * {@code canSeeTarget} are private, so they are written through
     * {@code MethodHandles.privateLookupIn} bound once. Both classes load
     * in the unnamed {@code app} module.
     */
    public static void restoreBookkeeping(Object zombieObj) {
        if (!(zombieObj instanceof IsoZombie)) {
            return;
        }
        IsoZombie zombie = (IsoZombie) zombieObj;
        bindBookkeepingHandles();
        zombie.timeSinceSeenFlesh = 0.0F;
        GameTime gameTime = GameTime.getInstance();
        if (gameTime != null) {
            zombie.setTargetSeenTime(zombie.getTargetSeenTime() + gameTime.getRealworldSecondsSinceLastUpdate());
        }
        boolean spottedNew = isSpottedNew();
        try {
            if (setBonusSpotTime != null) {
                setBonusSpotTime.invokeExact(zombie, spottedNew ? 720.0F : 120.0F);
            }
            if (spottedNew && setCanSeeTarget != null) {
                setCanSeeTarget.invokeExact(zombie, zombie.isTargetVisible());
            }
        } catch (Throwable t) {
            logFailure(t);
        }
    }

    public static void bindBookkeepingHandles() {
        if (bookkeepingBound) {
            return;
        }
        bookkeepingBound = true;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(IsoZombie.class, MethodHandles.lookup());
            setBonusSpotTime = lookup.findSetter(IsoZombie.class, "bonusSpotTime", float.class);
            setCanSeeTarget = lookup.findSetter(IsoZombie.class, "canSeeTarget", boolean.class);
            System.out.println(LOG_PREFIX + " bookkeeping setters bound");
        } catch (Throwable t) {
            logFailure(t);
        }
    }

    public static boolean isSpottedNew() {
        try {
            return SandboxOptions.instance != null
                    && SandboxOptions.instance.lore != null
                    && SandboxOptions.instance.lore.spottedLogic.getValue();
        } catch (Throwable t) {
            return true;
        }
    }

    public static AwarenessSystem.SpotAction decideUnchecked(
            Object zombie, Object other, boolean incomingForced) {
        callCount++;
        AwarenessSystem system = AwarenessSystem.getInstance();
        DetectionConfig config = system.getConfig();
        if (config == null || !config.enabled || !config.isValid()) {
            return AwarenessSystem.SpotAction.PASS_THROUGH;
        }
        if (incomingForced) {
            return AwarenessSystem.SpotAction.PASS_THROUGH;
        }
        if (!(zombie instanceof IsoZombie) || !(other instanceof IsoPlayer)) {
            return AwarenessSystem.SpotAction.PASS_THROUGH;
        }
        IsoZombie isoZombie = (IsoZombie) zombie;
        IsoPlayer player = (IsoPlayer) other;
        if (isRemoteOrCleanup(isoZombie, player)) {
            return AwarenessSystem.SpotAction.PASS_THROUGH;
        }
        GameTime gameTime = GameTime.getInstance();
        if (gameTime == null) {
            return AwarenessSystem.SpotAction.PASS_THROUGH;
        }
        AwarenessRecord record = system.getOrCreate(isoZombie, player);
        boolean vanillaHasTarget = isoZombie.getTarget() == player;
        DetectionFactors factors = ExposureEvaluator.evaluate(isoZombie, player, config);
        boolean promote = system.evaluate(
                record,
                gameTime.getMultipliedSecondsSinceLastUpdate(),
                gameTime.getWorldAgeHours(),
                factors,
                vanillaHasTarget);
        if (promote) {
            PROMOTED.set(record);
            logPromote(isoZombie, player, record);
            return AwarenessSystem.SpotAction.PROMOTE;
        }
        logSkip(isoZombie, player, record);
        return AwarenessSystem.SpotAction.SKIP;
    }

    /**
     * Remote ownership and vanilla early-exit/cleanup gates from
     * {@code spottedNew} ({@code characters/IsoZombie.java:2063-2096}). Passing
     * through lets vanilla update {@code vectorToTarget} or clear targets.
     *
     * <p>Parameters stay {@code Object} so JVM tests can inspect this class
     * without loading {@code projectzomboid.jar}.
     */
    public static boolean isRemoteOrCleanup(Object zombie, Object player) {
        IsoZombie isoZombie = (IsoZombie) zombie;
        IsoPlayer isoPlayer = (IsoPlayer) player;
        if (GameClient.client && isoZombie.isRemoteZombie()) {
            return true;
        }
        if (isoZombie.getCurrentSquare() == null || isoPlayer.getCurrentSquare() == null) {
            return true;
        }
        if (GameClient.client && (GameClient.connection == null || !GameClient.connection.isReady())) {
            return true;
        }
        if (isoZombie.isReanimatedForGrappleOnly() || isoZombie.isUseless()) {
            return true;
        }
        IsoGridSquare zombieSquare = isoZombie.getCurrentSquare();
        if (zombieSquare.getProperties().has(IsoFlagType.smoke)) {
            return true;
        }
        if (isoPlayer.isDead() || isoPlayer.isGhostMode()) {
            return true;
        }
        if (GameClient.client || GameServer.server) {
            NetworkPlayerAI networkAi = isoPlayer.getNetworkCharacterAI();
            if (networkAi != null && networkAi.isDisconnected()) {
                return true;
            }
        }
        return false;
    }

    public static void logSkip(Object zombie, Object player, AwarenessRecord record) {
        boolean first = record != null && !record.loggedFirstSkip;
        if (first) {
            record.loggedFirstSkip = true;
        }
        long now = System.nanoTime();
        if (!first && now - lastSkipLogNanos < LOG_INTERVAL_NS) {
            return;
        }
        lastSkipLogNanos = now;
        System.out.println(
                LOG_PREFIX
                        + " skip #"
                        + callCount
                        + " zombie="
                        + System.identityHashCode(zombie)
                        + " player="
                        + System.identityHashCode(player)
                        + " awareness="
                        + formatAwareness(record == null ? 0.0f : record.awareness)
                        + formatFactorSuffix(record));
    }

    public static void logPromote(Object zombie, Object player, AwarenessRecord record) {
        if (record != null && record.loggedPromote) {
            return;
        }
        if (record != null) {
            record.loggedPromote = true;
        }
        System.out.println(
                LOG_PREFIX
                        + " promote #"
                        + callCount
                        + " zombie="
                        + System.identityHashCode(zombie)
                        + " player="
                        + System.identityHashCode(player)
                        + " awareness="
                        + formatAwareness(record == null ? 0.0f : record.awareness)
                        + formatFactorSuffix(record));
    }

    public static void logRejected(Object zombie, Object player, AwarenessRecord record) {
        boolean first = record != null && !record.loggedFirstReject;
        if (first) {
            record.loggedFirstReject = true;
        }
        long now = System.nanoTime();
        if (!first && now - lastRejectLogNanos < LOG_INTERVAL_NS) {
            return;
        }
        lastRejectLogNanos = now;
        System.out.println(
                LOG_PREFIX
                        + " promote rejected zombie="
                        + System.identityHashCode(zombie)
                        + " player="
                        + System.identityHashCode(player)
                        + " awareness="
                        + formatAwareness(record == null ? 0.0f : record.awareness)
                        + " accepted=false");
    }

    public static void logFailure(Throwable t) {
        if (loggedFailure) {
            return;
        }
        loggedFailure = true;
        System.out.println(LOG_PREFIX + " spotted() advice failed; vanilla spotting continues");
        t.printStackTrace();
    }

    public static String formatAwareness(float awareness) {
        return String.format("%.2f", awareness);
    }

    public static String formatFactorSuffix(AwarenessRecord record) {
        if (record == null) {
            return "";
        }
        String reason = record.lastBlockedReason == null || record.lastBlockedReason.isEmpty()
                ? "none"
                : record.lastBlockedReason;
        return " gain="
                + formatAwareness(record.lastGainMultiplier)
                + " dist="
                + formatAwareness(record.lastDistance)
                + " range="
                + formatAwareness(record.lastEffectiveRange)
                + " facing="
                + formatAwareness(record.lastFacingDot)
                + " reason="
                + reason;
    }
}
