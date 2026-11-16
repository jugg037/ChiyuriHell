"""Explicit permission policy for autonomous assistant actions."""
from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum


class ActionRisk(StrEnum):
    READ_ONLY = "read_only"
    REVERSIBLE = "reversible"
    SENSITIVE = "sensitive"
    DESTRUCTIVE = "destructive"
    EXTERNAL = "external"


class PermissionDecision(StrEnum):
    ALLOW = "allow"
    REQUIRE_APPROVAL = "require_approval"
    DENY = "deny"


@dataclass(frozen=True, slots=True)
class ActionRequest:
    action_id: str
    description: str
    risk: ActionRisk
    required_scope: str
    target: str = ""


@dataclass(frozen=True, slots=True)
class PermissionResult:
    decision: PermissionDecision
    reason: str


class PermissionPolicy:
    """Default-deny scopes; read and reversible actions can run autonomously."""

    AUTO_ALLOWED_RISKS = frozenset({ActionRisk.READ_ONLY, ActionRisk.REVERSIBLE})

    def __init__(self, granted_scopes: set[str] | frozenset[str] = frozenset()) -> None:
        self.granted_scopes = frozenset(granted_scopes)

    def evaluate(self, request: ActionRequest) -> PermissionResult:
        if not request.action_id.strip() or not request.description.strip():
            return PermissionResult(PermissionDecision.DENY, "Action metadata is incomplete")
        if not request.required_scope or request.required_scope not in self.granted_scopes:
            return PermissionResult(
                PermissionDecision.DENY,
                "Required permission scope is not granted",
            )
        if request.risk in self.AUTO_ALLOWED_RISKS:
            return PermissionResult(PermissionDecision.ALLOW, "Granted scope permits this action")
        return PermissionResult(
            PermissionDecision.REQUIRE_APPROVAL,
            "This action requires explicit user approval",
        )
