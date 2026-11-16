# Implementation roadmap

## Stage 0 — repository and CI baseline
- [x] Separate active development branch.
- [x] Python package and desktop shell.
- [x] SSD-aware private storage paths.
- [x] Windows CI matrix.
- [x] Green lint and test jobs (first full core run verified below).

## Stage 1 — local application core
- [x] SQLite conversation memory with FTS5 search.
- [x] Capability-based model adapter contract and router.
- [x] Conversation orchestration service.
- [x] CPU/RAM and optional NVIDIA VRAM telemetry.
- [x] Runtime health and hardware status panel (initial).\n- [ ] Settings UI.
- [ ] Resource-aware queue and worker lifecycle.

## Stage 2 — model integrations
- [x] llama.cpp local chat adapter (model manager remains pending).
- [ ] Vision adapter and image understanding.
- [ ] ComfyUI image workflow adapter.
- [ ] ComfyUI video workflow adapter and cancellable queue.
- [ ] Speech-to-text, VAD and text-to-speech adapters.

## Stage 3 — unified personality and connected assistant
- [x] Structured personality profiles and local JSON storage.
- [x] Russian-prompt-to-profile service contract (model callback + JSON validation).
- [x] Explicit LAN device registry with separate read/control grants.
- [x] Confirmation gate for device control operations.
- [x] Bounded HTTPS reader with approved-host policy.
- [x] SearXNG search-provider adapter with bounded JSON responses.
- [x] Source-labelled search context injected into the assistant system message.
- [x] Desktop web-search action with clickable source links.
- [ ] Persona Studio UI, profile preview and active-profile selection.
- [ ] Permission UI, encrypted credential storage and append-only audit log.
- [ ] SSH / HTTP API / SMB / WebSocket device connectors.
- [ ] Permission-aware Windows UI Automation and visible action confirmations.
- [ ] Inject one active persona consistently into chat, voice, vision and media tasks.

## Stage 4 — packaging and first usable build
- [ ] Windows x64 portable build.
- [ ] Installer and update channel.
- [ ] Fresh-machine installation test.
- [ ] Release asset and checksum verification.

The roadmap marks only code already committed. A checked integration item does not imply that a real model backend is connected until its adapter and hardware tests are complete.
\n\n## First usable prototype target\n\nThe initial desktop prototype includes a gothic Qt chat window, persistent SQLite conversation history, FTS5 search, a llama.cpp HTTP adapter, background inference/status workers, and best-effort CPU/RAM/NVIDIA VRAM telemetry. A model server and model weights must be installed separately. The CI pipeline creates a portable Windows x64 artifact after both quality jobs pass.\n