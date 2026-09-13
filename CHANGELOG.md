# 2.0.0-beta.2 — Impostor Syndrome crafting compatibility

- Built against NeoForge 21.1.249 and lowered the loader requirement to match Impostor Syndrome – Reimagined 0.6-hotfix. EMI remains pinned to 1.1.24 for NeoForge 1.21.1 and must be installed separately.
- Added optional verification of plain KubeJS shaped/shapeless recipe wrappers. Ingredient actions and scripted output modifiers are rejected before their methods execute.
- Cancel jobs immediately on the client logout event and clear held shortcuts.
- Added a pinned integration profile with the pack's JEI, KubeJS, Create, compressed blocks, Crafting Tweaks and Polymorph+, plus independent examples of its modified furnace, piston and shaft recipes. Custom stations remain unsupported.
- Added pack installation instructions and explicit limits on the compatibility claim. See the test report for actual results; the complete pack is not certified.

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
