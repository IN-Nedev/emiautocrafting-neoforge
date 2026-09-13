// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;

public final class Quantities {
    private Quantities() {}
    public static long batches(long items, long output) {
        if (items < 0 || output <= 0) throw new IllegalArgumentException("Invalid recipe quantity");
        return items / output + (items % output == 0 ? 0 : 1);
    }
    public static long remaining(long total, long available) {
        if (total <= 0 || available < 0) throw new IllegalArgumentException("Target must be positive");
        return available >= total ? 0 : total - available;
    }
    public static int dispatch(long batches, int limit) {
        if (batches <= 0 || limit <= 0) throw new IllegalArgumentException("Invalid batch");
        return (int) Math.min(batches, (long) limit);
    }
}
