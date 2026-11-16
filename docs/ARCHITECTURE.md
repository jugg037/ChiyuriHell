# Architecture baseline

## Runtime shape

Start with a modular monolith and a small number of isolated worker processes. Do not introduce a distributed microservice stack or require Docker for desktop use.

- **Desktop UI:** PySide6 / Qt Widgets. The UI communicates with the application core through typed service interfaces.
- **Application core:** Python package responsible for session state, permissions, task routing and lifecycle.
- **Model gateway:** local HTTP/RPC adapters for text, vision, speech and media engines. Each adapter advertises capabilities and health.
- **Workers:** heavy inference engines run out-of-process so they can be restarted or unloaded independently.
- **Memory:** SQLite with FTS5 first. Add embeddings/vector retrieval only when justified by real use cases.
- **Storage:** user-configurable SSD paths. Keep private data outside the repository.
- **Windows integration:** dedicated adapters for Windows UI Automation and approved application integrations. Raw mouse/keyboard simulation is not the only automation path.

## Resource management

The target GPU has 8 GB VRAM. The scheduler must treat this as a shared budget, not assume every model fits simultaneously.

1. Query available VRAM/RAM before starting a workload.
2. Serialize or prioritize heavy GPU tasks when memory is constrained.
3. Unload or park the text model before image/video generation when required.
4. Support CPU/RAM offload as a slower fallback, with clear estimates and cancellation.
5. Expose queue state, progress, cancellation and worker health to the UI.
6. Never download multi-gigabyte model weights silently.

## Initial engine direction

These are integration candidates, not locked dependencies:

- Text inference: llama.cpp with GGUF models.
- Image and video workflows: ComfyUI as a shared backend, with separate validated workflows.
- Speech recognition: Whisper-family runtime; VAD adapter can use Silero.
- Speech synthesis: pluggable local TTS adapter.
- Vision: a model adapter selected after VRAM and quality tests.

Each model integration must record source, exact revision, quantization, licence, disk footprint, VRAM/RAM measurements and supported tasks.

## Security and permissions

- Secrets are stored locally and excluded from Git.
- Local network endpoints bind to loopback by default.
- Windows automation requires explicit user enablement and visible status.
- Destructive or externally visible actions require confirmation.
- Model cards and workflow files are treated as untrusted input.
- Generation capabilities are governed by the selected model's licence and applicable law; the application does not promise universal or unlimited output.

## Compatibility and packaging

Target Windows 10 22H2 x64 and Windows 11 x64. Validate Python, Qt/PySide6, CUDA and inference-runtime combinations on CI and real hardware before freezing versions. Package the desktop app with a reproducible Windows build and installer only after the runtime is stable.
