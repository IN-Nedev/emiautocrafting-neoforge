# Verification report — 2.0.0-beta.4

14 September 2026, Linux, Java 21, Minecraft 1.21.1, NeoForge 21.1.249 and EMI 1.1.24+1.21.1+neoforge.

## Results

| Check | Result | Evidence |
|---|---|---|
| Build and JUnit | 36 tests passed; no failures, errors or skips | [Build](test-evidence/build-beta4.txt), [JUnit XML](test-evidence/beta4-junit/) |
| Production artifact | Java 21; 35 production classes match the gameplay runtime; no test fixtures or optional mod classes bundled | [Artifact and SHA256](test-evidence/artifact-check-beta4.txt) |
| Capped-stack reproduction before the fix | Recipe filled, then timed out before output pickup | [Reproduction](test-evidence/station-cap-reproduction-beta3.txt) |
| Focused gameplay regression after the fix | 13 passing assertions; no failures | [Runtime report](test-evidence/station-cap-fix-beta4.txt) |
| Actual Prism installation | Beta 4 installed; installed SHA256 matches the tested artifact | [Artifact record](test-evidence/artifact-check-beta4.txt) |
| User's multiplayer server | Manual retest pending | Not covered by the isolated runtime |

## Reproduced failure and fix

The user reported that a Crafting Station connected to a Sophisticated chest filled recipes but never collected their output on a multiplayer server. A disposable fixture reproduced that symptom with 128 logs in one Sophisticated Storage slot, within the capacity provided by its tier-one stack upgrade.

Crafting Station exposes at most 64 of those logs through its normal source slot. Moving one log into the crafting grid leaves the source displaying 64, so the addon observes 65 logs in total. Beta 3 treated this as a conservation failure, kept waiting and never dispatched output pickup. The reproduction ended with all 128 logs retained and no planks produced.

Beta 4 accounts for material revealed by an exact verified transfer from a source that was and remains capped. It allows only bounded gains for the exact selected input item/components, then updates the expected stock for the following craft. Inventory losses, unrelated gains, incorrect grids/results and incomplete server snapshots still fail verification. Output pickup and its ingredient/output/remainder accounting remain separately confirmed; a timeout does not retry crafting automatically.

The same valid 128-log fixture now completes, collects exactly four planks and leaves exactly 127 logs in the actual server inventory. This establishes a cause for the reported symptom; confirmation on the user's particular server is still pending.

## Coverage

The focused runtime loads the installed Crafting Station, JEI, Sophisticated Storage and Sophisticated Core JARs. It uses real inventories in an integrated server and forces EMI's `onServer` flag to false to exercise the client-only recipe-transfer path. EMI is still installed in that integrated server; this is not a dedicated-server-without-EMI test.

The 13 passing assertions comprise startup, text-focus and quantity-dialog checks, grouped-panel behavior, paired outcome/conservation assertions for four station scenarios, and disconnect cancellation. The scenarios are:

- A Sophisticated chest recursive chain: two stored logs become one wooden pickaxe, three planks and two sticks.
- A Sophisticated chest with a tier-one stack upgrade: 128 stored logs become 127 logs and four collected planks.
- An ordinary connected-chest chain with the same pickaxe and surplus counts.
- A cake recipe that collects one cake and retains all three returned buckets.

The four new unit tests check capped-stock reconciliation, unchanged ordinary transfers, rejected losses/unrelated gains, and rejected uncapped/excessive gains. The other 32 tests cover planning, quantities, controls and scheduler confirmation behavior.

Broader station, Bookwyrm, AE2, shortage, UI and baseline coverage belongs to [beta 3](TEST-REPORT-beta3.md), including its 50-assertion regression and 17-assertion follow-up. Those broader gameplay suites were not repeated for beta 4. Beta 4 changes station capped-source accounting and synchronization diagnostics. No new speed claim is derived from the focused run.

## Installation and limits

After the user closed Minecraft, the tested JAR was installed in the actual **Impostor Syndrome – Reimagined 0.6-hotfix** instance. Its existing EMI remains installed. Beta 3 and the prior config were backed up under `minecraft/autocrafting-backups/beta4-20260914-173444`. There is exactly one active addon JAR, and no test harness was installed in the main pack. Existing shortcuts, `pacingTicks=0` and `groupCraftingJob=true` were retained; temporary diagnostic logging was restored to false.

SHA256: `f97b83e5e5ba4d1c03dcb627e5e1e643da945f95baf20c7e7fc7dcd815ae1753`.

Beta 4 has not yet received a full-pack gameplay or real multiplayer-server pass. The earlier full Prism clone reached its title screen with beta 3, but its disposable world load did not finish; see the historical report. Commit/push remains pending the user's manual server retest.

Crafting Station planning still sees only quantities exposed by its native slots. The fix handles revealed quantities during transfer; it does not expose every hidden item in oversized storage for initial planning. Simultaneous external storage changes can halt verification. Supported recipes and menus, including the untested wireless-menu and unsupported machine/AE2 CPU boundaries, remain as described in [pack compatibility](PACK-COMPATIBILITY.md).

## Reproduction

With Java 21 and the installed pack's mods available, run:

```sh
./gradlew --no-daemon -Pintegration -PclientFillOnly -PstationOnly \
  '-PstorageModsDirectory=/absolute/path/to/the/pack/minecraft/mods' \
  -PtestMode=station-cap-fix -PtestPace=0 \
  -PtestFilter=storage_station_sophisticated_chain,storage_station_sophisticated_oversized,storage_station_chain,storage_station_buckets,disconnect \
  build runClient
```

Read `run-storage/runtime-tests-station-cap-fix.txt`; a successful process exit alone does not establish a gameplay pass. The harness creates disposable verification worlds and must not be used on personal worlds or public servers.

Historical reports: [beta 3](TEST-REPORT-beta3.md), [beta 2](TEST-REPORT-beta2.md), [beta 1](TEST-REPORT-beta1.md).
