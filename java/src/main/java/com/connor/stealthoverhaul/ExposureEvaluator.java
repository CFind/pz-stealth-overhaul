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

import java.util.Set;
import org.joml.Vector3f;
import zombie.SandboxOptions;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.iso.IsoCell;
import zombie.iso.IsoDirections;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.LosUtil;
import zombie.iso.Vector2;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.weather.ClimateManager;
import zombie.network.GameServer;
import zombie.scripting.objects.CharacterTrait;
import zombie.vehicles.BaseVehicle;

/**
 * Reads engine state into {@link DetectionFactors}. Math stays on
 * {@link DetectionFactors} so JVM tests do not need a running game.
 *
 * <p>Uses public engine APIs only. Vanilla's private
 * {@code updateVisionRadius} and {@code isVehicleBetween} are reproduced from
 * {@code characters/IsoZombie.java:2262-2270, 2426-2445, 5542-5573}.
 * Directional cover is ported through {@link CoverSystem} from
 * {@code characters/IsoZombie.java:346-539, 2012-2060, 2244-2261}.
 *
 * <p>Every member is public. ZombieBuddy inlines spotted advice into
 * {@code IsoZombie}, which cannot see private members of this class.
 */
public final class ExposureEvaluator {
    public static final ThreadLocal<DetectionFactors> SCRATCH =
            ThreadLocal.withInitial(DetectionFactors::new);
    public static final ThreadLocal<Vector2> LOOK = new ThreadLocal<Vector2>();
    public static final CoverSystem.SquareAccess<IsoGridSquare> COVER_SQUARE_ACCESS =
            new CoverSystem.SquareAccess<IsoGridSquare>() {
                @Override
                public int objectCount(IsoGridSquare square) {
                    return square == null || square.getObjects() == null ? 0 : square.getObjects().size();
                }

                @Override
                public String spriteNameAt(IsoGridSquare square, int index) {
                    if (square == null || square.getObjects() == null) {
                        return null;
                    }
                    IsoObject object = square.getObjects().get(index);
                    if (object == null) {
                        return null;
                    }
                    IsoSprite sprite = object.getSprite();
                    return sprite == null ? null : sprite.getName();
                }

                @Override
                public IsoGridSquare adjacent(IsoGridSquare square, CoverSystem.Direction direction) {
                    if (square == null || direction == null) {
                        return null;
                    }
                    return square.getAdjacentSquare(toIsoDirection(direction));
                }
            };

    public static Vector2 lookVector() {
        Vector2 look = LOOK.get();
        if (look == null) {
            look = new Vector2();
            LOOK.set(look);
        }
        return look;
    }

    /**
     * Current potential vision radius for this zombie under this player's
     * lighting and weather. Does not create or update awareness records.
     */
    public static float potentialRange(Object zombieObj, Object playerObj, DetectionConfig config) {
        if (!(zombieObj instanceof IsoZombie) || !(playerObj instanceof IsoPlayer) || config == null) {
            return 0.0f;
        }
        IsoZombie zombie = (IsoZombie) zombieObj;
        IsoPlayer player = (IsoPlayer) playerObj;
        IsoGridSquare zombieSquare = zombie.getCurrentSquare();
        IsoGridSquare playerSquare = player.getCurrentSquare();
        if (zombieSquare == null || playerSquare == null) {
            return 0.0f;
        }
        float light = readTargetLight(player, playerSquare);
        float rain = 0.0f;
        float fog = 0.0f;
        ClimateManager climate = ClimateManager.getInstance();
        if (climate != null) {
            rain = climate.getRainIntensity();
            fog = climate.getFogIntensity();
        }
        return DetectionFactors.effectiveRange(
                light,
                rain,
                fog,
                zombie.sight,
                readLoreSight(),
                zombie.getWornItemsVisionModifier(),
                zombie.getEatBodyTarget() != null,
                config);
    }

    public static DetectionFactors evaluate(Object zombie, Object player, DetectionConfig config) {
        DetectionFactors factors = SCRATCH.get().reset();
        if (!(zombie instanceof IsoZombie) || !(player instanceof IsoPlayer) || config == null) {
            return factors.block(DetectionFactors.BLOCKED_FACTOR);
        }
        return fill(factors, (IsoZombie) zombie, (IsoPlayer) player, config);
    }

    public static DetectionFactors fill(
            DetectionFactors factors, IsoZombie zombie, IsoPlayer player, DetectionConfig config) {
        IsoGridSquare zombieSquare = zombie.getCurrentSquare();
        IsoGridSquare playerSquare = player.getCurrentSquare();
        if (zombieSquare == null || playerSquare == null) {
            return factors.block(DetectionFactors.BLOCKED_FACTOR);
        }

        float distance = zombie.DistTo(player);
        factors.distance = distance;

        int floorDiff = Math.abs(playerSquare.getZ() - zombieSquare.getZ());
        if (floorDiff > config.maximumFloorDifference) {
            return factors.block(DetectionFactors.BLOCKED_FLOOR);
        }

        float light = readTargetLight(player, playerSquare);
        float rain = 0.0f;
        float fog = 0.0f;
        ClimateManager climate = ClimateManager.getInstance();
        if (climate != null) {
            rain = climate.getRainIntensity();
            fog = climate.getFogIntensity();
        }

        int loreSight = readLoreSight();
        float wornVision = zombie.getWornItemsVisionModifier();
        boolean eating = zombie.getEatBodyTarget() != null;
        boolean inactive = zombie.inactive;
        int sight = zombie.sight;

        factors.effectiveRange = DetectionFactors.effectiveRange(
                light, rain, fog, sight, loreSight, wornVision, eating, config);
        if (distance > factors.effectiveRange) {
            return factors.block(DetectionFactors.BLOCKED_RANGE);
        }
        factors.distanceFactor = DetectionFactors.distanceFactor(distance, factors.effectiveRange);

        Vector2 look = lookVector();
        zombie.getLookVector(look);
        factors.facingDot = DetectionFactors.facingDot(
                look.x, look.y, player.getX() - zombie.getX(), player.getY() - zombie.getY());
        if (distance > config.closeRange && factors.facingDot < config.rearCutoffDot) {
            return factors.block(DetectionFactors.BLOCKED_REAR);
        }
        factors.angleFactor = DetectionFactors.angleFactor(factors.facingDot, distance, config);

        if (!hasLineOfSight(zombie, zombieSquare, playerSquare)) {
            return factors.block(DetectionFactors.BLOCKED_LOS);
        }

        boolean playerInVehicle = player.getVehicle() != null;
        boolean vehicleBetween = isVehicleBetween(zombie, player);
        factors.vehicleFactor = DetectionFactors.vehicleFactor(vehicleBetween, playerInVehicle, distance);
        if (factors.vehicleFactor <= 0.0f) {
            return factors.block(DetectionFactors.BLOCKED_VEHICLE);
        }

        factors.lightFactor = DetectionFactors.lightFactor(light, config);
        if (factors.lightFactor <= 0.0f) {
            return factors.block(DetectionFactors.BLOCKED_LIGHT);
        }

        float movementLength = 0.0f;
        Vector2 movement = player.getMovementLastFrame();
        if (movement != null) {
            movementLength = movement.getLength();
        }
        boolean sneaking = player.isSneaking();
        boolean running = player.isRunning();
        boolean aiming = player.isAiming();
        factors.movementFactor = DetectionFactors.movementFactor(
                movementLength, running, sneaking, aiming, distance, config);

        float sneakSpotMod = sneaking ? player.getSneakSpotMod() : 1.0f;
        factors.postureAndSkillFactor =
                DetectionFactors.postureAndSkillFactor(sneaking, sneakSpotMod, config);

        factors.traitFactor = DetectionFactors.traitFactor(
                player.hasTrait(CharacterTrait.INCONSPICUOUS),
                player.hasTrait(CharacterTrait.CONSPICUOUS),
                config);
        factors.zombieSightFactor = DetectionFactors.zombieSightFactor(sight, loreSight, config);
        factors.weatherAndActivityFactor =
                DetectionFactors.weatherAndActivityFactor(eating, inactive, config);

        boolean sameSquare = zombieSquare == playerSquare;
        float coverCoefficient = CoverSystem.resolveCoefficient(
                sneaking,
                sameSquare,
                zombieSquare.getX(),
                zombieSquare.getY(),
                playerSquare.getX(),
                playerSquare.getY(),
                playerSquare,
                COVER_SQUARE_ACCESS);
        factors.coverEvaluated = true;
        factors.coverFactor = DetectionFactors.coverFactor(coverCoefficient);
        if (factors.coverFactor <= 0.0f) {
            return factors.block(DetectionFactors.BLOCKED_COVER);
        }
        factors.clothingFactor = DetectionFactors.clothingFactor(wornVision);

        return factors.markExposed();
    }

    /**
     * Target-square light. spottedNew averages RGB
     * ({@code IsoZombie.java:2107-2113}); {@code getLightLevel} is the public
     * equivalent using max RGB ({@code IsoGridSquare.java:11139-11145}).
     */
    public static float readTargetLight(IsoPlayer player, IsoGridSquare playerSquare) {
        int playerIndex = 0;
        if (!GameServer.server && player != null) {
            playerIndex = player.getIndex();
        }
        if (playerIndex < 0 || playerIndex > 3) {
            playerIndex = 0;
        }
        return playerSquare.getLightLevel(playerIndex);
    }

    /**
     * {@code LosUtil.lineClear} is the public vision walk that treats closed
     * doors, curtains, barricades, and blocked windows as
     * {@code TestResults.Blocked} ({@code iso/LosUtil.java:21-188},
     * {@code IsoGridSquare.java:8178-8212}).
     */
    public static boolean hasLineOfSight(
            IsoZombie zombie, IsoGridSquare zombieSquare, IsoGridSquare playerSquare) {
        if (zombieSquare == playerSquare) {
            return true;
        }
        IsoCell cell = zombie.getCell();
        if (cell == null && IsoWorld.instance != null) {
            cell = IsoWorld.instance.getCell();
        }
        if (cell == null) {
            return false;
        }
        int x0 = zombieSquare.getX();
        int y0 = zombieSquare.getY();
        int z0 = zombieSquare.getZ();
        int x1 = playerSquare.getX();
        int y1 = playerSquare.getY();
        int z1 = playerSquare.getZ();
        if (x0 == x1 && y0 == y1 && z0 == z1) {
            return true;
        }
        LosUtil.TestResults result = LosUtil.lineClear(cell, x0, y0, z0, x1, y1, z1, false);
        return result != LosUtil.TestResults.Blocked;
    }

    /**
     * Public reconstruction of private {@code IsoZombie.isVehicleBetween}
     * ({@code IsoZombie.java:2426-2445}).
     */
    public static boolean isVehicleBetween(IsoZombie zombie, IsoPlayer player) {
        if (IsoWorld.instance == null) {
            return false;
        }
        IsoCell cell = IsoWorld.instance.getCell();
        if (cell == null) {
            return false;
        }
        Set<BaseVehicle> vehicles = cell.getVehicles();
        if (vehicles == null || vehicles.isEmpty()) {
            return false;
        }
        Vector3f start = BaseVehicle.allocVector3f();
        Vector3f end = BaseVehicle.allocVector3f();
        Vector3f hit = BaseVehicle.allocVector3f();
        try {
            for (BaseVehicle vehicle : vehicles) {
                if (vehicle == null) {
                    continue;
                }
                start.set(player.getX(), player.getY(), player.getZ() + 0.1f);
                end.set(zombie.getX(), zombie.getY(), zombie.getZ() + 0.1f);
                if (vehicle.getIntersectPoint(start, end, hit) != null) {
                    return true;
                }
            }
            return false;
        } finally {
            BaseVehicle.releaseVector3f(start);
            BaseVehicle.releaseVector3f(end);
            BaseVehicle.releaseVector3f(hit);
        }
    }

    public static int readLoreSight() {
        try {
            if (SandboxOptions.instance != null
                    && SandboxOptions.instance.lore != null
                    && SandboxOptions.instance.lore.sight != null) {
                return SandboxOptions.instance.lore.sight.getValue();
            }
        } catch (Throwable ignored) {
            return 2;
        }
        return 2;
    }

    public static IsoDirections toIsoDirection(CoverSystem.Direction direction) {
        switch (direction) {
            case N:
                return IsoDirections.N;
            case W:
                return IsoDirections.W;
            case S:
                return IsoDirections.S;
            case E:
                return IsoDirections.E;
            default:
                throw new IllegalArgumentException("Unsupported cover direction: " + direction);
        }
    }
}
