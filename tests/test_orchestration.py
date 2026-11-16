from dataclasses import dataclass

import pytest

from chiyuri_ai.models import Capability, GenerationResult, ModelRouter
from chiyuri_ai.orchestration import ModelCoordinator, SpecialistTask


@dataclass
class FakeAdapter:
    model_id: str
    capabilities: frozenset[Capability]
    output: str

    def is_available(self):
        return True

    def generate(self, request):
        return GenerationResult(self.model_id, self.output)


def test_specialist_results_are_synthesized_by_central_chat_model():
    router = ModelRouter()
    router.register(FakeAdapter("vision-1", frozenset({Capability.VISION}), "A red car"))
    router.register(FakeAdapter("central-1", frozenset({Capability.CHAT}), "I see a red car"))
    router.set_preferred("central-1")

    result = ModelCoordinator(router).execute(
        "What is in this picture?",
        system_prompt="You are Chiyuri",
        tasks=[SpecialistTask("look", Capability.VISION, "Describe the image")],
    )

    assert result.assistant_message == "I see a red car"
    assert result.central_model_id == "central-1"
    assert result.specialists[0].model_id == "vision-1"


def test_unavailable_specialist_is_reported_and_central_model_still_answers():
    router = __import__("chiyuri_ai.models", fromlist=["ModelRouter"]).ModelRouter()
    router.register(FakeAdapter("central-1", frozenset({Capability.CHAT}), "Partial answer"))
    result = ModelCoordinator(router).execute(
        "Do the task",
        tasks=[SpecialistTask("video", Capability.VIDEO, "Analyse clip")],
    )
    assert result.assistant_message == "Partial answer"
    assert "NoModelAvailable" in result.specialists[0].error


def test_chat_cannot_be_delegated_as_a_specialist():
    with pytest.raises(ValueError, match="central chat"):
        SpecialistTask("chat", Capability.CHAT, "Take over identity")


def test_specialist_tasks_are_bounded_and_unique():
    router = __import__("chiyuri_ai.models", fromlist=["ModelRouter"]).ModelRouter()
    router.register(FakeAdapter("central-1", frozenset({Capability.CHAT}), "ok"))
    task = SpecialistTask("same", Capability.VISION, "inspect")
    with pytest.raises(ValueError, match="unique"):
        ModelCoordinator(router).execute("task", tasks=[task, task])
    with pytest.raises(ValueError, match="At most"):
        ModelCoordinator(router).execute(
            "task",
            tasks=[SpecialistTask(str(i), Capability.VISION, "inspect") for i in range(5)],
        )
