# Upstream review (13 September 2026)

## Exact sources

- Primary derivative: [digestlotion/emiautocrafting](https://github.com/digestlotion/emiautocrafting), `1a4887ef35d28a66c0e05dad1b1e01e916a08ff8`. Main still points to the supplied snapshot; configuration is Minecraft 1.20.1, EMI 1.1.24+1.20.1, Java 17, Yarn and Architectury, Fabric/Forge.
- Target API review: [EMI 1.21](https://github.com/emilyploszaj/emi/tree/1.21), `81f6453c0d0b6df2e503d0bc5f19f1c73fd12173`. This branch still targets 1.21.1 and EMI 1.1.24. Compilation uses the published **Mojang-mapped** `dev.emi:emi-neoforge:1.1.24+1.21.1`, including its published sources. The branch contains post-release internal refactoring, so published signatures take precedence.
- Behavioural reference: [GTNH NotEnoughItems](https://github.com/GTNewHorizons/NotEnoughItems), `a845e6c3e53b72cdca3e30bff8f174b115dcbc7f`. The supplied `6cd7697ca0b7b82e33c9c552733b48d70cc25405` is two commits older. The two later changes concern fluid display/registration. Reviewed AutoCraftingManager, RecipeChainIterator and RecipeChainMath; no NEI source is bundled.
- Public addon fork checked: `Onyxmn/emiautocrafting`, main `f1dad1eb272d9b5210be051409492d6064a7e9f1`; still 1.20.1. GitHub branches/forks and a web search did not identify a 1.21.1 port. This is not an exhaustive claim about private or unindexed work.
- Modrinth's EMI 1.21.1 NeoForge release list still lists `1.1.24+1.21.1+neoforge` as newest (published 13 May 2026). The requested Maven dependency resolved successfully.
- Build toolchain: NeoForge 21.1.250; ModDevGradle 2.0.147; Gradle 9.2.1; Java 21. Mojang mappings throughout the port. Wrapper distribution SHA-256 is pinned.

## Original addon review

Read EmiAutocrafting, EmiAutocraftingConfig, EmiScreenManagerMixin, EmiConfigMixin, SyntheticMixin, all Gradle configuration, Fabric/Forge entrypoints and metadata.

These are source-level findings, not claims that the old addon was reproduced in a 1.20.1 game:

| Review target | Evidence and port decision |
|---|---|
| Missing tree | Both `craftToNode(ingredient)` paths dereference `BoM.tree`. New input/start guards require a tree. |
| Missing/changing screen | The original filler and Tom's branch use the current handled screen without a stable menu identity. A job now captures the screen/menu objects, world recipe epoch and preferences. |
| Static lock | The original sets a static boolean and resets it only on the normal return path, with no `finally`. The new controller has explicit terminal states and catches exceptions. |
| Batch conversion | `neededBatches` is cast directly to int. The new planner uses checked long arithmetic and clamps outstanding batches to one before converting to int. |
| Premature recalculation | Original recalculates immediately after performFill returns. StandardRecipeHandler can return true immediately after sending a packet. The new controller waits for verified inventory. |
| Duplicate ingredient subtrees | Original getNode returns the first equal ingredient in a depth-first traversal. The port operates on one selected full tree, preserving recipe and selected output identity. |
| Tom's Storage | Original imports Tom's classes, includes storage dependencies and adds a custom click/transfer loop. None is carried into the core. |
| Consumed input | Original stackInteraction unconditionally consumes its matching bind, even if craftToNode returns false. New input only consumes actions it handles, and guards text focus/context/key-repeat. |

The old config-inheritance and synthetic-tooltip mixins are replaced by NeoForge client configuration, contextual key events and a small status strip. The packet observer mixin observes incoming vanilla menu contents and recipe reloads on the client thread.

## Target API findings

- **BoM / MaterialTree / MaterialNode:** preserve hearts and per-tree resolutions; the selected root output matters because MaterialTree's constructor initially chooses the first output. EMI stores a goal amount and a batch multiplier. Preparing through the addon sets goal amount to one and batches to the requested item total.
- **TreeCost:** allocates stock/remainders across branches for EMI presentation, but uses unchecked multiplication and double-based ceiling. Execution uses a bounded immutable projection and checked-integer ledger. Existing stock is used before missing production, and legitimate surplus is retained.
- **EmiFavorites.updateSynthetic:** retained for the crafting sidebar, after execution quantities have been checked.
- **EmiRecipeFiller / StandardRecipeHandler / EmiCraftContext:** retained for exact, component-aware ingredient fill and INVENTORY destination. A one-execution recipe projection fixes ingredient alternatives selected by preflight. The real recipe manager must match the selected recipe ID and assembled output.
- **Immediate versus transfer:** only the exact built-in CraftingRecipeHandler and InventoryRecipeHandler classes are accepted, on exact vanilla menu classes. A generic Standard handler, coerced handler, machine handler or JEI transfer result cannot establish immediate crafting support.
- **FillRecipeC2SPacket:** server assistance executes normal menu output clicks. Its cleanup can offer/drop leftover grid items. The port requires an initially empty grid, clears its own remainders through checked quick-moves, and reserves output/remainder capacity before dispatch.
- **Client fallback:** when EMI is absent from the server, EMI emits ordinary container clicks. A locally predicted click need not generate an authoritative slot delta when prediction agrees, so ordinary slot-change counting is insufficient.
- **JemiRecipeHandler:** supplies an empty inventory and calls a JEI transfer handler. Its success can be transfer-only and does not implement INVENTORY destination semantics. It is not treated as an execution adapter. EMI's built-in vanilla handlers remain the intended path when JEI is also present.
- **GTNH reference:** recalculate from accessible inventory after progress, bound each craft request, and stop when context changes. Its 1.7.10 background-thread execution is not copied into Minecraft 1.21.1.

## Server confirmation in 1.21.1

Inspected Mojang-mapped ServerGamePacketListenerImpl.handleContainerClick, AbstractContainerMenu.isValidSlotIndex/doClick, and ClientPacketListener.handleContainerContent in the generated target sources/bytecode.

A QUICK_MOVE with slot -1 returns without changing inventory. A state ID of -1 differs from the menu's 15-bit state counter and causes `broadcastFullState()`. After an operation the addon sends this no-op snapshot request, then observes a **received ClientboundContainerSetContentPacket**. It compares the exact component-aware multiset of accessible input/inventory slots to `before - inputs + output + actual recipe remainders`, requires an empty cursor, and requires current slots to match that received snapshot. An unrelated update or local prediction does not satisfy the expected inventory delta.

The initial plan also requests an authoritative snapshot. Only one operation is unconfirmed at a time. Partial snapshots remain pending. A timeout requests a fresh snapshot and quarantines the menu; there is no automatic retry. Closing/reopening is required before an explicit restart. Cancellation stops future actions, and any operation already sent may still complete.

This method deliberately depends on the inspected vanilla 1.21.1 menu protocol. Servers that alter/reject this mechanism are unsupported and will stop on a timeout. It is not a universal transaction acknowledgement API.

A second, narrow accessor reads the vanilla recipe-book search field's focus state so shortcuts do not intercept its select-all/copy actions. EMI search and ordinary single/multiline text fields are guarded separately.
