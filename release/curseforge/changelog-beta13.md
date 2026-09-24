## 2.0.0-beta.13

- Fix false “Stored quantity differs” stalls in AE2 and AE2WTLib wireless crafting terminals.
- Confirm grid clearing, recipe filling, and the exact collected output from server menu snapshots even when AE2 network counts update separately or other devices change storage. Partial batches remain blocked.

Verified with AE2WTLib 19.5.1 in disposable integrated and dedicated-server tests, including a one-item network change during grid clearing and 300 ms simulated round-trip latency.

Minecraft 1.21.1, NeoForge, Java 21. Requires EMI 1.1.24+1.21.1 for NeoForge. Install the main JAR on the client, replacing older addon JARs. Optional storage mods are not bundled.
