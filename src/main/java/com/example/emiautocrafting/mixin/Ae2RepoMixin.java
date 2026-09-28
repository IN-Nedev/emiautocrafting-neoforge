// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.mixin;

import com.example.emiautocrafting.emi.JobSidebar;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional AE2 hook; wireless terminals use the same client repository. */
@Pseudo
@Mixin(targets = "appeng.client.gui.me.common.Repo", remap = false)
public abstract class Ae2RepoMixin {
    @Inject(method = "handleUpdate(ZLjava/util/List;)V", at = @At("TAIL"))
    private void autocrafting$stockUpdated(boolean fullUpdate, List<?> entries, CallbackInfo ci) {
        // Coalesce packet fragments and recalculate on the following client tick.
        JobSidebar.storageChanged();
    }
}
