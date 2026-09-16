// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.client;

import com.example.emiautocrafting.emi.EmiBridge;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.List;

/** The selected step and dependency path, retained after a batch stops. */
public record CraftingProblem(String item, EmiRecipe recipe, List<String> path, String reason,
        String selection, String recipeId, String category, String recipeClass, String serializer) {
    public static CraftingProblem of(EmiBridge.Frozen tree, String item, EmiRecipe recipe, List<String> path, String reason) {
        var raw = recipe == null ? null : EmiBridge.rawRecipe(recipe);
        String selection = recipe == tree.rootRecipe() ? "Batch target"
                : tree.resolutions().containsValue(recipe) ? "Choice made inside this tree"
                : tree.preferences().containsValue(recipe) ? "Preferred recipe (EMI heart)" : "EMI default recipe";
        return new CraftingProblem(item, recipe, List.copyOf(path), reason, selection,
                recipe == null ? "none" : String.valueOf(recipe.getId()),
                recipe == null ? "none" : recipe.getCategory().getName().getString(),
                raw == null ? "unavailable" : raw.value().getClass().getName(),
                raw == null ? "unavailable" : String.valueOf(BuiltInRegistries.RECIPE_SERIALIZER.getKey(raw.value().getSerializer())));
    }
    public String summary() { return "Cannot craft " + item + ": " + reason; }
    public String report() {
        return summary() + "\nTree: " + String.join(" -> ", path) + "\nRecipe: " + recipeId
                + "\nCategory: " + category + "\nSelected by: " + selection
                + "\nRecipe class: " + recipeClass + "\nSerializer: " + serializer;
    }
}
