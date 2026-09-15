// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.mixin;

import com.example.emiautocrafting.emi.JobSidebar;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.runtime.EmiFavorites;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EmiFavorites.class, remap = false)
public class EmiFavoritesMixin {
    @ModifyVariable(method = "updateSynthetic", at = @At("HEAD"), argsOnly = true)
    private static EmiPlayerInventory autocrafting$storageInventory(EmiPlayerInventory inventory) { return JobSidebar.inventory(inventory); }
    @Inject(method = "updateSynthetic", at = @At("TAIL"))
    private static void autocrafting$groupTree(EmiPlayerInventory inventory, CallbackInfo ci) { JobSidebar.capture(); }
}
