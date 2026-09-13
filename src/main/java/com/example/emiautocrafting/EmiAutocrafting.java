// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting;

import com.example.emiautocrafting.client.*;
import com.example.emiautocrafting.core.JobController;
import com.example.emiautocrafting.emi.EmiBridge;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import com.example.emiautocrafting.mixin.RecipeBookComponentAccessor;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import static com.example.emiautocrafting.core.KeyBindings.matches;
import org.slf4j.Logger;
import java.util.*;

@Mod(value = EmiAutocrafting.MOD_ID, dist = Dist.CLIENT)
public final class EmiAutocrafting {
    public static final String MOD_ID = "emiautocrafting";
    private static final Logger LOG = LogUtils.getLogger();
    public static long syncSequence, recipeEpoch;
    private static long ticks, feedbackUntil;
    private static String feedback = "";
    private static MenuPort port;
    private static final JobController<MenuPort.Operation> JOB = new JobController<>();
    private static final Set<Integer> PRESSED = new HashSet<>();
    private static AbstractContainerMenu quarantined;
    private static boolean mustReopenInventory;
    public EmiAutocrafting(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, EmiAutocraftingConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new ConfigurationScreen(mod, parent));
        NeoForge.EVENT_BUS.addListener(EmiAutocrafting::tick);
        NeoForge.EVENT_BUS.addListener(EmiAutocrafting::keyPressed);
        NeoForge.EVENT_BUS.addListener(EmiAutocrafting::keyReleased);
        NeoForge.EVENT_BUS.addListener(EmiAutocrafting::render);
        NeoForge.EVENT_BUS.addListener(EmiAutocrafting::mouse);
        NeoForge.EVENT_BUS.addListener(EmiAutocrafting::logout);
        LOG.info("EMI Autocrafting 2.0.0-beta.2: Minecraft 1.21.1, NeoForge 21.1.249, EMI 1.1.24");
    }
    public static JobController.State state() { return JOB.state(); }
    public static String status() { return JOB.message(); }
    public static void feedback(String text) { feedback = text; feedbackUntil = ticks + 200; }
    public static void quarantine(AbstractContainerMenu menu) { quarantined = menu; mustReopenInventory = true; }
    public static void diagnostic(String message, Object... arguments) {
        if (EmiAutocraftingConfig.DIAGNOSTICS.get()) LOG.info(message, arguments);
    }
    public static void logError(Exception error) {
        if (EmiAutocraftingConfig.DIAGNOSTICS.get()) LOG.error("Crafting failure; state={}, menu={}", JOB.state(), port == null ? null : port.menu().getClass().getName(), error);
    }
    public static void onContent(ClientboundContainerSetContentPacket packet) {
        syncSequence++;
        if (port != null && JOB.active()) port.onSnapshot(new MenuSnapshot(syncSequence, packet.getContainerId(), packet.getItems(), packet.getCarriedItem()));
    }
    private static void tick(ClientTickEvent.Post event) {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || mc.player == null || (quarantined != null && mc.player.containerMenu != quarantined)) {
            quarantined = null; mustReopenInventory = false;
        }
        if (port != null) {
            var previous = JOB.state();
            JOB.tick(ticks, EmiAutocraftingConfig.TIMEOUT.get(), EmiAutocraftingConfig.PACE.get(), port);
            if (previous != JOB.state()) {
                String message = JOB.message();
                if (JOB.state() == JobController.State.COMPLETED && message.equals("Completed")) message = "Completed: " + port.total() + " " + port.targetLabel() + " (batch surplus retained)";
                feedback(message);
                if (JOB.state() == JobController.State.BLOCKED && previous == JobController.State.WAITING) quarantine(port.menu());
                if (EmiAutocraftingConfig.DIAGNOSTICS.get()) LOG.info("Autocrafting state={} target={} message={}", JOB.state(), port.targetLabel(), message);
            }
        }
    }
    public static void cancel() {
        if (JOB.state() == JobController.State.WAITING && port != null) quarantine(port.menu());
        JOB.cancel("Cancelled; completed items were retained"); feedback(JOB.message());
    }
    public static boolean start(boolean single) {
        if (JOB.active()) return false;
        var screen = EmiBridge.screen();
        if (screen == null || !EmiBridge.hasTree()) return false;
        if (mustReopenInventory && screen.getMenu() == quarantined) {
            feedback("Reopen the crafting interface to check inventory before retrying"); return false;
        }
        try {
            port = new MenuPort(screen, EmiBridge.freeze());
            Minecraft.getInstance().setScreen(screen);
            JOB.start(single, ticks); feedback("Planning"); return true;
        } catch (IllegalArgumentException | ArithmeticException error) { feedback(error.getMessage()); return false; }
    }
    private static void keyPressed(ScreenEvent.KeyPressed.Pre event) {
        Screen screen = event.getScreen();
        if (screen instanceof QuantityScreen || EmiBridge.searchFocused() || textFocused(screen)) return;
        if (screen instanceof RecipeUpdateListener recipeScreen) {
            EditBox search = ((RecipeBookComponentAccessor) recipeScreen.getRecipeBookComponent()).autocrafting$searchBox();
            if (search != null && search.isFocused() && recipeScreen.getRecipeBookComponent().isVisible()) return;
        }
        if (EmiBridge.screen() == null) return;
        int key = event.getKeyCode(), mods = event.getModifiers();
        boolean prepare = matches(EmiAutocraftingConfig.PREPARE.get(), key, mods);
        boolean toggle = matches(EmiAutocraftingConfig.TOGGLE.get(), key, mods);
        boolean step = matches(EmiAutocraftingConfig.STEP.get(), key, mods);
        boolean cancel = matches(EmiAutocraftingConfig.CANCEL.get(), key, mods);
        if (!prepare && !toggle && !step && !cancel) return;
        if (!PRESSED.add(key)) { if (JOB.active()) event.setCanceled(true); return; }
        boolean consumed = false;
        if (cancel && JOB.active()) { cancel(); consumed = true; }
        else if (toggle && JOB.active()) { cancel(); consumed = true; }
        else if (prepare && !JOB.active()) {
            try { Minecraft.getInstance().setScreen(new QuantityScreen(screen, EmiBridge.hovered())); consumed = true; }
            catch (IllegalArgumentException e) { feedback(e.getMessage()); }
        } else if (toggle || step) consumed = start(step);
        if (consumed) event.setCanceled(true);
    }
    private static void keyReleased(ScreenEvent.KeyReleased.Pre event) { PRESSED.remove(event.getKeyCode()); }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        cancel(); PRESSED.clear();
    }
    private static void mouse(ScreenEvent.MouseButtonPressed.Pre event) { if (JOB.active()) cancel(); }
    private static boolean textFocused(GuiEventListener listener) {
        if ((listener instanceof EditBox || listener instanceof MultiLineEditBox) && listener.isFocused()) return true;
        if (listener instanceof ContainerEventHandler container && container.getFocused() != null) return textFocused(container.getFocused());
        return false;
    }
    private static void render(ScreenEvent.Render.Post event) {
        if (EmiBridge.screen() == null || (!JOB.active() && ticks > feedbackUntil)) return;
        Minecraft mc = Minecraft.getInstance();
        String text = JOB.active() ? JOB.message() : feedback;
        text = mc.font.plainSubstrByWidth(text, Math.max(100, event.getScreen().width - 24));
        int x = (event.getScreen().width - mc.font.width(text)) / 2;
        event.getGuiGraphics().fill(x - 5, 3, x + mc.font.width(text) + 5, 17, 0xD0101820);
        event.getGuiGraphics().drawString(mc.font, text, x, 6, 0xFFFFFF);
    }
}
