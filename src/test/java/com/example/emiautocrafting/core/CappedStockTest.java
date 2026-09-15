// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CappedStockTest {
    @Test void revealedInputIsRetainedWhileNextCraftStillConsumesExactlyOne() {
        assertEquals(Map.of("log", 64L, "plank", 4L), CappedStock.afterTransfer(
                Map.of("log", 64L), Map.of("log", 65L), Map.of("log", 63L, "plank", 4L), Map.of("log", 64L)).orElseThrow());
    }
    @Test void ordinaryTransferNeedsNoRelaxation() {
        assertEquals(Map.of("plank", 4L), CappedStock.afterTransfer(
                Map.of("log", 1L), Map.of("log", 1L), Map.of("plank", 4L), Map.of()).orElseThrow());
    }
    @Test void lossOrUnrelatedGainCannotConfirmTransfer() {
        var before = Map.of("log", 64L, "diamond", 2L);
        assertTrue(CappedStock.afterTransfer(before, Map.of("log", 65L, "diamond", 1L), before, Map.of("log", 64L)).isEmpty());
        assertTrue(CappedStock.afterTransfer(before, Map.of("log", 65L, "diamond", 3L), before, Map.of("log", 64L)).isEmpty());
    }
    @Test void uncappedInputsAndExcessiveIncreasesAreRejected() {
        var before = Map.of("log", 64L);
        assertTrue(CappedStock.afterTransfer(before, Map.of("log", 65L), before, Map.of()).isEmpty());
        assertTrue(CappedStock.afterTransfer(before, Map.of("log", 129L), before, Map.of("log", 64L)).isEmpty());
    }
}
