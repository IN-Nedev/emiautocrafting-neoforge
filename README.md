# EMI Autocrafting — Minecraft 1.21.1 NeoForge

A GPL-3.0-only derivative of digestlotion's emiautocrafting addon. Prepare one EMI recipe tree, request a finite **total number of items**, and start once. The client crafts supported intermediate recipes, rechecks the server inventory, and continues until complete or blocked.

**Beta release:** see [the test report](docs/TEST-REPORT.md) for the exact runtime coverage and remaining limitations.

## Install

Use Java 21, **Minecraft 1.21.1**, **NeoForge 21.1.249 or later in the 21.1 series**, and **EMI 1.1.24+1.21.1 for NeoForge**. EMI displays its installed version as `1.1.24+1.21.1+neoforge`. The addon intentionally pins this EMI release because it uses EMI internals. This release is built against NeoForge 21.1.249, matching Impostor Syndrome – Reimagined 0.6-hotfix.

Use this port instead of the original emiautocrafting JAR. Put EMI and `emiautocrafting-neoforge-1.21.1-2.0.0-beta.6.jar` in the **client's** `mods` folder. Replace any older addon JAR. Do not install the sources JAR or the runtime-test mod. The addon is not required on a dedicated server. EMI on the server is optional: the addon uses EMI's server-assisted fill when available and its ordinary container-click fallback otherwise.

Storage integrations are optional: Crafting Station 2.1.1, Ars Nouveau 5.13.1 and AE2 19.2.17 are detected when installed. They are not bundled or required.

For Impostor Syndrome – Reimagined, keep the pack's JEI and add EMI separately. See [pack installation and compatibility](docs/PACK-COMPATIBILITY.md) for exact versions, tested paths and remaining limits.

## Use

1. Open a crafting table, Crafting Station, Bookwyrm crafting lectern, or AE2 crafting terminal. Materials can be in your inventory or storage connected to that interface. Start with an empty crafting grid and cursor, and keep one empty inventory slot for storage crafting.
2. Use EMI's existing hearts to select preferred intermediate recipes. Resolve tags/alternatives in EMI's recipe tree where needed.
3. Hover a recipe's **output** or a recipe-associated favourite and press **Ctrl+A**. Enter the total number of output items you want, then select **Prepare tree**.
4. Press **Ctrl+C** to start. This returns to the underlying crafting interface and runs the selected tree. Existing final and intermediate items count toward the target.
5. Press **Ctrl+C** again, or **Ctrl+X**, to cancel. Press **N** for one confirmed recipe execution. Results already crafted remain yours.

For a wooden pickaxe, select oak planks and sticks as preferred recipes, resolve the relevant planks/log alternatives, supply two logs, prepare a total of one pickaxe and start. The addon crafts planks, sticks and the pickaxe. Existing sticks or planks reduce the work.

EMI's existing tree also works without the preparation dialog: its root amount multiplied by its batch count becomes the requested total. A tree prepared by this addon uses one root item per batch, so the tree's quantity is an item total. Eight pistons with three already present requests five more. A recipe yielding four items with five still needed executes twice and keeps all eight produced items.

Shortcuts only work with an EMI crafting context. Text fields retain select-all/copy behaviour, including EMI and vanilla recipe-book search. On macOS these defaults use the **Control** key. Change shortcuts, timeout, pacing and diagnostic logging in **Mods → EMI Autocrafting → Config**, or `config/emiautocrafting-client.toml`. Binding strings accept CTRL, SHIFT, ALT and A–Z, 0–9 or F1–F12; NONE disables a shortcut.

The tree appears in its own **Craft batch** panel with the target, obtained count and remaining ingredients. Click its header to collapse/expand it, scroll to page through ingredients, or use **Tree** to edit it and **Clear** to remove the batch. Ordinary EMI favourites remain separate. Editing/clearing the batch stops the job; collapsing it keeps crafting. Missing materials open a complete, scrollable shortage list after accessible storage has been counted.

The default `pacingTicks = 0` starts the next operation as soon as the previous one is confirmed, with at most one dispatch per client tick. Existing configs keep their previous pacing value unless you change it. Native lectern/AE2 refills are reused when they match the next checked recipe. Outputs are combined into inventory stacks.

An inventory click during a job cancels future actions so you can take over. Closing the screen, disconnecting, recipe reloads or changing recipe choices also cancels. Blocked jobs never resume automatically. After a synchronization failure, close and reopen the interface before explicitly restarting.

## Scope and limits

- Vanilla 3×3 tables and 2×2 inventory grids, Crafting Station adjacent inventories, Ars Nouveau storage lecterns with a Bookwyrm crafting upgrade and linked inventories, and AE2 crafting terminals. These adapters execute ordinary crafting-grid recipes.
- Normal shaped and shapeless recipes, including datapack/mod recipes that use these standard recipe classes. Real recipe matching, assembled output, ingredient components and remaining-item behaviour are checked. Deterministic reusable/damageable-tool remainders are supported inside these standard recipes and were checked using isolated test fixtures.
- KubeJS's known shaped/shapeless wrappers are also accepted when their ingredient-action list and output-modifier name are empty. Actual recipe matching still applies, including non-mirrored patterns. Scripted outputs and ingredient actions remain unsupported; KubeJS is optional.
- One tree, a positive total up to 1,000,000,000 items, bounded depth/node count and at most 100,000 confirmed recipe executions per run. Arithmetic that exceeds long limits stops the job. Only one recipe execution is dispatched at a time.
- Main inventory/hotbar plus storage exposed by the open supported interface are counted together. Armour/offhand, locked player slots and AE2 craftable-only patterns are excluded. No nearby-chest scanning is added to vanilla tables. Crafting Station counts the quantities its native slots expose; oversized slots may be capped by the station mod. Transfers from capped slots can reveal additional material; beta 4 accounts for that before collecting the result. Ars/AE2 use their native remote stock lists.
- Space checks are deliberately conservative: reserve capacity for output and returned items **before** consuming inputs. A nearly full inventory may be refused even when consumption would free space.
- The initial grid must be empty. Returned containers may remain in the grid on completion; they are retained, and normal grid cleanup occurs before another step if needed.
- Existing inputs from an unsupported machine can be used. Missing machine production blocks the tree.
- Other custom/dynamic/chance-based recipe classes, arbitrary damageable-tool recipes, processing machines and network crafting CPU jobs are not supported. A modded item with a deterministic remainder inside an ordinary shaped/shapeless recipe still uses the real recipe's remainder method, but broad tool compatibility is not claimed.
- No Tom's Storage, Refined Storage, Extended Crafting or arbitrary custom bench adapter, pattern creation or multi-goal planner is included. AE2 means immediate crafting using stored items, not submitting CPU autocrafting jobs. The wireless crafting terminal is recognized through the same protocol; it has not been separately runtime-tested. JEI transfer-only handlers are not accepted as immediate crafting handlers.
- Servers that change normal 1.21.1 menu synchronization may reject or time out the snapshot check. The addon stops instead of repeatedly sending an uncertain craft.
- Planning requires enough currently accessible base materials for the remaining tree before proceeding; it does not deliberately spend materials on a known-incomplete plan.

## Build and verify

Install JDK 21 and run:

```sh
./gradlew build
```

Windows: `gradlew.bat build`. Gradle downloads the pinned dependencies. Release JAR and sources JAR are produced in `build/libs`. Mojang mappings are used throughout; there is no remapping or shadowing of unrelated mods.

```sh
./gradlew runClient
./gradlew runServer
./gradlew -Pintegration runClient
./gradlew -Pintegration -PtoolFixtures runClient
./gradlew -Pintegration -PpackCompatibility runClient
```

The toolFixtures option adds isolated test items and tool recipes to the integrated test world only. Do not enable it when connecting to a server without those fixtures. The packCompatibility option downloads a pinned sample of the pack's crafting mods and copies independently authored recipe fixtures into `run-pack/kubejs`; it does not install the full pack. The opt-in integration run creates disposable test worlds and executes the development harness in `src/testMod`. It is excluded from release artifacts. Dedicated test runs use `-PserverOnly -PserverDir=/absolute/test/server`, and optionally `-PserverWithoutEmi`; the companion client uses `-Pintegration -PtestMode=dedicated-emi` or `dedicated-no-emi`. A local offline test server, operator permissions for `AutocraftTest`, and the included fixture datapack are needed for the harness commands. Do not use the harness against an existing personal world or public server.

See [upstream review](docs/UPSTREAM-REVIEW.md), [test report](docs/TEST-REPORT.md), [changelog](CHANGELOG.md) and [attribution](NOTICE.md).
