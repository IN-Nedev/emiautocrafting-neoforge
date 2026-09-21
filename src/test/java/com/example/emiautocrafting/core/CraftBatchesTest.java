// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftBatchesTest {
    private int limit(long requested, int output, long budget, long pooled, int smallest) {
        return CraftBatches.limit(requested, output, 64, Map.of("wood", 2L), Map.of("wood", budget),
                Map.of("wood", pooled), Map.of("wood", smallest));
    }
    @Test void exactRequestDoesNotShiftCraftEverything() { assertEquals(20, limit(20, 1, 256, 256, 0)); }
    @Test void multipleOutputsFitOneCursorStack() { assertEquals(16, limit(48, 4, 256, 256, 0)); }
    @Test void reservationsLimitBatch() { assertEquals(3, limit(20, 1, 6, 256, 0)); }
    @Test void unevenGridCannotBorrowFromAnotherSlot() { assertEquals(1, limit(20, 1, 21, 0, 1)); }
    @Test void nativeRefillsMustCoverEveryMatchingSlot() { assertEquals(3, limit(20, 1, 26, 5, 1)); }
    @Test void playerOnlyTransferFallsBackToOne() { assertEquals(1, limit(20, 1, 256, 0, 0)); }
    @Test void largeRemoteCountsCannotOverflow() { assertEquals(64, limit(Long.MAX_VALUE, 1, Long.MAX_VALUE, Long.MAX_VALUE, 64)); }
}
