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



def test_load_unity_inventory_validates_and_persists_report(tmp_path: Path):
    import json

    from chiyuri_ai.avatar import load_unity_inventory

    report = tmp_path / "avatar.json"
    data = {
        "schemaVersion": 1,
        "avatarName": "Chiyuri",
        "unityVersion": "2022.3",
        "componentTypes": ["Animator"],
        "toggleCandidates": ["Hat_Toggle"],
        "animators": [{"hierarchyPath": "Chiyuri", "parameters": [], "layers": []}],
        "warnings": [],
    }
    report.write_text(json.dumps(data), encoding="utf-8")

    loaded = load_unity_inventory(report)
    saved = AvatarEvolutionStore(tmp_path / "library").save_unity_inventory(loaded)

    assert loaded["avatarName"] == "Chiyuri"
    assert json.loads(saved.read_text(encoding="utf-8")) == data


def test_load_unity_inventory_rejects_unknown_schema(tmp_path: Path):
    from chiyuri_ai.avatar import load_unity_inventory

    report = tmp_path / "bad.json"
    report.write_text('{"schemaVersion": 99, "avatarName": "X"}', encoding="utf-8")
    with pytest.raises(ValueError, match="верс"):
        load_unity_inventory(report)


def test_load_unity_inventory_rejects_malformed_animators(tmp_path: Path):
    from chiyuri_ai.avatar import load_unity_inventory

    report = tmp_path / "bad.json"
    report.write_text(
        '{"schemaVersion":1,"avatarName":"X","animators":[{"parameters":{}}]}',
        encoding="utf-8",
    )
    with pytest.raises(ValueError, match="parameters"):
        load_unity_inventory(report)
