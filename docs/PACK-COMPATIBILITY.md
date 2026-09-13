# Impostor Syndrome – Reimagined

## Install beta 2 in your own pack instance

1. Close Minecraft. In the CurseForge app, open your installed pack's menu and choose **Open Folder**.
2. Open that instance's `mods` folder and copy in `emiautocrafting-neoforge-1.21.1-2.0.0-beta.2.jar`. Remove any older emiautocrafting addon JAR so only one version is installed.
3. Add [EMI 1.1.24 for NeoForge 1.21.1](https://modrinth.com/mod/emi/version/1.1.24%2B1.21.1%2Bneoforge), whose filename is `emi-1.1.24+1.21.1+neoforge.jar`, to the same folder. The inspected pack includes JEI but does not include EMI. Keep JEI installed.
4. Launch the pack normally. Its NeoForge 21.1.249 can remain as supplied. The addon and EMI require Minecraft 1.21.1 and Java 21.
5. Open a **vanilla crafting table** with materials in your inventory and an empty grid/cursor. Choose the intermediate recipes with EMI's hearts, hover the target recipe output, press **Ctrl+A**, enter the desired total and then press **Ctrl+C** to start. Press Ctrl+C or Ctrl+X to cancel; N performs one step. On macOS, use Control for these defaults.

This is a local installation; you do not need to upload anything to CurseForge or a server. The addon belongs on the client. A dedicated server does not need the addon, and server-side EMI is optional. Download the mod JAR, not the source ZIP or sources JAR. When updating the pack later, check that both client JARs are still present and that the pack still uses these Minecraft/loader versions.

## What was addressed

The inspected release is [0.6-hotfix, file 8811966](https://www.curseforge.com/minecraft/modpacks/impostor-syndrome-reimagined/files/8811966), released 5 September 2026. Its manifest has 637 mod entries, uses NeoForge 21.1.249 and recommends 14,400 MB RAM. The test machine has 8 GB RAM, so **the complete pack was not launched**. Compatibility here means a tested sample of its crafting stack and representative table recipes, not certification of every mod, recipe or world.

Beta 1 required NeoForge 21.1.250 and rejected KubeJS's special recipe wrapper classes. Beta 2 builds against 21.1.249 and accepts the known shaped/shapeless KubeJS wrappers only when they contain no ingredient actions or output scripts. The original recipe still verifies its actual ingredients, output and returned items, and every execution waits for server inventory confirmation.

The sample uses these versions from the pack's manifest, plus the required EMI release:

| Mod | Version | CurseForge project / file |
|---|---|---|
| JEI | 19.44.0.405 | 238222 / 8732390 |
| KubeJS | 2101.7.2-build.374 | 238086 / 8715199 |
| Rhino | 2101.2.8-build.91 | 416294 / 8463898 |
| Architectury | 13.0.11 | 419699 / 8492726 |
| Crafting Tweaks | 21.1.11 | 233071 / 8697050 |
| Balm | 21.0.65 | 531761 / 8645517 |
| Polymorph+ | 1.3.1+1.21.1 | 1586874 / 8750431 |
| AllTheCompressed | 4.4.0 | 514045 / 7361502 |
| Create | 6.0.10 | 328085 / 7963363 |
| Crafting Station: J/EMI Edition Updated | 2.1.1 | 1127715 / 7932261 |
| EMI (added separately) | 1.1.24+1.21.1+neoforge | 580555; pinned Maven artifact |

Nested dependencies such as Flywheel, Ponder and KumaAPI load from those mods. Recipe fixtures in `src/packTest` were independently authored for these tests; the full pack, its scripts and its assets are not redistributed.

## Supported examples and boundaries

- **Furnace:** four ordinary cobblestone, four compressed cobblestone and one coal block. The addon can make the compressed blocks and coal block first, sharing the cobblestone stock correctly. Existing furnaces and compressed blocks count toward the requested total.
- **Piston:** the modified recipe uses a Create shaft. The fixture follows the pack's shaft yield of two shafts from two andesite alloy. Existing alloy can feed this table-crafting chain.
- **Machines:** producing missing andesite alloy in a Create mixer is unsupported. Craft that material yourself and put it in your inventory, then restart the job. Choose the intended recipe in EMI when an ingredient has multiple possible sources.
- **KubeJS:** plain shaped/shapeless wrappers work. Ingredient actions such as `keepIngredient` and scripted `modifyResult` recipes remain blocked. The pack's component-dependent bee recipes are outside this support boundary.
- **Interfaces:** use the vanilla 3×3 table or player 2×2 grid. Crafting Station, Crafting on a Stick, Extended Crafting tables and AE2/storage terminals have no execution adapters. Only the selected Crafting Station's rejection was runtime-tested; the other interfaces are outside scope.
- **JEI / Polymorph+:** the sampled stack loads with EMI, and the tested addon keys and vanilla-menu crafting work together. This is not an exhaustive test of either mod's UI. Conflicting recipes are refused when the real matching recipe differs from the chosen EMI recipe.

The sampled EMI/JEI/Create combination logs duplicate recipe IDs and synthetic toolbox recipe warnings while indexing recipes. The tested crafting paths operate despite those warnings; the addon does not fix or hide them. Broader recipe-viewer behavior and the remaining pack mods are unverified.

See [the verification report](TEST-REPORT.md) for results and evidence. The earlier dedicated-server tests cover the unchanged server-assisted and container-click paths; they were performed on beta 1, not on this full modpack.

## Reproduce the sample

With JDK 21, run from the source project:

```sh
./gradlew build
./gradlew -Pintegration -PpackCompatibility runClient
./gradlew -Pintegration -PpackCompatibility -PpackagedTest -PtestMode=packaged-pack runClient
```

The last command loads the distributable addon JAR, excludes its loose development classes, and runs the test harness in a separate `run-packaged` directory. It asserts the addon's code-source path before testing. This is an isolated Gradle launch, not a CurseForge launcher installation. Inspect `runtime-tests-packaged-pack.txt` in that directory; a successful game-process exit alone does not prove that assertions passed.
