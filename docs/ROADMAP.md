# Implementation roadmap

## Stage 0 — repository and CI baseline
- [x] Separate active development branch.
- [x] Python package and desktop shell.
- [x] SSD-aware private storage paths.
- [x] Windows CI matrix.
- [x] Green lint and test jobs.
- [x] Portable Windows x64 artifact.
- [x] Windows installer artifact with Start Menu and optional desktop shortcuts.
- [ ] Fresh-machine installation test.
- [ ] Release asset and checksum verification.

## Stage 1 — local application core
- [x] SQLite conversation memory with FTS5 search.
- [x] Capability-based model adapter contract and router.
- [x] Conversation orchestration service.
- [x] CPU/RAM and optional NVIDIA VRAM telemetry.
- [ ] Settings UI.
- [ ] Resource-aware queue and worker lifecycle.

## Stage 2 — model integrations
- [x] llama.cpp local chat adapter (model manager remains pending).
- [ ] Vision adapter and image understanding.
- [ ] ComfyUI image workflow adapter.
- [ ] ComfyUI video workflow adapter and cancellable queue.
- [ ] Speech-to-text, VAD and text-to-speech adapters.

## Stage 3 — unified personality and connected assistant
- [x] Structured personality profiles and local JSON storage.
- [x] Russian-prompt-to-profile service contract.
- [x] Explicit LAN device registry with separate read/control grants.
- [x] Confirmation gate for device control operations.
- [x] Bounded HTTPS reader with approved-host policy.
- [x] SearXNG search-provider adapter.
- [ ] Persona Studio UI and cross-modal persona injection.
- [x] Append-only SQLite audit log with secret redaction and mutation triggers.\n- [x] Permission-aware tool registry with validation, identity policy and pre-execution gating.\n- [ ] Permission UI and encrypted credential storage.
- [ ] SSH / HTTP API / SMB / WebSocket device connectors.
- [ ] Permission-aware Windows UI Automation.

## Stage 4 — avatar body and evolution
- [x] Read-only avatar project inventory and basic Animator parameter extraction.
- [x] Local upgrade-candidate records with HTTPS source and licence requirements.
- [ ] Unity-side inspector for Animator layers, states, transitions and expression menus.
- [ ] Semantic animation/prop/toggle capability catalogue.
- [ ] Isolated animation discovery and validation scene.
- [ ] Unity runtime bridge for expressions, props, motion and desktop interaction.
- [ ] Upgrade staging, preview, approval, versioning and rollback.
- [ ] Automated research and compatibility scoring for candidate assets.

The roadmap marks only code already committed. A checked item means its code and automated
tests exist; it does not imply a Unity project has been supplied or tested.


## Stage 5 — owner-bound identity and safety foundations
- [x] Define user-primary identity principles and healthy disagreement boundaries.
- [x] Add deterministic deny rules for self-replication, remote self-deployment, covert
      persistence, concealment and owner-binding changes.
- [x] Require user initiation plus explicit approval before private identity-state export.
- [ ] Enforce deny-by-default network egress with Windows Firewall integration.
- [ ] Isolate model process from executable, permission-policy and audit-store writes.
- [ ] Add signed update verification, tamper-evident audit storage and security integration tests.
