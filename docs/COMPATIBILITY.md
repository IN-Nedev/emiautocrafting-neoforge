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

Stack upgrades are supported during repeated crafting, including when a source drops below its displayed stack limit. Crafting Station transfers keep sources occupied when returning unused ingredients, preventing Sophisticated Storage from redirecting those items into another upgraded stack. The initial displayed-count limitation still applies.

## Execution limits

- Start with an empty cursor. Vanilla grids must also be empty. Supported storage grids can reuse matching ingredients or return the whole grid to the player inventory, provided there is room. Storage crafting requires an empty player inventory slot for collecting output.
- Space is reserved for outputs and returned containers before inputs are consumed. A nearly full inventory can be refused even if the craft would free a slot.
- Concurrent changes to the ingredients, output or returned items can stop confirmation. Changes to unrelated stored items do not block the current operation.
- Quark initializes compass components after crafting. A crafted compass whose components change this way can time out after its output is retained; it is not automatically retried. This behaviour is also reproducible in beta 8.
- Machines, arbitrary custom benches, dynamic/chance-based recipe classes and AE2 CPU crafting are not supported.
- Lectern and AE2 recipes without remainders can execute up to 64 crafts, bounded by one output stack, the remaining request, ingredient reservations and native refill supply, per confirmed operation. Uneven grid stacks reduce the batch limit. Recipes with returned containers or tools, single-step mode, and Crafting Station output pickup use one execution at a time.
- One tree runs at a time, with at most one operation dispatched per client tick. Jobs stop after 100,000 confirmed craft operations or if quantity arithmetic exceeds its limits. Cancellation stops later operations; a batch already sent to the server can finish.
- Servers must preserve normal Minecraft 1.21.1 menu synchronization. On a timeout, reopen the interface before restarting.

[Testing and reproduction](TEST-REPORT.md) describes the measured coverage. Compatibility is scoped to the listed interfaces and recipe types.

## Understanding a blocked tree

A blocked-recipe screen identifies the item, recipe ID, category, selection source, and path from the batch target. The same details, including the raw recipe class and serializer, are logged even when verbose diagnostic logging is disabled. Existing items are counted first, so an unsupported intermediate does not block a batch if enough of that item is already accessible.

Removing a heart changes the global preferred recipe. A recipe chosen inside the current tree can still override it. Clearing and preparing the batch again discards those local choices while retaining global preferences. Ordinary sidebar favourites are bookmarks and do not control recipe selection.
