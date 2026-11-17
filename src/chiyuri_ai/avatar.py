"""Safe inventory and evolution proposals for Chiyuri's single persistent avatar."""
from __future__ import annotations

import json
import math
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




def _validate_string_list(value: object, label: str, *, limit: int = 5000) -> None:
    if not isinstance(value, list) or len(value) > limit:
        raise ValueError(f"Некорректный список в отчёте: {label}")
    if any(not isinstance(item, str) or len(item) > 2048 for item in value):
        raise ValueError(f"Некорректные текстовые элементы в отчёте: {label}")


def _validate_optional_text(record: dict[str, object], key: str, label: str) -> None:
    value = record.get(key)
    if value is not None and (not isinstance(value, str) or len(value) > 2048):
        raise ValueError(f"Некорректное текстовое поле: {label}.{key}")


def _validate_report_records(data: dict[str, object]) -> None:
    for key in ("componentTypes", "toggleCandidates", "warnings"):
        _validate_string_list(data.get(key, []), key)

    game_objects = data.get("gameObjects", [])
    if not isinstance(game_objects, list) or len(game_objects) > 20000:
        raise ValueError("Некорректный список объектов иерархии.")
    for index, item in enumerate(game_objects):
        label = f"gameObjects[{index}]"
        if not isinstance(item, dict):
            raise ValueError(f"Некорректная запись: {label}")
        _validate_optional_text(item, "hierarchyPath", label)
        _validate_string_list(item.get("componentTypes", []), f"{label}.componentTypes", limit=256)
        if not isinstance(item.get("activeSelf", False), bool) or not isinstance(
            item.get("activeInHierarchy", False), bool
        ):
            raise ValueError(f"Некорректное состояние активности: {label}")

    descriptors = data.get("sdkDescriptors", [])
    if not isinstance(descriptors, list) or len(descriptors) > 100:
        raise ValueError("Некорректный список SDK-дескрипторов.")
    for index, item in enumerate(descriptors):
        label = f"sdkDescriptors[{index}]"
        if not isinstance(item, dict):
            raise ValueError(f"Некорректная запись: {label}")
        for key in ("hierarchyPath", "expressionParametersAsset", "expressionsMenuAsset"):
            _validate_optional_text(item, key, label)

    animators = data.get("animators", [])
    if not isinstance(animators, list) or len(animators) > 1000:
        raise ValueError("Некорректный список Animator.")
    for animator_index, animator in enumerate(animators):
        label = f"animators[{animator_index}]"
        if not isinstance(animator, dict):
            raise ValueError(f"Некорректная запись Animator: {label}")
        _validate_optional_text(animator, "hierarchyPath", label)
        _validate_optional_text(animator, "controllerName", label)

        parameters = animator.get("parameters", [])
        if not isinstance(parameters, list) or len(parameters) > 2000:
            raise ValueError(f"Некорректный список параметров Animator: {label}")
        for parameter_index, parameter in enumerate(parameters):
            parameter_label = f"{label}.parameters[{parameter_index}]"
            if not isinstance(parameter, dict):
                raise ValueError(f"Некорректная запись параметра: {parameter_label}")
            for key in ("name", "type"):
                _validate_optional_text(parameter, key, parameter_label)
            for key in ("defaultFloat", "defaultInt"):
                value = parameter.get(key)
                if value is not None and (
                    isinstance(value, bool)
                    or not isinstance(value, (int, float))
                    or not math.isfinite(value)
                ):
                    raise ValueError(f"Некорректное числовое поле: {parameter_label}.{key}")
            if "defaultBool" in parameter and not isinstance(parameter["defaultBool"], bool):
                raise ValueError(f"Некорректное логическое поле: {parameter_label}.defaultBool")

        layers = animator.get("layers", [])
        if not isinstance(layers, list) or len(layers) > 256:
            raise ValueError(f"Некорректный список слоёв Animator: {label}")
        for layer_index, layer in enumerate(layers):
            layer_label = f"{label}.layers[{layer_index}]"
            if not isinstance(layer, dict):
                raise ValueError(f"Некорректная запись слоя: {layer_label}")
            _validate_optional_text(layer, "name", layer_label)
            states = layer.get("states", [])
            if not isinstance(states, list) or len(states) > 10000:
                raise ValueError(f"Некорректный список состояний: {layer_label}")
            for state_index, state in enumerate(states):
                state_label = f"{layer_label}.states[{state_index}]"
                if not isinstance(state, dict):
                    raise ValueError(f"Некорректная запись состояния: {state_label}")
                for key in ("name", "fullPath", "motionName", "motionType"):
                    _validate_optional_text(state, key, state_label)
                transitions = state.get("transitions", [])
                if not isinstance(transitions, list) or len(transitions) > 2000:
                    raise ValueError(f"Некорректный список переходов: {state_label}")
                for transition_index, transition in enumerate(transitions):
                    transition_label = f"{state_label}.transitions[{transition_index}]"
                    if not isinstance(transition, dict):
                        raise ValueError(f"Некорректная запись перехода: {transition_label}")
                    _validate_optional_text(transition, "destination", transition_label)
                    _validate_string_list(
                        transition.get("conditions", []),
                        f"{transition_label}.conditions",
                        limit=256,
                    )
                blend_children = state.get("blendChildren", [])
                if not isinstance(blend_children, list) or len(blend_children) > 5000:
                    raise ValueError(f"Некорректный список Blend Tree: {state_label}")
                for child_index, child in enumerate(blend_children):
                    child_label = f"{state_label}.blendChildren[{child_index}]"
                    if not isinstance(child, dict):
                        raise ValueError(f"Некорректный элемент Blend Tree: {child_label}")
                    _validate_optional_text(child, "motionName", child_label)
                    for key in ("threshold", "timeScale"):
                        value = child.get(key)
                        if value is not None and (
                            isinstance(value, bool)
                            or not isinstance(value, (int, float))
                            or not math.isfinite(value)
                        ):
                            raise ValueError(f"Некорректное числовое поле: {child_label}.{key}")

        overrides = animator.get("overrides", [])
        if not isinstance(overrides, list) or len(overrides) > 5000:
            raise ValueError(f"Некорректный список переопределений Animator: {label}")
        for override_index, override in enumerate(overrides):
            override_label = f"{label}.overrides[{override_index}]"
            if not isinstance(override, dict):
                raise ValueError(f"Некорректная запись переопределения: {override_label}")
            for key in ("originalClipName", "overrideClipName"):
                _validate_optional_text(override, key, override_label)


def load_unity_inventory(path: Path) -> dict[str, object]:
    """Load and deeply validate the bounded JSON report exported by the Unity inspector."""
    source = path.expanduser().resolve(strict=True)
    if not source.is_file() or source.stat().st_size > 10 * 1024 * 1024:
        raise ValueError("Отчёт Unity должен быть файлом размером не более 10 МБ.")
    try:
        data = json.loads(source.read_text(encoding="utf-8-sig"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError("Файл не является корректным JSON-отчётом Unity.") from exc
    if not isinstance(data, dict) or data.get("schemaVersion") != 1:
        raise ValueError("Неподдерживаемая версия формата отчёта Unity.")
    avatar_name = data.get("avatarName")
    if not isinstance(avatar_name, str) or not avatar_name.strip() or len(avatar_name) > 512:
        raise ValueError("В отчёте отсутствует корректное имя аватара.")
    for key in ("unityVersion", "generatedUtc"):
        _validate_optional_text(data, key, "report")
    _validate_report_records(data)
    return data


class AvatarEvolutionStore:
    """Persists inventory snapshots and sourced upgrade candidates outside Git."""

    def __init__(self, directory: Path) -> None:
        self.directory = directory
        self.directory.mkdir(parents=True, exist_ok=True)

    def save_inventory(self, inventory: AvatarInventory) -> Path:
        return self._write("inventory.json", inventory.to_dict())

    def save_unity_inventory(self, inventory: dict[str, object]) -> Path:
        return self._write("unity-inventory.json", inventory)

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
