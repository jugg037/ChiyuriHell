"""Versioned, bounded JSON protocol between the AI core and Unity avatar runtime."""
from __future__ import annotations

import json
import re
from dataclasses import asdict, dataclass
from uuid import uuid4

PROTOCOL_VERSION = 1
MAX_MESSAGE_BYTES = 256 * 1024
ALLOWED_MESSAGE_TYPES = frozenset({
    "avatar.hello", "avatar.state", "avatar.capabilities", "avatar.play_action",
    "avatar.set_expression", "avatar.set_prop", "avatar.action_result",
    "desktop.action_started", "desktop.action_completed",
})


@dataclass(frozen=True, slots=True)
class AvatarMessage:
    message_type: str
    payload: dict[str, object]
    request_id: str
    version: int = PROTOCOL_VERSION

    @classmethod
    def create(
        cls, message_type: str, payload: dict[str, object], request_id: str | None = None
    ) -> AvatarMessage:
        return cls(message_type, payload, request_id or str(uuid4()))

    def to_json(self) -> str:
        self.validate()
        encoded = json.dumps(asdict(self), ensure_ascii=False, separators=(",", ":"))
        if len(encoded.encode("utf-8")) > MAX_MESSAGE_BYTES:
            raise ValueError("Avatar message exceeds the size limit")
        return encoded

    @classmethod
    def from_json(cls, raw: str) -> AvatarMessage:
        if len(raw.encode("utf-8")) > MAX_MESSAGE_BYTES:
            raise ValueError("Avatar message exceeds the size limit")
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError("Invalid avatar message JSON") from exc
        expected = {"message_type", "payload", "request_id", "version"}
        if not isinstance(data, dict) or set(data) != expected:
            raise ValueError("Avatar message has an invalid shape")
        if not isinstance(data["payload"], dict):
            raise ValueError("Avatar payload must be an object")
        message = cls(data["message_type"], data["payload"], data["request_id"], data["version"])
        message.validate()
        return message

    def validate(self) -> None:
        if self.version != PROTOCOL_VERSION:
            raise ValueError("Unsupported avatar protocol version")
        if self.message_type not in ALLOWED_MESSAGE_TYPES:
            raise ValueError("Unsupported avatar message type")
        if not isinstance(self.request_id, str) or not re.fullmatch(
            r"[A-Za-z0-9_-]{1,64}", self.request_id
        ):
            raise ValueError("Invalid avatar request id")
        if not isinstance(self.payload, dict):
            raise ValueError("Avatar payload must be an object")
