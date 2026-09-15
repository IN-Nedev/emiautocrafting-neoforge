# 2.0.0-beta.6 — Verify the items involved in each operation

- Stop blocking a craft because an unused stored item changes its data components, arrives or leaves. Each operation verifies exact keys/counts for its ingredients, output and returned items, including reusable ingredients whose net count is unchanged. Grid, result, cursor and relevant source slots still require authoritative confirmation.
- Rebase background inventory changes before the next operation and plan subsequent steps from current accessible stock. Capped-input reconciliation remains bounded and applies within the same operation scope.
- Reproduce beta 5's "expected 1, got 0" stall with server-side metadata changes to an unused item, then verify the packaged fix with ordinary and oversized Sophisticated chest stock. Add unit coverage for missing outputs, unconsumed ingredients and reusable tools under scoped verification.

# 2.0.0-beta.5 — Crafting Station display synchronization

- Use Crafting Station's existing client display cache for the side inventories whose slot counts came from the menu-opening packet. Separately refreshed client block-entity inventories can no longer overwrite the displayed post-transfer stock and prevent output pickup. The server still uses its real inventory handlers.
- Retain the exact synchronization reason in timeout messages and include bounded item IDs/counts in slot-mismatch diagnostics.
- Add a regression that injects a delayed client chest refresh after recipe filling, plus a netherite-chest case with ingredients outside the visible slot window. No pacing or crafting-confirmation checks are relaxed.

# 2.0.0-beta.4 — Crafting Station stacked-storage fix

- Fix filled recipes stalling before output pickup when an adjacent Sophisticated chest has oversized stacks. Crafting Station caps each displayed source slot to one normal stack; transfers can reveal previously uncounted material. Reconcile only bounded gains from still-capped slots containing the exact selected inputs, then verify the craft against the updated stock. Losses and unrelated gains still fail verification.
- Display the specific synchronization wait reason and replace full inventory dumps with bounded diagnostics.
- Add the exact installed Sophisticated Storage/Core versions to the opt-in storage profile, client-only EMI transfer tests, and capped-count regression fixtures. The addon is still client-side.

# 2.0.0-beta.3 — Storage crafting, grouped batches and faster execution

- Add optional Crafting Station, Bookwyrm lectern and AE2 crafting-terminal adapters. Count connected storage, transfer exact ingredients through native protocols, verify the filled grid, craft once and confirm the resulting stock. AE2 CPU crafting is not submitted.
- Show the complete missing-material list after counting accessible inventories.
- Put the active tree in a collapsible Craft batch panel, with progress, ingredient pages and Tree/Clear actions. Ordinary favourites stay separate.
- Default to zero extra pacing ticks. Advance planning immediately after confirmation while retaining the one-operation-per-tick limit, timeouts and cancellation. Reuse exact native grid refills and consolidate output stacks.
- Add real chest/lectern/finite ME-cell fixtures, large-batch conservation checks, shortage checks and UI grouping checks, plus a test-only Prism harness.

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
