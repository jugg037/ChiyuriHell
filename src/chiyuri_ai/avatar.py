"""Safe inventory and evolution proposals for Chiyuri's single persistent avatar."""
from __future__ import annotations

import json
import os
import re
from dataclasses import asdict, dataclass, field
from datetime import UTC, datetime
from pathlib import Path
from urllib.parse import urlparse

SUPPORTED_ASSETS = {
    ".fbx", ".vrm", ".blend", ".prefab", ".controller", ".anim", ".asset",
    ".mat", ".png", ".jpg", ".jpeg", ".tga", ".psd", ".shader", ".unitypackage",
    ".vrca", ".unity",
}
TEXT_ASSETS = {".prefab", ".controller", ".anim", ".asset", ".mat", ".unity"}
IGNORED_DIRS = {
    ".git", "library", "temp", "obj", "logs", "user settings", "build", "builds", ".vs",
}
MAX_TEXT_BYTES = 2 * 1024 * 1024
PARAMETER_BLOCK = re.compile(
    r"m_AnimatorParameters:\s*(.*?)(?=\n\s*m_AnimatorLayers:|\Z)", re.DOTALL
)
PARAMETER_NAME = re.compile(r"^\s*- m_Name:\s*(.+?)\s*$", re.MULTILINE)


@dataclass(frozen=True, slots=True)
class AvatarAsset:
    relative_path: str
    kind: str
    size_bytes: int


@dataclass(frozen=True, slots=True)
class AvatarInventory:
    project_root: str
    scanned_at: str
    assets: list[AvatarAsset] = field(default_factory=list)
    animation_files: list[str] = field(default_factory=list)
    animator_controllers: list[str] = field(default_factory=list)
    animator_parameters: list[str] = field(default_factory=list)
    likely_toggles: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, object]:
        return asdict(self)


@dataclass(frozen=True, slots=True)
class UpgradeCandidate:
    candidate_id: str
    title: str
    source_url: str
    asset_format: str
    licence: str
    compatibility_notes: str = ""
    status: str = "candidate"

    def __post_init__(self) -> None:
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", self.candidate_id):
            raise ValueError("Invalid upgrade candidate id")
        parsed = urlparse(self.source_url)
        if parsed.scheme != "https" or not parsed.hostname:
            raise ValueError("Upgrade source must be an HTTPS URL")
        if not self.title.strip() or not self.asset_format.strip() or not self.licence.strip():
            raise ValueError("Title, format and licence are required")
        if self.status not in {"candidate", "approved", "rejected", "applied"}:
            raise ValueError("Invalid upgrade status")


class AvatarProjectScanner:
    """Read-only, bounded scanner for Unity projects and standalone avatar folders."""

    def scan(self, root: Path) -> AvatarInventory:
        root = root.expanduser().resolve(strict=True)
        if not root.is_dir():
            raise ValueError("Avatar source must be a directory")
        assets: list[AvatarAsset] = []
        animations: list[str] = []
        controllers: list[str] = []
        parameters: set[str] = set()
        warnings: list[str] = []
        for current, dirs, files in os.walk(root, followlinks=False):
            current_path = Path(current)
            dirs[:] = sorted(
                name for name in dirs
                if name.casefold() not in IGNORED_DIRS
                and not (current_path / name).is_symlink()
            )
            for filename in sorted(files):
                path = current_path / filename
                if path.is_symlink() or path.suffix.casefold() not in SUPPORTED_ASSETS:
                    continue
                try:
                    relative = path.relative_to(root).as_posix()
                    size = path.stat().st_size
                except OSError:
                    warnings.append(f"Не удалось прочитать метаданные: {path.name}")
                    continue
                suffix = path.suffix.casefold()
                kind = {
                    ".fbx": "model", ".vrm": "avatar", ".blend": "model",
                    ".prefab": "prefab", ".controller": "animator_controller",
                    ".anim": "animation", ".mat": "material",
                    ".png": "texture", ".jpg": "texture", ".jpeg": "texture",
                    ".tga": "texture", ".psd": "texture", ".shader": "shader",
                    ".unitypackage": "package", ".vrca": "avatar_package",
                    ".unity": "scene", ".asset": "unity_asset",
                }[suffix]
                assets.append(AvatarAsset(relative, kind, size))
                if suffix == ".anim":
                    animations.append(relative)
                if suffix == ".controller":
                    controllers.append(relative)
                if suffix in TEXT_ASSETS and size <= MAX_TEXT_BYTES:
                    try:
                        content = path.read_text(encoding="utf-8", errors="ignore")
                    except OSError:
                        warnings.append(f"Не удалось прочитать: {relative}")
                        continue
                    match = PARAMETER_BLOCK.search(content)
                    if match:
                        parameters.update(
                            item.strip().strip('"')
                            for item in PARAMETER_NAME.findall(match.group(1))
                            if item.strip()
                        )
                elif suffix in TEXT_ASSETS and size > MAX_TEXT_BYTES:
                    warnings.append(f"Файл пропущен из-за размера: {relative}")
        sorted_parameters = sorted(parameters, key=str.casefold)
        toggles = [
            name for name in sorted_parameters
            if re.search(r"(toggle|enable|active|show|hide|visible|onoff)", name, re.I)
        ]
        return AvatarInventory(
            project_root=str(root),
            scanned_at=datetime.now(UTC).isoformat(),
            assets=sorted(assets, key=lambda item: item.relative_path.casefold()),
            animation_files=sorted(animations, key=str.casefold),
            animator_controllers=sorted(controllers, key=str.casefold),
            animator_parameters=sorted_parameters,
            likely_toggles=sorted(toggles, key=str.casefold),
            warnings=warnings,
        )


class AvatarEvolutionStore:
    """Persists inventory snapshots and sourced upgrade candidates outside Git."""

    def __init__(self, directory: Path) -> None:
        self.directory = directory
        self.directory.mkdir(parents=True, exist_ok=True)

    def save_inventory(self, inventory: AvatarInventory) -> Path:
        return self._write("inventory.json", inventory.to_dict())

    def save_candidate(self, candidate: UpgradeCandidate) -> Path:
        return self._write(f"candidate-{candidate.candidate_id}.json", asdict(candidate))

    def list_candidates(self) -> list[UpgradeCandidate]:
        result: list[UpgradeCandidate] = []
        for path in sorted(self.directory.glob("candidate-*.json")):
            data = json.loads(path.read_text(encoding="utf-8"))
            result.append(UpgradeCandidate(**data))
        return result

    def _write(self, filename: str, data: dict[str, object]) -> Path:
        target = self.directory / filename
        temporary = target.with_suffix(target.suffix + ".tmp")
        temporary.write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        temporary.replace(target)
        return target
