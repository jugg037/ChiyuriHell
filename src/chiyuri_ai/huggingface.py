"""Hugging Face model discovery and safe GGUF downloads.

Only public model metadata is queried without authentication. Downloads are explicit,
limited to GGUF files, stored under the user's private Models directory, and never
executed automatically.
"""
from __future__ import annotations

import re
import shutil
from dataclasses import dataclass
from pathlib import Path, PurePosixPath

from huggingface_hub import HfApi, hf_hub_download

_REPO_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}/[A-Za-z0-9][A-Za-z0-9_.-]{0,95}$")
_MAX_SEARCH_RESULTS = 100


@dataclass(frozen=True, slots=True)
class HubModel:
    repo_id: str
    downloads: int
    likes: int
    pipeline_tag: str | None
    tags: tuple[str, ...]
    description: str = ""


@dataclass(frozen=True, slots=True)
class HubModelFile:
    filename: str
    size_bytes: int | None


class HuggingFaceCatalog:
    """Read-only discovery client for public Hugging Face model repositories."""

    def __init__(self, token: str | None = None, api: HfApi | None = None) -> None:
        self._api = api or HfApi(token=token)

    def search(
        self,
        query: str = "",
        author: str = "huihui-ai",
        limit: int = 30,
    ) -> list[HubModel]:
        if not isinstance(query, str) or len(query) > 200:
            raise ValueError("Search query must be a string of at most 200 characters")
        if not isinstance(author, str) or len(author) > 100:
            raise ValueError("Author filter is invalid")
        if not isinstance(limit, int) or not 1 <= limit <= _MAX_SEARCH_RESULTS:
            raise ValueError(f"Search limit must be between 1 and {_MAX_SEARCH_RESULTS}")
        models = self._api.list_models(
            search=query.strip() or None,
            author=author.strip() or None,
            sort="downloads",
            direction=-1,
            limit=limit,
        )
        results = []
        for item in models:
            repo_id = getattr(item, "id", None)
            if not isinstance(repo_id, str) or not _REPO_ID.fullmatch(repo_id):
                continue
            tags = tuple(str(tag) for tag in (getattr(item, "tags", None) or []))
            card = getattr(item, "card_data", None)
            description = ""
            if card is not None:
                description = str(getattr(card, "description", "") or "")[:500]
            results.append(
                HubModel(
                    repo_id=repo_id,
                    downloads=max(0, int(getattr(item, "downloads", 0) or 0)),
                    likes=max(0, int(getattr(item, "likes", 0) or 0)),
                    pipeline_tag=getattr(item, "pipeline_tag", None),
                    tags=tags,
                    description=description,
                )
            )
        return results

    def list_gguf_files(self, repo_id: str) -> list[HubModelFile]:
        _validate_repo_id(repo_id)
        info = self._api.model_info(repo_id, files_metadata=True)
        files = []
        for sibling in getattr(info, "siblings", None) or []:
            filename = getattr(sibling, "rfilename", "")
            if not isinstance(filename, str) or not filename.lower().endswith(".gguf"):
                continue
            _validate_filename(filename)
            size = getattr(sibling, "size", None)
            lfs = getattr(sibling, "lfs", None)
            if size is None and lfs is not None:
                size = getattr(lfs, "size", None)
            files.append(
                HubModelFile(filename=filename, size_bytes=size if isinstance(size, int) else None)
            )
        return sorted(files, key=lambda item: (item.size_bytes is None, item.size_bytes or 0, item.filename))


class HuggingFaceModelDownloader:
    """Downloads one selected public GGUF file into private application storage."""

    def __init__(self, models_directory: Path, token: str | None = None) -> None:
        self.models_directory = Path(models_directory).expanduser().resolve()
        self._token = token

    def download(self, repo_id: str, filename: str) -> Path:
        _validate_repo_id(repo_id)
        _validate_filename(filename)
        if not filename.lower().endswith(".gguf"):
            raise ValueError("Only GGUF model files are currently supported by this downloader")
        destination = (self.models_directory / repo_id.replace("/", "--")).resolve()
        if self.models_directory not in destination.parents:
            raise ValueError("Model destination escapes the configured Models directory")
        destination.mkdir(parents=True, exist_ok=True)

        file_size = _known_size(repo_id, filename, self._token)
        if file_size is not None:
            free_bytes = shutil.disk_usage(self.models_directory).free
            if file_size > free_bytes:
                raise OSError(
                    f"Not enough free space: model needs {file_size} bytes, "
                    f"but only {free_bytes} bytes are available"
                )
        downloaded = hf_hub_download(
            repo_id=repo_id,
            filename=filename,
            local_dir=destination,
            token=self._token,
            force_download=False,
        )
        result = Path(downloaded).resolve()
        if destination not in result.parents or not result.is_file():
            raise RuntimeError("Hugging Face returned a file outside the selected model directory")
        return result


def _known_size(repo_id: str, filename: str, token: str | None) -> int | None:
    info = HfApi(token=token).model_info(repo_id, files_metadata=True)
    for sibling in getattr(info, "siblings", None) or []:
        if getattr(sibling, "rfilename", None) == filename:
            size = getattr(sibling, "size", None)
            lfs = getattr(sibling, "lfs", None)
            if size is None and lfs is not None:
                size = getattr(lfs, "size", None)
            return size if isinstance(size, int) and size >= 0 else None
    raise FileNotFoundError("The selected model file is no longer present on Hugging Face")


def _validate_repo_id(repo_id: str) -> None:
    if not isinstance(repo_id, str) or not _REPO_ID.fullmatch(repo_id):
        raise ValueError("Invalid Hugging Face repository identifier")


def _validate_filename(filename: str) -> None:
    if not isinstance(filename, str) or not filename or len(filename) > 512:
        raise ValueError("Invalid model filename")
    path = PurePosixPath(filename)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise ValueError("Model filename must be a safe relative path")
