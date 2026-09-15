// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EmiAutocraftingConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> PREPARE, TOGGLE, STEP, CANCEL;
    public static final ModConfigSpec.IntValue TIMEOUT, PACE;
    public static final ModConfigSpec.BooleanValue DIAGNOSTICS, GROUPED_JOB;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Keyboard shortcuts: CTRL/SHIFT/ALT plus A-Z, 0-9, or F1-F12. NONE disables a shortcut.").push("keys");
        PREPARE = b.define("prepareTree", "CTRL+A");
        TOGGLE = b.define("startOrStop", "CTRL+C");
        STEP = b.define("singleStep", "N");
        CANCEL = b.define("cancel", "CTRL+X"); b.pop();
        TIMEOUT = b.comment("Client ticks to wait for a verified full server menu update.").defineInRange("timeoutTicks", 200, 40, 2400);
        PACE = b.comment("Extra client ticks after a confirmed craft. Zero runs as soon as the server confirms, at most once per client tick.").defineInRange("pacingTicks", 0, 0, 100);
        GROUPED_JOB = b.comment("Put the active recipe tree in its own collapsible sidebar batch, separate from favourites.").define("groupCraftingJob", true);
        DIAGNOSTICS = b.define("diagnosticLogging", false);
        SPEC = b.build();
    }
}
