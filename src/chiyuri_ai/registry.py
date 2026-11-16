"""Permission- and identity-aware registry for callable assistant tools."""
from __future__ import annotations

from collections.abc import Callable, Mapping
from dataclasses import dataclass
from typing import Any
from uuid import uuid4

from chiyuri_ai.audit import AuditLog
from chiyuri_ai.identity import (
    IdentityDecision,
    IdentityIntent,
    OwnerBoundIdentityPolicy,
)
from chiyuri_ai.permissions import (
    ActionRequest,
    ActionRisk,
    PermissionDecision,
    PermissionPolicy,
)

InputValidator = Callable[[Mapping[str, Any]], bool]
ToolHandler = Callable[[Mapping[str, Any]], Any]


@dataclass(frozen=True, slots=True)
class ToolSpec:
    name: str
    description: str
    required_scope: str
    risk: ActionRisk
    validator: InputValidator
    handler: ToolHandler
    identity_intent: IdentityIntent = IdentityIntent.NORMAL


@dataclass(frozen=True, slots=True)
class ToolResult:
    status: str
    message: str
    result: Any = None
    request_id: str = ""


class ToolRegistry:
    """Only explicitly registered tools can run; policy checks precede handlers."""

    def __init__(
        self,
        permissions: PermissionPolicy,
        identity_policy: OwnerBoundIdentityPolicy,
        audit_log: AuditLog,
    ) -> None:
        self._permissions = permissions
        self._identity_policy = identity_policy
        self._audit = audit_log
        self._tools: dict[str, ToolSpec] = {}

    def register(self, spec: ToolSpec) -> None:
        if not spec.name or not spec.name.replace("_", "").replace(".", "").isalnum():
            raise ValueError("Tool name must contain only letters, digits, dots or underscores")
        if spec.name in self._tools:
            raise ValueError(f"Tool already registered: {spec.name}")
        if not callable(spec.validator) or not callable(spec.handler):
            raise ValueError("Tool validator and handler must be callable")
        if not spec.description.strip() or not spec.required_scope.strip():
            raise ValueError("Tool description and permission scope are required")
        self._tools[spec.name] = spec

    def dispatch(self, name: str, arguments: Mapping[str, Any]) -> ToolResult:
        request_id = str(uuid4())
        spec = self._tools.get(name)
        if spec is None:
            self._audit.append(
                action_id=name, request_id=request_id, decision="deny",
                outcome="unknown_tool", description="Unregistered tool invocation",
            )
            return ToolResult("denied", "Tool is not registered", request_id=request_id)
        if not isinstance(arguments, Mapping):
            return self._record_denial(spec, request_id, "invalid_arguments")
        try:
            valid = spec.validator(arguments)
        except Exception:
            valid = False
        if not valid:
            return self._record_denial(spec, request_id, "invalid_arguments")

        identity = self._identity_policy.evaluate(spec.identity_intent)
        if identity.decision is not IdentityDecision.ALLOW:
            self._audit.append(
                action_id=spec.name, request_id=request_id, scope=spec.required_scope,
                risk=spec.risk.value, description=spec.description,
                decision=identity.decision.value, outcome="blocked_by_identity_policy",
            )
            status = (
                "denied" if identity.decision is IdentityDecision.DENY else "approval_required"
            )
            return ToolResult(status, identity.reason, request_id=request_id)

        permission = self._permissions.evaluate(
            ActionRequest(
                action_id=spec.name,
                description=spec.description,
                risk=spec.risk,
                required_scope=spec.required_scope,
            )
        )
        if permission.decision is not PermissionDecision.ALLOW:
            self._audit.append(
                action_id=spec.name, request_id=request_id, scope=spec.required_scope,
                risk=spec.risk.value, description=spec.description,
                decision=permission.decision.value, outcome="handler_not_called",
            )
            status = (
                "approval_required"
                if permission.decision is PermissionDecision.REQUIRE_APPROVAL
                else "denied"
            )
            return ToolResult(status, permission.reason, request_id=request_id)

        # Fail closed: if the start event cannot be persisted, the handler is not invoked.
        self._audit.append(
            action_id=spec.name, request_id=request_id, scope=spec.required_scope,
            risk=spec.risk.value, description=spec.description,
            decision="allow", outcome="started",
        )
        try:
            result = spec.handler(dict(arguments))
        except Exception as exc:
            self._audit.append(
                action_id=spec.name, request_id=request_id, scope=spec.required_scope,
                risk=spec.risk.value, description=spec.description,
                decision="allow", outcome="failed",
                metadata={"error_type": type(exc).__name__},
            )
            return ToolResult("failed", "Tool execution failed", request_id=request_id)

        self._audit.append(
            action_id=spec.name, request_id=request_id, scope=spec.required_scope,
            risk=spec.risk.value, description=spec.description,
            decision="allow", outcome="completed",
        )
        return ToolResult("completed", "Tool executed", result=result, request_id=request_id)

    def _record_denial(self, spec: ToolSpec, request_id: str, reason: str) -> ToolResult:
        self._audit.append(
            action_id=spec.name, request_id=request_id, scope=spec.required_scope,
            risk=spec.risk.value, description=spec.description,
            decision="deny", outcome=reason,
        )
        return ToolResult("denied", reason, request_id=request_id)
