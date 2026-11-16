"""User-configured OpenAI-compatible chat providers.

Secrets are referenced by environment-variable name and are never persisted here.
External conversation transfer requires an explicit per-provider consent flag.
"""

from __future__ import annotations

import json
import os
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from urllib.parse import urlsplit

from chiyuri_ai.egress import EgressDenied, NetworkEgressPolicy, _PinnedHTTPSConnection
from chiyuri_ai.models import Capability, GenerationRequest, GenerationResult


_PROVIDER_ID = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
_ENV_NAME = re.compile(r"^[A-Z_][A-Z0-9_]{0,127}$")
_MAX_REQUEST_BYTES = 2_000_000
_MAX_RESPONSE_BYTES = 4_000_000


@dataclass(frozen=True, slots=True)
class ProviderConfig:
    """Non-secret configuration for one explicitly connected chat provider."""

    provider_id: str
    name: str
    base_url: str
    model: str
    api_key_env: str = ""
    enabled: bool = True
    data_sharing_approved: bool = False
    timeout_seconds: float = 90.0

    def __post_init__(self) -> None:
        if not isinstance(self.provider_id, str) or not _PROVIDER_ID.fullmatch(self.provider_id):
            raise ValueError("Provider id must contain 1-64 ASCII letters, digits, '_' or '-'")
        if not isinstance(self.name, str) or not self.name.strip():
            raise ValueError("Provider name is required")
        if not isinstance(self.model, str) or not self.model.strip():
            raise ValueError("Provider model name is required")
        if self.api_key_env and not _ENV_NAME.fullmatch(self.api_key_env):
            raise ValueError("API key must be referenced by a valid environment variable name")
        if not isinstance(self.enabled, bool) or not isinstance(self.data_sharing_approved, bool):
            raise ValueError("Provider enablement and data-sharing consent must be boolean")
        if not isinstance(self.timeout_seconds, (int, float)) or not 1 <= self.timeout_seconds <= 600:
            raise ValueError("Provider timeout must be between 1 and 600 seconds")
        parsed = urlsplit(self.base_url)
        try:
            port = parsed.port or 443
        except ValueError as exc:
            raise ValueError("Provider URL contains an invalid port") from exc
        if (
            parsed.scheme.lower() != "https"
            or not parsed.hostname
            or parsed.username
            or parsed.password
            or parsed.query
            or parsed.fragment
            or port != 443
        ):
            raise ValueError("Cloud providers must use an HTTPS URL on port 443 without credentials")
        if not self.base_url.strip() or any(char.isspace() for char in self.base_url):
            raise ValueError("Provider URL cannot be empty or contain whitespace")


class ProviderStore:
    """Stores provider settings without API-key values."""

    def __init__(self, directory: Path) -> None:
        self.directory = directory
        self.directory.mkdir(parents=True, exist_ok=True)

    def save(self, config: ProviderConfig) -> Path:
        path = self.directory / f"{config.provider_id}.json"
        temporary = path.with_suffix(".json.tmp")
        temporary.write_text(
            json.dumps(asdict(config), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        temporary.replace(path)
        return path

    def load(self, provider_id: str) -> ProviderConfig:
        if not _PROVIDER_ID.fullmatch(provider_id):
            raise ValueError("Invalid provider id")
        data = json.loads(
            (self.directory / f"{provider_id}.json").read_text(encoding="utf-8")
        )
        if not isinstance(data, dict) or data.get("provider_id") != provider_id:
            raise ValueError("Provider configuration is invalid")
        return ProviderConfig(**data)

    def list_configs(self) -> list[ProviderConfig]:
        configs = []
        for path in sorted(self.directory.glob("*.json")):
            if not _PROVIDER_ID.fullmatch(path.stem):
                continue
            configs.append(self.load(path.stem))
        return configs

    def delete(self, provider_id: str) -> None:
        if not _PROVIDER_ID.fullmatch(provider_id):
            raise ValueError("Invalid provider id")
        (self.directory / f"{provider_id}.json").unlink(missing_ok=True)


class OpenAICompatibleAdapter:
    """Chat adapter for explicitly configured HTTPS OpenAI-compatible APIs."""

    def __init__(self, config: ProviderConfig) -> None:
        self.config = config
        parsed = urlsplit(config.base_url)
        self._host = parsed.hostname or ""
        self._base_path = parsed.path.rstrip("/")
        self._policy = NetworkEgressPolicy({self._host})

    @property
    def model_id(self) -> str:
        return f"{self.config.provider_id}:{self.config.model}"

    @property
    def capabilities(self) -> frozenset[Capability]:
        return frozenset({Capability.CHAT})

    def is_available(self) -> bool:
        if not self.config.enabled or not self.config.data_sharing_approved:
            return False
        return not self.config.api_key_env or bool(os.getenv(self.config.api_key_env))

    def generate(self, request: GenerationRequest) -> GenerationResult:
        if request.capability not in self.capabilities:
            raise ValueError(f"Unsupported capability: {request.capability.value}")
        if not self.config.enabled:
            raise PermissionError("Provider is disabled")
        if not self.config.data_sharing_approved:
            raise PermissionError(
                "Sending conversation data to this provider requires explicit consent"
            )
        api_key = os.getenv(self.config.api_key_env) if self.config.api_key_env else None
        if self.config.api_key_env and not api_key:
            raise RuntimeError(
                f"API key environment variable '{self.config.api_key_env}' is not configured"
            )

        temperature = float(request.options.get("temperature", 0.7))
        max_tokens = int(request.options.get("max_tokens", 1024))
        if not 0 <= temperature <= 2:
            raise ValueError("temperature must be between 0 and 2")
        if not 1 <= max_tokens <= 32768:
            raise ValueError("max_tokens must be between 1 and 32768")

        messages = []
        if request.system_prompt.strip():
            messages.append({"role": "system", "content": request.system_prompt})
        messages.append({"role": "user", "content": request.prompt})
        payload = json.dumps(
            {
                "model": self.config.model,
                "messages": messages,
                "temperature": temperature,
                "max_tokens": max_tokens,
                "stream": False,
            },
            ensure_ascii=False,
        ).encode("utf-8")
        if len(payload) > _MAX_REQUEST_BYTES:
            raise ValueError("Provider request exceeds the configured size limit")

        url = f"{self.config.base_url.rstrip('/')}/chat/completions"
        try:
            endpoint = self._policy.authorize(
                url,
                contains_user_data=True,
                user_approved=self.config.data_sharing_approved,
            )
        except EgressDenied as exc:
            raise PermissionError(str(exc)) from exc

        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "ChiyuriAI/0.1",
        }
        if api_key:
            headers["Authorization"] = f"Bearer {api_key}"
        connection = _PinnedHTTPSConnection(
            endpoint.host,
            endpoint.port,
            endpoint.addresses,
            timeout=float(self.config.timeout_seconds),
        )
        try:
            connection.request("POST", endpoint.request_target, body=payload, headers=headers)
            response = connection.getresponse()
            if 300 <= response.status < 400:
                raise RuntimeError("Provider redirects are blocked")
            response_body = response.read(_MAX_RESPONSE_BYTES + 1)
            if response.status >= 400:
                raise RuntimeError(f"Provider returned HTTP {response.status}")
            if len(response_body) > _MAX_RESPONSE_BYTES:
                raise RuntimeError("Provider response exceeded the configured size limit")
        except (OSError, TimeoutError) as exc:
            raise RuntimeError("Provider request failed") from exc
        finally:
            connection.close()

        try:
            result = json.loads(response_body.decode("utf-8"))
            output = result["choices"][0]["message"]["content"]
        except (UnicodeDecodeError, json.JSONDecodeError, KeyError, IndexError, TypeError) as exc:
            raise RuntimeError("Provider returned an unexpected response") from exc
        if not isinstance(output, str) or not output.strip():
            raise RuntimeError("Provider returned an empty or unsupported response")
        usage = result.get("usage", {})
        if not isinstance(usage, dict):
            usage = {}
        return GenerationResult(
            model_id=self.model_id,
            output=output.strip(),
            metadata={"usage": usage, "provider_id": self.config.provider_id},
        )
