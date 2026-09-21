## 2.0.0-beta.9

- Speed up Bookwyrm lectern and AE2 terminal crafting by collecting ordinary recipes in bounded batches of up to one output stack per server confirmation.
- Respect the requested quantity: a request for 20 chests crafts 20, even when more materials are available. Native recipe output multiples still apply.
- Clear all leftover crafting-grid stacks in one verified operation when the recipe changes, and reuse matching ingredients already in the grid.
- Limit batches by reserved materials, native refill supply and uneven grid stacks. Keep one execution per confirmation for returned containers, tools and single-step mode.
- Preserve server inventory checks and stop uncertain operations without automatic retries.

Minecraft 1.21.1, NeoForge, Java 21. Requires EMI 1.1.24+1.21.1 for NeoForge. Install the main JAR on the client, replacing the older addon JAR. Optional storage mods are not bundled.
