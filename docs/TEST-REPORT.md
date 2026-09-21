# Testing

## Release verification

Version **2.0.0-beta.10** uses Minecraft 1.21.1, NeoForge 21.1.249, Java 21, EMI 1.1.24+1.21.1, AE2 19.2.17 and Ars Nouveau 5.13.1.

- **57 unit tests passed.** New cases cover player ingredients split across repeated grid slots, partial and uneven grids, insufficient balanced supply, and 16-item ingredient stack limits.
- **25 integrated-server gameplay scenarios passed**, loading the packaged JAR. They include player-only and mixed AE2 inputs, partial grids, stack limits, the 200-housing chain, complete-cell inventory capacity, returned buckets, single-step operation, shortages, and the Crafting Station upgraded-stack regressions.
- **10 dedicated-server gameplay scenarios passed with 600 ms added RTT**, including the large chains, player/network ingredient distribution, partial and uneven grids, grid clearing, returned buckets and single-step mode.
- Complete 1k ME storage cells have a stack limit of one. A request for 200 stops safely when player space runs out: 26 cells at AE2 and 27 at the lectern in these fixtures. Server-side accounting verifies all remaining ingredients/intermediates, an empty cursor and no dropped items. This is an expected capacity stop, not a completed 200-cell craft.
- Test launches set master volume to zero before Minecraft starts. Dedicated-server fixtures arrange materials and check conservation through local files; production crafting still uses only normal Minecraft and native storage-mod packets.

The complete-cell fixture starts with 1,400 redstone, 800 certus quartz crystals, 200 logic processors, 400 iron, 200 copper, 500 certus quartz dust and 400 glass. The 200-housing benchmark starts with 600 redstone, 400 iron, 200 copper, 500 certus quartz dust and 400 glass; it makes 400 quartz glass and 200 housings, consuming every supplied ingredient. Machine-made inputs are supplied, not automated.

The same recipe chain was also measured on a separate local dedicated server with **600 ms added round-trip latency**, without server EMI or the production addon:

| Interface | Release | 200 housings | Craft confirmations |
| --- | --- | ---: | ---: |
| AE2 crafting terminal | beta 9 | 467.005 s | 207 |
| AE2 crafting terminal | beta 10 | 11.487 s | 11 |
| Bookwyrm lectern | beta 9 | 9.817 s | 11 |
| Bookwyrm lectern | beta 10 | 9.800 s | 11 |

Both versions produced exactly 200 housings and consumed the finite ingredient supply without drops. Each chain performs 300 recipe executions: 100 quartz-glass crafts and 200 housing crafts. Measurements cover the active crafting job, excluding game startup and fixture setup. These are single local runs with artificial latency, not a performance guarantee for a public server or the complete modpack. The dedicated server logged no tick-overload warnings; other server plugins, recipe changes, jitter and low TPS remain outside this benchmark.

Release SHA-256: `dfb03448d11be98c919a71df866a51e3d08b23952b06f33ee29f437eff33a76b`. The JAR contains 45 production classes and no test or third-party mod classes.

## Previous release: beta 9

Version **2.0.0-beta.9** was tested with Java 21, Minecraft 1.21.1, NeoForge 21.1.249 and EMI 1.1.24+1.21.1 for NeoForge.

- **51 unit tests passed**, including exact requested amounts, multi-output stack limits, reserved materials, uneven grid stacks and very large stored quantities.
- **52 gameplay scenarios passed their expected checks** across storage, controls/tools and isolated Quark profiles, including the documented compass stop case. Existing outputs reduced a twenty-chest request to thirteen new chests in one confirmation.
- Lectern and AE2 runs each crafted exactly 20 chests in one confirmed craft operation. A 192-plank job used three craft confirmations instead of the previous 48. These are operation counts, not wall-clock speed ratios; latency and server load still affect elapsed time.
- Nine occupied grid slots were retained in one CLEAR_GRID operation. Matching prefilled grids were reused. Full inventories stopped before moving materials; uneven ingredient stacks completed with the expected leftovers.
- Three cakes returned all nine buckets, one execution at a time. Single-step mode produced one execution even with enough materials for twenty.
- Existing Crafting Station/Sophisticated stack-upgrade cases and supported Quark recipes passed. A native station-opening slot-count error occurred once during combined fixture setup, before an addon craft; the affected station case passed in a fresh world.
- Quark's post-craft compass component changes still cause a verification timeout with the crafted item retained. This was reproduced against the original beta 8 JAR and checked as an expected stop-without-retry case in beta 9. The deterministic component fixture passes without Quark.

Tests load the packaged JAR in disposable integrated-server worlds, with EMI's `onServer` flag forced false. They exercise client-only transfers, but do not establish dedicated-server-without-EMI compatibility or cover every mod combination. The release contains 43 production classes and no test or third-party mod classes.

Release SHA-256: `f5d7a3f41a23b9444e6e293a995fbb719169c6ca3adcf8f2991c2870880ca26a`.

## Previous release: beta 8

Version **2.0.0-beta.8** was tested with Java 21, Minecraft 1.21.1, NeoForge 21.1.249 and EMI 1.1.24+1.21.1 for NeoForge.

- **44 unit tests passed**, including bounded reconciliation when an oversized stack crosses below its displayed limit.
- **17 gameplay scenarios passed** using the packaged release JAR and client-only transfers: nine station regressions and eight compatibility cases, with 39 total assertions including startup, UI and item conservation.
- Upgraded-stack cases crafted eight chests from 70 planks, eight from 122 planks (crossing the cap midway), and sixteen from two stacks of 70. Server-side conservation checks confirmed the expected remaining planks and chest totals.
- Existing oversized-stack handling, hidden source slots, unrelated item metadata changes, a 192-plank batch, and returned cake buckets passed. A delayed client block-entity update was injected in the oversized-stack case.
- Quark mixed-material and exclusion recipes passed, with invalid single-wood input rejected. Factory Manager crafting completed through both Crafting Station and the Bookwyrm lectern using connected storage. AE2 recursive crafting and tree preference diagnostics also passed.

The new boundary and multiple-stack cases failed against beta 7 with all inputs still present. The failures were displayed-count mismatches, not material loss. Beta 8 keeps a source occupied when returning leftover ingredients and permits only bounded material revealed by the exact withdrawals.

The tests use disposable integrated-server worlds with EMI's `onServer` flag forced false. They do not establish dedicated-server-without-EMI compatibility or cover every storage upgrade and mod combination.

Release SHA-256: `6a383cf1d87c8bdf890e11e276f754ea3c1422b8ea45f670b8033e93275797cd`.

## Previous release: beta 7

Version **2.0.0-beta.7** was tested with Java 21, Minecraft 1.21.1, NeoForge 21.1.249 and EMI 1.1.24+1.21.1 for NeoForge.

- **43 unit tests passed**, including preservation of the first blocked dependency and use of supplied intermediates.
- **21 gameplay scenarios passed** across the packaged compatibility and KubeJS runs, with 34 total assertions including startup, UI and item-conservation checks.
- Quark mixed-wood chests and an exclusion-wrapped glass recipe completed. Invalid single-wood combinations stopped before consuming ingredients.
- A Factory Manager completed both at a vanilla table and through a Bookwyrm lectern using linked storage. Crafting Station with Sophisticated Storage and AE2 recursive crafting also passed.
- Tree diagnostics correctly identified a smelting step, accepted supplied stone, respected removal of its global preference, and identified a local tree choice that overrode that removal.
- Recursive crafting, returned buckets, component-sensitive alternatives, player crafting, cancellation and occupied-grid/cursor checks passed. Plain KubeJS crafting completed; scripted actions and output modifiers were rejected without consuming inputs.

These tests loaded the release JAR in disposable worlds on an integrated server with EMI's `onServer` flag forced false. They exercise client-only transfers, but do not establish dedicated-server-without-EMI compatibility or cover every mod combination. The blocked-recipe screen was also visually checked for wrapping and accessible controls.

Release SHA-256: `a891535dbb651f700286c2f5e95c32b8660b18d5cfde9824b4061e9fd18fec8b`.

## Previous release: beta 6

Version **2.0.0-beta.6** was tested with Java 21, Minecraft 1.21.1, NeoForge 21.1.249 and EMI 1.1.24+1.21.1 for NeoForge.

- **42 unit tests passed**, covering planning, quantities, controls, scheduling, capped storage and operation-scoped stock checks.
- **35 packaged-runtime assertions passed** across 19 scenarios plus startup/UI checks. Assertions include separate state and item-conservation checks within a scenario.
- The gameplay harness loaded the release JAR, with production classes excluded from development mod paths. Test fixtures and optional mod classes are absent from the release artifact.

Runtime coverage includes recursive crafting, returned buckets, component-sensitive outputs, reusable and damageable tools, missing materials, cancellation, and rejected crafts that time out without retrying. Storage scenarios cover Crafting Station with Sophisticated chests, Bookwyrm lecterns and AE2 terminals.

Crafting Station regressions cover hidden source slots, oversized stacks, delayed client storage updates and metadata changes to unrelated items. The unrelated-item regression reproduces the earlier output-pickup stall without relying on a specific third-party item.

The latest storage run used an integrated server with EMI's `onServer` flag forced false to exercise client-only transfer. EMI remained installed on that server; this is not a dedicated-server-without-EMI test. Coverage does not extend to every mod combination or custom server synchronization protocol.

Release SHA-256: `c97ce58b4e81a3cda6497cf092a783e55a5f91a0ea0a0ff714c4f49bb6543b81`.

The repository cleanup was checked with a fresh build and the standalone KubeJS profile. All three recipe cases passed: plain shapeless crafting completed, and both scripted-action cases stopped without consuming inputs. The release JAR hash remained unchanged.

## Unit tests and development client

```sh
./gradlew build
./gradlew runClient
```

JUnit reports are generated under `build/reports/tests/test`. Build output and runtime logs are not committed.

Machine-specific profiles and archived fixtures can be kept in `.local-testing/`, which is excluded from Git.

## Gameplay harness

The opt-in harness creates disposable worlds and is excluded from release artifacts. Do not run it in an existing world or against a public server.

```sh
./gradlew -Pintegration -PtoolFixtures runClient
./gradlew -Pintegration -PkubejsFixtures runClient
```

`toolFixtures` adds reusable/damageable test items. `kubejsFixtures` loads KubeJS 2101.7.2-build.374 and isolated recipes that accept plain wrappers and reject scripted actions/output modifiers.

To test storage integrations, supply a directory containing the compatible mods and their dependencies:

```sh
./gradlew -Pintegration -PtoolFixtures -PclientFillOnly -PpackagedTest \
  -PstorageModsDirectory=/path/to/integration-mods \
  -PtestMode=storage -PtestPace=0 build runClient
```

Use `-PtestFilter=name1,name2` to select scenarios, `-PstationOnly` to load only Crafting Station and Sophisticated Storage, or `-PstaleStorageUpdate` to reproduce delayed client inventory updates. Read `runtime-tests-<mode>.txt` in the run directory; a successful process exit alone does not establish a gameplay pass.

The stack-upgrade regressions are `storage_station_sophisticated_cap_boundary`, `storage_station_sophisticated_cap_midbatch`, and `storage_station_sophisticated_cap_multiple`. They use real stack upgrades and verify total item conservation on the test server.

For Quark recipe tests, supply Quark, Zeta and Super Factory Manager with `-PquarkModsDirectory`. Combine it with the storage profile to exercise the same recipe through a lectern:

```sh
./gradlew -Pintegration -PpackagedTest -PclientFillOnly \
  -PquarkModsDirectory=/path/to/integration-mods \
  -PstorageModsDirectory=/path/to/integration-mods \
  -PtestFilter=quark_mixed_chests,quark_same_wood,quark_factory_manager,quark_exclusion_glass,storage_lectern_quark_manager \
  build runClient
```

For dedicated-server tests, run a local fixture server with `-PserverOnly`, optionally `-PserverWithoutEmi`, and a separate directory selected by `-PserverDir`. Connect the integration client using `-PtestMode=dedicated-emi` or `dedicated-no-emi`. The harness expects a local offline server, the fixture datapack, and operator access for `AutocraftTest`. `scripts/latency-proxy.py` adds 300 ms each way between local ports 25566 and 25565.

## Dedicated storage benchmark setup

Use a separate local server directory with `server-ip=127.0.0.1`, an offline `AutocraftTest` operator, and `level-name=Autocrafting verification`. The fixture mod refuses other world/player names or a non-loopback bind. It is excluded from the release JAR. Keep this environment separate from personal worlds and public servers.

```sh
./gradlew -Pintegration -PserverOnly -PserverFixtures -PserverWithoutEmi \
  -PserverDir=/path/to/disposable-server \
  -PstorageModsDirectory=/path/to/integration-mods runServer

python3 scripts/latency-proxy.py --delay-ms 300

./gradlew -Pintegration -PpackagedTest \
  -PstorageModsDirectory=/path/to/integration-mods \
  -PtestMode=dedicated-benchmark -PtestPort=25566 -PtestPace=0 \
  -PtestFilter=storage_ae2_housings200,storage_lectern_housings200 runClient
```

The proxy adds 300 ms in each direction while preserving packet order and throughput. Server and client share a local fixture exchange directory; override it with `-PfixtureDirectory`. Use `-PclientDir` for an isolated client directory and `-PtestAddonJar=/absolute/path/to/release.jar` to compare an older packaged release. Neither EMI nor the production addon is installed on the dedicated fixture server. Runtime reports include elapsed craft time and craft-confirmation count; inspect all scenario results rather than relying on Gradle's exit status.
