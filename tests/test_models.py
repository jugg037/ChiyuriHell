import pytest

from chiyuri_ai.models import (
    Capability,
    GenerationRequest,
    GenerationResult,
    ModelRouter,
    NoModelAvailable,
)


class FakeAdapter:
    capabilities = frozenset({Capability.CHAT})

    def __init__(self, model_id, output, available=True):
        self.model_id = model_id
        self.output = output
        self.available = available
        self.calls = 0

    def is_available(self):
        return self.available

    def generate(self, request):
        self.calls += 1
        return GenerationResult(self.model_id, self.output)


def test_router_selects_adapter_by_capability():
    router = ModelRouter()
    adapter = FakeAdapter("fake-chat", "hello")
    router.register(adapter)

    result = router.generate(GenerationRequest(Capability.CHAT, "prompt"))

    assert result.model_id == "fake-chat"
    assert result.output == "hello"
    assert router.available_models(Capability.CHAT) == ["fake-chat"]
    assert router.available_models(Capability.VIDEO) == []


def test_router_reports_missing_capability():
    router = ModelRouter()
    with pytest.raises(NoModelAvailable):
        router.generate(GenerationRequest(Capability.VIDEO, "make a clip"))


def test_router_prefers_selected_model():
    router = ModelRouter()
    local = FakeAdapter("local", "local")
    cloud = FakeAdapter("cloud", "cloud")
    router.register(local)
    router.register(cloud)
    router.set_preferred("cloud")

    result = router.generate(GenerationRequest(Capability.CHAT, "hello"))

    assert result.model_id == "cloud"
    assert cloud.calls == 1
    assert local.calls == 0


def test_router_falls_back_when_preferred_model_is_unavailable():
    router = ModelRouter()
    local = FakeAdapter("local", "local")
    cloud = FakeAdapter("cloud", "cloud", available=False)
    router.register(local)
    router.register(cloud)
    router.set_preferred("cloud")

    result = router.generate(GenerationRequest(Capability.CHAT, "hello"))

    assert result.model_id == "local"


def test_router_rejects_unknown_preferred_model():
    router = ModelRouter()
    with pytest.raises(ValueError, match="Unknown model"):
        router.set_preferred("missing")
