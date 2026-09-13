// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.client;

import com.example.emiautocrafting.EmiAutocrafting;
import com.example.emiautocrafting.emi.EmiBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class QuantityScreen extends Screen {
    private final Screen parent;
    private final EmiBridge.Selection selection;
    private EditBox quantity;
    private String error = "";
    public QuantityScreen(Screen parent, EmiBridge.Selection selection) {
        super(Component.literal("Prepare crafting tree")); this.parent = parent; this.selection = selection;
    }
    protected void init() {
        quantity = new EditBox(font, width / 2 - 90, height / 2 - 10, 180, 20, Component.literal("Requested total items"));
        quantity.setMaxLength(10); quantity.setValue(Long.toString(selection.total()));
        quantity.setFilter(value -> value.matches("[0-9]*"));
        addRenderableWidget(quantity); setInitialFocus(quantity);
        addRenderableWidget(Button.builder(Component.literal("Prepare tree"), button -> prepare()).bounds(width / 2 - 90, height / 2 + 22, 180, 20).build());
    }
    private void prepare() {
        try {
            long total = Long.parseLong(quantity.getValue());
            EmiBridge.prepare(selection, total);
            minecraft.setScreen(parent);
            EmiAutocrafting.feedback("Tree prepared: " + total + " " + selection.output().getItemStack().getHoverName().getString());
        } catch (IllegalArgumentException e) { error = "Enter a total from 1 to 1,000,000,000"; }
    }
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 257 || key == 335) { prepare(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 64, 0xFFFFFF);
        graphics.drawCenteredString(font, selection.output().getItemStack().getHoverName(), width / 2, height / 2 - 44, 0xFFFFFF);
        graphics.drawCenteredString(font, "Total items wanted, including items you already have", width / 2, height / 2 - 28, 0xBBBBBB);
        graphics.drawCenteredString(font, error, width / 2, height / 2 + 50, 0xFF7777);
    }
    public void onClose() { minecraft.setScreen(parent); }
    public boolean isPauseScreen() { return false; }
}
