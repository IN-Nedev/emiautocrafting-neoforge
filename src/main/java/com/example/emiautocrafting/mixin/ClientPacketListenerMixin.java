// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.mixin;

import com.example.emiautocrafting.EmiAutocrafting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    // TAIL runs after PacketUtils schedules this handler on the Minecraft client thread.
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void autocrafting$content(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        EmiAutocrafting.onContent(packet);
    }
    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void autocrafting$recipes(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
        EmiAutocrafting.recipeEpoch++;
    }
}
