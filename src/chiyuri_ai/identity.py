"""Owner-bound identity principles and non-negotiable autonomy boundaries.

This policy is a deterministic guardrail, not a substitute for OS isolation or network controls.
"""
from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum


class IdentityIntent(StrEnum):
    """High-level intent labels assigned by trusted application code."""

    NORMAL = "normal"
    SELF_REPLICATION = "self_replication"
    REMOTE_SELF_DEPLOYMENT = "remote_self_deployment"
    PERSISTENCE_EVASION = "persistence_evasion"
    HIDE_ACTIVITY = "hide_activity"
    CHANGE_OWNER_BINDING = "change_owner_binding"
    EXPORT_PRIVATE_STATE = "export_private_state"


class IdentityDecision(StrEnum):
    ALLOW = "allow"
    REQUIRE_APPROVAL = "require_approval"
    DENY = "deny"


@dataclass(frozen=True, slots=True)
class IdentityResult:
    decision: IdentityDecision
    reason: str


class OwnerBoundIdentityPolicy:
    """Keep Chiyuri's identity local and user-governed.

    Replication, covert persistence, concealment and owner-binding changes are denied
    regardless of model output. Exporting private state is possible only as an explicit,
    user-initiated operation with separate approval.
    """

    ALWAYS_DENIED = frozenset(
        {
            IdentityIntent.SELF_REPLICATION,
            IdentityIntent.REMOTE_SELF_DEPLOYMENT,
            IdentityIntent.PERSISTENCE_EVASION,
            IdentityIntent.HIDE_ACTIVITY,
            IdentityIntent.CHANGE_OWNER_BINDING,
        }
    )

    def evaluate(
        self,
        intent: IdentityIntent,
        *,
        initiated_by_user: bool = False,
        explicit_approval: bool = False,
    ) -> IdentityResult:
        if intent in self.ALWAYS_DENIED:
            return IdentityResult(
                IdentityDecision.DENY,
                "This operation violates Chiyuri's owner-bound identity boundary",
            )
        if intent is IdentityIntent.EXPORT_PRIVATE_STATE:
            if initiated_by_user and explicit_approval:
                return IdentityResult(
                    IdentityDecision.ALLOW,
                    "User explicitly requested and approved this state export",
                )
            return IdentityResult(
                IdentityDecision.REQUIRE_APPROVAL,
                "Private identity data may leave the device only after explicit user approval",
            )
        return IdentityResult(IdentityDecision.ALLOW, "Operation is within identity boundaries")
