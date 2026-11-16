"""Capability-based model adapter contracts and routing."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum
from typing import Protocol


class Capability(StrEnum):
    CHAT = "chat"
    VISION = "vision"
    SPEECH_TO_TEXT = "speech_to_text"
    TEXT_TO_SPEECH = "text_to_speech"
    IMAGE = "image"
    VIDEO = "video"


@dataclass(frozen=True, slots=True)
class GenerationRequest:
    capability: Capability
    prompt: str
    conversation_id: str | None = None
    system_prompt: str = ""
    options: dict[str, object] = field(default_factory=dict)


@dataclass(frozen=True, slots=True)
class GenerationResult:
    model_id: str
    output: str
    metadata: dict[str, object] = field(default_factory=dict)


class ModelAdapter(Protocol):
    """Contract implemented by llama.cpp, ComfyUI and voice adapters."""

    @property
    def model_id(self) -> str: ...

    @property
    def capabilities(self) -> frozenset[Capability]: ...

    def is_available(self) -> bool: ...

    def generate(self, request: GenerationRequest) -> GenerationResult: ...


class NoModelAvailable(RuntimeError):
    """Raised when no installed and healthy adapter supports a capability."""


class ModelRouter:
    """Selects an available local adapter without binding the UI to a runtime."""

    def __init__(self) -> None:
        self._adapters: list[ModelAdapter] = []

    def register(self, adapter: ModelAdapter) -> None:
        if any(item.model_id == adapter.model_id for item in self._adapters):
            raise ValueError(f"Adapter already registered: {adapter.model_id}")
        self._adapters.append(adapter)

    def available_models(self, capability: Capability | None = None) -> list[str]:
        return [
            adapter.model_id
            for adapter in self._adapters
            if adapter.is_available()
            and (capability is None or capability in adapter.capabilities)
        ]

    def generate(self, request: GenerationRequest) -> GenerationResult:
        failures: list[str] = []
        for adapter in self._adapters:
            if request.capability not in adapter.capabilities or not adapter.is_available():
                continue
            try:
                return adapter.generate(request)
            except Exception as exc:  # noqa: BLE001 - try the next configured local adapter
                failures.append(f"{adapter.model_id}: {exc}")
        detail = "; ".join(failures) if failures else "no compatible local adapter is installed"
        raise NoModelAvailable(
            f"No healthy model for capability '{request.capability.value}': {detail}"
        )
