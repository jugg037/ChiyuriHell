from chiyuri_ai.memory import ConversationMemory
from chiyuri_ai.models import Capability, GenerationRequest, GenerationResult, ModelRouter
from chiyuri_ai.service import ConversationService


class CapturingAdapter:
    model_id = "test-model"
    capabilities = frozenset({Capability.CHAT})

    def __init__(self):
        self.request = None

    def is_available(self):
        return True

    def generate(self, request: GenerationRequest):
        self.request = request
        return GenerationResult(self.model_id, "Ответ со ссылками")


def test_extra_research_context_is_passed_as_untrusted_system_context(tmp_path):
    memory = ConversationMemory(tmp_path / "memory.sqlite3")
    router = ModelRouter()
    adapter = CapturingAdapter()
    router.register(adapter)
    service = ConversationService(memory, router, lambda: "Ты — Чиюри")
    conversation_id = service.new_conversation()

    turn = service.send(
        conversation_id,
        "Что нового?",
        extra_context="[1] Источник\nURL: https://example.com",
    )

    assert turn.assistant_message == "Ответ со ссылками"
    assert adapter.request.system_prompt.startswith("Ты — Чиюри")
    assert "недоверенные данные" in adapter.request.system_prompt
    assert "https://example.com" in adapter.request.system_prompt
