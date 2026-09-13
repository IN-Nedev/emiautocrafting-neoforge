# 2.0.0-beta.1 — 1.21.1 NeoForge port

- Replaced the 1.20.1 Architectury/Yarn Fabric/Forge build with Java 21, Mojang mappings and pinned NeoForge/EMI dependencies.
- Retained EMI recipe preferences, tree resolutions and crafting sidebar; added recipe/output-preserving preparation and an explicit total-items dialog.
- Replaced OS key-repeat crafting and the static lock with a finite client-tick job controller, single-step mode and cancellation.
- Added checked quantity calculations, shared-stock allocation, existing-item accounting, batch surplus and bounded traversal.
- Added exact ingredient selection, real recipe/output/remainder preflight, conservative space checks and deliberate grid handling.
- Added authoritative full-menu verification after each operation, pacing, timeouts and manual restart after uncertain results.
- Added configurable contextual shortcuts, text-focus guards and restrained status feedback.
- Removed hardcoded Tom's Storage imports and transfer loops. Optional storage and machine adapters are deferred.
- Preserved GPL-3.0-only licensing and upstream attribution; added unit and opt-in runtime test sources.
