// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.emi;

import com.example.emiautocrafting.EmiAutocrafting;
import com.example.emiautocrafting.EmiAutocraftingConfig;
import com.example.emiautocrafting.client.MenuPort;
import dev.emi.emi.api.*;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.bom.*;
import dev.emi.emi.runtime.*;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** One collapsible batch for the active tree; ordinary EMI favourites remain untouched. */
@EmiEntrypoint
public final class JobSidebar implements EmiPlugin {
    private static MaterialTree tracked;
    private static List<EmiFavorite.Synthetic> entries = List.of();
    private static boolean collapsed;
    private static int page;
    private static long obtained;
    private static MenuPort preview;

    public void register(EmiRegistry registry) {
        registry.addGenericExclusionArea((screen, consumer) -> { Bounds b = bounds(screen); if (b != null) consumer.accept(b); });
        registry.addGenericStackProvider((screen, x, y) -> {
            EmiFavorite.Synthetic item = hovered(screen, x, y);
            return item == null ? EmiStackInteraction.EMPTY : new EmiStackInteraction(item, item.getRecipe(), true);
        });
    }
    public static void track(MaterialTree tree) {
        if (tree != tracked) { tracked = tree; entries = List.of(); obtained = 0; page = 0; collapsed = false; preview = null; }
    }
    public static void reset() { tracked = null; entries = List.of(); preview = null; }
    public static void progress(MaterialTree tree, long count) { if (tree == tracked) obtained = count; }
    public static boolean grouped() {
        return tracked != null && BoM.tree == tracked && BoM.craftingMode && EmiAutocraftingConfig.GROUPED_JOB.get();
    }
    public static void capture() {
        if (!grouped()) return;
        entries = List.copyOf(EmiFavorites.syntheticFavorites);
        EmiFavorites.syntheticFavorites.clear();
    }
    public static EmiPlayerInventory inventory(EmiPlayerInventory fallback) {
        if (!grouped()) return fallback;
        var screen = EmiBridge.screen();
        if (screen == null) return fallback;
        try {
            if (preview == null || preview.menu() != screen.getMenu()) preview = new MenuPort(screen, EmiBridge.freeze());
            var stock = preview.previewStock();
            obtained = stock.getOrDefault(new StackKey(tracked.goal.ingredient.getEmiStacks().getFirst().getItemStack()), 0L);
            return new EmiPlayerInventory(stock.entrySet().stream().map(e -> EmiStack.of(e.getKey().stack()).setAmount(e.getValue())).toList());
        } catch (IllegalArgumentException | ArithmeticException error) { return fallback; }
    }
    public static Bounds bounds(Screen screen) {
        if (!grouped() || !(screen instanceof AbstractContainerScreen<?> container)) return null;
        int right = container.getGuiLeft() + container.getXSize() + 5;
        int available = screen.width - right - 5;
        int x = right;
        if (available < 100) { available = container.getGuiLeft() - 10; x = 5; }
        if (available < 100 || screen.height < 142) return new Bounds(5, 21, Math.min(134, screen.width - 10), 22);
        int w = Math.min(134, available);
        int columns = Math.max(1, (w - 10) / 18);
        int rows = Math.min(5, Math.max(1, (entries.size() + columns - 1) / columns));
        int h = collapsed ? 22 : Math.min(screen.height - 52, 72 + rows * 18);
        return new Bounds(x, 21, w, Math.max(22, h));
    }
    private static int columns(Bounds b) { return Math.max(1, (b.width() - 10) / 18); }
    private static int capacity(Bounds b) { return columns(b) * Math.max(1, (b.height() - 72) / 18); }
    private static int pages(Bounds b) { return Math.max(1, (entries.size() + capacity(b) - 1) / capacity(b)); }
    private static boolean expanded(Bounds b) { return !collapsed && b.height() > 22; }
    private static EmiFavorite.Synthetic hovered(Screen screen, double x, double y) {
        Bounds b = bounds(screen);
        if (b == null || !expanded(b) || x < b.x() + 5 || y < b.y() + 48 || y >= b.bottom() - 24) return null;
        int col = (int) (x - b.x() - 5) / 18, row = (int) (y - b.y() - 48) / 18;
        if (col < 0 || col >= columns(b) || row < 0 || row * columns(b) >= capacity(b)) return null;
        int index = Math.min(page, pages(b) - 1) * capacity(b) + row * columns(b) + col;
        return index < entries.size() ? entries.get(index) : null;
    }
    public static void render(Screen screen, GuiGraphics g, int mouseX, int mouseY, float delta) {
        Bounds b = bounds(screen); if (b == null) return;
        var font = Minecraft.getInstance().font;
        g.fill(b.x(), b.y(), b.right(), b.bottom(), 0xEF14212C);
        g.fill(b.x(), b.y(), b.x() + 2, b.bottom(), 0xFF62B8CD);
        g.fill(b.x() + 2, b.y(), b.right(), b.y() + 21, 0xEF244353);
        tracked.goal.ingredient.render(g, b.x() + 4, b.y() + 3, delta, 1);
        g.drawString(font, font.plainSubstrByWidth("Craft batch", b.width() - 35), b.x() + 23, b.y() + 7, 0xFFFFFF);
        g.drawString(font, expanded(b) ? "−" : "+", b.right() - 10, b.y() + 7, 0xA9DDE8);
        if (!expanded(b)) return;
        long total;
        try { total = Math.multiplyExact(tracked.batches, tracked.goal.amount); }
        catch (ArithmeticException error) { total = Long.MAX_VALUE; }
        String name = tracked.goal.ingredient.getEmiStacks().getFirst().getItemStack().getHoverName().getString();
        g.drawString(font, font.plainSubstrByWidth(name, b.width() - 10), b.x() + 5, b.y() + 25, 0xFFFFFF);
        g.drawString(font, font.plainSubstrByWidth(Math.min(obtained, total) + " / " + total + " items", b.width() - 10), b.x() + 5, b.y() + 36, 0xA9DDE8);
        page = Math.min(page, pages(b) - 1);
        for (int i = 0; i < capacity(b) && page * capacity(b) + i < entries.size(); i++) {
            int x = b.x() + 5 + i % columns(b) * 18, y = b.y() + 48 + i / columns(b) * 18;
            g.fill(x, y, x + 17, y + 17, 0xFF0E1820);
            entries.get(page * capacity(b) + i).render(g, x + 1, y + 1, delta, 7);
        }
        g.fill(b.x() + 4, b.bottom() - 21, b.right() - 4, b.bottom() - 20, 0xFF42606F);
        g.drawString(font, "Tree", b.x() + 5, b.bottom() - 13, 0xA9DDE8);
        g.drawString(font, "Clear", b.right() - 32, b.bottom() - 13, 0xC6CDD2);
        if (pages(b) > 1 && mouseX >= b.x() && mouseX < b.right() && mouseY >= b.y() && mouseY < b.y() + 22)
            g.renderTooltip(font, Component.literal("Scroll batch: page " + (page + 1) + "/" + pages(b)), mouseX, mouseY);
        EmiFavorite.Synthetic item = hovered(screen, mouseX, mouseY);
        if (item != null) {
            String label = item.getEmiStacks().getFirst().getItemStack().getHoverName().getString();
            g.renderTooltip(font, font.split(Component.literal(label + " — " + item.amount + " remaining"), 250), mouseX, mouseY);
        }
    }
    public static boolean click(Screen screen, double x, double y, int button) {
        Bounds b = bounds(screen); if (b == null || !b.contains((int) x, (int) y)) return false;
        if (y < b.y() + 22) {
            if (b.height() == 22 && !collapsed) { EmiAutocrafting.cancel(); EmiApi.viewRecipeTree(); }
            else { collapsed = !collapsed; EmiScreenManager.forceRecalculate(); }
        } else if (expanded(b) && y >= b.bottom() - 22) {
            EmiAutocrafting.cancel();
            if (x >= b.right() - 38) {
                BoM.tree = null; BoM.craftingMode = false; tracked = null; entries = List.of();
                EmiFavorites.syntheticFavorites.clear(); EmiScreenManager.forceRecalculate();
            } else EmiApi.viewRecipeTree();
        } else {
            EmiFavorite.Synthetic item = hovered(screen, x, y);
            if (item != null) {
                EmiAutocrafting.cancel();
                if (button == 1) EmiApi.displayUses(item.getStack());
                else if (item.getRecipe() != null) EmiApi.displayRecipe(item.getRecipe());
                else EmiApi.displayRecipes(item.getStack());
            }
        }
        return true;
    }
    public static boolean scroll(Screen screen, double x, double y, double amount) {
        Bounds b = bounds(screen); if (b == null || !b.contains((int) x, (int) y)) return false;
        page = Math.clamp(page - (int) Math.signum(amount), 0, pages(b) - 1); return true;
    }
}
