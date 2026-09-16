// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.client;

import dev.emi.emi.api.EmiApi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

/** Keeps long recipe errors readable instead of truncating them in the status banner. */
public final class CraftingProblemScreen extends Screen {
    private final Screen parent;
    private final CraftingProblem problem;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private int offset;
    public CraftingProblemScreen(Screen parent, CraftingProblem problem) {
        super(Component.literal("Crafting stopped")); this.parent = parent; this.problem = problem;
    }
    protected void init() {
        lines.clear();
        for (String paragraph : List.of("Item: " + problem.item(), problem.reason(),
                "Tree: " + String.join(" → ", problem.path()), "Recipe: " + problem.recipeId(),
                "Type: " + problem.category(), "Selected by: " + problem.selection(),
                "Supply this item yourself, or select another recipe in EMI. Furnace and machine steps must be done separately.",
                "Choices inside the tree override hearts. Removing a sidebar favourite does not change either. Clear the batch and prepare it again to reset choices inside the tree.")) {
            lines.addAll(font.split(Component.literal(paragraph), Math.min(480, width - 36)));
            lines.add(FormattedCharSequence.EMPTY);
        }
        int w = Math.min(155, (width - 36) / 2), x = width / 2;
        var view = addRenderableWidget(Button.builder(Component.literal("View recipe"), b -> {
            minecraft.setScreen(parent); EmiApi.displayRecipe(problem.recipe());
        }).bounds(x - w - 3, height - 55, w, 20).build());
        view.active = problem.recipe() != null;
        addRenderableWidget(Button.builder(Component.literal("Edit tree"), b -> {
            minecraft.setScreen(parent); EmiApi.viewRecipeTree();
        }).bounds(x + 3, height - 55, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Copy details"), b -> minecraft.keyboardHandler.setClipboard(problem.report()))
                .bounds(x - w - 3, height - 30, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back to crafting"), b -> onClose())
                .bounds(x + 3, height - 30, w, 20).build());
        offset = Math.min(offset, Math.max(0, lines.size() - rows()));
    }
    private int rows() { return Math.max(1, (height - 108) / 11); }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        offset = Math.clamp(offset - (int) Math.signum(vertical) * 3, 0, Math.max(0, lines.size() - rows()));
        return true;
    }
    public void render(GuiGraphics g, int x, int y, float tick) {
        renderBackground(g, x, y, tick); super.render(g, x, y, tick);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        int left = Math.max(18, (width - 480) / 2);
        for (int row = 0; row < rows() && row + offset < lines.size(); row++)
            g.drawString(font, lines.get(row + offset), left, 36 + row * 11, 0xE0E0E0);
        if (lines.size() > rows()) g.drawCenteredString(font, "Scroll for more", width / 2, height - 70, 0xBBBBBB);
    }
    public void onClose() { minecraft.setScreen(parent); }
    public boolean isPauseScreen() { return false; }
}
