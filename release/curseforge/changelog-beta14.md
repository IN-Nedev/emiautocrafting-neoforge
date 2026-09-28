## 2.0.0-beta.14

- Fix AE2 and AE2WTLib stock previews staying empty or stale until an item is manually inserted or extracted.
- Refresh available materials when a terminal opens and when AE2 network item updates arrive. Storage detection also works with the grouped batch panel disabled.

Verified with an unchanged, empty player inventory, live server-side stock changes, reopening the terminal, and a launch without AE2 installed. The wireless menu shares the tested AE2 repository hook; its UI remains a live-user check.

Minecraft 1.21.1, NeoForge, Java 21. Requires EMI 1.1.24+1.21.1 for NeoForge. Install the main JAR on the client, replacing older addon JARs. Optional storage mods are not bundled.
