# Craft an EMI recipe tree from start to finish

**EMI Autocrafting – NeoForge** turns your selected EMI recipe tree into a finite crafting batch. Choose how many items you want in total, start the batch, and the addon crafts supported intermediate recipes using materials available through your open crafting interface.

This is an independent **Minecraft 1.21.1 NeoForge port and derivative of [digestlotion's EMI Autocrafting](https://github.com/digestlotion/emiautocrafting)**, released under GPL-3.0-only. It requires EMI and is not an official EMI release.

## Features

- Craft recursive recipe chains, counting final items and intermediates you already have.
- Use your inventory together with storage exposed by supported crafting interfaces.
- See the exact missing materials before a batch starts.
- Keep the active tree in its own collapsible **Craft batch** panel, with progress and Tree/Clear controls.
- Advance as soon as the previous operation is confirmed, with configurable pacing.
- Stop a batch with a shortcut or take over with a manual inventory click. Uncertain crafts stop with a reason and are not retried automatically.

## Requirements and installation

- Minecraft **1.21.1** and **Java 21**.
- **NeoForge 21.1.249 or later within the 21.1 series**.
- **EMI 1.1.24+1.21.1 for NeoForge**, installed separately. This exact EMI version is required because the addon uses EMI internals.

Put EMI and this addon's release JAR in your client's `mods` folder. Replace any older version of this addon; do not install it alongside the original EMI Autocrafting. The sources JAR is for developers.

The addon runs on the client and is not required on a dedicated server. EMI's server-assisted recipe fill is used when available, with its ordinary container-click fallback otherwise. Keep dependencies required by your modpack, including JEI where applicable.

## Supported crafting interfaces

- Vanilla crafting tables and the player's 2×2 crafting grid.
- **Crafting Station**, including inventories exposed by the station's adjacent-storage tabs.
- **Ars Nouveau Bookwyrm storage lecterns** with the crafting upgrade and linked inventories.
- **Applied Energistics 2 crafting terminals**, using actual stored materials.

These integrations are optional and are not bundled. Runtime coverage uses Crafting Station **2.1.1**, Ars Nouveau **5.13.1**, and AE2 **19.2.17**, including Crafting Station with Sophisticated Storage chests. See the [test report](https://github.com/IN-Nedev/emiautocrafting-neoforge/blob/main/docs/TEST-REPORT.md) for the exact coverage.

AE2 support performs immediate crafting from stored items; it does not create patterns or submit crafting CPU jobs. The wireless crafting terminal is recognized but has not been separately runtime-tested.

## Getting started

1. Open a supported crafting interface. Start with an empty crafting grid and cursor, and keep one empty player inventory slot when crafting from connected storage.
2. Choose preferred intermediate recipes using EMI's hearts, and resolve recipe-tree ingredient alternatives as needed.
3. Hover a recipe output or a recipe-associated favourite and press **Ctrl+A**. Enter the total number of output items you want and choose **Prepare tree**.
4. Press **Ctrl+C** to start. Existing items count toward your requested total.
5. Press **Ctrl+C** again or **Ctrl+X** to stop. **N** executes one confirmed recipe step.

For example, prepare one wooden pickaxe with preferred plank and stick recipes. With the required logs available, the addon crafts planks, then sticks, then the pickaxe. Existing sticks or planks reduce the work.

The shortcuts are configurable under **Mods → EMI Autocrafting → Config**. Text fields keep their normal editing shortcuts. Closing the interface, disconnecting, changing recipes or manually clicking inventory slots cancels further actions.

## Current beta limits

This release supports ordinary shaped/shapeless crafting recipes and plain KubeJS wrappers without scripted ingredient actions or output modifiers. Processing machines, arbitrary custom benches, dynamic/chance-based recipes, and network crafting CPU jobs are outside its scope.

Planning counts storage exposed by the open interface. Crafting Station can cap displayed oversized stacks, so the initial plan may see less than the chest physically contains. Vanilla tables do not gain nearby-inventory scanning. Space checks reserve room for outputs and returned containers before consuming inputs.

The complete Impostor Syndrome – Reimagined modpack and every multiplayer server configuration are not certified. The beta has automated packaged-JAR coverage for the supported storage integrations; please include your mod versions, recipe, interface and displayed status when [reporting a problem](https://github.com/IN-Nedev/emiautocrafting-neoforge/issues).

## Credits and source

Original addon by **digestlotion**. EMI by **Emily Ploszaj and contributors**, installed separately. This port preserves the upstream attribution and GPL-3.0-only license.

[Source code](https://github.com/IN-Nedev/emiautocrafting-neoforge) · [Issue tracker](https://github.com/IN-Nedev/emiautocrafting-neoforge/issues) · [License](https://github.com/IN-Nedev/emiautocrafting-neoforge/blob/main/LICENSE)
