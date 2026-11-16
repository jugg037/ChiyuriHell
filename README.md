# Chiyuri AI

Chiyuri AI is a local-first, modular desktop assistant for Windows 10 (22H2, x64) and Windows 11 (x64).

The initial target PC profile is an Intel Core i7-12700-class CPU, 64 GB RAM and an NVIDIA RTX 3060 Ti 8 GB GPU. The application must remain useful when optional AI backends are unavailable and manage GPU/CPU memory carefully.

## Principles

- One coherent assistant identity, conversation context and memory across enabled capabilities.
- Local-first processing; cloud services are optional adapters.
- Separate, restartable worker processes for large model runtimes.
- Explicit resource scheduling for the shared 8 GB VRAM budget.
- Models, caches, generated media, personal memory and workspaces stay outside Git.
- Model capabilities and licences are documented; no promise of universally unrestricted generation.
- Windows 10 and 11 compatibility is tested in CI where hosted runners permit.

## Planned capabilities

1. Desktop shell and settings.
2. Conversation engine, model routing and shared context.
3. Persistent memory (SQLite/FTS5).
4. Speech input/output and voice activity detection.
5. Image understanding and image generation.
6. Video generation through queued workflows.
7. Windows automation with explicit user permissions.
8. Diagnostics, resource monitoring, updates and model management.

## Development

Requirements: 64-bit Windows 10 22H2 or Windows 11, Python 3.11+, and Git.

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -e ".[dev]"
pytest
ruff check .
python -m chiyuri_ai
```

The initial application shell is intentionally small. AI runtimes and model downloads will be integrated incrementally after compatibility and VRAM tests.

## Storage

The application will let the user choose separate locations for configuration, models, generated media/library, temporary cache and backups. Do not commit model weights, private conversations, API keys, generated media or local runtime caches.

## Branches

- `main`: stable, tested snapshots.
- `develop`: active integration branch.
- `feature/*`: isolated feature work.

A public release is not complete until its installer/package asset is attached and verified.
