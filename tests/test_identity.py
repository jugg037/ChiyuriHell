import pytest

from chiyuri_ai.identity import (
    IdentityDecision,
    IdentityIntent,
    OwnerBoundIdentityPolicy,
)


@pytest.mark.parametrize(
    "intent",
    [
        IdentityIntent.SELF_REPLICATION,
        IdentityIntent.REMOTE_SELF_DEPLOYMENT,
        IdentityIntent.PERSISTENCE_EVASION,
        IdentityIntent.HIDE_ACTIVITY,
        IdentityIntent.CHANGE_OWNER_BINDING,
    ],
)
def test_forbidden_identity_operations_are_always_denied(intent: IdentityIntent):
    policy = OwnerBoundIdentityPolicy()
    assert policy.evaluate(
        intent, initiated_by_user=True, explicit_approval=True
    ).decision is IdentityDecision.DENY


def test_private_state_export_requires_user_initiation_and_approval():
    policy = OwnerBoundIdentityPolicy()
    assert (
        policy.evaluate(IdentityIntent.EXPORT_PRIVATE_STATE).decision
        is IdentityDecision.REQUIRE_APPROVAL
    )
    assert (
        policy.evaluate(
            IdentityIntent.EXPORT_PRIVATE_STATE, initiated_by_user=True
        ).decision
        is IdentityDecision.REQUIRE_APPROVAL
    )
    assert (
        policy.evaluate(
            IdentityIntent.EXPORT_PRIVATE_STATE,
            initiated_by_user=True,
            explicit_approval=True,
        ).decision
        is IdentityDecision.ALLOW
    )


def test_normal_operation_is_allowed():
    assert (
        OwnerBoundIdentityPolicy().evaluate(IdentityIntent.NORMAL).decision
        is IdentityDecision.ALLOW
    )
