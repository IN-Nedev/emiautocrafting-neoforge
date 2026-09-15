// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Complete shortages from the checked planner, after subtracting accessible storage. */
public final class MissingItemsScreen extends Screen {
    private final Screen parent;
    private final String source;
    private final List<Map.Entry<String, Long>> items;
    private int offset;
    public MissingItemsScreen(Screen parent, Map<String, Long> missing, String source) {
        super(Component.literal("Missing crafting materials"));
        this.parent = parent; this.source = source;
        items = missing.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
    }
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Back to crafting"), b -> onClose())
                .bounds(width / 2 - 90, height - 30, 180, 20).build());
        offset = Math.min(offset, Math.max(0, items.size() - rows()));
    }
    private int rows() { return Math.max(1, (height - 116) / 20); }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        offset = Math.clamp(offset - (int) Math.signum(vertical) * 3, 0, Math.max(0, items.size() - rows()));
        return true;
    }
    public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        renderBackground(g, mouseX, mouseY, tick);
        super.render(g, mouseX, mouseY, tick);
        int left = Math.max(12, width / 2 - 210), right = Math.min(width - 12, width / 2 + 210);
        g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        g.drawCenteredString(font, font.plainSubstrByWidth(source, width - 24), width / 2, 38, 0xBBBBBB);
        g.drawCenteredString(font, "These amounts are still needed. Crafting is stopped.", width / 2, 53, 0xBBBBBB);
        String hovered = null;
        for (int row = 0; row < rows() && row + offset < items.size(); row++) {
            var entry = items.get(row + offset); int y = 73 + row * 20;
            g.fill(left, y, right, y + 19, 0xC0101820);
            String count = Long.toString(entry.getValue());
            g.drawString(font, font.plainSubstrByWidth(entry.getKey(), right - left - 22 - font.width(count)), left + 5, y + 5, 0xFFFFFF);
            g.drawString(font, count, right - font.width(count) - 5, y + 5, 0xFF8888);
            if (mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + 19)
                hovered = entry.getKey();
        }
        if (items.size() > rows()) g.drawCenteredString(font, "Scroll: " + (offset + 1) + "–" + Math.min(items.size(), offset + rows()) + " of " + items.size(), width / 2, height - 45, 0xBBBBBB);
        if (hovered != null) g.renderTooltip(font, font.split(Component.literal(hovered), Math.min(300, width - 30)), mouseX, mouseY);
    }
    public void onClose() { minecraft.setScreen(parent); }
    public boolean isPauseScreen() { return false; }
}
