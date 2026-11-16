"""User-selectable application storage locations."""
from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class StoragePaths:
    """Paths for private user data; none belong in the source repository."""
    root: Path
    models: Path
    library: Path
    cache: Path
    backups: Path

    @classmethod
    def from_environment(cls) -> StoragePaths:
        configured = os.environ.get("CHIYURI_HOME")
        root = Path(configured).expanduser() if configured else Path.home() / "ChiyuriAI"
        return cls(root, root / "Models", root / "Library", root / "Cache", root / "Backups")

    @property
    def avatars(self) -> Path:
        return self.library / "Avatars"

    @property
    def providers(self) -> Path:
        return self.library / "Providers"

    def create(self) -> None:
        paths = (
            self.root, self.models, self.library, self.cache, self.backups,
            self.avatars, self.providers,
        )
        for path in paths:
            path.mkdir(parents=True, exist_ok=True)
