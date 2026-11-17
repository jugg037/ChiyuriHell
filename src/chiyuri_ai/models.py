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
    """Contract implemented by llama.cpp, cloud APIs and media adapters."""

    @property
    def model_id(self) -> str: ...

    @property
    def capabilities(self) -> frozenset[Capability]: ...

    def is_available(self) -> bool: ...

    def generate(self, request: GenerationRequest) -> GenerationResult: ...


class NoModelAvailable(RuntimeError):
    """Raised when no installed and healthy adapter supports a capability."""


class ModelRouter:
    """Selects a preferred compatible adapter and falls back to other providers."""

    def __init__(self) -> None:
        self._adapters: list[ModelAdapter] = []
        self._preferred_model_id: str | None = None

    def register(self, adapter: ModelAdapter) -> None:
        if any(item.model_id == adapter.model_id for item in self._adapters):
            raise ValueError(f"Adapter already registered: {adapter.model_id}")
        self._adapters.append(adapter)

    @property
    def preferred_model_id(self) -> str | None:
        return self._preferred_model_id

    def set_preferred(self, model_id: str | None) -> None:
        if model_id is not None and not any(
            adapter.model_id == model_id for adapter in self._adapters
        ):
            raise ValueError(f"Unknown model: {model_id}")
        self._preferred_model_id = model_id

    @staticmethod
    def _is_available(adapter: ModelAdapter) -> bool:
        import logging

        try:
            return adapter.is_available()
        except Exception as exc:  # noqa: BLE001 - one broken adapter must not break routing
            logging.getLogger(__name__).warning(
                "Availability check failed for model %s (%s)",
                adapter.model_id,
                type(exc).__name__,
                exc_info=True,
            )
            return False

    def available_models(self, capability: Capability | None = None) -> list[str]:
        return [
            adapter.model_id
            for adapter in self._adapters
            if self._is_available(adapter)
            and (capability is None or capability in adapter.capabilities)
        ]

    def generate(self, request: GenerationRequest) -> GenerationResult:
        compatible = [
            adapter for adapter in self._adapters
            if request.capability in adapter.capabilities and self._is_available(adapter)
        ]
        if self._preferred_model_id is not None:
            compatible.sort(key=lambda item: item.model_id != self._preferred_model_id)
        failures: list[str] = []
        for adapter in compatible:
            try:
                return adapter.generate(request)
            except Exception as exc:  # noqa: BLE001 - fall back to another configured provider
                logging.getLogger(__name__).warning(
                    "Model adapter %s failed (%s)",
                    adapter.model_id,
                    type(exc).__name__,
                    exc_info=True,
                )
                failures.append(f"{adapter.model_id}: {type(exc).__name__}")
        detail = "; ".join(failures) if failures else "no compatible provider is configured"
        raise NoModelAvailable(
            f"No healthy model for capability '{request.capability.value}': {detail}"
        )
