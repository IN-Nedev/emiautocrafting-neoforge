// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

/** Keep menu packets independent of asynchronously refreshed client block-entity inventories. */
@Pseudo
@Mixin(targets = "com.leclowndu93150.craftingstationjei.menu.CraftingStationMenu", remap = false)
public abstract class CraftingStationMenuMixin {
    @Shadow @Final public Level world;
    @Shadow @Final private Map<Direction, Integer> clientSlotCountOverride;

    @Inject(method = "useClientCacheFor", at = @At("HEAD"), cancellable = true)
    private void autocrafting$serverBackedDisplay(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        // The opening packet supplies the real slot counts. The station already fills and
        // predicts this cache through its vanilla/custom menu packets. Reading live block
        // entities instead lets their separately arriving updates undo a confirmed transfer.
        // Never redirect the server's real inventory handlers into a display cache.
        if (world.isClientSide && clientSlotCountOverride.containsKey(direction)) cir.setReturnValue(true);
    }
}
