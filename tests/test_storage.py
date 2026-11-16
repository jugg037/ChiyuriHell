from pathlib import Path

from chiyuri_ai.storage import StoragePaths


def test_storage_paths_can_be_redirected_to_another_drive(monkeypatch, tmp_path: Path):
    root = tmp_path / "AI-SSD"
    monkeypatch.setenv("CHIYURI_HOME", str(root))

    paths = StoragePaths.from_environment()

    assert paths.root == root
    assert paths.models == root / "Models"
    assert paths.library == root / "Library"
    assert paths.cache == root / "Cache"
    assert paths.backups == root / "Backups"
    assert paths.avatars == root / "Library" / "Avatars"


def test_create_builds_private_storage_layout(tmp_path: Path):
    root = tmp_path / "ChiyuriAI"
    paths = StoragePaths(
        root=root,
        models=root / "Models",
        library=root / "Library",
        cache=root / "Cache",
        backups=root / "Backups",
    )

    paths.create()

    assert all(path.is_dir() for path in (
        paths.root, paths.models, paths.library, paths.cache, paths.backups, paths.avatars
    ))
