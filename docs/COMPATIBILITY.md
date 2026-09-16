# Compatibility

Minecraft 1.21.1, Java 21, NeoForge 21.1.249+ within the 21.1 series, and EMI 1.1.24+1.21.1 for NeoForge are required. Other loaders and EMI releases are not supported by this build.

## Crafting interfaces

| Interface | Tested version | Available materials |
| --- | --- | --- |
| Vanilla crafting table / player grid | Minecraft 1.21.1 | Main inventory and hotbar |
| Crafting Station | 2.1.1 | Player inventory and all native adjacent-inventory tabs |
| Ars Nouveau Bookwyrm crafting lectern | 5.13.1 | Player inventory and linked storage |
| AE2 crafting terminal | 19.2.17 | Player inventory and stored item quantities |

The storage integrations are optional and are not bundled. Crafting Station tests include Sophisticated Storage chests, hidden source slots and oversized stacks. The wireless AE2 crafting terminal uses the same adapter but has not been separately runtime-tested.

## Recipes and quantities

Standard shaped/shapeless recipes use their real ingredient matching, assembled output and returned-item rules. Plain KubeJS shaped/shapeless wrappers are accepted when they have no scripted ingredient actions or output modifiers.

Quark 4.1-482 (Zeta 1.1-40) mixed-material crafting recipes and exclusion wrappers around supported recipes are also recognized. Mixed recipes expose their actual material tag to EMI instead of a placeholder wood. Ingredient selection must pass Quark's own matcher and resolve to the selected recipe; a combination that instead makes a variant chest is rejected before items move. Supply a valid mixture or select another chest recipe.

Existing outputs and intermediates count toward the requested total. Recipes are executed in whole batches, so surplus output is retained. Planning requires enough accessible base materials for the remaining tree.

Armour, offhand, locked slots and AE2 craftable-only patterns are excluded. Vanilla tables do not gain nearby-storage access. Crafting Station reports the quantities exposed by its native slots; capped oversized stacks can make the initial plan see less than the chest contains. Material revealed during transfer is accounted for before output pickup.

## Execution limits

- Start with an empty grid and cursor. Storage crafting requires an empty player inventory slot for collecting output.
- Space is reserved for outputs and returned containers before inputs are consumed. A nearly full inventory can be refused even if the craft would free a slot.
- Concurrent changes to the ingredients, output or returned items can stop confirmation. Changes to unrelated stored items do not block the current operation.
- Machines, arbitrary custom benches, dynamic/chance-based recipe classes and AE2 CPU crafting are not supported.
- One tree runs at a time, with at most one operation dispatched per client tick. Jobs stop after 100,000 recipe executions or if quantity arithmetic exceeds its limits.
- Servers must preserve normal Minecraft 1.21.1 menu synchronization. On a timeout, reopen the interface before restarting.

[Testing and reproduction](TEST-REPORT.md) describes the measured coverage. Compatibility is scoped to the listed interfaces and recipe types.

## Understanding a blocked tree

A blocked-recipe screen identifies the item, recipe ID, category, selection source, and path from the batch target. The same details, including the raw recipe class and serializer, are logged even when verbose diagnostic logging is disabled. Existing items are counted first, so an unsupported intermediate does not block a batch if enough of that item is already accessible.

Removing a heart changes the global preferred recipe. A recipe chosen inside the current tree can still override it. Clearing and preparing the batch again discards those local choices while retaining global preferences. Ordinary sidebar favourites are bookmarks and do not control recipe selection.
