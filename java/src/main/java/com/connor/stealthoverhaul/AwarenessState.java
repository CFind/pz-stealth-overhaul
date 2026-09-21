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
 * Pair awareness state from {@code docs/mod.md}. {@code DETECTED} is only set
 * after vanilla accepts a mod-promoted target.
 */
public enum AwarenessState {
    UNAWARE,
    SUSPICIOUS,
    DETECTED
}
