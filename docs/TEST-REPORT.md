# Testing

## Release verification

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
