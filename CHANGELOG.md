# Changelog

## 2.0.0-beta.12

- Return leftover AE2 crafting-grid materials to network storage first, placing only rejected overflow into the player inventory.
- Verify both network insertion and full-network fallback without losing ingredients.

## 2.0.0-beta.11

- Recognize AE2WTLib wireless crafting terminals as AE2 crafting menus, so stored materials are included in the batch plan.
- Support Sophisticated Core next-tier upgrade recipes, preserving settings from the previous upgrade and counting configured outputs toward the requested total.
- Report the installed addon version accurately in the startup log.

## 2.0.0-beta.10

- Batch AE2 recipes that use intermediate ingredients in the player inventory by loading enough material into each crafting slot before collecting output.
- Keep matching AE2 grid ingredients while filling missing slots, avoiding a separate clear operation for the same recipe.
- Bound player-to-grid transfers by ingredient stack limits, available materials and the requested output. Verify the completed transfer before crafting.

## 2.0.0-beta.9

- Collect lectern and AE2 crafts in bounded batches of up to one output stack per server confirmation, respecting outstanding quantities and reserved ingredients.
- Clear leftover storage grids in one verified operation and reuse matching ingredients, including prefilled stacks.
- Account for native refill supply and uneven grid stacks when sizing batches. Keep returned containers, tools and single-step mode at one execution per confirmation.
- Retain aggregate server checks and stop partial or uncertain batches without retrying.

## 2.0.0-beta.8

- Fix Crafting Station batches stopping when extraction brings an upgraded storage stack below the normal displayed stack limit.
- Return unused transferred ingredients to their original slots, preventing generic recipe filling from merging upgraded stacks and hiding visible quantities.
- Bound newly revealed stock by the exact withdrawals from each capped source. Output, remainder and server confirmation checks remain required.

## 2.0.0-beta.7

- Name blocked recipes and their dependency paths in a readable detail screen, with links to the recipe and tree and copyable diagnostics.
- Show whether a blocked recipe came from a tree choice, an EMI heart, or a default; explain how those choices differ from sidebar favourites.
- Support Quark mixed-material recipes and verified exclusion wrappers. Show the real material alternatives and check Quark's matching rules before transferring ingredients.
- Refresh the tree from current preferences when starting a batch while preserving explicit choices within that tree.

## 2.0.0-beta.6

- Fix output-pickup stalls when an unrelated stored item changes metadata, arrives or leaves.
- Verify the ingredients, output and returned items for each operation, then refresh stock before planning the next step.

## 2.0.0-beta.5

- Keep Crafting Station's displayed storage synchronized with its server-backed menu cache.
- Include the synchronization failure reason in timeout messages.

## 2.0.0-beta.4

- Account for material revealed while transferring from capped, oversized Crafting Station storage slots.
- Add bounded synchronization diagnostics.

## 2.0.0-beta.3

- Add Crafting Station, Bookwyrm lectern and AE2 crafting-terminal integrations.
- Count connected storage and show all missing materials.
- Add a separate, collapsible Craft batch panel.
- Advance immediately after confirmation by default, reuse matching grid refills and combine output stacks.

## 2.0.0-beta.2

- Support plain KubeJS shaped/shapeless recipes without scripted actions or output modifiers.
- Set the minimum NeoForge version to 21.1.249.
- Cancel active jobs immediately on disconnect.

## 2.0.0-beta.1

- Port to Minecraft 1.21.1, NeoForge, Java 21 and Mojang mappings.
- Add finite target quantities, intermediate crafting and existing-item accounting.
- Add contextual shortcuts, single-step execution and cancellation.
- Verify inventory changes after each operation and stop on uncertain results.
