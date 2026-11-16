"""Avatar-only execution gateway for visible desktop interactions.

The AI core can request semantic avatar actions, but this module has no direct
mouse, keyboard, shell, or operating-system execution implementation. A future
Unity runtime must advertise the capability and perform the action itself.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Protocol
from uuid import uuid4

from chiyuri_ai.permissions import (
    ActionRequest,
    ActionRisk,
    PermissionDecision,
    PermissionPolicy,
)

_ACTION_ID = re.compile(r"^[a-z][a-z0-9_.-]{1,63}$")
_MAX_PARAMETERS_BYTES = 32_768


@dataclass(frozen=True, slots=True)
class AvatarAction:
    action_id: str
    description: str
    risk: ActionRisk
    required_scope: str
    target: str = ""
    parameters: dict[str, object] | None = None

    def validate(self) -> None:
        if not isinstance(self.action_id, str) or not _ACTION_ID.fullmatch(self.action_id):
            raise ValueError("Invalid avatar action id")
        if not isinstance(self.description, str) or not self.description.strip():
            raise ValueError("Avatar action description is required")
        if not isinstance(self.required_scope, str) or not self.required_scope.strip():
            raise ValueError("Avatar action permission scope is required")
        if not isinstance(self.parameters or {}, dict):
            raise ValueError("Avatar action parameters must be an object")
        try:
            encoded = json.dumps(
                self.parameters or {}, ensure_ascii=False, allow_nan=False
            ).encode("utf-8")
        except (TypeError, ValueError) as exc:
            raise ValueError("Avatar action parameters must be valid JSON values") from exc
        if len(encoded) > _MAX_PARAMETERS_BYTES:
            raise ValueError("Avatar action parameters exceed the size limit")


@dataclass(frozen=True, slots=True)
class AvatarExecution:
    request_id: str
    status: str
    message: str
    result: dict[str, object] | None = None


class AvatarRuntime(Protocol):
    """Implemented by the connected avatar runtime, not by OS-side shortcuts."""

    @property
    def capabilities(self) -> frozenset[str]: ...

    def execute_action(
        self,
        action_id: str,
        parameters: dict[str, object],
        request_id: str,
    ) -> AvatarExecution: ...


class AvatarActionGateway:
    """Allows actions only through capabilities advertised by the live avatar."""

    def __init__(
        self,
        runtime: AvatarRuntime,
        permission_policy: PermissionPolicy,
    ) -> None:
        self.runtime = runtime
        self.permission_policy = permission_policy

    def execute(self, action: AvatarAction, *, approved: bool = False) -> AvatarExecution:
        action.validate()
        request_id = str(uuid4())
        if action.action_id not in self.runtime.capabilities:
            return AvatarExecution(
                request_id, "unsupported", "Аватар не объявил такую возможность."
            )

        permission = self.permission_policy.evaluate(
            ActionRequest(
                action_id=action.action_id,
                description=action.description,
                risk=action.risk,
                required_scope=action.required_scope,
                target=action.target,
            )
        )
        if permission.decision is PermissionDecision.DENY:
            return AvatarExecution(request_id, "denied", permission.reason)
        if permission.decision is PermissionDecision.REQUIRE_APPROVAL and not approved:
            return AvatarExecution(request_id, "approval_required", permission.reason)

        # The only execution path is the avatar runtime. No direct desktop fallback exists.
        return self.runtime.execute_action(
            action.action_id,
            action.parameters or {},
            request_id,
        )
