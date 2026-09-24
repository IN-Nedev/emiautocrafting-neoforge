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
    public enum Kind { SYNC, CLEAR, CLEAR_GRID, FILL, CRAFT }
    public record Operation(Kind kind, EmiRecipe recipe, int clearSlot, Map<StackKey, Long> expected,
            Map<StackKey, Long> before, List<ItemStack> beforeSlots, String label, long outstandingBatches, StockScope<StackKey> scope) {}
    private final Minecraft mc = Minecraft.getInstance();
    private final AbstractContainerScreen<?> screen;
    private final AbstractContainerMenu menu;
    private final EmiBridge.Frozen tree;
    private final int gridSize, gridEnd;
    private final List<Integer> grid = new ArrayList<>();
    private final StorageCrafting storage;
    private final int outputSlot, slotCount;
    private Operation prepared;
    private List<ItemStack> preparedGrid;
    private List<StorageCrafting.Move> clearMoves = List.of();
    private final boolean single;
    private Map<String, Long> missing = Map.of();
    private CraftingProblem problem;
    private final List<Integer> inventory = new ArrayList<>(), accessible = new ArrayList<>();
    private final long recipeEpoch;
    private long sequence;
    private boolean initialized, hasCrafted;
    private MenuSnapshot confirmed;
    private String waitReason = "";
    private final Set<String> visited = new HashSet<>();
    public MenuPort(AbstractContainerScreen<?> screen, EmiBridge.Frozen tree) {
        this(screen, tree, false);
    }
    public MenuPort(AbstractContainerScreen<?> screen, EmiBridge.Frozen tree, boolean single) {
        this.screen = screen; this.menu = screen.getMenu(); this.tree = tree;
        this.single = single;
        storage = StorageCrafting.find(menu);
        if (storage == null && menu.getClass() != CraftingMenu.class && menu.getClass() != InventoryMenu.class)
            throw new IllegalArgumentException("Open a crafting table, Crafting Station, Bookwyrm lectern or AE2 crafting terminal (including wireless)");
        gridSize = storage != null || menu instanceof CraftingMenu ? 3 : 2; gridEnd = gridSize * gridSize;
        if (storage == null) {
            int start = gridSize == 3 ? 10 : 9;
            for (int i = start; i < start + 36; i++) inventory.add(i);
            for (int i = 1; i <= gridEnd; i++) grid.add(i);
            accessible.addAll(grid); accessible.addAll(inventory);
            outputSlot = 0; slotCount = menu.slots.size();
        } else {
            inventory.addAll(storage.inventory); grid.addAll(storage.grid); accessible.addAll(storage.sources);
            outputSlot = storage.output; slotCount = storage.slotCount;
        }
        recipeEpoch = EmiAutocrafting.recipeEpoch;
    }
    public AbstractContainerMenu menu() { return menu; }
    public long total() { return tree.total(); }
    public String targetLabel() { return tree.output().getItemStack().getHoverName().getString(); }
    public Map<String, Long> missing() { return missing; }
    public CraftingProblem problem() { return problem; }
    public String inventoryLabel() { return storage == null ? "Player inventory" : "Player inventory + " + storage.label(); }
    public boolean valid() {
        return mc.player != null && mc.level != null && mc.getConnection() != null && mc.screen == screen
                && mc.player.containerMenu == menu && (storage == null ? menu.stillValid(mc.player) : storage.valid()) && !mc.player.isSpectator()
                && recipeEpoch == EmiAutocrafting.recipeEpoch && EmiBridge.unchanged(tree);
    }
    private List<ItemStack> slots() { return menu.slots.stream().limit(slotCount).map(s -> s.getItem().copy()).toList(); }
    private Map<StackKey, Long> stock(MenuSnapshot snapshot) {
        var result = snapshot.stock(accessible);
        if (storage != null) storage.remoteStock().forEach((key, count) -> result.merge(key, count, Math::addExact));
        return result;
    }
    private Map<StackKey, Long> stock() { return stock(new MenuSnapshot(0, menu.containerId, slots(), menu.getCarried())); }
    public Map<StackKey, Long> previewStock() { return stock(); }
    public long targetCount(Map<StackKey, Long> stock) { return EmiBridge.targetCount(tree, stock); }
    public JobController.Decision<Operation> plan() {
        if (!menu.getCarried().isEmpty()) return JobController.Decision.blocked("Put the cursor stack away before crafting");
        if (!initialized) return JobController.Decision.ready(new Operation(Kind.SYNC, null, -1, null, stock(), slots(), "Checking inventory", 1, new StockScope<>(Set.of())));
        for (int i : accessible) if (!menu.getSlot(i).getItem().isEmpty() && !menu.getSlot(i).mayPickup(mc.player))
            return JobController.Decision.blocked("An ingredient slot cannot be extracted");
        Map<StackKey, Long> stock = stock();
        if (prepared != null) {
            if (!(storage != null && storage.isAe2()) && !prepared.scope().matches(prepared.before(), stock)
                    || !matchesPreparedGrid() || !ItemStack.matches(menu.getSlot(outputSlot).getItem(), prepared.recipe().getOutputs().getFirst().getItemStack()))
                return JobController.Decision.blocked("Storage or transferred ingredients changed; reopen the menu before retrying");
            if (!storage.readyForBatch(preparedGrid, Math.toIntExact(prepared.outstandingBatches())))
                return JobController.Decision.blocked("Transferred ingredients no longer cover the batch; reopen the menu before retrying");
            ItemStack result = prepared.recipe().getOutputs().getFirst().getItemStack();
            int destination = storage.outputDestination(result.copyWithCount(Math.toIntExact(result.getCount() * prepared.outstandingBatches())));
            return JobController.Decision.ready(new Operation(Kind.CRAFT, prepared.recipe(), destination, prepared.scope().rebase(prepared.expected(), stock), stock, slots(), prepared.label(), prepared.outstandingBatches(), prepared.scope()));
        }
        // Native result pickup uses an empty buffer. Merge confirmed output stacks so large jobs
        // keep that buffer available; this operation changes no item quantities.
        if (storage != null && hasCrafted) for (int index : inventory) {
            if (storage.mergeDestination(index) >= 0)
                return JobController.Decision.ready(new Operation(Kind.CLEAR, null, index, stock, stock, slots(), "Stacking crafted items", 1, scopeFor(menu.getSlot(index).getItem())));
        }
        long current = targetCount(stock);
        JobSidebar.progress(tree.tree(), current);
        if (current >= tree.total()) return JobController.Decision.completedResult();
        for (int i : grid) {
            if (!menu.getSlot(i).getItem().isEmpty()) {
                if (!hasCrafted && storage == null) return JobController.Decision.blocked("Clear the crafting grid before starting");
                // Native storage menus may refill the exact next recipe automatically.
                // Preflight below can reuse that verified grid, or clear it when the recipe changes.
                if (storage != null) continue;
                List<ItemStack> sim = new ArrayList<>(inventory.stream().map(s -> menu.getSlot(s).getItem().copy()).toList());
                if (!insert(sim, menu.getSlot(i).getItem().copy())) return JobController.Decision.blocked("Not enough inventory space for returned items; clear the grid");
                return JobController.Decision.ready(new Operation(Kind.CLEAR, null, i, stock, stock, slots(), "Retaining returned items", 1, scopeFor(menu.getSlot(i).getItem())));
            }
        }
        var root = EmiBridge.project(tree, stock.keySet());
        var planned = new TreePlanner<StackKey, EmiRecipe>().plan(root, tree.total(), stock);
        missing = Collections.unmodifiableMap(new LinkedHashMap<>(planned.missing()));
        EmiBridge.updateSidebar(stock);
        if (planned.obstacle() != null) {
            var blocked = planned.blockedNode();
            return blocked(blocked, planned.blockedPath(), planned.obstacle());
        }
        if (!planned.missing().isEmpty()) {
            var first = planned.missing().entrySet().iterator().next();
            return JobController.Decision.blocked("Missing: " + first.getValue() + " " + first.getKey()
                    + (missing.size() > 1 ? " (and " + (missing.size() - 1) + " more item types)" : ""));
        }
        if (planned.steps().isEmpty()) return JobController.Decision.blocked("No supported step can make progress");
        var next = planned.steps().getFirst();
        try { return JobController.Decision.ready(preflight(next, stock)); }
        catch (IllegalArgumentException error) {
            List<String> path = new ArrayList<>(); findPath(root, next.node(), path);
            return blocked(next.node(), path, error.getMessage());
        }
    }
    private JobController.Decision<Operation> blocked(TreePlanner.Node<StackKey, EmiRecipe> node, List<String> path, String reason) {
        problem = CraftingProblem.of(tree, node.label(), node.recipe(), path, reason);
        return JobController.Decision.blocked(problem.summary());
    }
    private static boolean findPath(TreePlanner.Node<StackKey, EmiRecipe> root, TreePlanner.Node<StackKey, EmiRecipe> target, List<String> path) {
        path.add(root.label());
        if (root == target) return true;
        for (var child : root.inputs()) if (findPath(child, target, path)) return true;
        path.removeLast(); return false;
    }
    private Operation preflight(TreePlanner.Step<StackKey, EmiRecipe> step, Map<StackKey, Long> before) {
        EmiRecipe recipe = step.node().recipe();
        if (!(recipe instanceof EmiCraftingRecipe crafting)) throw new IllegalArgumentException("Unsupported dynamic crafting recipe");
        RecipeHolder<?> holder = EmiBridge.rawRecipe(recipe);
        if (holder == null) throw new IllegalArgumentException("The selected recipe is unavailable; prepare the tree again");
        CraftingRecipe raw = CraftingCompatibility.verify(holder.value());
        if (!raw.canCraftInDimensions(gridSize, gridSize)) throw new IllegalArgumentException("Requires a 3×3 crafting table");
        var handler = storage == null ? EmiBridge.handler(recipe, screen) : storage.handler();
        if (handler.getOutputSlot(menu) != menu.getSlot(outputSlot)) throw new IllegalArgumentException("This handler only transfers ingredients");
        List<Slot> targetSlots = handler.getCraftingSlots(recipe, menu);
        List<EmiIngredient> exact = new ArrayList<>();
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(gridEnd, ItemStack.EMPTY));
        Map<StackKey, Long> budget = new LinkedHashMap<>(step.available());
        Map<StackKey, Long> used = new LinkedHashMap<>();
        // Backtracking avoids greedy failure for overlapping alternatives (at most nine recipe slots).
        List<ItemStack> selected = new ArrayList<>(Collections.nCopies(recipe.getInputs().size(), ItemStack.EMPTY));
        java.util.function.Predicate<List<ItemStack>> accepts = choices -> {
            if (!CraftingCompatibility.constrainedMaterials(raw)) return true;
            List<ItemStack> candidate = new ArrayList<>(Collections.nCopies(gridEnd, ItemStack.EMPTY));
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).isEmpty()) continue;
                if (i >= targetSlots.size() || targetSlots.get(i) == null) return false;
                int index = this.grid.indexOf(targetSlots.get(i).index);
                if (index < 0) return false;
                candidate.set(index, choices.get(i));
            }
            return raw.matches(CraftingInput.of(gridSize, gridSize, candidate), mc.level);
        };
        if (!assign(recipe.getInputs(), 0, budget, selected, new int[]{0}, accepts))
            throw new IllegalArgumentException(CraftingCompatibility.mixedMaterials(raw)
                    ? "Quark's mixed-material recipe rejects the available combination; supply different wood types or select another chest recipe"
                    : CraftingCompatibility.constrainedMaterials(raw)
                    ? "Quark rejects the available material combination; mix material types or select another recipe"
                    : "Required recipe ingredients are not available without using reserved materials");
        for (int i = 0; i < selected.size(); i++) {
            ItemStack chosen = selected.get(i);
            exact.add(chosen.isEmpty() ? EmiStack.EMPTY : EmiStack.of(chosen).comparison(c -> Comparison.compareComponents()));
            if (chosen.isEmpty()) continue;
            if (i >= targetSlots.size() || targetSlots.get(i) == null) throw new IllegalArgumentException("Requires a 3×3 crafting table");
            Slot target = targetSlots.get(i);
            if (!target.mayPlace(chosen) || target.getMaxStackSize(chosen) < chosen.getCount()) throw new IllegalArgumentException("Crafting slot cannot accept this ingredient");
            grid.set(this.grid.indexOf(target.index), chosen.copy());
            used.merge(new StackKey(chosen), (long) chosen.getCount(), Math::addExact);
        }
        CraftingInput input = CraftingInput.of(gridSize, gridSize, grid);
        if (!raw.matches(input, mc.level)) throw new IllegalArgumentException("The real recipe does not accept the selected ingredients");
        var actualMatch = mc.level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, mc.level);
        if (actualMatch.isEmpty() || !actualMatch.get().id().equals(holder.id()))
            throw new IllegalArgumentException("Another recipe matches this grid; resolve the recipe conflict first");
        ItemStack output = raw.assemble(input, mc.level.registryAccess());
        ItemStack advertised = recipe.getOutputs().getFirst().getItemStack();
        if (!CraftingCompatibility.matchesPreview(raw, output, advertised) || output.isEmpty()) throw new IllegalArgumentException("Recipe output differs from EMI's deterministic output");
        NonNullList<ItemStack> remainders = raw.getRemainingItems(input);
        int batches = storage != null && !single && !CraftingCompatibility.copiesUpgradeSettings(raw) && remainders.stream().allMatch(ItemStack::isEmpty)
                ? storage.batchLimit(used, step.available(), grid, output, step.batches()) : 1;
        ItemStack collected = output.copyWithCount(output.getCount() * batches);
        Map<StackKey, Long> expected = new LinkedHashMap<>(before);
        used.forEach((key, amount) -> subtract(expected, key, Math.multiplyExact(amount, batches)));
        add(expected, collected);
        for (ItemStack remainder : remainders) add(expected, remainder);
        Set<StackKey> tracked = new LinkedHashSet<>(used.keySet());
        tracked.add(new StackKey(output));
        for (ItemStack remainder : remainders) if (!remainder.isEmpty()) tracked.add(new StackKey(remainder));
        var scope = new StockScope<>(tracked);
        if (expected.equals(before)) throw new IllegalArgumentException("This recipe makes no measurable progress");
        // At least one consumed ingredient must disappear, preventing repeatable shift-click catalysts.
        boolean consumed = used.entrySet().stream().anyMatch(e -> remainders.stream()
                .filter(s -> !s.isEmpty() && new StackKey(s).equals(e.getKey())).mapToLong(ItemStack::getCount).sum() < e.getValue());
        if (!consumed) throw new IllegalArgumentException("Recipes that return every ingredient need a dedicated adapter");
        List<ItemStack> simulated = new ArrayList<>(inventory.stream().map(i -> menu.getSlot(i).getItem().copy()).toList());
        // Reserve output and remainder space even before consumption: conservative, never drops to make room.
        if (!insert(simulated, collected.copy())) throw new IllegalArgumentException("Not enough space for the output");
        for (ItemStack remainder : remainders) if (!insert(simulated, remainder.copy())) throw new IllegalArgumentException("Not enough space for returned items");
        EmiRecipe exactRecipe = new EmiCraftingRecipe(exact, EmiStack.of(output), recipe.getId(), crafting.shapeless) {
            @Override public boolean canFit(int width, int height) { return raw.canCraftInDimensions(width, height); }
        };
        if (storage != null) {
            preparedGrid = grid.stream().map(ItemStack::copy).toList();
            if (!matchesPreparedGrid() && this.grid.stream().anyMatch(index -> !menu.getSlot(index).getItem().isEmpty())
                    && !(matchesPreparedGrid(true) && storage.canFillWithoutClearing(preparedGrid))) {
                clearMoves = storage.planGridClear();
                Set<StackKey> returned = new LinkedHashSet<>();
                for (var move : clearMoves) returned.add(new StackKey(menu.getSlot(move.source()).getItem()));
                return new Operation(Kind.CLEAR_GRID, null, -1, before, before, slots(), "Retaining crafting grid", 1, new StockScope<>(returned));
            }
            int destination = storage.outputDestination(collected);
            String label = collected.getCount() + " " + output.getHoverName().getString();
            prepared = new Operation(Kind.CRAFT, exactRecipe, -1, expected, before, slots(), label, batches, scope);
            if (matchesPreparedGrid() && storage.readyForBatch(preparedGrid, batches) && ItemStack.matches(menu.getSlot(outputSlot).getItem(), output))
                return new Operation(Kind.CRAFT, exactRecipe, destination, expected, before, slots(), label, batches, scope);
            return new Operation(Kind.FILL, exactRecipe, -1, before, before, slots(), "Checking transferred ingredients", batches, scope);
        }
        return new Operation(Kind.CRAFT, exactRecipe, -1, expected, before, slots(), output.getHoverName().getString(), step.batches(), scope);
    }
    private boolean assign(List<EmiIngredient> inputs, int index, Map<StackKey, Long> budget, List<ItemStack> result, int[] visits,
            java.util.function.Predicate<List<ItemStack>> accepts) {
        if (++visits[0] > 10000) return false;
        if (index == inputs.size()) return accepts.test(result);
        EmiIngredient input = inputs.get(index);
        if (input.isEmpty()) return assign(inputs, index + 1, budget, result, visits, accepts);
        // Vanilla crafting consumes one item per occupied slot; larger EMI quantities are unsupported.
        if (input.getAmount() != 1 || input.getChance() != 1) return false;
        for (StackKey key : new ArrayList<>(budget.keySet())) {
            if (budget.get(key) < 1 || input.getEmiStacks().stream().noneMatch(s -> s.isEqual(EmiStack.of(key.stack())))) continue;
            budget.put(key, budget.get(key) - 1); result.set(index, key.stack());
            if (assign(inputs, index + 1, budget, result, visits, accepts)) return true;
            budget.put(key, budget.get(key) + 1); result.set(index, ItemStack.EMPTY);
        }
        return false;
    }
    public void dispatch(Operation operation) {
        if (!valid() || (operation.kind() != Kind.SYNC && (!sameRelevantSlots(operation.beforeSlots(), slots(), operation.scope()) || !menu.getCarried().isEmpty())))
            throw new IllegalArgumentException("Inventory changed before the craft; start again");
        EmiAutocrafting.diagnostic("Dispatch kind={} recipe={} handler={} menu={} itemTypes={} expectedTypes={} emiServer={} batches={}", operation.kind(),
                operation.recipe() == null ? null : operation.recipe().getId(),
                operation.recipe() == null ? null : storage == null ? EmiBridge.handler(operation.recipe(), screen).getClass().getName() : storage.label(),
                menu.containerId, operation.before().size(), operation.expected() == null ? 0 : operation.expected().size(), dev.emi.emi.platform.EmiClient.onServer, operation.outstandingBatches());
        sequence = EmiAutocrafting.syncSequence;
        confirmed = null;
        waitReason = "";
        if (storage != null && !storage.isAe2() && operation.kind() != Kind.SYNC && !operation.scope().matches(operation.before(), stock()))
            throw new IllegalArgumentException("Connected inventory changed before the transfer; start again");
        if (operation.kind() == Kind.FILL) {
            if (!storage.fill(operation.recipe(), preparedGrid, screen, Math.toIntExact(operation.outstandingBatches()))) throw new IllegalArgumentException("Storage recipe transfer was rejected");
        } else if (operation.kind() == Kind.CRAFT && storage != null) {
            if (!matchesPreparedGrid() || !ItemStack.matches(menu.getSlot(outputSlot).getItem(), operation.recipe().getOutputs().getFirst().getItemStack())
                    || !menu.getSlot(operation.clearSlot()).getItem().isEmpty()) throw new IllegalArgumentException("The crafting grid or output destination changed");
            storage.craft(operation.clearSlot(), Math.toIntExact(operation.outstandingBatches()));
        } else if (operation.kind() == Kind.CRAFT) {
            if (!EmiBridge.fill(operation.recipe(), screen, operation.outstandingBatches())) {
                requestSnapshot();
                throw new IllegalArgumentException("Recipe transfer was rejected; reopen the menu before retrying");
            }
        } else if (operation.kind() == Kind.CLEAR_GRID) {
            for (var move : clearMoves) if (!ItemStack.matches(operation.beforeSlots().get(move.destination()), menu.getSlot(move.destination()).getItem()))
                throw new IllegalArgumentException("Inventory changed before clearing the grid");
            storage.clearGrid(clearMoves);
        } else if (operation.kind() == Kind.CLEAR) {
            if (storage == null) mc.gameMode.handleInventoryMouseClick(menu.containerId, operation.clearSlot(), 0, ClickType.QUICK_MOVE, mc.player);
            else storage.clear(operation.clearSlot());
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
        if (snapshot.menuId() == menu.containerId && snapshot.slots().size() != slotCount)
            pending("Server menu has " + snapshot.slots().size() + " slots; expected " + slotCount);
        if (snapshot.menuId() == menu.containerId && snapshot.sequence() > sequence && snapshot.slots().size() == slotCount) confirmed = snapshot;
    }
    public JobController.Confirmation confirm(Operation operation) {
        if (confirmed == null) return pending("Waiting for a full server menu snapshot");
        if (!confirmed.cursor().isEmpty() || !menu.getCarried().isEmpty()) return pending("Waiting for the cursor stack to be deposited");
        for (int i = 0; i < slotCount; i++) if (relevantSlot(i, confirmed.slots().get(i), menu.getSlot(i).getItem(), operation.scope())
                && !ItemStack.matches(confirmed.slots().get(i), menu.getSlot(i).getItem()))
            return pending("Server snapshot differs from displayed slot " + i + " (" + menu.getSlot(i).getClass().getSimpleName()
                    + "): server " + stackSummary(confirmed.slots().get(i)) + ", displayed " + stackSummary(menu.getSlot(i).getItem()));
        if (operation.kind() == Kind.SYNC) { initialized = true; return JobController.Confirmation.CONFIRMED; }
        var actual = stock(confirmed);
        if (operation.kind() == Kind.FILL && !storage.readyForBatch(preparedGrid, Math.toIntExact(operation.outstandingBatches())))
            return pending("Waiting for enough transferred ingredients for the batch");
        if (operation.kind() == Kind.FILL && matchesPreparedGrid()
                && ItemStack.matches(menu.getSlot(outputSlot).getItem(), operation.recipe().getOutputs().getFirst().getItemStack())) {
            var scope = operation.scope();
            // AE2 synchronizes terminal slots and its stored-item repository separately.
            // The server-confirmed grid and result prove that native filling completed.
            if (storage.isAe2()) {
                prepared = new Operation(Kind.CRAFT, prepared.recipe(), -1, prepared.expected(), actual,
                        confirmed.slots(), prepared.label(), prepared.outstandingBatches(), scope);
                return JobController.Confirmation.CONFIRMED;
            }
            var rebased = CappedStock.afterTransfer(scope.project(operation.before()), scope.project(actual), scope.project(prepared.expected()),
                    storage.revealLimits(operation.beforeSlots(), confirmed.slots()));
            if (rebased.isPresent()) {
                if (!scope.matches(actual, operation.before())) EmiAutocrafting.diagnostic("Verified transfer revealed previously capped storage material; rebasing the next craft");
                prepared = new Operation(Kind.CRAFT, prepared.recipe(), -1, scope.rebase(rebased.get(), actual), actual, confirmed.slots(), prepared.label(), prepared.outstandingBatches(), scope);
                return JobController.Confirmation.CONFIRMED;
            }
        }
        if (storage != null && storage.isAe2()) {
            if (operation.kind() == Kind.CLEAR_GRID) {
                // AE2 accepts the cursor into network storage, then returns any rejected
                // remainder to the player. An empty server grid and cursor prove completion.
                if (grid.stream().anyMatch(index -> !confirmed.slots().get(index).isEmpty()))
                    return pending("Waiting for the crafting grid to empty");
                return JobController.Confirmation.CONFIRMED;
            }
            if (operation.kind() == Kind.CLEAR) {
                if (!confirmed.slots().get(operation.clearSlot()).isEmpty())
                    return pending("Waiting for the moved slot to empty");
                var beforePlayer = new MenuSnapshot(0, menu.containerId, operation.beforeSlots(), ItemStack.EMPTY).stock(inventory);
                if (!operation.scope().matches(beforePlayer, confirmed.stock(inventory)))
                    return pending("Player inventory differs after stacking crafted items");
                return JobController.Confirmation.CONFIRMED;
            }
            if (operation.kind() == Kind.CRAFT) {
                ItemStack output = operation.recipe().getOutputs().getFirst().getItemStack();
                ItemStack collected = output.copyWithCount(Math.toIntExact(Math.multiplyExact((long) output.getCount(), operation.outstandingBatches())));
                // The initially empty destination is server-authoritative. Requiring the
                // exact collected stack rejects partial batches without relying on AE2's
                // asynchronously refreshed network totals.
                if (!ItemStack.matches(confirmed.slots().get(operation.clearSlot()), collected))
                    return pending("Waiting for the exact crafted output in the player inventory");
                return finishCraft(operation, actual);
            }
        }
        if (!operation.scope().matches(operation.expected(), actual)) {
            for (var key : operation.scope().keys()) if (!Objects.equals(actual.getOrDefault(key, 0L), operation.expected().getOrDefault(key, 0L)))
                return pending("Stored quantity differs for " + key.label() + ": expected " + operation.expected().getOrDefault(key, 0L) + ", got " + actual.getOrDefault(key, 0L));
        }
        if (operation.kind() == Kind.FILL && !matchesPreparedGrid()) return pending("Transferred grid differs from the selected ingredients");
        if (operation.kind() == Kind.FILL && !ItemStack.matches(menu.getSlot(outputSlot).getItem(), operation.recipe().getOutputs().getFirst().getItemStack()))
            return pending("Server crafting result differs from the selected recipe: got " + menu.getSlot(outputSlot).getItem().getCount() + " " + menu.getSlot(outputSlot).getItem().getHoverName().getString());
        if (operation.kind() == Kind.CLEAR && !confirmed.slots().get(operation.clearSlot()).isEmpty()) return pending("Waiting for the moved slot to empty");
        if (operation.kind() == Kind.CLEAR_GRID && grid.stream().anyMatch(index -> !confirmed.slots().get(index).isEmpty()))
            return pending("Waiting for the crafting grid to empty");
        if (operation.kind() == Kind.CRAFT) return finishCraft(operation, actual);
        return JobController.Confirmation.CONFIRMED;
    }
    private JobController.Confirmation finishCraft(Operation operation, Map<StackKey, Long> actual) {
        String fingerprint = operation.recipe().getId() + ":" + new TreeMap<>(stringStock(operation.expected()));
        if (!visited.add(fingerprint)) return JobController.Confirmation.REJECTED;
        hasCrafted = true;
        prepared = null;
        EmiAutocrafting.diagnostic("Confirmed recipe={} menu={} snapshot={} itemTypes={}",
                operation.recipe().getId(), menu.containerId, confirmed.sequence(), actual.size());
        return JobController.Confirmation.CONFIRMED;
    }
    private JobController.Confirmation pending(String reason) {
        if (!waitReason.equals(reason)) { waitReason = reason; EmiAutocrafting.diagnostic("Waiting menu={}: {}", menu.containerId, reason); }
        return JobController.Confirmation.PENDING;
    }
    private static String stackSummary(ItemStack stack) {
        return stack.isEmpty() ? "empty" : stack.getCount() + " " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
    public String waitingMessage() { return waitReason.isEmpty() ? "Waiting for server" : waitReason; }
    public boolean countsAsCraft(Operation operation) { return operation.kind() == Kind.CRAFT; }
    private boolean matchesPreparedGrid() {
        return matchesPreparedGrid(false);
    }
    private boolean matchesPreparedGrid(boolean allowMissing) {
        if (preparedGrid == null) return false;
        for (int i = 0; i < grid.size(); i++) {
            ItemStack actual = menu.getSlot(grid.get(i)).getItem(), expected = preparedGrid.get(i);
            if (allowMissing && actual.isEmpty()) continue;
            if (expected.isEmpty() ? !actual.isEmpty() : !ItemStack.isSameItemSameComponents(actual, expected)
                    || actual.getCount() < expected.getCount()) return false;
        }
        return true;
    }
    public void timeout() { requestSnapshot(); EmiAutocrafting.quarantine(menu); }
    public void rejected() { requestSnapshot(); EmiAutocrafting.quarantine(menu); }
    public String describe(Operation operation) { return operation.label(); }
    public void error(Exception error) { EmiAutocrafting.quarantine(menu); EmiAutocrafting.logError(error); }
    private static Map<String, Long> stringStock(Map<StackKey, Long> stock) {
        Map<String, Long> result = new HashMap<>(); stock.forEach((k,v) -> result.put(k.toString(), v)); return result;
    }
    private static StockScope<StackKey> scopeFor(ItemStack stack) { return new StockScope<>(Set.of(new StackKey(stack))); }
    private boolean relevantSlot(int index, ItemStack before, ItemStack after, StockScope<StackKey> scope) {
        return index == outputSlot || grid.contains(index)
                || !before.isEmpty() && scope.keys().contains(new StackKey(before))
                || !after.isEmpty() && scope.keys().contains(new StackKey(after));
    }
    private boolean sameRelevantSlots(List<ItemStack> a, List<ItemStack> b, StockScope<StackKey> scope) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (relevantSlot(i, a.get(i), b.get(i), scope) && !ItemStack.matches(a.get(i), b.get(i))) return false;
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
