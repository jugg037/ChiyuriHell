import pytest

from chiyuri_ai.models import (
    Capability,
    GenerationRequest,
    GenerationResult,
    ModelRouter,
    NoModelAvailable,
)


class FakeAdapter:
    model_id = "fake-chat"
    capabilities = frozenset({Capability.CHAT})

    def is_available(self):
        return True

    def generate(self, request):
        return GenerationResult(self.model_id, f"echo: {request.prompt}")


def test_router_selects_adapter_by_capability():
    router = ModelRouter()
    router.register(FakeAdapter())

    result = router.generate(GenerationRequest(Capability.CHAT, "hello"))

    assert result.model_id == "fake-chat"
    assert result.output == "echo: hello"
    assert router.available_models(Capability.CHAT) == ["fake-chat"]
    assert router.available_models(Capability.VIDEO) == []


def test_router_reports_missing_capability():
    router = ModelRouter()

    with pytest.raises(NoModelAvailable):
        router.generate(GenerationRequest(Capability.VIDEO, "make a clip"))
