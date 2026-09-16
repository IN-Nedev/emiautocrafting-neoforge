// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.emi;

import com.example.emiautocrafting.EmiAutocrafting;
import com.example.emiautocrafting.client.CraftingCompatibility;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.stack.*;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.*;

/** Quark advertises one placeholder wood even though mixed recipes accept a material tag. */
final class QuarkRecipes {
    private QuarkRecipes() {}
    static void register(EmiRegistry registry) {
        for (RecipeHolder<?> holder : registry.getRecipeManager().getRecipes()) {
            if (!CraftingCompatibility.mixedMaterials(holder.value())) continue;
            try {
                var field = holder.value().getClass().getDeclaredField("tag"); field.setAccessible(true);
                @SuppressWarnings("unchecked") TagKey<Item> tag = (TagKey<Item>) field.get(holder.value());
                List<EmiIngredient> inputs = new ArrayList<>();
                for (int slot = 0; slot < 9; slot++) inputs.add(slot == 4 ? EmiStack.EMPTY : EmiIngredient.of(tag));
                var output = holder.value().getResultItem(Minecraft.getInstance().level.registryAccess());
                var replacement = new MixedMaterialsRecipe(inputs, EmiStack.of(output), holder.id());
                // Removal predicates also see recipes added later by JEI integration and other plugins.
                registry.removeRecipes(recipe -> holder.id().equals(recipe.getId()) && !(recipe instanceof MixedMaterialsRecipe));
                registry.addRecipe(replacement);
            } catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
                EmiAutocrafting.diagnostic("Cannot describe Quark mixed recipe {}: {}", holder.id(), error.toString());
            }
        }
    }
    private static final class MixedMaterialsRecipe extends EmiCraftingRecipe {
        MixedMaterialsRecipe(List<EmiIngredient> inputs, EmiStack output, net.minecraft.resources.ResourceLocation id) {
            super(inputs, output, id, false);
        }
        @Override public boolean canFit(int width, int height) { return width == 3 && height == 3; }
    }
}
