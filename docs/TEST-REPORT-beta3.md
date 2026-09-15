# Verification report — 2.0.0-beta.3

14 September 2026, Linux, Java 21, Minecraft 1.21.1, NeoForge 21.1.249 and EMI 1.1.24+1.21.1+neoforge.

## Results

| Check | Result | Evidence |
|---|---|---|
| Final build and JUnit | 32 passed; no failures | [Build](test-evidence/build-beta3.txt), [JUnit XML](test-evidence/beta3-junit/) |
| Production artifact | Java 21; no test fixtures or optional mod classes bundled | [Artifact and SHA256](test-evidence/artifact-check-beta3.txt) |
| Isolated storage and baseline gameplay | 50 passing assertions; no failures | [Runtime report](test-evidence/storage-regression-beta3.txt) |
| Final remainder/shortage follow-up | 17 passing assertions; no failures; production class bytes match the installed JAR | [Runtime report](test-evidence/storage-final-beta3.txt) |
| Speed comparison | 128 planks: 199 → 34 client ticks | [Benchmark](test-evidence/speed-comparison-beta3.txt) |
| Full Prism pack | Title screen reached with 723 mods; gameplay verification incomplete | See below |

The storage runtime loads the exact installed Crafting Station 2.1.1, Ars Nouveau 5.13.1 and AE2 19.2.17 JARs, plus their required companion mods. Real integrated-server inventories and native transfer protocols are used. The test harness is excluded from the release JAR.

The 50 assertions include startup, text-focus/quantity-dialog/grouped-panel checks, the existing vanilla/controller/tool cases, a speed case, two assertions for each of 12 storage scenarios (job outcome and item conservation), recipe reload and disconnect. These are assertions, not 50 distinct features. The final follow-up checks run the same production class bytes as the installed JAR; its 17 assertions overlap startup/shortage coverage and add three storage cake recipes, each producing exactly one cake and retaining all three buckets. The shortage UI contents are checked for both names and exact quantities.

## Storage and UI coverage

- Crafting Station and Bookwyrm fixtures use two real linked chests. Their two logs start entirely outside the player inventory. The recursive chain ends with one wooden pickaxe, three planks and two sticks.
- AE2 uses a powered ME crafting terminal, drive and finite 1k item cell with the same seed stock. No creative item supply or CPU autocrafting is used.
- Each menu converts 48 external logs to 192 planks in exactly three player stacks, checking item conservation. Measured ticks were 143 for Crafting Station, 151 for the lectern and 97 for AE2; native protocols require separate confirmed transfer/crafting operations.
- One-log shortages retain the existing log and produce no partial chain. A two-shortage fixture supplies its log through storage but lacks two diamonds and three redstone dust. The missing-material screen opens for each menu.
- Grouping checks preserve a sentinel ordinary favourite, remove duplicate synthetic tree entries and collapse/expand without changing the job state. EMI hides the panel when its target is complete. Screenshots are captured from Minecraft's rendered framebuffer: [grouped batch](test-evidence/grouped-batch-beta3.png), [two missing materials](test-evidence/missing-items-beta3.png), [AE2 cake and returned buckets](test-evidence/ae2-remainders-beta3.png).
- Baseline regressions include inventory/cursor/grid guards, component-sensitive output and alternatives, returned buckets, reusable/damageable tools, single-step, cancellation, recipe reload, disconnect and a server-rejected operation that times out without retrying.
- Unit tests cover quantities, overflow, planner reservations/alternatives, controls and scheduler confirmations. Zero extra pacing still dispatches at most one operation per tick and waits while a server result is pending.

The 5.85× speed comparison uses the same 32-execution vanilla-plank request in an isolated runtime. At 20 ticks/second the observed times are 9.95s versus 1.70s. Actual speed depends on the menu, server latency and pack load.

## Full pack and installation

A separate Prism clone of **Impostor Syndrome – Reimagined 0.6-hotfix** loaded the packaged beta 3 and reached the title screen with 723 loaded mods. Its test-world load then failed while Runelic read its JSON config during datapack loading; the log reported EOF, although the config parsed successfully afterward. The clone was stopped when the user elected to test manually. This is not a completed full-pack gameplay pass, and it is not evidence that the addon caused the config-read failure.

The final beta 3 JAR was installed into the actual **Impostor Syndrome – Reimagined** instance alongside its existing EMI. The previous beta 2 JAR and config were backed up under the instance's `minecraft/autocrafting-backups` directory. The installed SHA256 matches the artifact record. Its config enables `pacingTicks=0` and `groupCraftingJob=true`. No test harness or personal saves were installed/modified there.

## Limits

Only normal shaped/shapeless grid crafting and known plain KubeJS wrappers are supported. Machine execution, scripted recipe actions/results, arbitrary custom benches, AE2 CPU jobs/pattern creation and craftable-only stock remain unsupported. The wireless crafting menu is recognized but has not been separately runtime-tested. Crafting Station counts quantities its native slots expose; oversized drawer stocks may be capped. External concurrent storage changes can halt verification.

Storage tests use EMI on the integrated server. The older dedicated-server matrices with/without server EMI are historical beta 1 evidence and were not repeated here. All GUI scales, every pack recipe, every storage device and every component combination have not been exhaustively checked.

Historical results remain in [beta 2](TEST-REPORT-beta2.md) and [beta 1](TEST-REPORT-beta1.md). Reproduction commands are in [pack compatibility](PACK-COMPATIBILITY.md).
