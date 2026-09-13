// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.core;

import java.util.Locale;

/** GLFW key/modifier values, kept independent of the Minecraft runtime. */
public final class KeyBindings {
    private KeyBindings() {}
    public static boolean matches(String binding, int key, int modifiers) {
        if (binding == null || binding.isBlank()) return false;
        String[] parts = binding.toUpperCase(Locale.ROOT).split("\\+", -1);
        int expectedMods = 0;
        for (int i = 0; i < parts.length - 1; i++) expectedMods |= switch (parts[i].trim()) {
            case "CTRL", "CONTROL" -> 2;
            case "SHIFT" -> 1;
            case "ALT" -> 4;
            default -> 0x4000;
        };
        String last = parts[parts.length - 1].trim();
        int expectedKey = -1;
        if (last.matches("[A-Z0-9]")) expectedKey = last.charAt(0);
        else if (last.matches("F([1-9]|1[0-2])")) expectedKey = 290 + Integer.parseInt(last.substring(1)) - 1;
        return expectedKey >= 0 && key == expectedKey && (modifiers & (2 | 1 | 4 | 8)) == expectedMods;
    }
}
