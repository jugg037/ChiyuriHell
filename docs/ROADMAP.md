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

## Stage 3 — Windows assistant
- [ ] Permission-aware Windows UI Automation.
- [ ] User-visible action confirmations and audit log.
- [ ] Shared memory and persona settings across all capabilities.

## Stage 4 — packaging and first usable build
- [ ] Windows x64 portable build.
- [ ] Installer and update channel.
- [ ] Fresh-machine installation test.
- [ ] Release asset and checksum verification.

The roadmap marks only code already committed. A checked integration item does not imply that a real model backend is connected until its adapter and hardware tests are complete.
\n\n## First usable prototype target\n\nThe initial desktop prototype includes a gothic Qt chat window, persistent SQLite conversation history, FTS5 search, a llama.cpp HTTP adapter, background inference/status workers, and best-effort CPU/RAM/NVIDIA VRAM telemetry. A model server and model weights must be installed separately. The CI pipeline creates a portable Windows x64 artifact after both quality jobs pass.\n