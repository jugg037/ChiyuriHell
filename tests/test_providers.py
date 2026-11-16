import pytest

from chiyuri_ai.models import Capability, GenerationRequest
from chiyuri_ai.providers import (
    OpenAICompatibleAdapter,
    ProviderConfig,
    ProviderStore,
)


def config(**overrides):
    values = {
        "provider_id": "cloud-a",
        "name": "Cloud provider",
        "base_url": "https://api.example.com/v1",
        "model": "chat-model",
        "api_key_env": "CHIYURI_TEST_API_KEY",
        "data_sharing_approved": True,
    }
    values.update(overrides)
    return ProviderConfig(**values)


def test_provider_config_rejects_non_https_and_credentials():
    with pytest.raises(ValueError):
        config(base_url="http://api.example.com/v1")
    with pytest.raises(ValueError):
        config(base_url="https://user:pass@api.example.com/v1")


def test_provider_config_requires_explicit_data_sharing_consent():
    adapter = OpenAICompatibleAdapter(config(data_sharing_approved=False))
    assert adapter.is_available() is False
    with pytest.raises(PermissionError, match="explicit consent"):
        adapter.generate(GenerationRequest(Capability.CHAT, "hello"))


def test_provider_store_round_trip_without_secret_values(tmp_path, monkeypatch):
    monkeypatch.setenv("CHIYURI_TEST_API_KEY", "never-persist-this")
    store = ProviderStore(tmp_path / "providers")
    original = config()
    path = store.save(original)

    assert store.load("cloud-a") == original
    assert store.list_configs() == [original]
    assert "never-persist-this" not in path.read_text(encoding="utf-8")
    assert "CHIYURI_TEST_API_KEY" in path.read_text(encoding="utf-8")


def test_provider_store_rejects_path_traversal(tmp_path):
    store = ProviderStore(tmp_path)
    with pytest.raises(ValueError):
        store.load("../secret")


def test_adapter_requires_configured_api_key(monkeypatch):
    monkeypatch.delenv("CHIYURI_TEST_API_KEY", raising=False)
    adapter = OpenAICompatibleAdapter(config())
    assert adapter.is_available() is False
    with pytest.raises(RuntimeError, match="environment variable"):
        adapter.generate(GenerationRequest(Capability.CHAT, "hello"))


def test_adapter_advertises_chat_only():
    adapter = OpenAICompatibleAdapter(config())
    assert adapter.capabilities == frozenset({Capability.CHAT})
    assert adapter.model_id == "cloud-a:chat-model"


def test_provider_store_delete(tmp_path):
    store = ProviderStore(tmp_path)
    store.save(config())
    store.delete("cloud-a")
    assert store.list_configs() == []
