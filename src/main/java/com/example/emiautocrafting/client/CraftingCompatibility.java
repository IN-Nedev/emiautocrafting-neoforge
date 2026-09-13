// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.client;

import net.minecraft.world.item.crafting.*;
import java.util.List;

/** Optional KubeJS boundary: only its known wrappers with vanilla output/remainder behaviour. */
final class CraftingCompatibility {
    private CraftingCompatibility() {}

    static CraftingRecipe verify(Recipe<?> recipe) {
        Class<?> type = recipe.getClass();
        if (type == ShapedRecipe.class || type == ShapelessRecipe.class) return (CraftingRecipe) recipe;
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
        throw new IllegalArgumentException("Unsupported crafting recipe; this recipe needs a dedicated adapter");
    }
}
