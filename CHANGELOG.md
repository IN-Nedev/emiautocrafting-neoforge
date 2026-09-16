# Changelog

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
