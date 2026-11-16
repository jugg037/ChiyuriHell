"""Application-level conversation orchestration."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

from chiyuri_ai.memory import ConversationMemory
from chiyuri_ai.models import Capability, GenerationRequest, ModelRouter


@dataclass(frozen=True, slots=True)
class Turn:
    conversation_id: str
    user_message: str
    assistant_message: str
    model_id: str


class ConversationService:
    """Persists a user turn and routes it through the selected chat adapter."""

    def __init__(
        self,
        memory: ConversationMemory,
        router: ModelRouter,
        system_prompt_provider: Callable[[], str] | None = None,
    ) -> None:
        self.memory = memory
        self.router = router
        self.system_prompt_provider = system_prompt_provider or (lambda: "")

    def new_conversation(self, title: str = "Новый диалог") -> str:
        return self.memory.create_conversation(title)

    def send(self, conversation_id: str, message: str) -> Turn:
        self.memory.add_message(conversation_id, "user", message)
        history = self.memory.get_messages(conversation_id)
        prompt = "\n".join(
            f"{item['role']}: {item['content']}" for item in history
        )
        result = self.router.generate(
            GenerationRequest(
                capability=Capability.CHAT,
                prompt=prompt,
                conversation_id=conversation_id,
                system_prompt=self.system_prompt_provider(),
            )
        )
        self.memory.add_message(conversation_id, "assistant", result.output)
        return Turn(
            conversation_id=conversation_id,
            user_message=message,
            assistant_message=result.output,
            model_id=result.model_id,
        )
