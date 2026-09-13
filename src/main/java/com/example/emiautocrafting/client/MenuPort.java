// SPDX-License-Identifier: GPL-3.0-only
package com.example.emiautocrafting.client;

import com.example.emiautocrafting.EmiAutocrafting;
import com.example.emiautocrafting.core.*;
import com.example.emiautocrafting.emi.*;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.stack.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.*;

public final class MenuPort implements JobController.Port<MenuPort.Operation> {
    public enum Kind { SYNC, CLEAR, CRAFT }
    public record Operation(Kind kind, EmiRecipe recipe, int clearSlot, Map<StackKey, Long> expected,
            Map<StackKey, Long> before, List<ItemStack> beforeSlots, String label, long outstandingBatches) {}
    private final Minecraft mc = Minecraft.getInstance();
    private final AbstractContainerScreen<?> screen;
    private final AbstractContainerMenu menu;
    private final EmiBridge.Frozen tree;
    private final int gridSize, gridEnd;
    private final List<Integer> inventory = new ArrayList<>(), accessible = new ArrayList<>();
    private final long recipeEpoch;
    private long sequence;
    private boolean initialized, hasCrafted;
    private MenuSnapshot confirmed;
    private final Set<String> visited = new HashSet<>();
    public MenuPort(AbstractContainerScreen<?> screen, EmiBridge.Frozen tree) {
        this.screen = screen; this.menu = screen.getMenu(); this.tree = tree;
        if (menu.getClass() != CraftingMenu.class && menu.getClass() != InventoryMenu.class)
            throw new IllegalArgumentException("Open a vanilla crafting table or player inventory");
        gridSize = menu instanceof CraftingMenu ? 3 : 2; gridEnd = gridSize * gridSize;
        int start = gridSize == 3 ? 10 : 9;
        for (int i = start; i < start + 36; i++) inventory.add(i);
        for (int i = 1; i <= gridEnd; i++) accessible.add(i);
        accessible.addAll(inventory);
        recipeEpoch = EmiAutocrafting.recipeEpoch;
    }
    public AbstractContainerMenu menu() { return menu; }
    public long total() { return tree.total(); }
    public String targetLabel() { return tree.output().getItemStack().getHoverName().getString(); }
    public boolean valid() {
        return mc.player != null && mc.level != null && mc.getConnection() != null && mc.screen == screen
                && mc.player.containerMenu == menu && menu.stillValid(mc.player) && !mc.player.isSpectator()
                && recipeEpoch == EmiAutocrafting.recipeEpoch && EmiBridge.unchanged(tree);
    }
    private List<ItemStack> slots() { return menu.slots.stream().map(s -> s.getItem().copy()).toList(); }
    private Map<StackKey, Long> stock() { return new MenuSnapshot(0, menu.containerId, slots(), menu.getCarried()).stock(accessible); }
    public JobController.Decision<Operation> plan() {
        if (!menu.getCarried().isEmpty()) return JobController.Decision.blocked("Put the cursor stack away before crafting");
        if (!initialized) return JobController.Decision.ready(new Operation(Kind.SYNC, null, -1, null, stock(), slots(), "Checking inventory", 1));
        for (int i : accessible) if (!menu.getSlot(i).mayPickup(mc.player)) return JobController.Decision.blocked("An ingredient slot cannot be extracted");
        Map<StackKey, Long> stock = stock();
        long current = stock.getOrDefault(new StackKey(tree.output().getItemStack()), 0L);
        if (current >= tree.total()) return JobController.Decision.completedResult();
        for (int i = 1; i <= gridEnd; i++) {
            if (!menu.getSlot(i).getItem().isEmpty()) {
                if (!hasCrafted) return JobController.Decision.blocked("Clear the crafting grid before starting");
                List<ItemStack> sim = new ArrayList<>(inventory.stream().map(s -> menu.getSlot(s).getItem().copy()).toList());
                if (!insert(sim, menu.getSlot(i).getItem().copy())) return JobController.Decision.blocked("Not enough inventory space for returned items; clear the grid");
                return JobController.Decision.ready(new Operation(Kind.CLEAR, null, i, stock, stock, slots(), "Retaining returned items", 1));
            }
        }
        var root = EmiBridge.project(tree, stock.keySet());
        var planned = new TreePlanner<StackKey, EmiRecipe>().plan(root, tree.total(), stock);
        if (planned.obstacle() != null) return JobController.Decision.blocked(planned.obstacle());
        if (!planned.missing().isEmpty()) {
            var first = planned.missing().entrySet().iterator().next();
            return JobController.Decision.blocked("Missing: " + first.getValue() + " " + first.getKey());
        }
        if (planned.steps().isEmpty()) return JobController.Decision.blocked("No supported step can make progress");
        // TreeCost drives the existing sidebar; checked planning has already validated the quantities.
        EmiBridge.updateSidebar(stock);
        var next = planned.steps().getFirst();
        try { return JobController.Decision.ready(preflight(next, stock)); }
        catch (IllegalArgumentException error) { return JobController.Decision.blocked(error.getMessage()); }
    }
    private Operation preflight(TreePlanner.Step<StackKey, EmiRecipe> step, Map<StackKey, Long> before) {
        EmiRecipe recipe = step.node().recipe();
        if (!(recipe instanceof EmiCraftingRecipe crafting)) throw new IllegalArgumentException("Unsupported dynamic crafting recipe");
        RecipeHolder<?> holder = EmiBridge.rawRecipe(recipe);
        if (holder == null) throw new IllegalArgumentException("The selected recipe is unavailable; prepare the tree again");
        CraftingRecipe raw = CraftingCompatibility.verify(holder.value());
        if (!raw.canCraftInDimensions(gridSize, gridSize)) throw new IllegalArgumentException("Requires a 3×3 crafting table");
        var handler = EmiBridge.handler(recipe, screen);
        if (handler.getOutputSlot(menu) != menu.getSlot(0)) throw new IllegalArgumentException("This handler only transfers ingredients");
        List<Slot> targetSlots = handler.getCraftingSlots(recipe, menu);
        List<EmiIngredient> exact = new ArrayList<>();
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(gridEnd, ItemStack.EMPTY));
        Map<StackKey, Long> budget = new LinkedHashMap<>(step.available());
        Map<StackKey, Long> used = new LinkedHashMap<>();
        // Backtracking avoids greedy failure for overlapping alternatives (at most nine recipe slots).
        List<ItemStack> selected = new ArrayList<>(Collections.nCopies(recipe.getInputs().size(), ItemStack.EMPTY));
        if (!assign(recipe.getInputs(), 0, budget, selected, new int[]{0}))
            throw new IllegalArgumentException("Required recipe ingredients are not available without using reserved materials");
        for (int i = 0; i < selected.size(); i++) {
            ItemStack chosen = selected.get(i);
            exact.add(chosen.isEmpty() ? EmiStack.EMPTY : EmiStack.of(chosen).comparison(c -> Comparison.compareComponents()));
            if (chosen.isEmpty()) continue;
            if (i >= targetSlots.size() || targetSlots.get(i) == null) throw new IllegalArgumentException("Requires a 3×3 crafting table");
            Slot target = targetSlots.get(i);
            if (!target.mayPlace(chosen) || target.getMaxStackSize(chosen) < chosen.getCount()) throw new IllegalArgumentException("Crafting slot cannot accept this ingredient");
            grid.set(target.index - 1, chosen.copy());
            used.merge(new StackKey(chosen), (long) chosen.getCount(), Math::addExact);
        }
        CraftingInput input = CraftingInput.of(gridSize, gridSize, grid);
        if (!raw.matches(input, mc.level)) throw new IllegalArgumentException("The real recipe does not accept the selected ingredients");
        var actualMatch = mc.level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, mc.level);
        if (actualMatch.isEmpty() || !actualMatch.get().id().equals(holder.id()))
            throw new IllegalArgumentException("Another recipe matches this grid; resolve the recipe conflict first");
        ItemStack output = raw.assemble(input, mc.level.registryAccess());
        ItemStack advertised = recipe.getOutputs().getFirst().getItemStack();
        if (!ItemStack.matches(output, advertised) || output.isEmpty()) throw new IllegalArgumentException("Recipe output differs from EMI's deterministic output");
        NonNullList<ItemStack> remainders = raw.getRemainingItems(input);
        Map<StackKey, Long> expected = new LinkedHashMap<>(before);
        used.forEach((key, amount) -> subtract(expected, key, amount));
        add(expected, output);
        for (ItemStack remainder : remainders) add(expected, remainder);
        if (expected.equals(before)) throw new IllegalArgumentException("This recipe makes no measurable progress");
        // At least one consumed ingredient must disappear, preventing repeatable shift-click catalysts.
        boolean consumed = used.entrySet().stream().anyMatch(e -> remainders.stream()
                .filter(s -> !s.isEmpty() && new StackKey(s).equals(e.getKey())).mapToLong(ItemStack::getCount).sum() < e.getValue());
        if (!consumed) throw new IllegalArgumentException("Recipes that return every ingredient need a dedicated adapter");
        List<ItemStack> simulated = new ArrayList<>(inventory.stream().map(i -> menu.getSlot(i).getItem().copy()).toList());
        // Reserve output and remainder space even before consumption: conservative, never drops to make room.
        if (!insert(simulated, output.copy())) throw new IllegalArgumentException("Not enough space for the output");
        for (ItemStack remainder : remainders) if (!insert(simulated, remainder.copy())) throw new IllegalArgumentException("Not enough space for returned items");
        EmiRecipe exactRecipe = new EmiCraftingRecipe(exact, EmiStack.of(output), recipe.getId(), crafting.shapeless) {
            @Override public boolean canFit(int width, int height) { return raw.canCraftInDimensions(width, height); }
        };
        return new Operation(Kind.CRAFT, exactRecipe, -1, expected, before, slots(), output.getHoverName().getString(), step.batches());
    }
    private boolean assign(List<EmiIngredient> inputs, int index, Map<StackKey, Long> budget, List<ItemStack> result, int[] visits) {
        if (++visits[0] > 10000) return false;
        if (index == inputs.size()) return true;
        EmiIngredient input = inputs.get(index);
        if (input.isEmpty()) return assign(inputs, index + 1, budget, result, visits);
        // Vanilla crafting consumes one item per occupied slot; larger EMI quantities are unsupported.
        if (input.getAmount() != 1 || input.getChance() != 1) return false;
        for (StackKey key : new ArrayList<>(budget.keySet())) {
            if (budget.get(key) < 1 || input.getEmiStacks().stream().noneMatch(s -> s.isEqual(EmiStack.of(key.stack())))) continue;
            budget.put(key, budget.get(key) - 1); result.set(index, key.stack());
            if (assign(inputs, index + 1, budget, result, visits)) return true;
            budget.put(key, budget.get(key) + 1); result.set(index, ItemStack.EMPTY);
        }
        return false;
    }
    public void dispatch(Operation operation) {
        if (!valid() || (operation.kind() != Kind.SYNC && (!sameSlots(operation.beforeSlots(), slots()) || !menu.getCarried().isEmpty())))
            throw new IllegalArgumentException("Inventory changed before the craft; start again");
        EmiAutocrafting.diagnostic("Dispatch kind={} recipe={} handler={} menu={} before={} expected={}", operation.kind(),
                operation.recipe() == null ? null : operation.recipe().getId(),
                operation.recipe() == null ? null : EmiBridge.handler(operation.recipe(), screen).getClass().getName(),
                menu.containerId, operation.before(), operation.expected());
        sequence = EmiAutocrafting.syncSequence;
        confirmed = null;
        if (operation.kind() == Kind.CRAFT) {
            if (!EmiBridge.fill(operation.recipe(), screen, operation.outstandingBatches())) {
                requestSnapshot();
                throw new IllegalArgumentException("Recipe transfer was rejected; reopen the menu before retrying");
            }
        } else if (operation.kind() == Kind.CLEAR) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, operation.clearSlot(), 0, ClickType.QUICK_MOVE, mc.player);
        }
        requestSnapshot();
    }
    public void requestSnapshot() {
        if (mc.getConnection() == null || mc.player == null || mc.player.containerMenu != menu) return;
        // MC 1.21.1: QUICK_MOVE at -1 returns without touching stacks; stateId -1 forces broadcastFullState.
        // Unlike outside PICKUP (-999), this cannot drop a cursor item.
        mc.getConnection().send(new ServerboundContainerClickPacket(menu.containerId, -1, -1, 0,
                ClickType.QUICK_MOVE, menu.getCarried().copy(), new Int2ObjectOpenHashMap<>()));
    }
    public void onSnapshot(MenuSnapshot snapshot) {
        if (snapshot.menuId() == menu.containerId && snapshot.sequence() > sequence && snapshot.slots().size() == menu.slots.size()) confirmed = snapshot;
    }
    public JobController.Confirmation confirm(Operation operation) {
        if (confirmed == null || !confirmed.cursor().isEmpty()) return JobController.Confirmation.PENDING;
        if (!sameSlots(confirmed.slots(), slots()) || !menu.getCarried().isEmpty()) return JobController.Confirmation.PENDING;
        if (operation.kind() == Kind.SYNC) { initialized = true; return JobController.Confirmation.CONFIRMED; }
        if (!confirmed.stock(accessible).equals(operation.expected())) return JobController.Confirmation.PENDING;
        if (operation.kind() == Kind.CLEAR && !confirmed.slots().get(operation.clearSlot()).isEmpty()) return JobController.Confirmation.PENDING;
        if (operation.kind() == Kind.CRAFT) {
            String fingerprint = operation.recipe().getId() + ":" + new TreeMap<>(stringStock(operation.expected()));
            if (!visited.add(fingerprint)) return JobController.Confirmation.REJECTED;
            hasCrafted = true;
            EmiAutocrafting.diagnostic("Confirmed recipe={} menu={} snapshot={} inventory={}",
                    operation.recipe().getId(), menu.containerId, confirmed.sequence(), confirmed.stock(accessible));
        }
        return JobController.Confirmation.CONFIRMED;
    }
    public boolean countsAsCraft(Operation operation) { return operation.kind() == Kind.CRAFT; }
    public void timeout() { requestSnapshot(); EmiAutocrafting.quarantine(menu); }
    public String describe(Operation operation) { return operation.label(); }
    public void error(Exception error) { EmiAutocrafting.quarantine(menu); EmiAutocrafting.logError(error); }
    private static Map<String, Long> stringStock(Map<StackKey, Long> stock) {
        Map<String, Long> result = new HashMap<>(); stock.forEach((k,v) -> result.put(k.toString(), v)); return result;
    }
    private static boolean sameSlots(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }
    private static void add(Map<StackKey, Long> stock, ItemStack stack) {
        if (!stack.isEmpty()) stock.merge(new StackKey(stack), (long) stack.getCount(), Math::addExact);
    }
    private static void subtract(Map<StackKey, Long> stock, StackKey key, long amount) {
        long left = stock.getOrDefault(key, 0L) - amount;
        if (left < 0) throw new IllegalArgumentException("An ingredient was reserved twice");
        if (left == 0) stock.remove(key); else stock.put(key, left);
    }
    private static boolean insert(List<ItemStack> inventory, ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        for (ItemStack existing : inventory) {
            if (ItemStack.isSameItemSameComponents(existing, incoming)) {
                int count = Math.min(incoming.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(count); incoming.shrink(count); if (incoming.isEmpty()) return true;
            }
        }
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i).isEmpty()) {
                int count = Math.min(incoming.getCount(), incoming.getMaxStackSize());
                inventory.set(i, incoming.copyWithCount(count));
                incoming.shrink(count); if (incoming.isEmpty()) return true;
            }
        }
        return false;
    }
}
