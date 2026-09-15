// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;

import java.util.*;

/** Exact item keys participating in one operation, including unchanged reusable ingredients. */
public record StockScope<K>(Set<K> keys) {
    public StockScope { keys = Set.copyOf(keys); }

    public boolean matches(Map<K, Long> expected, Map<K, Long> actual) {
        return keys.stream().allMatch(k -> Objects.equals(expected.getOrDefault(k, 0L), actual.getOrDefault(k, 0L)));
    }

    public Map<K, Long> project(Map<K, Long> stock) {
        Map<K, Long> result = new LinkedHashMap<>();
        for (K key : keys) if (stock.getOrDefault(key, 0L) != 0) result.put(key, stock.get(key));
        return result;
    }

    /** Keep newly observed background stock while retaining exact expectations for this operation. */
    public Map<K, Long> rebase(Map<K, Long> expected, Map<K, Long> current) {
        Map<K, Long> result = new LinkedHashMap<>(current);
        for (K key : keys) {
            long count = expected.getOrDefault(key, 0L);
            if (count == 0) result.remove(key); else result.put(key, count);
        }
        return Collections.unmodifiableMap(result);
    }
}
