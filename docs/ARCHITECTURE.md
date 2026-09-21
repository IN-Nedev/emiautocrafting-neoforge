# Execution boundary

`EmiBridge` contains the EMI internals: selected recipe/output, preference and resolution snapshots, bounded MaterialNode projection, native sidebar updates, handler selection and fill. `TreePlanner` owns a single component-aware stock ledger. It allocates sibling alternatives together using a residual flow graph, preserves ancestor reservations and carries batch surplus forward. Each next operation is rebuilt from current inventory.

`MenuPort` accepts exact vanilla CraftingMenu/InventoryMenu with EMI's exact built-in handlers, plus the optional exact menu classes recognized by `StorageCrafting`. It projects an exact one-execution ingredient choice, checks the real shaped/shapeless recipe and selected recipe ID, assembles the expected output and remainders, and reserves space. Shapeless grid-size behaviour is preserved in the exact-ingredient projection.

`CraftingCompatibility` admits the exact vanilla recipe classes and the exact known KubeJS shaped/shapeless wrapper classes with the expected vanilla superclass. For KubeJS it reads public accessors through reflection, requiring an empty ingredient-action list and empty output-modifier string before assembly or remainder evaluation. Unknown wrappers or accessor failures are rejected. KubeJS is not a required dependency. The original recipe still performs matching, so its mirroring rules are retained.

The reviewed Quark mixed-material class is accepted, and Quark exclusion wrappers are accepted only when their parent also passes verification. `QuarkRecipes` exposes the mixed recipe's real material tag to EMI. Ingredient selection for these recipes backtracks through available combinations until Quark's matcher accepts one; the final recipe-manager winner, output, remainders and stock checks still apply. Reflection keeps Quark optional.

`TreePlanner` retains the first blocked node and its dependency path, after accounting for supplied intermediates. `CraftingProblem` adds the selected recipe's identifier, category and source: batch target, local tree choice, global preference or default. `CraftingProblemScreen` presents the cause and links back to EMI; the batch panel can reopen it. Full details are logged even with verbose diagnostics disabled. Starting a batch recalculates EMI's tree from current preferences without discarding explicit local choices.

`JobController` is pure Java and advances on client ticks. Synchronization and remainder-clear operations use the same waiting state but do not count as a crafted step. A confirmed execution can plan and dispatch the next operation in the same tick, but dispatch returns immediately and there is at most one dispatch per tick. Extra pacing defaults to zero; each operation must still confirm before another is sent. No crafting loop runs inside a key callback.

The client logout event cancels immediately and clears held-shortcut tracking; cancellation does not depend on a later world tick after disconnect.

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Planning: start
    Planning --> Ready: preflight passes
    Ready --> Waiting: dispatch one operation
    Waiting --> Planning: server snapshot verifies result
    Planning --> Completed: total satisfied
    Waiting --> Completed: confirmed single step
    Planning --> Blocked: missing / unsupported / space
    Waiting --> Blocked: rejection / timeout
    Planning --> Cancelled: context changes
    Ready --> Cancelled: context changes
    Waiting --> Cancelled: context changes
    Ready --> Failed: unexpected error
    Planning --> Failed: unexpected error
    Waiting --> Failed: unexpected error
    Blocked --> Planning: explicit fresh start
    Cancelled --> Planning: explicit fresh start
    Completed --> Planning: explicit fresh start
    Failed --> Planning: explicit fresh start
```

The incoming full-menu snapshot is copied after the client packet handler applies it on the Minecraft thread. Each operation has a `StockScope` containing its exact ingredient, output and remainder keys, including reusable inputs with zero net consumption. Matching requires the expected counts for every scoped key, an empty cursor, and agreement between the received snapshot and current grid, result and scoped item slots. CLEAR scopes the moved item. SYNC obtains a fresh full snapshot and checks the grid/result/cursor before planning. Merely returning true from EMI, moving an unrelated slot, or predicting a result locally cannot confirm a craft.

Unused stored items may change metadata, arrive or leave without invalidating an operation that does not touch them. After FILL, capped-source reconciliation runs on the scoped quantities; background quantities are taken from the new inventory. A prepared CRAFT retains its exact scoped expectation while rebasing unrelated stock again before dispatch. Subsequent planning reads current accessible inventory. Relevant losses, missing outputs and changed reusable inputs still cannot confirm; component matching itself is not relaxed.

The synchronization request is a vanilla no-op QUICK_MOVE at slot -1 with state ID -1. The inspected 1.21.1 server returns a full menu state for that mismatch. Timeouts request a snapshot and quarantine the menu; they never automatically repeat a craft. There is no addon network channel and no server entrypoint.

## Optional storage protocols

`StorageCrafting` uses optional reflection against the installed versions; there are no hard class references or bundled storage mods. Crafting Station contributes every native adjacent inventory slot, including hidden tabs. Ars supplies its stored-item quantities. AE2 supplies stored amounts from its client repository, excluding craftable-only entries and non-item keys. The player inventory, grid and remote list form a single component-aware stock ledger.

Station fills use planned vanilla clicks that place the selected ingredients and return each cursor remainder to its original source. The lectern and AE2 use their own transfer packets. AE2 receives explicit templates with craftMissing=false. A FILL operation requires matching real ingredients and result, a new server snapshot and unchanged scoped stock except for bounded material revealed by capped station source slots. Matching grid stacks may contain extra copies of the exact ingredient.

Lectern and AE2 CRAFT operations send a bounded number of native single-craft actions, accumulate one output stack on the cursor and deposit it once into an initially empty player slot. `CraftBatches` limits execution by the outstanding request, stack capacity, reserved ingredient budget, refillable stock and grid quantities. AE2 cannot refill from loose player ingredients, so its FILL step first places the missing batch supply into each matching grid slot using planned vanilla clicks. Native AE2 transfer preserves those stacks and fills remaining empty slots. Placement accounts for each slot's capacity and existing count, with a shared player-inventory budget across repeated ingredients. FILL confirmation and the prepared CRAFT both require enough network/grid supply for the entire batch. A partly filled compatible AE2 grid can be topped up without clearing it. Recipes with remainders, single-step mode and Crafting Station stay at one execution. Confirmation verifies the aggregate consumption and output; partial/rejected batches are never retried automatically.

A changed storage recipe plans all grid-to-player moves before dispatch. Each whole source stack has reserved capacity, including merges with earlier moves. CLEAR_GRID checks every destination before sending, then requires an empty grid/cursor and unchanged quantities for every returned item in one confirmation. It never uses outside clicks or the lectern clear button that can drop overflow. Confirmed output stacks are merged through quantity-preserving CLEAR operations.

`CraftingStationMenuMixin` selects the station's existing client display cache for directions declared by its opening packet. Vanilla full/slot updates and the station's own side-slot packets populate that cache; local click prediction remains native. The alternative native path reads a live block-entity inventory, whose separate updates can overwrite already-synchronized menu quantities. The cache prevents that second source of client state from undoing a transfer. The mixin is optional (`@Pseudo`) and explicitly checks `world.isClientSide`; server inventory access is unchanged. Full-snapshot verification and item conservation remain required.

AE2's `MEStorageScreen` appends `RepoSlot` display entries directly to the client menu, outside its client-slot registry. They and registered client-only slots are excluded from server slot comparisons. Remote inventory updates may arrive separately; confirmation waits for the aggregate quantity change too. Native transfer success alone never confirms consumption.

`JobSidebar` registers an EMI exclusion area and stack provider. A pinned-EMI mixin captures synthetic tree entries into the batch panel and removes their duplicate presentation, leaving ordinary favourites intact. EMI's tree cost presentation receives the accessible storage ledger. The panel supports collapse, paging, tree access and removal. `MissingItemsScreen` shows every planner shortage, with quantities, and requires explicit restart after materials are supplied.

## Capped Crafting Station counts

Crafting Station 2.1.1 clamps `SideContainerSlot.getItem()` to the item's normal stack size, even when a Sophisticated chest contains a larger stack. Transferring one item can therefore turn a visible total of 64 into 65 without creating any material. A source can also cross below the cap: extracting eight from 70 real planks changes the displayed total from 64 to 70 (62 in storage and eight in the grid).

`StorageCrafting.revealLimits` uses the exact number withdrawn from each capped source minus its visible decrease. It covers both sources that remain saturated and those that cross below the cap. Station fills take half-stacks when returning leftovers, keeping each source occupied. This prevents Sophisticated Storage from redirecting insertion into an emptied slot to another upgraded stack, which would conceal previously visible material. A source is fully extracted only when every picked item is needed. `CappedStock.afterTransfer` accepts only nonnegative gains bounded by those withdrawals; losses, gains of unrelated materials, and excessive gains fail. The authoritative exact filled grid/result and empty cursor are required first. The next craft's expected stock is rebased by the revealed quantities; its consumption, output and remainder checks remain exact. No uncertain craft is retried.
