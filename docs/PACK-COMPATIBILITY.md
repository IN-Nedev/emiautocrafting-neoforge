# Impostor Syndrome – Reimagined: beta 6

This installation targets **Impostor Syndrome – Reimagined 0.6-hotfix**, Minecraft 1.21.1, NeoForge 21.1.249 and Java 21. The older Impostor Syndrome pack is a different pack.

## Prism installation and use

In Prism, select **Impostor Syndrome – Reimagined → Edit → Mods**. Keep the pack's JEI, EMI `1.1.24+1.21.1+neoforge`, and one addon JAR: `emiautocrafting-neoforge-1.21.1-2.0.0-beta.6.jar`. Remove older addon JARs from the active mods directory. The sources JAR and runtime-test harness are not player mods.

Open one of the interfaces below with an empty cursor/grid. Keep one empty player inventory slot for native storage result pickup. Prepare a total using **Ctrl+A** over the recipe output, choose intermediate recipes with EMI's hearts/tree, then **Ctrl+C** starts/stops. **Ctrl+X** cancels and **N** executes one confirmed recipe. Click out of a search field before using shortcuts; text fields keep their copy/select-all behaviour.

| Interface | Materials counted and used |
|---|---|
| Vanilla table / player 2×2 grid | Main inventory and hotbar |
| Crafting Station, version 2.1.1 | Player inventory plus native adjacent inventory slots, including other container tabs |
| Ars Nouveau 5.13.1 Bookwyrm crafting lectern | Player inventory plus inventories linked through the lectern's normal storage setup |
| AE2 19.2.17 ME crafting terminal | Player inventory, crafting grid and actual items in the connected ME storage |

The active tree occupies its own **Craft batch** panel. Its header collapses/expands without cancelling. **Tree** opens the editable tree and stops the job; **Clear** removes the batch. Favourites remain separate. A blocked material check shows every missing material and quantity after subtracting accessible stock. Add those materials and explicitly restart.

The installed config uses `pacingTicks = 0` and `groupCraftingJob = true`. Zero means no extra delay after server confirmation; it does not bypass confirmation. Outputs are merged into stacks and exact native grid refills are reused where possible.

## Verification and limits

The test setup uses a separate Prism instance named **Impostor Syndrome – Autocrafting Test**, with the installed pack's mods, scripts/configuration and assets, plus a test harness. It creates its own disposable worlds; personal saves are not used. The harness is kept out of the main instance. See [the current test report](TEST-REPORT.md) for results and evidence.

Tests also run against the exact installed Crafting Station, Ars and AE2 JARs in a smaller isolated runtime. Real chests are linked to the station and lectern; AE2 uses a powered terminal and a finite 1k item cell. The chain test starts with two logs entirely in storage and checks one pickaxe, three planks and two sticks afterward. Large jobs check that 48 stored logs become exactly 192 planks in three player stacks. Shortage cases verify that existing stored items are retained.

Supported recipe classes are normal shaped/shapeless crafting and the known plain KubeJS wrappers. Custom ingredient actions, scripted output modifiers, chance recipes, Create processing machines and AE2 CPU autocrafting jobs remain outside scope. Supply already-made machine products as ingredients. The ME adapter never requests craftable-only patterns or creates patterns. The wireless crafting menu is recognized through the same protocol but is not separately runtime-tested.

Other modded benches, Tom's Storage, Refined Storage and Extended Crafting are not covered. Crafting Station quantities are limited to what its native slots expose; oversized chest/drawer stocks may be capped. Beta 4 handles material revealed during transfer from these capped slots, fixing the filled-grid stall reproduced with a Sophisticated Storage stack upgrade. Simultaneous external storage changes can stop verification and require reopening the interface. Keep the native menu and its power/storage links available during crafting.

Beta 5 also keeps the station's displayed side inventories in its native server-backed cache. This prevents delayed client chest updates from overwriting the menu's post-transfer quantities. It does not add a delay or skip confirmation, and actual concurrent changes to the server's stored items can still halt a job.

Beta 6 verifies the exact items involved in each operation. An unused toolbox changing metadata, or another unrelated storage item arriving/leaving, no longer blocks that operation. Ingredients, output, returned items and reusable tools remain component-sensitive and require exact confirmed quantities. Concurrent changes to those involved items can still halt the job. Missing-material planning continues to use the full current accessible inventory.

Full-pack launch and the listed test recipes are evidence for those paths, not a claim that every mod, recipe or UI combination has been tested. The pack emits unrelated viewer/indexing/mixin warnings; the addon does not alter them. Historical sample and dedicated-server results remain in [the beta 2 report](TEST-REPORT-beta2.md) and [the beta 1 report](TEST-REPORT-beta1.md).

## Reproduce the storage profile

With Java 21, from this project:

```sh
./gradlew build
./gradlew -Pintegration -PtoolFixtures -PtestPace=0 \
  '-PstorageModsDirectory=/absolute/path/to/the/pack/minecraft/mods' runClient
```

The optional Gradle profile loads the installed station, Ars, AE2 and required companion JARs. It creates disposable worlds in `run-storage`. Read `runtime-tests-<mode>.txt` for failures; a clean process exit alone is not proof that assertions passed. Do not run the test harness against a personal world or a public server.
