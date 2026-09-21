// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.client;

import com.example.emiautocrafting.emi.StackKey;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Optional, explicitly identified menu protocols. No server/world access or storage mutation here. */
final class StorageCrafting {
    enum Kind { STATION, LECTERN, AE2 }
    private final Kind kind;
    private final AbstractContainerMenu menu;
    private final Map<Integer, List<Integer>> stationTransfers = new LinkedHashMap<>();
    final List<Integer> grid = new ArrayList<>(), inventory = new ArrayList<>(), sources = new ArrayList<>();
    final int output, slotCount;

    static StorageCrafting find(AbstractContainerMenu menu) {
        Kind kind = switch (menu.getClass().getName()) {
            case "com.leclowndu93150.craftingstationjei.menu.CraftingStationMenu" -> Kind.STATION;
            case "com.hollingsworth.arsnouveau.client.container.CraftingTerminalMenu" -> Kind.LECTERN;
            case "appeng.menu.me.items.CraftingTermMenu", "appeng.menu.me.items.WirelessCraftingTermMenu" -> Kind.AE2;
            default -> null;
        };
        return kind == null ? null : new StorageCrafting(menu, kind);
    }

    private StorageCrafting(AbstractContainerMenu menu, Kind kind) {
        this.menu = menu; this.kind = kind;
        var player = Minecraft.getInstance().player;
        int result = -1, count = menu.slots.size();
        for (int position = 0; position < menu.slots.size(); position++) {
            Slot slot = menu.slots.get(position);
            String name = slot.getClass().getName();
            // MEStorageScreen appends RepoSlots directly, outside AEBaseMenu's client-slot registry.
            if (kind == Kind.AE2 && (name.equals("appeng.client.gui.me.common.RepoSlot")
                    || (boolean) call(menu, "isClientSideSlot", new Class<?>[]{Slot.class}, slot))) {
                count = Math.min(count, position);
                continue;
            }
            if (slot.container == player.getInventory() && slot.getContainerSlot() < 36 && slot.mayPickup(player))
                inventory.add(slot.index);
            if (kind != Kind.AE2 && slot.index >= 1 && slot.index <= 9
                    || kind == Kind.AE2 && name.equals("appeng.menu.slot.CraftingMatrixSlot")) grid.add(slot.index);
            if (kind != Kind.AE2 && slot.index == 0
                    || kind == Kind.AE2 && name.equals("appeng.menu.slot.CraftingTermSlot")) result = slot.index;
            if (kind == Kind.STATION && name.equals("com.leclowndu93150.craftingstationjei.menu.CraftingStationMenu$SideContainerSlot"))
                sources.add(slot.index);
        }
        if (grid.size() != 9 || inventory.isEmpty() || result < 0)
            throw new IllegalArgumentException("This storage menu layout cannot be verified");
        output = result; slotCount = count;
        sources.addAll(grid); sources.addAll(inventory);
    }

    boolean valid() {
        // Ars's client menu has no server tile and its stillValid always returns false.
        // Server packets still perform the native distance/access checks.
        return kind == Kind.LECTERN || menu.stillValid(Minecraft.getInstance().player);
    }

    String label() {
        return switch (kind) { case STATION -> "Crafting Station inventories"; case LECTERN -> "Bookwyrm lectern storage"; case AE2 -> "AE2 stored items"; };
    }

    Map<StackKey, Long> remoteStock() {
        Map<StackKey, Long> stock = new LinkedHashMap<>();
        if (kind == Kind.LECTERN) {
            for (Object entry : (List<?>) call(menu, "getStoredItems"))
                add(stock, (ItemStack) call(entry, "getStack"), (long) call(entry, "getQuantity"));
        } else if (kind == Kind.AE2) {
            Object status = call(menu, "getLinkStatus");
            if (!(boolean) call(status, "connected")) return stock;
            Object repo = call(menu, "getClientRepo");
            if (repo != null) for (Object entry : (Collection<?>) call(repo, "getAllEntries")) {
                Object key = call(entry, "getWhat");
                if (key != null && key.getClass().getName().equals("appeng.api.stacks.AEItemKey"))
                    add(stock, (ItemStack) call(key, "toStack"), (long) call(entry, "getStoredAmount"));
            }
        }
        return stock;
    }

    private static void add(Map<StackKey, Long> stock, ItemStack stack, long count) {
        if (!stack.isEmpty() && count > 0) stock.merge(new StackKey(stack), count, Math::addExact);
    }

    StandardRecipeHandler<AbstractContainerMenu> handler() {
        return new StandardRecipeHandler<>() {
            public List<Slot> getInputSources(AbstractContainerMenu m) { return sources.stream().map(m::getSlot).toList(); }
            public List<Slot> getCraftingSlots(AbstractContainerMenu m) { return grid.stream().map(m::getSlot).toList(); }
            public Slot getOutputSlot(AbstractContainerMenu m) { return m.getSlot(output); }
            public boolean supportsRecipe(EmiRecipe recipe) { return recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING; }
        };
    }

    boolean fill(EmiRecipe recipe, List<ItemStack> items, AbstractContainerScreen<?> screen) {
        if (kind == Kind.STATION) return fillStation(items);
        if (kind == Kind.LECTERN) {
            var ingredients = items.stream().map(s -> s.isEmpty() ? List.<ItemStack>of() : List.of(s.copy())).toList();
            send(construct("com.hollingsworth.arsnouveau.common.network.ClientTransferHandlerPacket", new Class<?>[]{List.class}, ingredients));
        } else {
            NonNullList<ItemStack> templates = NonNullList.withSize(9, ItemStack.EMPTY);
            for (int i = 0; i < 9; i++) templates.set(i, items.get(i).copy());
            // Explicit templates and craftMissing=false: never submit a network autocrafting job.
            send(construct("appeng.core.network.serverbound.FillCraftingGridFromRecipePacket",
                    new Class<?>[]{ResourceLocation.class, NonNullList.class, boolean.class}, null, templates, false));
        }
        return true;
    }

    private boolean fillStation(List<ItemStack> items) {
        stationTransfers.clear();
        if (items.size() != grid.size() || !menu.getCarried().isEmpty()
                || grid.stream().anyMatch(index -> !menu.getSlot(index).getItem().isEmpty())) return false;
        // Plan every click before sending any. Return each cursor remainder to its original
        // source: EMI's generic fill can consolidate it into another upgraded stack, hiding
        // previously visible stock behind the station's normal-stack display cap.
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            if (item.isEmpty()) continue;
            if (item.getCount() != 1 || !menu.getSlot(grid.get(i)).mayPlace(item)) return false;
            int source = -1;
            for (int index : sources) {
                if (grid.contains(index)) continue;
                Slot slot = menu.getSlot(index);
                int assigned = stationTransfers.getOrDefault(index, List.of()).size();
                if (slot.mayPickup(Minecraft.getInstance().player) && slot.mayPlace(item)
                        && ItemStack.isSameItemSameComponents(slot.getItem(), item)
                        && slot.getItem().getCount() > assigned) { source = index; break; }
            }
            if (source < 0) return false;
            stationTransfers.computeIfAbsent(source, ignored -> new ArrayList<>()).add(grid.get(i));
        }
        for (var transfer : stationTransfers.entrySet()) {
            int source = transfer.getKey(), count = menu.getSlot(source).getItem().getCount();
            List<Integer> targets = transfer.getValue();
            int placed = 0;
            while (placed < targets.size()) {
                int remaining = targets.size() - placed;
                // Sophisticated Storage can redirect insertion into an empty slot to an
                // existing upgraded stack. Keep the source occupied whenever returning a
                // remainder; a right-click takes half of its displayed stack.
                boolean takeAll = count == remaining;
                int picked = takeAll ? count : (count + 1) / 2;
                int moved = Math.min(picked, remaining);
                click(source, takeAll ? 0 : 1);
                for (int i = 0; i < moved; i++) click(targets.get(placed++), 1);
                if (picked > moved) click(source);
                count -= moved;
            }
        }
        return true;
    }

    int outputDestination(ItemStack stack) {
        for (int index : inventory) {
            Slot slot = menu.getSlot(index);
            if (slot.getItem().isEmpty() && slot.mayPlace(stack) && slot.getMaxStackSize(stack) >= stack.getCount()) return index;
        }
        throw new IllegalArgumentException("Keep one empty inventory slot for verified storage crafting");
    }

    Map<StackKey, Long> revealLimits(List<ItemStack> before, List<ItemStack> after) {
        Map<StackKey, Long> limits = new LinkedHashMap<>();
        if (kind != Kind.STATION) return limits;
        for (var transfer : stationTransfers.entrySet()) {
            int index = transfer.getKey();
            if (inventory.contains(index)) continue;
            ItemStack old = before.get(index), now = after.get(index);
            // A capped source may stay saturated OR cross below the cap. Only the exact
            // withdrawn quantity that was not visibly subtracted can reveal hidden stock.
            if (!old.isEmpty() && old.getCount() == old.getMaxStackSize()
                    && (now.isEmpty() || ItemStack.isSameItemSameComponents(old, now))) {
                long visibleDecrease = old.getCount() - now.getCount();
                long withdrawn = transfer.getValue().size();
                if (visibleDecrease >= 0 && visibleDecrease <= withdrawn)
                    limits.merge(new StackKey(old), withdrawn - visibleDecrease, Math::addExact);
            }
        }
        return limits;
    }

    int batchLimit(Map<StackKey, Long> used, Map<StackKey, Long> available, List<ItemStack> recipeGrid, ItemStack output, long requested) {
        if (kind == Kind.STATION) return 1;
        // AE2 replenishes from the network/grid, not the player's loose ingredients.
        Map<StackKey, Long> pooled = remoteStock();
        if (kind == Kind.LECTERN)
            for (int index : inventory) add(pooled, menu.getSlot(index).getItem(), menu.getSlot(index).getItem().getCount());
        Map<StackKey, Integer> smallest = new LinkedHashMap<>();
        for (int i = 0; i < recipeGrid.size(); i++) {
            ItemStack wanted = recipeGrid.get(i), actual = menu.getSlot(grid.get(i)).getItem();
            if (!wanted.isEmpty()) smallest.merge(new StackKey(wanted),
                    ItemStack.isSameItemSameComponents(wanted, actual) ? actual.getCount() : 0, Math::min);
        }
        return com.example.emiautocrafting.core.CraftBatches.limit(requested, output.getCount(), output.getMaxStackSize(), used, available, pooled, smallest);
    }

    void craft(int destination, int batches) {
        if (batches < 1 || batches > 64) throw new IllegalArgumentException("Invalid crafting batch");
        for (int i = 0; i < batches; i++) craftToCursor();
        // One initially empty buffer holds the bounded result. A rejected craft cannot
        // pick up somebody else's stack or drop items; partial results remain verifiable.
        click(destination);
    }

    private void craftToCursor() {
        if (kind == Kind.AE2) {
            try {
                Class<?> type = Class.forName("appeng.helpers.InventoryAction");
                Object action = type.getField("CRAFT_ITEM").get(null);
                send(construct("appeng.core.network.serverbound.InventoryActionPacket",
                        new Class<?>[]{type, int.class, long.class}, action, output, 0L));
            } catch (ReflectiveOperationException error) { throw incompatible(error); }
        } else click(output);
    }

    record Move(int source, int destination) {}

    List<Move> planGridClear() {
        List<ItemStack> simulated = new ArrayList<>(menu.slots.stream().map(slot -> slot.getItem().copy()).toList());
        List<Move> moves = new ArrayList<>();
        for (int source : grid) {
            ItemStack stack = simulated.get(source);
            if (stack.isEmpty()) continue;
            int destination = -1;
            for (int index : inventory) {
                Slot slot = menu.getSlot(index);
                ItemStack existing = simulated.get(index);
                if (slot.mayPlace(stack) && (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, stack))
                        && existing.getCount() + stack.getCount() <= slot.getMaxStackSize(stack)) {
                    if (destination < 0 || !existing.isEmpty()) destination = index;
                    if (!existing.isEmpty()) break;
                }
            }
            if (destination < 0) throw new IllegalArgumentException("Not enough inventory space to retain the crafting grid");
            // Each entire source stack has a reserved destination; never use outside clicks.
            if (simulated.get(destination).isEmpty()) {
                simulated.set(destination, stack.copy());
            } else simulated.get(destination).grow(stack.getCount());
            moves.add(new Move(source, destination));
        }
        return List.copyOf(moves);
    }

    void clearGrid(List<Move> moves) {
        for (Move move : moves) { click(move.source()); click(move.destination()); }
    }

    int mergeDestination(int source) {
        ItemStack stack = menu.getSlot(source).getItem();
        if (stack.isEmpty()) return -1;
        for (int index : inventory) {
            Slot slot = menu.getSlot(index);
            if (index != source && ItemStack.isSameItemSameComponents(slot.getItem(), stack) && slot.mayPlace(stack)
                    && slot.getItem().getCount() + stack.getCount() <= slot.getMaxStackSize(stack)) return index;
        }
        return -1;
    }

    void clear(int index) {
        int destination = mergeDestination(index);
        if (destination < 0) destination = outputDestination(menu.getSlot(index).getItem());
        click(index); click(destination);
    }

    private void click(int index) { click(index, 0); }
    private void click(int index, int button) {
        Minecraft.getInstance().getConnection().send(new ServerboundContainerClickPacket(menu.containerId, -1,
                index, button, ClickType.PICKUP, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
    }

    private static void send(Object packet) { PacketDistributor.sendToServer((CustomPacketPayload) packet); }
    private static Object construct(String name, Class<?>[] types, Object... args) {
        try { return Class.forName(name).getConstructor(types).newInstance(args); }
        catch (ReflectiveOperationException | LinkageError error) { throw incompatible(error); }
    }
    private static Object call(Object target, String name) { return call(target, name, new Class<?>[0]); }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) {
        try { return target.getClass().getMethod(name, types).invoke(target, args); }
        catch (ReflectiveOperationException | LinkageError error) { throw incompatible(error); }
    }
    private static IllegalArgumentException incompatible(Throwable cause) {
        return new IllegalArgumentException("This storage mod version cannot be verified; reopen a supported crafting menu", cause);
    }
}
