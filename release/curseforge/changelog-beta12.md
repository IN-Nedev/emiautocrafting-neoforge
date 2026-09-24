## 2.0.0-beta.12

- Recognize AE2WTLib wireless crafting terminals, so batches can see the same stored materials shown by those terminals.
- Support Sophisticated Core next-tier upgrade recipes, including Advanced Void Upgrade, while preserving settings from the previous upgrade.
- Return leftover AE2 crafting-grid ingredients to network storage first. If storage cannot accept them, put the remainder in the player inventory.
- Count configured upgrades toward the target and craft them individually to preserve each item’s settings.

Minecraft 1.21.1, NeoForge, Java 21. Requires EMI 1.1.24+1.21.1 for NeoForge. Install the main JAR on the client, replacing older addon JARs. Optional storage mods are not bundled.

The AE2WTLib menu is recognized through its AE2 crafting-terminal subclass. Wireless-menu gameplay has not yet been separately automated; ordinary AE2 storage, configured upgrades, and both grid-return paths passed isolated gameplay tests.
