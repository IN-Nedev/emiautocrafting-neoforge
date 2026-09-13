// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QuantitiesTest {
    @Test void finalStockReducesTotal() { assertEquals(5, Quantities.remaining(8, 3)); assertEquals(0, Quantities.remaining(8, 9)); }
    @Test void surplusRoundsUp() { assertEquals(2, Quantities.batches(5, 4)); assertEquals(0, Quantities.batches(0, 4)); }
    @Test void maximumLongDoesNotOverflow() { assertEquals(2305843009213693952L, Quantities.batches(Long.MAX_VALUE, 4)); }
    @Test void clampBeforeIntegerConversion() { assertEquals(1, Quantities.dispatch(Long.MAX_VALUE, 1)); assertEquals(64, Quantities.dispatch(Long.MAX_VALUE, 64)); }
    @Test void rejectNonFiniteRequests() { assertThrows(IllegalArgumentException.class, () -> Quantities.remaining(0, 0)); assertThrows(IllegalArgumentException.class, () -> Quantities.batches(1, 0)); }
}
