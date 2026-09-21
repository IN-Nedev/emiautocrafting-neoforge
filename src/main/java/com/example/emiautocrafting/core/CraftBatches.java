// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;

/** Bounds native repeated crafts by cursor capacity, reservations and refillable ingredients. */
public final class CraftBatches {
    private CraftBatches() {}
    public record GridSlot<K>(K key, int count, int capacity) {}

    /** AE2 cannot refill from player inventory: distribute that supply across every matching slot first. */
    public static <K> int withPlayerFill(int upper, Map<K, Long> used, Map<K, Long> remote,
            Map<K, Long> player, List<GridSlot<K>> grid) {
        for (int batches = Math.min(64, upper); batches > 1; batches--) {
            Map<K, Long> needed = new LinkedHashMap<>();
            boolean fits = true;
            for (var slot : grid) {
                int minimum = gridMinimum(batches, remote.getOrDefault(slot.key(), 0L), used.get(slot.key()));
                if (minimum > slot.capacity()) { fits = false; break; }
                needed.merge(slot.key(), (long) Math.max(0, minimum - slot.count()), Math::addExact);
            }
            if (fits && needed.entrySet().stream().allMatch(e -> e.getValue() <= player.getOrDefault(e.getKey(), 0L))) return batches;
        }
        return 1; // Native single transfer can combine partial network and player supply.
    }

    public static int gridMinimum(int batches, long remote, long perCraft) {
        if (batches < 1 || batches > 64 || remote < 0 || perCraft < 1) throw new IllegalArgumentException("Invalid grid batch");
        return (int) Math.max(0, batches - remote / perCraft);
    }
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
