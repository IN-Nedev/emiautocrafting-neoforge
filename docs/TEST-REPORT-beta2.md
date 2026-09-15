# Verification report — 2.0.0-beta.2

Tests were performed on 13 September 2026 on macOS with Minecraft 1.21.1, NeoForge 21.1.249, Java 21.0.12.1+1 and EMI 1.1.24+1.21.1+neoforge. Gradle 9.2.1 and ModDevGradle 2.0.147 produced the distributable JAR.

## Results

| Check | Result | Evidence |
|---|---|---|
| Release build and JUnit | **BUILD SUCCESSFUL; 31 passed** | [Build](test-evidence/build-beta2.txt), [JUnit XML](test-evidence/beta2-junit/) |
| Release artifact | **Passed metadata, content and Java 21 checks** | [Artifact check](test-evidence/artifact-check-beta2.txt) |
| Packaged JAR with the pinned pack crafting sample | **30 passed; zero failures** | [Runtime assertions](test-evidence/packaged-pack-beta2.txt), [loaded versions and recipe classes](test-evidence/pack-runtime-versions-beta2.txt) |
| Packaged JAR without optional pack mods | **9 passed; zero failures** | [Runtime assertions](test-evidence/packaged-vanilla-beta2.txt), [loaded versions](test-evidence/vanilla-runtime-versions-beta2.txt) |

The 30 sample checks comprise one proof that the addon was loaded from its built JAR, two UI/key checks, 17 baseline crafting/controller scenarios, eight pack-specific scenarios, recipe reload and disconnect. The standalone regression adds proof of loading the same JAR without KubeJS/JEI/the optional pack mods, plus recursive crafting, returned buckets, four-input shapeless crafting, reusable/damageable-tool fixtures and disconnect. Overlapping scenarios across runs are not distinct feature counts.

Both profiles use the real rendered client, real integrated server, real inventories and EMI's production filling paths. The development-only harness arranges disposable worlds with commands, prepares EMI trees through its API, posts NeoForge keyboard events to start jobs, and checks the resulting state/items. In the packaged profile the addon's loose development classes are excluded and its code-source path is asserted to be the actual beta 2 JAR. This is a Gradle launch, not a CurseForge launcher profile or physical mouse/keyboard walkthrough.

## Pack-specific coverage

| Scenario | Verified result |
|---|---|
| Modified furnace from base stock | Forty cobblestone and nine coal produce one furnace through compressed blocks and a coal block, preserving the shared cobblestone budget. |
| Existing furnace/intermediates | One existing furnace plus the remaining recipe inputs reaches a total of two. |
| Modified piston chain | One existing piston plus raw planks material, cobblestone, redstone and two andesite alloy reaches a total of three pistons. The shaft recipe yields two shafts from two alloy. |
| Missing Create production | Selecting the mixer recipe blocks missing andesite alloy as an unsupported machine step; no piston is produced. |
| KubeJS ingredient action | A `keepIngredient` fixture is blocked with its iron input still in inventory and the grid empty. |
| KubeJS output script | A named `modifyResult` fixture is blocked before crafting, retaining its iron input and empty grid. |
| Plain KubeJS shapeless wrapper | Crafts successfully in the player 2×2 inventory. Runtime evidence confirms the special KubeJS wrapper class. |
| Crafting Station | Its real menu is rejected before any inventory operation. |

The baseline includes recursive logs → planks → sticks → pickaxe, existing intermediates/finals, multi-output surplus, missing inputs, returned cake buckets, 2×2 and 3×3 limits, component-bearing output and tag alternatives, full inventory, pre-filled grid/cursor, single-step, changed-tree/closed-GUI cancellation, and a server-rejected craft that times out without retry. Recipe reload and disconnect cancel future actions. Start events run with JEI, Crafting Tweaks and Polymorph+ installed; text-field select-all/copy remains intact in the tested EditBox and EMI search.

An earlier development run passed the crafting sample but exposed a harness exit-sequence error: it called `Minecraft.disconnect` without first closing the client level connection, leaving the integrated server running. The harness now follows the vanilla PauseScreen exit sequence; the final packaged run exits normally and includes a single disconnect PASS. This was a test-harness correction. Production also cancels immediately on NeoForge's logout event.

## Reproduce

Use JDK 21 from the source-project directory:

```sh
./gradlew build
./gradlew -Pintegration -PpackCompatibility -PpackagedTest -PtestMode=packaged-pack runClient
./gradlew -Pintegration -PpackagedTest -PtoolFixtures -PtestMode=packaged-vanilla -PtestFilter=logs_to_pickaxe,returned_buckets,player_four_input_shapeless,reusable_tool,damageable_tool_breaks,disconnect runClient
```

The pack sample downloads the exact mod artifacts listed in [pack compatibility](PACK-COMPATIBILITY.md); hashes are in [PACK-DEPENDENCY-SHA256.txt](PACK-DEPENDENCY-SHA256.txt). Its recipe fixtures are independently authored, not a copy of the full pack. No optional mods, fixtures or test classes enter the mod JAR. `packagedTest` asserts the production code is loaded from the JAR. Read `run-packaged/runtime-tests-<mode>.txt` for FAIL/HARNESS ERROR; process exit code alone does not prove success. Never run the harness against an existing personal world or public server.

JUnit groups remain five quantity tests, eight controller tests, fifteen planner tests and three binding tests. They check overflow/rounding, reservations and alternatives, bounds/cycles, delayed/rejected confirmations, pacing, timeout, cancellation, exceptions and one-step completion.

## Limits and historical evidence

- **The full Impostor Syndrome – Reimagined pack was not launched.** Its manifest recommends 14,400 MB RAM; the test Mac has 8 GB. The result applies to the pinned crafting sample and independent recipe fixtures, not all 637 manifest entries or every recipe/script.
- EMI/JEI/Create log duplicate recipe IDs and synthetic toolbox warnings during indexing. The exercised crafting paths pass despite those warnings. Broader viewer UI, Polymorph conflict selection and every optional mod are not certified.
- Use vanilla 3×3 and 2×2 menus. Custom stations, storage terminals and machines have no execution adapters. Chance-based/dynamic recipes and KubeJS ingredient actions/output scripts remain outside scope.
- There was no physical hover/key-repeat walkthrough, all-GUI-scale visual audit, exhaustive dropped-entity/component fuzz test, or separate test of every recipe-book text field.
- The beta 1 dedicated-server matrices verified operation with and without server EMI, including 600 ms added round-trip latency, with the addon absent on both servers. Those network paths are unchanged, but those tests used beta 1 / NeoForge 21.1.250 and were not repeated against this entire modpack. Full historical evidence and reproduction instructions remain in [the beta 1 report](TEST-REPORT-beta1.md).

This is a tested beta within the documented menu and recipe boundary. It cannot execute arbitrary EMI trees.
