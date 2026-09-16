## 2.0.0-beta.8

- Fix Crafting Station batches stopping when Sophisticated Storage stacks cross below the station's displayed stack limit.
- Keep unused transferred ingredients in their original slots so filling a recipe does not merge upgraded stacks and hide visible quantities.
- Preserve exact server checks for ingredients, outputs and returned items. Uncertain operations still stop without automatic retries.

Also includes the changes from beta 7, which was not uploaded to CurseForge:

- Support Quark mixed-material recipes and verified exclusion wrappers, including mixed-wood chests used by recursive crafting trees.
- Add a readable blocked-recipe screen naming the item, recipe, dependency path and whether it was selected by a tree choice, heart or default.
- Add View recipe, Edit tree and Copy details controls, plus a Why? button in the batch panel.
- Refresh recipe preferences when starting a batch while preserving explicit choices inside its tree.

Minecraft 1.21.1, NeoForge, Java 21. Requires EMI 1.1.24+1.21.1 for NeoForge. Install the main JAR on the client, replacing the older addon JAR. Optional storage mods are not bundled. See the repository test report for measured coverage and remaining limitations.
