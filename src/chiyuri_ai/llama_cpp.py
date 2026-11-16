"""OpenAI-compatible adapter for a local llama.cpp server."""

from __future__ import annotations

import json
import os
from urllib.error import URLError
from urllib.request import Request, urlopen

from chiyuri_ai.models import Capability, GenerationRequest, GenerationResult


class LlamaCppServerAdapter:
    """Connects to llama-server without requiring a cloud account or SDK."""

    def __init__(
        self,
        base_url: str | None = None,
        model_id: str | None = None,
        timeout_seconds: float = 180,
    ) -> None:
        self.base_url = (base_url or os.getenv(
            "CHIYURI_LLAMACPP_URL", "http://127.0.0.1:8080"
        )).rstrip("/")
        self._model_id = model_id or os.getenv("CHIYURI_LLAMACPP_MODEL", "local-llm")
        self.timeout_seconds = timeout_seconds

    @property
    def model_id(self) -> str:
        return self._model_id

    @property
    def capabilities(self) -> frozenset[Capability]:
        return frozenset({Capability.CHAT})

    def is_available(self) -> bool:
        try:
            request = Request(f"{self.base_url}/health", method="GET")
            with urlopen(request, timeout=1.5) as response:
                return 200 <= response.status < 300
        except (OSError, URLError, TimeoutError):
            return False

    def generate(self, request: GenerationRequest) -> GenerationResult:
        if request.capability not in self.capabilities:
            raise ValueError(f"Unsupported capability: {request.capability.value}")
        payload = {
            "model": self._model_id,
            "messages": [{"role": "user", "content": request.prompt}],
            "temperature": float(request.options.get("temperature", 0.7)),
            "max_tokens": int(request.options.get("max_tokens", 1024)),
            "stream": False,
        }
        body = json.dumps(payload).encode("utf-8")
        http_request = Request(
            f"{self.base_url}/v1/chat/completions",
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urlopen(http_request, timeout=self.timeout_seconds) as response:
                result = json.loads(response.read().decode("utf-8"))
        except (OSError, URLError, TimeoutError, json.JSONDecodeError) as exc:
            raise RuntimeError(f"llama.cpp request failed: {exc}") from exc

        try:
            output = result["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise RuntimeError("llama.cpp returned an unexpected response") from exc
        if not isinstance(output, str) or not output.strip():
            raise RuntimeError("llama.cpp returned an empty response")
        return GenerationResult(
            model_id=self._model_id,
            output=output.strip(),
            metadata={"usage": result.get("usage", {})},
        )
