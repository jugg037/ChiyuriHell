# Architecture baseline

## Runtime shape

Start with a modular monolith and a small number of isolated worker processes. Do not introduce
a distributed microservice stack or require Docker for desktop use.

- **Desktop UI:** PySide6 / Qt Widgets. The UI communicates with the application core through typed service interfaces.
- **Application core:** Python package responsible for session state, permissions, task routing and lifecycle.
- **Model gateway:** capability-based adapters for local and explicitly connected remote AI providers.
- **Workers:** heavy inference engines run out-of-process so they can be restarted or unloaded independently.
- **Memory:** SQLite with FTS5 first. Add embeddings/vector retrieval only when justified by real use cases.
- **Storage:** user-configurable SSD paths. Keep private data outside the source repository.
- **Windows integration:** dedicated adapters for Windows UI Automation and approved application integrations.

## Connected-provider identity model

Chiyuri is the persistent application-level identity, not an individual provider model.

- Provider adapters are replaceable execution engines. They do not own Chiyuri's identity or memory.
- The application owns conversation history, active personality profile, preferences and avatar state.
- The conversation service injects the active personality into every chat request.
- Provider selection is capability-based; a provider must not claim a capability its adapter has not implemented.
- Hosted provider use is explicit. Conversation data is not sent to a cloud provider until the user has approved data sharing for that provider.
- API-key values are never stored in provider configuration files. Configuration may reference an environment variable; secure OS credential storage remains future work.
- Provider endpoints use HTTPS, exact-host allowlisting, DNS validation and pinned public-IP connections. Redirects are rejected.
- A failed provider may be skipped in favour of another compatible provider, but errors must not leak secrets.

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

- Text inference: llama.cpp with GGUF models; OpenAI-compatible HTTPS APIs for hosted providers.
- Image and video workflows: ComfyUI as a shared backend, with separate validated workflows.
- Speech recognition: Whisper-family runtime; VAD adapter can use Silero.
- Speech synthesis: pluggable local TTS adapter.
- Vision: a model adapter selected after VRAM and quality tests.

Each model integration must record source, exact revision, quantization, licence, disk footprint,
VRAM/RAM measurements and supported tasks.

## Security and permissions

- Secrets are stored locally and excluded from Git.
- Local network endpoints bind to loopback by default.
- Windows automation requires explicit user enablement and visible status.
- Destructive or externally visible actions require confirmation.
- Model cards and workflow files are treated as untrusted input.
- Generation capabilities are governed by the selected model's licence and applicable law; the application does not promise universal or unlimited output.

## Compatibility and packaging

Target Windows 10 22H2 x64 and Windows 11 x64. Validate Python, Qt/PySide6, CUDA and inference-runtime combinations on CI and real hardware before freezing versions. Package the desktop app with a reproducible Windows build and installer only after the runtime is stable.

## Persona system

- Personality profiles are structured, versionable local JSON documents created from natural-language prompts (Russian is a first-class input language).
- A profile contains identity, description, trait sliders, speaking style, values, boundaries and additional system guidance.
- The same rendered persona prompt is supplied to text, voice, vision and media orchestration layers; modality-specific instructions are appended rather than replacing the persona.
- Profile generation is a model-assisted transformation into validated JSON. The user reviews and activates the result; edits never silently rewrite the active profile.
- Persona settings cannot grant tools, bypass permissions, or override platform, model, licence or safety constraints.

## Internet and local-network access

- Internet research is a separate capability with pluggable search providers and bounded HTTPS page readers. Search results and page contents are untrusted data, never executable instructions.
- Network access is off until configured. Internet hosts must be explicitly approved by the user; requests are bounded by time, response size and content type.
- LAN devices are enrolled explicitly in a local device registry. Each device receives independent discover, read and control grants.
- Protocol integrations implement a narrow connector contract. No generic arbitrary shell execution is exposed by default.
- Device discovery is user-initiated and restricted to networks the user is authorized to administer. No background port scanning.
- Control operations require per-action confirmation unless the user later creates a narrowly scoped trusted automation rule.
- All external actions must be attributable to a device, connector, permission grant and audit event.
