// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.client;

import net.minecraft.world.item.crafting.*;
import java.util.List;

/** Only reviewed recipe implementations with deterministic output/remainder behaviour. */
public final class CraftingCompatibility {
    public static final String QUARK_MIXED = "org.violetmoon.quark.content.building.recipe.MixedExclusionRecipe";
    private CraftingCompatibility() {}

    public static CraftingRecipe verify(Recipe<?> recipe) { return verify(recipe, 0); }
    public static boolean mixedMaterials(Recipe<?> recipe) { return recipe.getClass().getName().equals(QUARK_MIXED); }
    public static boolean constrainedMaterials(Recipe<?> recipe) {
        return mixedMaterials(recipe) || recipe.getClass().getName().equals("org.violetmoon.quark.base.recipe.ExclusionRecipe");
    }
    private static CraftingRecipe verify(Recipe<?> recipe, int depth) {
        if (depth > 8) throw new IllegalArgumentException("Recipe wrappers are nested too deeply");
        Class<?> type = recipe.getClass();
        if (type == ShapedRecipe.class || type == ShapelessRecipe.class) return (CraftingRecipe) recipe;
        // Quark 4.1: fixed output, vanilla remainders, with additional recipe-match exclusions.
        // Its actual matches() and recipe-manager winner must still pass before transfer.
        if (mixedMaterials(recipe) && recipe instanceof CraftingRecipe crafting) return crafting;
        if (type.getName().equals("org.violetmoon.quark.base.recipe.ExclusionRecipe") && recipe instanceof CraftingRecipe crafting) {
            try {
                var field = type.getDeclaredField("parent"); field.setAccessible(true);
                if (!(field.get(recipe) instanceof Recipe<?> parent)) throw new IllegalArgumentException("Unrecognized Quark recipe wrapper");
                verify(parent, depth + 1);
                return crafting;
            } catch (ReflectiveOperationException | LinkageError error) {
                throw new IllegalArgumentException("This Quark recipe version cannot be verified", error);
            }
        }
        boolean knownShaped = type.getName().equals("dev.latvian.mods.kubejs.recipe.special.ShapedKubeJSRecipe")
                && type.getSuperclass() == ShapedRecipe.class;
        boolean knownShapeless = type.getName().equals("dev.latvian.mods.kubejs.recipe.special.ShapelessKubeJSRecipe")
                && type.getSuperclass() == ShapelessRecipe.class;
        if (knownShaped || knownShapeless) {
            // Public accessors keep KubeJS optional. Unknown API shapes fail closed.
            // Never call assemble/remainder scripts to discover whether they are deterministic.
            try {
                Object actions = type.getMethod("kjs$getIngredientActions").invoke(recipe);
                Object modifier = type.getMethod("kjs$getModifyResult").invoke(recipe);
                if (actions instanceof List<?> list && list.isEmpty()
                        && modifier instanceof String name && name.isEmpty()) return (CraftingRecipe) recipe;
            } catch (ReflectiveOperationException | LinkageError error) {
                throw new IllegalArgumentException("This KubeJS recipe version cannot be verified", error);
            }
            throw new IllegalArgumentException("KubeJS ingredient actions or output scripts are unsupported");
        }
        throw new IllegalArgumentException("Unsupported custom crafting recipe; craft this item separately or choose another recipe");
    }
}
