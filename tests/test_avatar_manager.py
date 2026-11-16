from pathlib import Path

import pytest

from chiyuri_ai.avatar_manager import (
    INSPECTOR_RELATIVE_PATH,
    install_inspector,
    launch_unity,
    validate_unity_project,
)


def make_project(root: Path) -> Path:
    (root / "Assets").mkdir(parents=True)
    (root / "ProjectSettings").mkdir()
    return root


def test_validate_unity_project_requires_project_markers(tmp_path: Path):
    with pytest.raises(ValueError, match="Assets"):
        validate_unity_project(tmp_path)


def test_install_inspector_into_project(tmp_path: Path):
    project = make_project(tmp_path / "UnityProject")
    installed = install_inspector(project)

    assert installed == project / INSPECTOR_RELATIVE_PATH
    assert "ChiyuriAvatarInspector" in installed.read_text(encoding="utf-8")


def test_install_preserves_existing_inspector(tmp_path: Path):
    project = make_project(tmp_path / "UnityProject")
    destination = project / INSPECTOR_RELATIVE_PATH
    destination.parent.mkdir(parents=True)
    destination.write_text("// custom local version", encoding="utf-8")

    install_inspector(project)

    backup = destination.with_suffix(destination.suffix + ".chiyuri-backup")
    assert backup.read_text(encoding="utf-8") == "// custom local version"
    assert "ChiyuriAvatarInspector" in destination.read_text(encoding="utf-8")


def test_install_does_not_overwrite_existing_backup(tmp_path: Path):
    project = make_project(tmp_path / "UnityProject")
    destination = project / INSPECTOR_RELATIVE_PATH
    destination.parent.mkdir(parents=True)
    destination.write_text("// existing", encoding="utf-8")
    destination.with_suffix(destination.suffix + ".chiyuri-backup").write_text(
        "// backup", encoding="utf-8"
    )

    with pytest.raises(FileExistsError):
        install_inspector(project)


def test_launch_requires_exe(tmp_path: Path):
    project = make_project(tmp_path / "UnityProject")
    editor = tmp_path / "Unity.exe"
    editor.write_text("not a real executable", encoding="utf-8")

    with pytest.raises(OSError):
        launch_unity(editor, project)


def test_discover_unity_editors_finds_and_sorts_versions(tmp_path: Path):
    from chiyuri_ai.avatar_manager import discover_unity_editors

    root = tmp_path / "Unity" / "Hub" / "Editor"
    older = root / "2022.3.20f1" / "Editor" / "Unity.exe"
    newer = root / "6000.0.10f1" / "Editor" / "Unity.exe"
    older.parent.mkdir(parents=True)
    newer.parent.mkdir(parents=True)
    older.write_bytes(b"unity")
    newer.write_bytes(b"unity")

    assert discover_unity_editors((root,)) == [newer.resolve(), older.resolve()]


def test_discover_unity_editors_ignores_missing_roots(tmp_path: Path):
    from chiyuri_ai.avatar_manager import discover_unity_editors

    assert discover_unity_editors((tmp_path / "not-installed",)) == []
