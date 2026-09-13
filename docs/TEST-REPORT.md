# Verification report — 2.0.0-beta.1

Tests were performed on 13 September 2026 on macOS, using Minecraft 1.21.1, NeoForge 21.1.250, Java 21.0.12.1+1 and EMI's published NeoForge 1.1.24+1.21.1 artifact. Gradle 9.2.1 and ModDevGradle 2.0.147 built Mojang-mapped Java 21 classes.

## Results and evidence

| Check | Result | Evidence |
|---|---|---|
| Release build and JUnit | **BUILD SUCCESSFUL; 31 tests passed** | [build.txt](test-evidence/build.txt), JUnit XML |
| Packaged JAR | **Passed metadata, bytecode and content checks** | [artifact-check.txt](test-evidence/artifact-check.txt) |
| Singleplayer with deterministic tool fixtures | **21 passed** | [integrated-tools.txt](test-evidence/integrated-tools.txt) |
| Dedicated server with EMI, addon absent | **16 passed** | [dedicated-emi.txt](test-evidence/dedicated-emi.txt) |
| Dedicated server without EMI or addon, 600 ms added RTT | **18 passed** | [dedicated-no-emi-latency.txt](test-evidence/dedicated-no-emi-latency.txt) |
| Final dedicated EMI regression, reload and disconnect | **6 distinct checks passed** | [dedicated-final.txt](test-evidence/dedicated-final.txt) |

These are overlapping checks across environments, not distinct feature counts. The raw final evidence repeats the disconnect PASS while the client shuts down; it represents one check, not seven. The delivered harness adds a terminal guard to suppress duplicate reporting; that test-only change was [compiled successfully](test-evidence/harness-compile.txt). The in-game harness runs an actual rendered Minecraft client and an actual integrated/dedicated server. It uses server commands to arrange disposable fixtures, creates EMI trees through the real API, opens real menus and invokes the addon. Production execution then uses EMI filling and vanilla network/menu operations. Assertions inspect the real resulting inventory and controller state. The addon itself waits for received server menu snapshots before counting progress.

The runtime runs used development classes from this source project rather than installing the packaged JAR in a separate launcher profile. The final artifact is separately checked for metadata, Java version, required mixins and exclusion of test code.

The full dedicated EMI matrix predates the final sibling-allocation refinement and four-input shapeless fix. The subsequent latency and singleplayer runs exercise the refined planner; the singleplayer run exercises the shapeless fix and tool fixtures. The final dedicated regression checks the resulting implementation on the server-assisted path. Tool fixtures are confined to the integrated test world; they are absent from both dedicated servers and the release JAR.

## Coverage

| Requirement | Verified behaviour |
|---|---|
| Recursive tree | Two logs become planks, sticks and one wooden pickaxe in one job. |
| Existing intermediates/finals | Existing three planks/two sticks skip intermediate production; one existing pickaxe counts toward a total of two. |
| Multi-output rounding | A total of five sticks results in eight legitimate output items. |
| Shared stock and alternatives | Unit tests reject double allocation, preserve reservations and reassign overlapping alternatives. Runtime datapack crafting accepts birch planks in a planks tag. |
| Components | A datapack recipe produces a named/custom-data compass. The later dedicated and singleplayer tests start with a plain compass; the named target is still crafted, leaving two compasses. Production preflight and confirmation compare exact components. |
| Missing/unsupported dependencies | Missing logs block with quantity. Unit tests use existing machine outputs and block only missing unsupported production. |
| Returned containers | Cake retains at least three empty buckets across inventory/grid. |
| Reusable tool | Four executions consume four iron ingots and retain exactly one test stamp. |
| Damage and breakage | A durability-three test hammer produces three outputs, breaks through its actual remainder method, and blocks a target of four with the missing tool. |
| Full inventory | Output-space preflight blocks; no craft is dispatched to make space. |
| Grid/cursor | Existing grid contents or a held cursor stack block predictably; no automated cleanup of player-owned starting inputs is attempted. |
| Single step | Stops after one confirmed recipe execution, retaining four planks. Synchronization is not counted as a craft. |
| Cancellation | Changing the tree or closing the GUI cancels. Server `/reload` and client disconnect also cancelled, as recorded in the final regression. |
| Latency | An ordered TCP proxy adds 300 ms in each direction without intentionally throttling throughput. The whole no-EMI matrix passed through it. |
| Rejection | `doLimitedCrafting=true` plus revoked recipe knowledge prevents the output; the job times out, blocks and does not falsely complete or retry. |
| Cycles/unresolved/huge targets | Unit tests cover repeated recipe paths, depth bounds, unresolved branches, checked overflow, Long.MAX_VALUE arithmetic and clamping before int conversion. These extreme cases were not run as giant live crafting jobs. |
| 2×2/3×3 | Player inventory crafts sticks and a four-input shapeless fixture; a pickaxe step there explains that a 3×3 table is needed. |
| Input/UI | NeoForge key events preserve Ctrl+A/C in a focused EditBox and EMI search. The quantity dialog accepts Enter and retains recipe identity/item-total semantics. Pure binding tests cover modifiers and disabled/invalid bindings. |
| Server presence | Recorded `EmiClient.onServer` is true on the EMI server and false without EMI. Neither dedicated server loads the addon ([server mod lists](test-evidence/server-mod-lists.txt)). No optional storage mods are installed. |

JUnit groups: 5 quantity tests, 8 controller tests, 15 planner tests and 3 binding tests. Controller tests cover one operation in flight, delayed confirmation, pacing, rejection, timeout without retry, cancellation, exceptions and single-step completion.

## Reproduce

Use JDK 21. From the project directory:

```sh
./gradlew build
./gradlew -Pintegration -PtoolFixtures runClient
```

The integration task creates a fresh disposable flat world with commands enabled and exits after its checks. It writes `run/runtime-tests-integrated.txt`. Inspect that file for `FAIL` or `HARNESS ERROR`; a Gradle game-process exit alone does not prove the assertions passed.

For dedicated tests, use a disposable local server directory. Accept the Minecraft EULA, bind to `127.0.0.1:25565`, set `online-mode=false` for the development account, and grant operator access to `AutocraftTest` (offline UUID `c9a5ee66-eb82-3e82-93dc-fceca25aaf32`). Place `src/testMod/resources/data` in `<server>/world/datapacks/autocrafting_test/data` with a `pack.mcmeta` containing `{"pack":{"pack_format":48,"description":"Autocrafting test fixtures"}}`. Do not enable toolFixtures on these clients because their test items are absent on the dedicated server.

```sh
# First terminal, EMI server; wait until it is ready:
./gradlew -PserverOnly -PserverDir=/absolute/disposable-server runServer
# Second terminal:
./gradlew -Pintegration -PtestMode=dedicated-emi runClient

# For the other server path, restart the disposable server with:
./gradlew -PserverOnly -PserverWithoutEmi -PserverDir=/absolute/disposable-server runServer
# Start the local latency proxy in another terminal:
python3 scripts/latency-proxy.py
# Then connect the test client through it:
./gradlew -Pintegration -PtestMode=dedicated-no-emi-latency -PtestPort=25566 runClient
```

Optional `-PtestFilter=logs_to_pickaxe,player_four_input_shapeless,recipe_reload,disconnect` selects the final focused regression. All recipes/scenarios are in `src/testMod`; only the integrated tool fixtures additionally use `src/toolTest`. The harness has gained scenarios during development, so running `all` now includes the late reload/disconnect checks as well as the earlier recorded matrices.

## Limits of this verification

- No physical keyboard/mouse walkthrough of hovering EMI recipes was performed. The selection adapter was source-reviewed; the dialog and text-focus guards were exercised inside the game through APIs/events. Native key-repeat, OS focus loss, visual layout at every GUI scale and every recipe-book text field were not separately tested.
- JEI was not installed; no intended pack requiring both viewers was supplied. Transfer-only JEI handlers are rejected by design, but coexistence is unverified.
- No arbitrary modpack, custom crafting station, storage terminal, machine automation, network-job support or chance/dynamic recipe compatibility is claimed.
- Tool compatibility is demonstrated by deterministic fixture items in ordinary recipes, not every mod's tool or damage implementation.
- The latency proxy tests delay, not packet loss, disordered packets or every server plugin. Vanilla menu-protocol modifications can cause a timeout.
- Inventory totals and retained buckets/tools are asserted; there is no exhaustive world-wide dropped-entity or all-components fuzz test. The preflight refuses insufficient capacity and never intentionally drops items.
- The upstream 1.20.1 addon was inspected but not launched to reproduce its suspected defects. Port findings are source-level findings.

This is a tested beta for the documented vanilla menu/standard recipe boundary, not a claim that arbitrary EMI trees can execute.
