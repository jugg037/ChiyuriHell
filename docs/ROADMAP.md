# Implementation roadmap

## Stage 0 — repository and CI baseline
- [x] Separate active development branch.
- [x] Python package and desktop shell.
- [x] SSD-aware private storage paths.
- [x] Windows CI matrix.
- [ ] Green lint and test jobs.

## Stage 1 — local application core
- [x] SQLite conversation memory with FTS5 search.
- [x] Capability-based model adapter contract and router.
- [x] Conversation orchestration service.
- [x] CPU/RAM and optional NVIDIA VRAM telemetry.
- [ ] Settings UI and runtime health panel.
- [ ] Resource-aware queue and worker lifecycle.

## Stage 2 — model integrations
- [ ] llama.cpp local chat adapter and model manager.
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
