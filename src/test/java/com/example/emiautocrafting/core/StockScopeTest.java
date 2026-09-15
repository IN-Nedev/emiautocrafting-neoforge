// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StockScopeTest {
    private final StockScope<String> scope = new StockScope<>(Set.of("log", "plank", "reusable stamp"));

    @Test void unusedToolboxMetadataChangeDoesNotBlockAConfirmedCraft() {
        var expected = Map.of("plank", 4L, "toolbox old data", 1L);
        var actual = Map.of("plank", 4L, "toolbox new data", 1L);
        assertTrue(scope.matches(expected, actual));
        assertEquals(actual, scope.rebase(expected, actual));
    }

    @Test void unrelatedArrivalsAndDeparturesAreUsedByTheNextPlan() {
        var expected = Map.of("plank", 4L, "diamond", 2L);
        var actual = Map.of("plank", 4L, "iron", 3L);
        assertTrue(scope.matches(expected, actual));
        assertEquals(actual, scope.rebase(expected, actual));
    }

    @Test void MissingOutputAndUnconsumedInputStillFail() {
        var expected = Map.of("plank", 4L);
        assertFalse(scope.matches(expected, Map.of("plank", 3L, "toolbox new data", 1L)));
        assertFalse(scope.matches(expected, Map.of("log", 1L, "plank", 4L, "toolbox new data", 1L)));
    }

    @Test void UnchangedReusableInputStillMustExist() {
        assertFalse(scope.matches(Map.of("plank", 4L, "reusable stamp", 1L), Map.of("plank", 4L)));
    }

    @Test void CappedTransferCanRevealInputsWhileUnusedMetadataChanges() {
        var before = Map.of("log", 64L, "toolbox old data", 1L);
        var filled = Map.of("log", 65L, "toolbox new data", 1L);
        var afterCraft = Map.of("log", 63L, "plank", 4L, "toolbox old data", 1L);
        var rebased = CappedStock.afterTransfer(scope.project(before), scope.project(filled),
                scope.project(afterCraft), Map.of("log", 64L)).orElseThrow();
        assertEquals(Map.of("log", 64L, "plank", 4L, "toolbox new data", 1L), scope.rebase(rebased, filled));
        assertTrue(CappedStock.afterTransfer(scope.project(before), Map.of("log", 63L),
                scope.project(afterCraft), Map.of("log", 64L)).isEmpty());
    }
}
