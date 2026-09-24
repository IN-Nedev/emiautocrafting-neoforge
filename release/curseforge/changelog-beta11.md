## 2.0.0-beta.11

- Recognize AE2WTLib wireless crafting terminals. Batches can see the same stored materials shown by the wireless terminal, including diamonds.
- Support Sophisticated Core next-tier upgrade recipes such as Advanced Void Upgrade. The crafted upgrade retains settings from the input upgrade.
- Count configured upgrades toward the target and craft them one at a time to preserve each item’s settings.
- Fix the version shown in startup logs.

Minecraft 1.21.1, NeoForge, Java 21. Requires EMI 1.1.24+1.21.1 for NeoForge. Install the main JAR on the client, replacing the older addon JAR. Optional storage mods are not bundled.

The AE2WTLib menu is recognized through its AE2 crafting-terminal subclass. Wireless-menu gameplay has not yet been separately automated; ordinary AE2 storage and configured-upgrade crafting passed isolated gameplay tests.
