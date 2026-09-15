# Verification report — 2.0.0-beta.5

14 September 2026, Linux, Java 21, Minecraft 1.21.1, NeoForge 21.1.249 and EMI 1.1.24+1.21.1+neoforge.

## Observed problem and reproduction

After installing beta 4, the user still saw recipes fill without output pickup on their multiplayer server. With diagnostics enabled, repeated attempts stopped at `Server snapshot differs from displayed slot 379 (SideContainerSlot)`; another attempt named slot 383. The server had supplied a full menu snapshot. The failure occurred during FILL verification, before a CRAFT/output-pickup operation was sent. Changing the delay between confirmed crafts would not address this particular wait.

Inspection of the installed Crafting Station 2.1.1 implementation showed two client inventory paths: a native menu display cache populated by server slot packets, and live client block-entity inventories. The latter can change independently when block-entity data arrives.

A disposable regression injects a delayed client chest inventory refresh after filling a recipe. It restores the pre-transfer log count only in the client block entity, leaving the real server inventory untouched. Beta 4 then reports the same side-slot mismatch, never collects the result, and times out. [Reproduction evidence](test-evidence/station-stale-reproduction-beta4.txt). This reproduces the observed failure mode; it does not establish the exact packet sequence on the user's server.

Beta 5 selects the station's existing display cache on the client for directions declared by the server's menu-opening packet. Separately arriving block-entity data can no longer overwrite the menu's displayed transfer state. The server still uses its real inventory handlers. Native click prediction and vanilla/custom menu slot updates remain in use. Pacing, full-snapshot checks and item-conservation requirements are unchanged.

Timeout messages now retain the specific wait reason. Slot-mismatch diagnostics include bounded item IDs/counts rather than inventory/component dumps.

## Results

| Check | Result | Evidence |
|---|---|---|
| Build and JUnit | 37 passed; no failures, errors or skips | [Build](test-evidence/build-beta5.txt), [JUnit XML](test-evidence/beta5-junit/) |
| Delayed-refresh and station gameplay | 15 passing assertions | [Station report](test-evidence/station-cache-fix-beta5.txt) |
| Hidden slots, shortage, Bookwyrm and AE2 | 13 passing assertions | [Compatibility report](test-evidence/storage-cache-compatibility-beta5.txt) |
| Optional storage mods absent | 8 passing assertions, including rejected-craft timeout | [Baseline report](test-evidence/optional-absent-beta5.txt) |
| Production artifact | Java 21; production class bytes match the runtime; test fixtures and optional mod classes excluded | [Artifact and SHA256](test-evidence/artifact-check-beta5.txt) |
| Actual Prism installation | Beta 5 installed; hash matches the tested artifact | [Installation record](test-evidence/installation-beta5.txt) |
| User's multiplayer server | Retest pending | Not covered by the isolated fixtures |

The station regression completes a recursive pickaxe chain despite the injected stale client refresh, retaining exactly one pickaxe, three planks and two sticks from two stored logs. Other cases verify the earlier oversized-stack fix (128 logs become 127 logs plus four collected planks), ordinary adjacent chests, a 48-log batch producing 192 planks in three player stacks, and a cake returning all three buckets.

The compatibility run uses a netherite Sophisticated chest with its two logs in slot 100, outside the visible 54-slot window. The logs are found and used correctly. The one-log shortage retains its log without a partial craft. Bookwyrm and AE2 each complete the recursive chain with exact stock conservation. Grouping, text focus, quantity selection and disconnect checks also pass. Counts above are assertions, with shared startup/UI coverage, rather than distinct feature totals.

These runtime tests use real inventories in an integrated server. EMI's `onServer` flag is forced false to exercise client-only transfer. EMI is still installed in the integrated server; these are not dedicated-server-without-EMI results. No new speed claim is made. The wireless AE2 menu and arbitrary custom recipes remain outside this verification.

## Installation

The actual **Impostor Syndrome – Reimagined 0.6-hotfix** instance now contains beta 5 alongside its unchanged EMI. Minecraft was closed before replacement. Beta 4 and the previous config were backed up under `minecraft/autocrafting-backups/beta5-20260914-180411`. Exactly one addon JAR is active; no test harness was installed. Pacing, grouping and shortcuts were preserved, with diagnostic logging enabled for the next server retry. The user's actual server still needs a manual retest; commit/push remains pending that result.

## Reproduction commands

With Java 21 and the installed pack's mods available:

```sh
./gradlew --no-daemon -Pintegration -PclientFillOnly -PstationOnly -PstaleStorageUpdate \
  '-PstorageModsDirectory=/absolute/path/to/the/pack/minecraft/mods' \
  -PtestMode=station-cache-fix -PtestPace=0 \
  -PtestFilter=storage_station_sophisticated_chain,storage_station_sophisticated_oversized,storage_station_chain,storage_station_batch,storage_station_buckets,disconnect \
  build runClient

./gradlew --no-daemon -Pintegration -PclientFillOnly \
  '-PstorageModsDirectory=/absolute/path/to/the/pack/minecraft/mods' \
  -PtestMode=storage-cache-compatibility -PtestPace=0 \
  -PtestFilter=storage_station_sophisticated_hidden_chain,storage_lectern_chain,storage_ae2_chain,storage_station_missing,disconnect \
  runClient

./gradlew --no-daemon -Pintegration -PclientFillOnly \
  -PtestMode=optional-absent-beta5 -PtestPace=0 \
  -PtestFilter=logs_to_pickaxe,returned_buckets,server_rejects,disconnect runClient
```

Read the `runtime-tests-<mode>.txt` reports in `run-storage` or `run`; a successful process exit alone does not establish a gameplay pass. The stale-refresh fixture requires the disposable integrated verification world and is excluded from the release JAR. Never run the harness against personal worlds or public servers.

Supported interfaces, capped-stock planning limits and actual concurrent server inventory changes remain subject to [pack compatibility](PACK-COMPATIBILITY.md). Historical reports: [beta 4](TEST-REPORT-beta4.md), [beta 3](TEST-REPORT-beta3.md), [beta 2](TEST-REPORT-beta2.md), [beta 1](TEST-REPORT-beta1.md).
