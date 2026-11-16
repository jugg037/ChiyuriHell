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
        """Resolve the data root, allowing users to place it on another SSD."""
        configured = os.environ.get("CHIYURI_HOME")
        root = Path(configured).expanduser() if configured else Path.home() / "ChiyuriAI"
        return cls(
            root=root,
            models=root / "Models",
            library=root / "Library",
            cache=root / "Cache",
            backups=root / "Backups",
        )

    @property
    def avatars(self) -> Path:
        """Private storage for the primary avatar, evolution catalogue and snapshots."""
        return self.library / "Avatars"

    def create(self) -> None:
        """Create the directory layout when the application is first launched."""
        for path in (self.root, self.models, self.library, self.cache, self.backups, self.avatars):
            path.mkdir(parents=True, exist_ok=True)
