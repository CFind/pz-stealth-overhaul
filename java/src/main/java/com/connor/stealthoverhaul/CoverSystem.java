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

import java.util.Map;

/**
 * Engine-free port of the directional shelter lookup in
 * {@code IsoZombie.spottedNew}.
 *
 * <p>The sprite data and traversal semantics come from Project Zomboid
 * 42.20.4, {@code characters/IsoZombie.java:346-539, 2012-2060,
 * 2244-2261}. Source SHA-256:
 * {@code 0c1fb53a7763c6e73ab9041fcafa629d29ad9786b4327ec007b54cef596d2f1c}.
 */
public final class CoverSystem {
    public enum Direction {
        N(1),
        W(2),
        S(4),
        E(8);

        public final int bit;

        Direction(int bit) {
            this.bit = bit;
        }
    }

    /** Ordered-square access keeps the production traversal directly testable. */
    public interface SquareAccess<S> {
        int objectCount(S square);

        String spriteNameAt(S square, int index);

        S adjacent(S square, Direction direction);
    }

    public static final class Entry {
        public final int directionMask;
        public final float coefficient;

        public Entry(int directionMask, float coefficient) {
            this.directionMask = directionMask;
            this.coefficient = coefficient;
        }

        public boolean matches(Direction direction) {
            return direction != null && (directionMask & direction.bit) != 0;
        }
    }

    public static final int EXPECTED_ENTRY_COUNT = 95;
    public static final int EXPECTED_FULL_ENTRY_COUNT = 92;
    public static final int EXPECTED_PARTIAL_ENTRY_COUNT = 3;

    public static final Map<String, Entry> ENTRIES = Map.ofEntries(
            cover("trashcontainers_01_08", "NWSE", 100.0f),
            cover("trashcontainers_01_09", "NWSE", 100.0f),
            cover("trashcontainers_01_10", "NWSE", 100.0f),
            cover("trashcontainers_01_11", "NWSE", 100.0f),
            cover("trashcontainers_01_12", "NWSE", 100.0f),
            cover("trashcontainers_01_13", "NWSE", 100.0f),
            cover("trashcontainers_01_14", "NWSE", 100.0f),
            cover("trashcontainers_01_15", "NWSE", 100.0f),
            cover("f_bushes_1_96", "NWSE", 100.0f),
            cover("f_bushes_1_97", "NWSE", 100.0f),
            cover("f_bushes_1_98", "NWSE", 100.0f),
            cover("f_bushes_1_99", "NWSE", 100.0f),
            cover("f_bushes_1_100", "NWSE", 100.0f),
            cover("f_bushes_1_101", "NWSE", 100.0f),
            cover("f_bushes_1_102", "NWSE", 100.0f),
            cover("f_bushes_1_103", "NWSE", 100.0f),
            cover("f_bushes_1_104", "NWSE", 100.0f),
            cover("f_bushes_1_105", "NWSE", 100.0f),
            cover("f_bushes_1_106", "NWSE", 100.0f),
            cover("f_bushes_1_107", "NWSE", 100.0f),
            cover("f_bushes_1_108", "NWSE", 100.0f),
            cover("f_bushes_1_109", "NWSE", 100.0f),
            cover("f_bushes_1_110", "NWSE", 100.0f),
            cover("f_bushes_1_111", "NWSE", 100.0f),
            cover("f_bushes_2_0", "NWSE", 100.0f),
            cover("f_bushes_2_1", "NWSE", 100.0f),
            cover("f_bushes_2_2", "NWSE", 100.0f),
            cover("f_bushes_2_3", "NWSE", 100.0f),
            cover("f_bushes_2_4", "NWSE", 100.0f),
            cover("f_bushes_2_5", "NWSE", 100.0f),
            cover("f_bushes_2_6", "NWSE", 100.0f),
            cover("f_bushes_2_7", "NWSE", 100.0f),
            cover("f_bushes_2_10", "NWSE", 100.0f),
            cover("f_bushes_2_11", "NWSE", 100.0f),
            cover("f_bushes_2_12", "NWSE", 100.0f),
            cover("f_bushes_2_13", "NWSE", 100.0f),
            cover("f_bushes_2_14", "NWSE", 100.0f),
            cover("f_bushes_2_15", "NWSE", 100.0f),
            cover("f_bushes_2_16", "NWSE", 100.0f),
            cover("f_bushes_2_17", "NWSE", 100.0f),
            cover("vegetation_ornamental_01_0", "NWSE", 100.0f),
            cover("vegetation_ornamental_01_1", "NWSE", 100.0f),
            cover("vegetation_ornamental_01_2", "NWSE", 100.0f),
            cover("vegetation_ornamental_01_3", "NWSE", 100.0f),
            cover("vegetation_ornamental_01_4", "NWSE", 100.0f),
            cover("vegetation_ornamental_01_5", "NWSE", 100.0f),
            cover("vegetation_ornamental_01_6", "NWSE", 100.0f),
            cover("vegetation_ornamental_01_7", "NWSE", 100.0f),
            cover("vegetation_ornamental_01_10", "NWSE", 100.0f),
            cover("vegetation_ornamental_01_11", "NWSE", 100.0f),
            cover("vegetation_ornamental_01_12", "NWSE", 100.0f),
            cover("vegetation_ornamental_01_13", "NWSE", 100.0f),
            cover("fencing_01_96", "NWSE", 100.0f),
            cover("fencing_01_98", "NWSE", 100.0f),
            cover("fencing_01_100", "NWSE", 100.0f),
            cover("fencing_01_102", "NWSE", 100.0f),
            cover("fencing_01_32", "N", 100.0f),
            cover("fencing_01_33", "N", 100.0f),
            cover("fencing_01_34", "W", 100.0f),
            cover("fencing_01_35", "W", 100.0f),
            cover("fencing_01_36", "NW", 100.0f),
            cover("fencing_01_4", "W", 60.0f),
            cover("fencing_01_5", "N", 60.0f),
            cover("fencing_01_6", "NW", 60.0f),
            cover("carpentry_02_40", "W", 100.0f),
            cover("carpentry_02_41", "N", 100.0f),
            cover("carpentry_02_42", "NW", 100.0f),
            cover("carpentry_02_44", "W", 100.0f),
            cover("carpentry_02_45", "N", 100.0f),
            cover("carpentry_02_46", "NW", 100.0f),
            cover("carpentry_02_48", "W", 100.0f),
            cover("carpentry_02_49", "N", 100.0f),
            cover("carpentry_02_50", "NW", 100.0f),
            cover("fixtures_doors_fences_01_104", "W", 100.0f),
            cover("fixtures_doors_fences_01_105", "W", 100.0f),
            cover("fixtures_doors_fences_01_96", "W", 100.0f),
            cover("fixtures_doors_fences_01_97", "W", 100.0f),
            cover("fixtures_doors_fences_01_48", "W", 100.0f),
            cover("fixtures_doors_fences_01_49", "W", 100.0f),
            cover("fixtures_doors_fences_01_56", "W", 100.0f),
            cover("fixtures_doors_fences_01_57", "W", 100.0f),
            cover("fixtures_doors_fences_01_98", "N", 100.0f),
            cover("fixtures_doors_fences_01_99", "N", 100.0f),
            cover("fixtures_doors_fences_01_106", "N", 100.0f),
            cover("fixtures_doors_fences_01_107", "N", 100.0f),
            cover("fixtures_doors_fences_01_50", "N", 100.0f),
            cover("fixtures_doors_fences_01_51", "N", 100.0f),
            cover("fixtures_doors_fences_01_58", "N", 100.0f),
            cover("fixtures_doors_fences_01_59", "N", 100.0f),
            cover("fixtures_doors_fences_01_8", "W", 100.0f),
            cover("fixtures_doors_fences_01_9", "N", 100.0f),
            cover("fixtures_doors_fences_01_12", "W", 100.0f),
            cover("fixtures_doors_fences_01_13", "N", 100.0f),
            cover("fixtures_doors_fences_01_4", "W", 100.0f),
            cover("fixtures_doors_fences_01_5", "N", 100.0f));

    static {
        int full = 0;
        int partial = 0;
        for (Entry entry : ENTRIES.values()) {
            if (!Float.isFinite(entry.coefficient)
                    || entry.coefficient < 0.0f
                    || entry.coefficient > 1.0f
                    || entry.directionMask == 0) {
                throw new IllegalStateException("Invalid vanilla cover entry");
            }
            if (entry.coefficient == 1.0f) {
                full++;
            } else {
                partial++;
            }
        }
        if (ENTRIES.size() != EXPECTED_ENTRY_COUNT
                || full != EXPECTED_FULL_ENTRY_COUNT
                || partial != EXPECTED_PARTIAL_ENTRY_COUNT) {
            throw new IllegalStateException("Vanilla cover snapshot is incomplete");
        }
    }

    public CoverSystem() {}

    public static Direction directionToward(int zombieX, int zombieY, int playerX, int playerY) {
        int dx = zombieX - playerX;
        int dy = zombieY - playerY;
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? Direction.E : Direction.W;
        }
        return dy > 0 ? Direction.S : Direction.N;
    }

    public static Direction adjacentLookupDirection(Direction direction) {
        if (direction == Direction.S) {
            return Direction.N;
        }
        if (direction == Direction.E) {
            return Direction.W;
        }
        return direction;
    }

    public static Entry entryFor(String spriteName) {
        return spriteName == null ? null : ENTRIES.get(spriteName);
    }

    public static Entry firstMatchingEntry(String[] spriteNames, Direction direction) {
        if (spriteNames == null) {
            return null;
        }
        for (String spriteName : spriteNames) {
            Entry entry = entryFor(spriteName);
            if (entry != null && entry.matches(direction)) {
                return entry;
            }
        }
        return null;
    }

    public static <S> Entry firstMatchingEntry(S square, Direction direction, SquareAccess<S> access) {
        if (square == null || direction == null || access == null) {
            return null;
        }
        int count = access.objectCount(square);
        for (int index = 0; index < count; index++) {
            Entry entry = entryFor(access.spriteNameAt(square, index));
            if (entry != null && entry.matches(direction)) {
                return entry;
            }
        }
        return null;
    }

    public static <S> float resolveCoefficient(
            boolean sneaking,
            boolean sameSquare,
            int zombieX,
            int zombieY,
            int playerX,
            int playerY,
            S playerSquare,
            SquareAccess<S> access) {
        if (!sneaking || sameSquare || playerSquare == null || access == null) {
            return 0.0f;
        }
        Direction direction = directionToward(zombieX, zombieY, playerX, playerY);
        Entry entry = firstMatchingEntry(playerSquare, direction, access);
        if (entry == null) {
            S adjacent = access.adjacent(playerSquare, direction);
            entry = firstMatchingEntry(adjacent, adjacentLookupDirection(direction), access);
        }
        return entry == null ? 0.0f : entry.coefficient;
    }

    private static Map.Entry<String, Entry> cover(String name, String directions, float percentage) {
        int mask = 0;
        for (int index = 0; index < directions.length(); index++) {
            char direction = directions.charAt(index);
            if (direction == 'N') {
                mask |= Direction.N.bit;
            } else if (direction == 'W') {
                mask |= Direction.W.bit;
            } else if (direction == 'S') {
                mask |= Direction.S.bit;
            } else if (direction == 'E') {
                mask |= Direction.E.bit;
            } else {
                throw new IllegalArgumentException("Invalid vanilla cover direction: " + direction);
            }
        }
        return Map.entry(name, new Entry(mask, percentage / 100.0f));
    }
}
