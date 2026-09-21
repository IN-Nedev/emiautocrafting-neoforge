// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;

import java.util.Map;
import java.util.List;
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
    @Test void playerIntermediatesCanFillTwoGridSlotsForAStack() {
        assertEquals(64, CraftBatches.withPlayerFill(64, Map.of("glass",2L), Map.of(), Map.of("glass",128L),
                List.of(new CraftBatches.GridSlot<>("glass",0,64), new CraftBatches.GridSlot<>("glass",0,64))));
    }
    @Test void partlyFilledGridOnlyNeedsTheMissingSupply() {
        assertEquals(20, CraftBatches.withPlayerFill(20, Map.of("glass",2L), Map.of(), Map.of("glass",21L),
                List.of(new CraftBatches.GridSlot<>("glass",1,64), new CraftBatches.GridSlot<>("glass",18,64))));
    }
    @Test void unevenGridCannotConsumeTheSamePlayerItemsTwice() {
        assertEquals(11, CraftBatches.withPlayerFill(20, Map.of("glass",2L), Map.of(), Map.of("glass",10L),
                List.of(new CraftBatches.GridSlot<>("glass",1,64), new CraftBatches.GridSlot<>("glass",18,64))));
    }
    @Test void networkRemainderDoesNotCoverAnUnbalancedGrid() {
        assertEquals(19, CraftBatches.withPlayerFill(20, Map.of("wood",8L), Map.of("wood",159L), Map.of("wood",1L),
                java.util.Collections.nCopies(8,new CraftBatches.GridSlot<>("wood",0,64))));
    }
    @Test void sixteenStackIngredientsLimitPlayerFill() {
        assertEquals(16, CraftBatches.withPlayerFill(64, Map.of("pearl",1L), Map.of(), Map.of("pearl",64L),
                List.of(new CraftBatches.GridSlot<>("pearl",0,16))));
    }
    @Test void networkCanRefillBeyondSmallIngredientStacks() {
        assertEquals(64, CraftBatches.withPlayerFill(64, Map.of("pearl",1L), Map.of("pearl",48L), Map.of("pearl",16L),
                List.of(new CraftBatches.GridSlot<>("pearl",0,16))));
    }
}
