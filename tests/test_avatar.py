from pathlib import Path

import pytest

from chiyuri_ai.avatar import (
    AvatarEvolutionStore,
    AvatarProjectScanner,
    UpgradeCandidate,
)


def test_scanner_catalogues_avatar_assets_and_animator_parameters(tmp_path: Path):
    assets = tmp_path / "Assets"
    (assets / "Animations").mkdir(parents=True)
    (assets / "Controllers").mkdir()
    (assets / "Library").mkdir()
    (assets / "Animations" / "Wave.anim").write_text("animation", encoding="utf-8")
    (assets / "Chiyuri.fbx").write_bytes(b"fbx")
    (assets / "Library" / "ignored.anim").write_text("ignored", encoding="utf-8")
    controller = assets / "Controllers" / "Avatar.controller"
    controller.write_text(
        "m_AnimatorParameters:\n"
        "- m_Name: Hat_Toggle\n"
        "- m_Name: Smile\n"
        "m_AnimatorLayers:\n",
        encoding="utf-8",
    )

    inventory = AvatarProjectScanner().scan(tmp_path)

    assert any(item.relative_path == "Assets/Chiyuri.fbx" for item in inventory.assets)
    assert inventory.animation_files == ["Assets/Animations/Wave.anim"]
    assert inventory.animator_parameters == ["Hat_Toggle", "Smile"]
    assert inventory.likely_toggles == ["Hat_Toggle"]
    assert all("Library/" not in item.relative_path for item in inventory.assets)


def test_scanner_rejects_missing_or_non_directory_path(tmp_path: Path):
    with pytest.raises(FileNotFoundError):
        AvatarProjectScanner().scan(tmp_path / "missing")
    file_path = tmp_path / "file"
    file_path.write_text("x", encoding="utf-8")
    with pytest.raises(ValueError):
        AvatarProjectScanner().scan(file_path)


def test_upgrade_candidates_require_https_and_licence(tmp_path: Path):
    with pytest.raises(ValueError):
        UpgradeCandidate("hair01", "Hair", "http://example.com/hair", "FBX", "CC0")
    candidate = UpgradeCandidate(
        "hair01", "Hair", "https://example.com/hair", "FBX", "CC-BY-4.0",
        "Requires humanoid rig retargeting",
    )
    store = AvatarEvolutionStore(tmp_path / "avatar")
    store.save_candidate(candidate)

    assert store.list_candidates() == [candidate]


def test_inventory_snapshot_is_saved_atomically(tmp_path: Path):
    inventory = AvatarProjectScanner().scan(tmp_path)
    store = AvatarEvolutionStore(tmp_path / "avatar")

    saved = store.save_inventory(inventory)

    assert saved.is_file()
    assert not saved.with_suffix(".json.tmp").exists()
