// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.emi;

import com.example.emiautocrafting.core.TreePlanner;
import com.example.emiautocrafting.core.Quantities;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.recipe.handler.*;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.bom.*;
import dev.emi.emi.handler.CraftingRecipeHandler;
import dev.emi.emi.handler.InventoryRecipeHandler;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** The only boundary to EMI internal tree, favourites and fill implementations. Pinned to 1.1.24. */
public final class EmiBridge {
    public record Selection(EmiRecipe recipe, EmiStack output, long total) {}
    public record Frozen(MaterialTree tree, EmiRecipe rootRecipe, EmiStack output, long total,
            long treeBatches, long treeAmount, Object manager, Map<EmiIngredient, EmiRecipe> resolutions,
            Map<EmiIngredient, EmiRecipe> preferences, Map<EmiIngredient, EmiRecipe> defaults,
            Set<EmiRecipe> disabled) {}
    private EmiBridge() {}
    public static boolean searchFocused() { return EmiApi.isSearchFocused(); }
    public static boolean hasTree() { return BoM.tree != null && BoM.tree.goal != null; }
    public static AbstractContainerScreen<?> screen() { return EmiApi.getHandledScreen(); }
    public static Selection hovered() {
        EmiStackInteraction interaction = EmiApi.getHoveredStack(true);
        EmiIngredient ingredient = interaction.getStack();
        EmiRecipe recipe = interaction.getRecipeContext();
        if (recipe == null) recipe = EmiApi.getRecipeContext(ingredient);
        if (ingredient instanceof EmiFavorite favorite) ingredient = favorite.getStack();
        if (recipe == null) recipe = BoM.getRecipe(ingredient);
        if (recipe == null) throw new IllegalArgumentException("Hover a recipe output or select its preferred recipe with EMI's heart");
        EmiStack selected = null;
        for (EmiStack output : recipe.getOutputs()) {
            if (ingredient.getEmiStacks().stream().anyMatch(s -> s.isEqual(output, Comparison.compareComponents()))) {
                if (selected != null) throw new IllegalArgumentException("Hover one specific recipe output");
                selected = output;
            }
        }
        if (selected == null) throw new IllegalArgumentException("Hover the selected recipe's output");
        if (selected.getItemStack().isEmpty()) throw new IllegalArgumentException("Only item targets are supported");
        return new Selection(recipe, selected.copy(), Math.max(1, ingredient.getAmount()));
    }
    public static void prepare(Selection selection, long total) {
        if (total <= 0 || total > 1_000_000_000L) throw new IllegalArgumentException("Enter a total from 1 to 1,000,000,000");
        BoM.setGoal(selection.recipe());
        BoM.tree.goal = new MaterialNode(selection.output().copy().setAmount(1));
        BoM.tree.goal.defineRecipe(selection.recipe());
        BoM.tree.goal.amount = 1; // EMI batches now count requested items, not executions.
        BoM.tree.batches = total;
        BoM.tree.recalculate();
        BoM.craftingMode = true;
    }
    public static Frozen freeze() {
        if (!hasTree()) throw new IllegalArgumentException("Prepare an EMI recipe tree first");
        MaterialTree tree = BoM.tree;
        long total = Math.multiplyExact(tree.batches, tree.goal.amount);
        if (total <= 0 || total > 1_000_000_000L) throw new IllegalArgumentException("Target must be 1 to 1,000,000,000 items");
        if (tree.goal.ingredient.getEmiStacks().size() != 1 || tree.goal.ingredient.getEmiStacks().getFirst().getItemStack().isEmpty()) throw new IllegalArgumentException("Select one target output");
        return new Frozen(tree, tree.goal.recipe, tree.goal.ingredient.getEmiStacks().getFirst().copy(), total,
                tree.batches, tree.goal.amount, EmiApi.getRecipeManager(), new HashMap<>(tree.resolutions),
                new HashMap<>(BoM.addedRecipes), new HashMap<>(BoM.defaultRecipes), new HashSet<>(BoM.disabledRecipes));
    }
    public static boolean unchanged(Frozen plan) {
        return BoM.tree == plan.tree() && BoM.tree.goal.recipe == plan.rootRecipe()
                && BoM.tree.goal.ingredient.getEmiStacks().size() == 1
                && BoM.tree.goal.ingredient.getEmiStacks().getFirst().isEqual(plan.output(), Comparison.compareComponents())
                && BoM.tree.batches == plan.treeBatches() && BoM.tree.goal.amount == plan.treeAmount()
                && EmiApi.getRecipeManager() == plan.manager() && BoM.tree.resolutions.equals(plan.resolutions())
                && BoM.addedRecipes.equals(plan.preferences()) && BoM.defaultRecipes.equals(plan.defaults())
                && BoM.disabledRecipes.equals(plan.disabled());
    }
    public static TreePlanner.Node<StackKey, EmiRecipe> project(Frozen plan, Set<StackKey> inventory) {
        return projectNode(plan.tree().goal, inventory, new HashSet<>(), 0, new int[]{0}, true);
    }
    private static TreePlanner.Node<StackKey, EmiRecipe> projectNode(MaterialNode node, Set<StackKey> inventory,
            Set<EmiRecipe> path, int depth, int[] visits, boolean goal) {
        if (depth > 64 || ++visits[0] > 4096) throw new IllegalArgumentException("Recipe tree is too large or cyclic");
        if (node.recipe instanceof EmiResolutionRecipe && node.children != null && !node.children.isEmpty()) {
            var resolved = projectNode(node.children.getFirst(), inventory, path, depth + 1, visits, goal);
            return new TreePlanner.Node<>(resolved.label(), resolved.alternatives(), node.amount, resolved.output(),
                    resolved.outputCount(), resolved.recipe(), resolved.inputs(), resolved.returns(), node.catalyst, resolved.obstacle());
        }
        LinkedHashSet<StackKey> keys = new LinkedHashSet<>();
        for (EmiStack stack : node.ingredient.getEmiStacks()) {
            if (!stack.getItemStack().isEmpty()) keys.add(new StackKey(stack.getItemStack()));
        }
        for (StackKey key : inventory) {
            if (node.ingredient.getEmiStacks().stream().anyMatch(s -> goal
                    ? s.isEqual(EmiStack.of(key.stack()), Comparison.compareComponents()) : s.isEqual(EmiStack.of(key.stack())))) keys.add(key);
        }
        String label = keys.isEmpty() ? "unsupported ingredient" : keys.iterator().next().label();
        StackKey output = keys.isEmpty() ? null : keys.iterator().next();
        String obstacle = null;
        EmiRecipe recipe = node.recipe;
        boolean addedToPath = recipe != null && path.add(recipe);
        if (recipe != null && !addedToPath) obstacle = "Cyclic recipe: " + label;
        if (node.consumeChance != 1 || node.produceChance != 1) obstacle = "Chance-based recipes are not supported: " + label;
        if (recipe != null && recipe.getCategory() != VanillaEmiRecipeCategories.CRAFTING) obstacle = "Requires an unsupported machine: " + label;
        if (recipe != null && (recipe.getOutputs().size() != 1 || !recipe.supportsRecipeTree())) obstacle = "Unsupported dynamic recipe: " + label;
        List<TreePlanner.Node<StackKey, EmiRecipe>> children = new ArrayList<>();
        List<TreePlanner.Returned<StackKey>> returned = new ArrayList<>();
        if (obstacle == null && node.children != null) {
            for (MaterialNode child : node.children) {
                children.add(projectNode(child, inventory, path, depth + 1, visits, false));
                if (!child.remainder.isEmpty() && !child.remainder.getItemStack().isEmpty() && child.remainderAmount > 0) {
                    returned.add(new TreePlanner.Returned<>(new StackKey(child.remainder.getItemStack()), child.remainderAmount, !child.catalyst));
                }
            }
        }
        if (addedToPath) path.remove(recipe);
        if (recipe == null && node.ingredient.getEmiStacks().stream().anyMatch(s -> !EmiApi.getRecipeManager().getRecipesByOutput(s).isEmpty())) {
            label += " (select a preferred recipe in EMI if it should be crafted)";
        }
        return new TreePlanner.Node<>(label, List.copyOf(keys), node.amount, output,
                Math.max(1, node.divisor), recipe, children, returned, node.catalyst, obstacle);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static StandardRecipeHandler<AbstractContainerMenu> handler(EmiRecipe recipe, AbstractContainerScreen<?> screen) {
        var handler = EmiRecipeFiller.getFirstValidHandler(recipe, (AbstractContainerScreen) screen);
        // An arbitrary Standard handler or JEI transfer is not evidence of immediate crafting.
        if (handler == null || (handler.getClass() != CraftingRecipeHandler.class && handler.getClass() != InventoryRecipeHandler.class)) {
            throw new IllegalArgumentException("This interface has no verified immediate crafting handler");
        }
        return (StandardRecipeHandler<AbstractContainerMenu>) handler;
    }
    public static RecipeHolder<?> rawRecipe(EmiRecipe recipe) { return EmiPort.getRecipe(recipe.getId()); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean fill(EmiRecipe recipe, AbstractContainerScreen<?> screen, long outstandingBatches) {
        return EmiRecipeFiller.performFill(recipe, (AbstractContainerScreen) screen, EmiCraftContext.Type.CRAFTABLE,
                EmiCraftContext.Destination.INVENTORY, Quantities.dispatch(outstandingBatches, 1));
    }
    public static void updateSidebar(Map<StackKey, Long> inventory) {
        // TreeCost remains EMI's sidebar presentation; the execution ledger uses checked integer math.
        if (BoM.tree == null) return;
        List<EmiStack> stacks = new ArrayList<>();
        inventory.forEach((key, count) -> stacks.add(EmiStack.of(key.stack()).setAmount(count)));
        EmiFavorites.updateSynthetic(new EmiPlayerInventory(stacks));
    }
}
