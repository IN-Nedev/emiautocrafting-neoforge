# Verification report — 2.0.0-beta.6

14 September 2026, Linux, Java 21, Minecraft 1.21.1, NeoForge 21.1.249 and EMI 1.1.24+1.21.1+neoforge.

## Failure and fix

The user's beta 5 screenshot and log show a filled Create cogwheel recipe waiting on `Stored quantity differs for Pink Toolbox: expected 1, got 0`. That toolbox is unrelated to the recipe. The verifier compared every exact item/component key in the connected inventories, so an unused item's metadata change, arrival or departure could block output pickup. A changed component variant appears as loss of the old key even if the physical item remains present.

A controlled reproduction ran the **installed beta 5 JAR**, with production source classes excluded from the runtime. A native server menu listener changes the custom data of an unused compass in a Sophisticated chest when the grid/result changes. Beta 5 fills the recipe, reports `Compass: expected 1, got 0`, and times out. The final server inventory still contains the compass, with updated metadata, and all two logs; no planks were collected. [Reproduction](test-evidence/background-reproduction-beta5.txt). This verifies the failure mechanism with a generic component update; the exact component change on the user's Pink Toolbox was not captured.

Beta 6 gives each operation a `StockScope` containing its exact ingredients, output and remainders. Reusable ingredients are included even when their expected net change is zero. CLEAR verifies its moved item. A fresh server snapshot, empty cursor, correct grid/result, relevant slot agreement and exact scoped quantities are still required. Unrelated inventory changes are rebased before the next operation; subsequent planning uses the full current accessible stock. The earlier bounded capped-input reconciliation operates within the same scope.

No item-component matching, pacing or crafting timeout has been loosened for the items involved in the operation. Actual changes to those ingredients/outputs can still prevent confirmation.

## Results

| Check | Result | Evidence |
|---|---|---|
| Build and JUnit | 42 passed; no failures, errors or skips | [Build](test-evidence/build-beta6.txt), [JUnit XML](test-evidence/beta6-junit/) |
| Packaged beta 6 gameplay | 35 passing assertions across 19 scenarios plus startup/UI checks; no failures | [Runtime report](test-evidence/scoped-stock-beta6.txt) |
| Release artifact | Java 21; runtime loads the packaged JAR; test fixtures and optional mod classes excluded | [Artifact and SHA256](test-evidence/artifact-check-beta6.txt) |
| Actual Prism installation | Beta 6 installed; hash verified; EMI and config unchanged | [Installation](test-evidence/installation-beta6.txt) |
| User's multiplayer server | No independently captured successful beta 6 craft | Not covered by the isolated fixture; user subsequently approved publication |

The background-metadata chain now completes with exactly one pickaxe, three planks and two sticks, while the unused compass remains present through 31 metadata revisions. A combined capped-stock/background-update case converts 128 logs into 127 logs and four collected planks, retaining the compass.

The packaged regression also passes:

- Sophisticated chest ingredients in hidden slot 100; ordinary recursive crafting; a 48-log station batch yielding exactly 192 planks.
- Missing-material and multiple-shortage cases that retain existing inputs and show their quantities.
- Crafting Station, Bookwyrm and AE2 cake recipes retaining all three buckets, and Bookwyrm/AE2 recursive chains.
- Component-sensitive output/alternatives, reusable tools, damageable tools that stop when exhausted, four-input shapeless player crafting and disconnect cancellation.
- A server-rejected craft that produces nothing, times out and is not retried automatically.

Five new unit tests cover unused metadata changes, unrelated stock arrivals/departures, missing outputs/unconsumed inputs, reusable ingredient loss, and bounded capped-input reconciliation combined with an unrelated component change. The remaining 37 tests retain planner, quantity, control and scheduler coverage. Runtime assertion counts include overlapping startup/UI and separate state/conservation assertions, not 35 independent features.

Runtime tests use real storage and an integrated server, with EMI's `onServer` flag forced false to exercise client-only transfer. EMI is still installed in that integrated server. This is not a dedicated-server-without-EMI result or a full Reimagined gameplay pass. No new speed benchmark is claimed.

## Installation

The user had closed Minecraft. Beta 6 replaced beta 5 in the actual **Impostor Syndrome – Reimagined 0.6-hotfix** instance. Beta 5 and the config were backed up under `minecraft/autocrafting-backups/beta6-20260914-201700`. Exactly one addon JAR is active; no runtime harness was installed. EMI and the addon config are byte-for-byte unchanged, retaining zero extra pacing, grouped batches, existing shortcuts and diagnostic logging for the server retest.

SHA256: `c97ce58b4e81a3cda6497cf092a783e55a5f91a0ea0a0ff714c4f49bb6543b81`.

The user approved publication on 15 September 2026. This approval does not expand the measured runtime coverage above. Supported recipe/menu and capped-stock planning boundaries remain documented in [pack compatibility](PACK-COMPATIBILITY.md).

## Reproduce

With Java 21 and the installed pack's mods available:

```sh
./gradlew --no-daemon -Pintegration -PtoolFixtures -PclientFillOnly -PpackagedTest \
  '-PstorageModsDirectory=/absolute/path/to/the/pack/minecraft/mods' \
  -PtestMode=scoped-stock-beta6 -PtestPace=0 \
  -PtestFilter=storage_station_sophisticated_background_chain,storage_station_sophisticated_background_oversized,storage_station_sophisticated_hidden_chain,logs_to_pickaxe,returned_buckets,modded_components_alternative,reusable_tool,damageable_tool_breaks,player_four_input_shapeless,server_rejects,storage_station_buckets,storage_station_batch,storage_lectern_chain,storage_lectern_buckets,storage_ae2_chain,storage_ae2_buckets,storage_station_missing,storage_station_multi_missing,disconnect \
  build runClient
```

The earlier failure can be reproduced with `-PpackagedTest -PtestAddonJar=/absolute/path/to/emiautocrafting-neoforge-1.21.1-2.0.0-beta.5.jar`, `-PstationOnly`, and only the `storage_station_sophisticated_background_chain` filter. For this environment, the optional early-loading window was disabled in the disposable `run-packaged/config/fml.toml` after a window-handoff failure; the production pack's setting was not changed.

Read `run-packaged/runtime-tests-<mode>.txt`; process exit alone does not establish a gameplay pass. The harness is confined to disposable verification worlds and excluded from release artifacts. Do not use it on personal worlds or public servers.

Historical reports: [beta 5](TEST-REPORT-beta5.md), [beta 4](TEST-REPORT-beta4.md), [beta 3](TEST-REPORT-beta3.md), [beta 2](TEST-REPORT-beta2.md), [beta 1](TEST-REPORT-beta1.md).
