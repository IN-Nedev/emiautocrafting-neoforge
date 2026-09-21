// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;

import java.util.Map;

/** Bounds native repeated crafts by cursor capacity, reservations and refillable ingredients. */
public final class CraftBatches {
    private CraftBatches() {}
    public static <K> int limit(long requested, int outputCount, int stackLimit, Map<K, Long> used,
            Map<K, Long> available, Map<K, Long> refillable, Map<K, Integer> smallestGridStack) {
        if (requested < 1 || outputCount < 1 || stackLimit < outputCount || used.isEmpty())
            throw new IllegalArgumentException("Invalid crafting batch");
        long limit = Math.min(requested, Math.min(64, stackLimit / outputCount));
        for (var entry : used.entrySet()) {
            long perCraft = entry.getValue();
            if (perCraft < 1) throw new IllegalArgumentException("Invalid ingredient quantity");
            limit = Math.min(limit, available.getOrDefault(entry.getKey(), 0L) / perCraft);
            // Native menus do not rebalance uneven stacks across grid slots. AE2 may
            // extract pooled stock before touching a grid stack, so use the smallest slot.
            long refills = Math.min(64, refillable.getOrDefault(entry.getKey(), 0L) / perCraft);
            limit = Math.min(limit, refills + Math.min(64, smallestGridStack.getOrDefault(entry.getKey(), 0)));
        }
        // A single craft can still use native transfer from the player's inventory.
        return (int) Math.max(1, limit);
    }
}
