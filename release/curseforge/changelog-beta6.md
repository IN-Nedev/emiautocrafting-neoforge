## 2.0.0-beta.6 — First CurseForge beta

- Port EMI Autocrafting to Minecraft 1.21.1, NeoForge 21.1.249 and Java 21. Requires EMI 1.1.24+1.21.1 for NeoForge.
- Craft finite EMI recipe trees, including supported intermediate recipes and existing-item accounting.
- Add optional Crafting Station adjacent storage, Ars Nouveau Bookwyrm crafting lecterns, and AE2 stored-item crafting terminal support.
- Show missing materials and a separate collapsible Craft batch panel.
- Start the next operation immediately after confirmation by default, with configurable pacing and cancellation.
- Fix Crafting Station output pickup stalls caused by oversized Sophisticated chest stacks, stale client storage displays, and changes to unrelated stored-item metadata.
- Keep exact checks for each operation's ingredients, output and returned items, including reusable tools. Stop on uncertain results without automatically retrying.

Verification: 42 unit tests and 35 packaged-runtime assertions passed. Runtime assertions cover 19 scenarios plus startup/UI checks; they are not 35 independent features. See the repository test report for limits, including dedicated-server and full-modpack coverage.

Install only the main release JAR on the client. Replace older addon versions and install the required EMI version separately. Optional storage mods are not bundled.
