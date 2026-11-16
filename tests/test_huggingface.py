from types import SimpleNamespace

import pytest

from chiyuri_ai.huggingface import (
    HuggingFaceCatalog,
    HuggingFaceModelDownloader,
    _validate_filename,
    _validate_repo_id,
)


class FakeApi:
    def list_models(self, **kwargs):
        self.search_kwargs = kwargs
        return [
            SimpleNamespace(
                id="huihui-ai/model-a",
                downloads=120,
                likes=4,
                pipeline_tag="text-generation",
                tags=["gguf"],
                card_data=SimpleNamespace(description="A test model"),
            ),
            SimpleNamespace(
                id="../bad/repo",
                downloads=0,
                likes=0,
                pipeline_tag=None,
                tags=[],
                card_data=None,
            ),
        ]

    def model_info(self, repo_id, files_metadata=True):
        assert repo_id == "huihui-ai/model-a"
        return SimpleNamespace(
            siblings=[
                SimpleNamespace(rfilename="Q4_K_M/model.gguf", size=1024, lfs=None),
                SimpleNamespace(rfilename="README.md", size=50, lfs=None),
                SimpleNamespace(rfilename="../escape.gguf", size=10, lfs=None),
            ]
        )


def test_search_defaults_to_huihui_ai_and_filters_invalid_repo_ids():
    api = FakeApi()
    catalog = HuggingFaceCatalog(api=api)
    results = catalog.search("qwen")

    assert [item.repo_id for item in results] == ["huihui-ai/model-a"]
    assert api.search_kwargs["author"] == "huihui-ai"
    assert results[0].downloads == 120


def test_search_allows_explicit_all_authors_and_validates_limit():
    api = FakeApi()
    catalog = HuggingFaceCatalog(api=api)
    catalog.search(author="")
    assert api.search_kwargs["author"] is None
    with pytest.raises(ValueError):
        catalog.search(limit=0)


def test_list_gguf_files_only_returns_safe_gguf_files():
    files = HuggingFaceCatalog(api=FakeApi()).list_gguf_files("huihui-ai/model-a")
    assert [(item.filename, item.size_bytes) for item in files] == [
        ("Q4_K_M/model.gguf", 1024)
    ]


def test_repo_and_filename_validation():
    _validate_repo_id("huihui-ai/model-a")
    _validate_filename("Q4_K_M/model.gguf")
    with pytest.raises(ValueError):
        _validate_repo_id("../model")
    with pytest.raises(ValueError):
        _validate_filename("../model.gguf")
    with pytest.raises(ValueError):
        _validate_filename("/absolute/model.gguf")
    with pytest.raises(ValueError):
        _validate_filename("folder" + chr(92) + ".." + chr(92) + "escape.gguf")
    with pytest.raises(ValueError):
        _validate_filename("folder//model.gguf")


def test_downloader_rejects_non_gguf_before_network(tmp_path):
    downloader = HuggingFaceModelDownloader(tmp_path)
    with pytest.raises(ValueError, match="Only GGUF"):
        downloader.download("huihui-ai/model-a", "weights.safetensors")
