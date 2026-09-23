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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CoverSystemTest {
    private static final float EPS = 1.0e-5f;
    private static final String SNAPSHOT_SHA256 =
            "715bdd7435dc5badce149082fcfb32a16b015deb7b391592d9b5f79a0d3a4d54";

    private static final CoverSystem.SquareAccess<FakeSquare> ACCESS =
            new CoverSystem.SquareAccess<FakeSquare>() {
                @Override
                public int objectCount(FakeSquare square) {
                    return square.names.size();
                }

                @Override
                public String spriteNameAt(FakeSquare square, int index) {
                    return square.names.get(index);
                }

                @Override
                public FakeSquare adjacent(FakeSquare square, CoverSystem.Direction direction) {
                    square.adjacentCalls++;
                    return square.adjacent.get(direction);
                }
            };

    @Test
    void snapshotMatchesReviewedVanillaTriples() throws NoSuchAlgorithmException {
        assertEquals(95, CoverSystem.ENTRIES.size());
        long full = CoverSystem.ENTRIES.values().stream()
                .filter(entry -> entry.coefficient == 1.0f)
                .count();
        assertEquals(92, full);
        assertEquals(3, CoverSystem.ENTRIES.size() - full);
        assertEquals(0.6f, CoverSystem.entryFor("fencing_01_4").coefficient, EPS);
        assertEquals(0.6f, CoverSystem.entryFor("fencing_01_5").coefficient, EPS);
        assertEquals(0.6f, CoverSystem.entryFor("fencing_01_6").coefficient, EPS);
        assertEquals(SNAPSHOT_SHA256, snapshotDigest());
    }

    @Test
    void dominantAxisAndTiesMatchSpottedNew() {
        assertEquals(CoverSystem.Direction.E, CoverSystem.directionToward(3, 0, 0, 0));
        assertEquals(CoverSystem.Direction.W, CoverSystem.directionToward(-3, 0, 0, 0));
        assertEquals(CoverSystem.Direction.S, CoverSystem.directionToward(0, 3, 0, 0));
        assertEquals(CoverSystem.Direction.N, CoverSystem.directionToward(0, -3, 0, 0));
        assertEquals(CoverSystem.Direction.S, CoverSystem.directionToward(2, 2, 0, 0));
        assertEquals(CoverSystem.Direction.N, CoverSystem.directionToward(-2, -2, 0, 0));
        assertEquals(CoverSystem.Direction.N, CoverSystem.directionToward(4, 4, 4, 4));
    }

    @Test
    void adjacentLookupUsesVanillaNorthWestStorage() {
        assertEquals(CoverSystem.Direction.N,
                CoverSystem.adjacentLookupDirection(CoverSystem.Direction.N));
        assertEquals(CoverSystem.Direction.W,
                CoverSystem.adjacentLookupDirection(CoverSystem.Direction.W));
        assertEquals(CoverSystem.Direction.N,
                CoverSystem.adjacentLookupDirection(CoverSystem.Direction.S));
        assertEquals(CoverSystem.Direction.W,
                CoverSystem.adjacentLookupDirection(CoverSystem.Direction.E));
    }

    @Test
    void allDirectionSpriteOnPlayerSquareCoversEveryObserverSide() {
        FakeSquare player = square("f_bushes_1_96");
        assertEquals(1.0f, resolve(player, 0, -3, 0, 0), EPS);
        assertEquals(1.0f, resolve(player, -3, 0, 0, 0), EPS);
        assertEquals(1.0f, resolve(player, 0, 3, 0, 0), EPS);
        assertEquals(1.0f, resolve(player, 3, 0, 0, 0), EPS);
        assertEquals(0, player.adjacentCalls);
    }

    @Test
    void directionalSpriteOnlyMatchesRequestedSide() {
        FakeSquare player = square("fencing_01_5");
        assertEquals(0.6f, resolve(player, 0, -3, 0, 0), EPS);
        assertEquals(0.0f, resolve(player, 3, 0, 0, 0), EPS);
    }

    @Test
    void playerSquareAndObjectOrderTakePriority() {
        FakeSquare player = square("unknown", "fencing_01_4", "f_bushes_1_96");
        FakeSquare west = square("f_bushes_1_96");
        player.adjacent.put(CoverSystem.Direction.W, west);
        assertEquals(0.6f, resolve(player, -3, 0, 0, 0), EPS);

        player.names.set(1, "f_bushes_1_96");
        player.names.set(2, "fencing_01_4");
        assertEquals(1.0f, resolve(player, -3, 0, 0, 0), EPS);
        assertEquals(0, player.adjacentCalls);
    }

    @Test
    void wrongDirectionEntryDoesNotStopLookup() {
        FakeSquare player = square("fencing_01_5", "fencing_01_4");
        assertEquals(0.6f, resolve(player, -3, 0, 0, 0), EPS);
    }

    @Test
    void adjacentSquareIsSearchedOnlyTowardObserver() {
        FakeSquare player = square();
        FakeSquare north = square("f_bushes_1_96");
        FakeSquare south = square("f_bushes_1_96");
        player.adjacent.put(CoverSystem.Direction.N, north);
        player.adjacent.put(CoverSystem.Direction.S, south);
        assertEquals(1.0f, resolve(player, 0, -3, 0, 0), EPS);

        player.adjacent.remove(CoverSystem.Direction.N);
        assertEquals(0.0f, resolve(player, 0, -3, 0, 0), EPS);
    }

    @Test
    void southAndEastNeighborsUseNorthAndWestMasks() {
        FakeSquare player = square();
        player.adjacent.put(CoverSystem.Direction.S, square("fencing_01_5"));
        player.adjacent.put(CoverSystem.Direction.E, square("fencing_01_4"));
        assertEquals(0.6f, resolve(player, 0, 3, 0, 0), EPS);
        assertEquals(0.6f, resolve(player, 3, 0, 0, 0), EPS);
    }

    @Test
    void ineligiblePairsDoNotScanSquares() {
        FakeSquare player = square("f_bushes_1_96");
        assertEquals(0.0f, CoverSystem.resolveCoefficient(
                false, false, 0, -2, 0, 0, player, ACCESS), EPS);
        assertEquals(0.0f, CoverSystem.resolveCoefficient(
                true, true, 0, -2, 0, 0, player, ACCESS), EPS);
        assertEquals(0, player.adjacentCalls);
    }

    @Test
    void unknownNullAndChangedSpriteNamesAreReadLive() {
        FakeSquare player = square(null, "fencing_01_04", "unknown");
        assertEquals(0.0f, resolve(player, -3, 0, 0, 0), EPS);
        assertNull(CoverSystem.entryFor("fencing_01_04"));

        player.names.set(1, "fencing_01_4");
        assertEquals(0.6f, resolve(player, -3, 0, 0, 0), EPS);
        player.names.remove(1);
        assertEquals(0.0f, resolve(player, -3, 0, 0, 0), EPS);
    }

    @Test
    void entryLookupReturnsImmutableSnapshotObject() {
        CoverSystem.Entry first = CoverSystem.entryFor("trashcontainers_01_08");
        assertSame(first, CoverSystem.entryFor("trashcontainers_01_08"));
        assertEquals(1.0f, first.coefficient, EPS);
    }

    private static float resolve(
            FakeSquare player, int zombieX, int zombieY, int playerX, int playerY) {
        return CoverSystem.resolveCoefficient(
                true, false, zombieX, zombieY, playerX, playerY, player, ACCESS);
    }

    private static FakeSquare square(String... names) {
        FakeSquare square = new FakeSquare();
        if (names != null) {
            for (String name : names) {
                square.names.add(name);
            }
        }
        return square;
    }

    private static String snapshotDigest() throws NoSuchAlgorithmException {
        List<String> names = new ArrayList<String>(CoverSystem.ENTRIES.keySet());
        names.sort(Comparator.naturalOrder());
        StringBuilder canonical = new StringBuilder();
        for (String name : names) {
            CoverSystem.Entry entry = CoverSystem.ENTRIES.get(name);
            canonical.append(name)
                    .append('|')
                    .append(directionString(entry.directionMask))
                    .append('|')
                    .append(Math.round(entry.coefficient * 100.0f))
                    .append('\n');
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value & 0xff));
        }
        return hex.toString();
    }

    private static String directionString(int mask) {
        StringBuilder value = new StringBuilder(4);
        CoverSystem.Direction[] order = {
            CoverSystem.Direction.N,
            CoverSystem.Direction.W,
            CoverSystem.Direction.S,
            CoverSystem.Direction.E
        };
        for (CoverSystem.Direction direction : order) {
            if ((mask & direction.bit) != 0) {
                value.append(direction.name());
            }
        }
        return value.toString();
    }

    private static final class FakeSquare {
        final List<String> names = new ArrayList<String>();
        final EnumMap<CoverSystem.Direction, FakeSquare> adjacent =
                new EnumMap<CoverSystem.Direction, FakeSquare>(CoverSystem.Direction.class);
        int adjacentCalls;
    }
}
