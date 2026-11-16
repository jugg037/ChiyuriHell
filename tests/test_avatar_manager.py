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
