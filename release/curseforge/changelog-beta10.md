## 2.0.0-beta.10

- Speed up AE2 recipe chains that use intermediates in your inventory. Load enough ingredients into each crafting slot for a bounded batch instead of transferring and collecting them one craft at a time.
- Keep matching AE2 ingredients in the grid while filling missing slots, avoiding unnecessary grid clearing.
- Respect ingredient stack limits, remaining quantities and reserved materials. Verify the complete transfer and crafted result before continuing.

Outputs are collected into your inventory. Complete ME storage cells do not stack, so a large request still stops when inventory space runs out. Items are not automatically deposited into storage.

Minecraft 1.21.1, NeoForge, Java 21. Requires EMI 1.1.24+1.21.1 for NeoForge. Install the main JAR on the client, replacing the older addon JAR. Optional storage mods are not bundled.
