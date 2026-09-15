// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;

import java.util.*;

/** Reconciles material revealed by extracting from a storage slot whose displayed count is capped. */
public final class CappedStock {
    private CappedStock() { }

    public static <K> Optional<Map<K, Long>> afterTransfer(Map<K, Long> before, Map<K, Long> transferred,
            Map<K, Long> afterCraft, Map<K, Long> revealLimits) {
        Set<K> keys = new HashSet<>(before.keySet()); keys.addAll(transferred.keySet());
        Map<K, Long> result = new LinkedHashMap<>(afterCraft);
        for (K key : keys) {
            long revealed = Math.subtractExact(transferred.getOrDefault(key, 0L), before.getOrDefault(key, 0L));
            // Filling only moves items. Losses and gains outside identified capped input slots fail.
            if (revealed < 0 || revealed > revealLimits.getOrDefault(key, 0L)) return Optional.empty();
            if (revealed > 0) result.merge(key, revealed, Math::addExact);
        }
        return Optional.of(Collections.unmodifiableMap(result));
    }
}
