// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ControlsTest {
    @Test void exactModifiersPreserveOtherShortcuts() {
        assertTrue(KeyBindings.matches("CTRL+A",65,2));
        assertFalse(KeyBindings.matches("CTRL+A",65,0));
        assertFalse(KeyBindings.matches("CTRL+A",65,8));
        assertFalse(KeyBindings.matches("CTRL+A",65,3));
    }
    @Test void disabledAndInvalidBindingsNeverMatchUnknownKeys() {
        assertFalse(KeyBindings.matches("NONE",-1,0));
        assertFalse(KeyBindings.matches("invalid",-1,0));
        assertFalse(KeyBindings.matches("",65,0));
        assertFalse(KeyBindings.matches("CTRL+",65,2));
    }
    @Test void configuredFunctionKeysAndModifiers() {
        assertTrue(KeyBindings.matches("ALT+SHIFT+F12",301,5));
        assertFalse(KeyBindings.matches("ALT+SHIFT+F12",300,5));
    }
}
