// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EmiAutocraftingConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> PREPARE, TOGGLE, STEP, CANCEL;
    public static final ModConfigSpec.IntValue TIMEOUT, PACE;
    public static final ModConfigSpec.BooleanValue DIAGNOSTICS;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Keyboard shortcuts: CTRL/SHIFT/ALT plus A-Z, 0-9, or F1-F12. NONE disables a shortcut.").push("keys");
        PREPARE = b.define("prepareTree", "CTRL+A");
        TOGGLE = b.define("startOrStop", "CTRL+C");
        STEP = b.define("singleStep", "N");
        CANCEL = b.define("cancel", "CTRL+X"); b.pop();
        TIMEOUT = b.comment("Client ticks to wait for a verified full server menu update.").defineInRange("timeoutTicks", 200, 40, 2400);
        PACE = b.comment("Minimum client ticks between confirmed crafts.").defineInRange("pacingTicks", 4, 2, 100);
        DIAGNOSTICS = b.define("diagnosticLogging", false);
        SPEC = b.build();
    }
}
