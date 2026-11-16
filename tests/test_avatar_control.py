from dataclasses import dataclass, field

import pytest

from chiyuri_ai.avatar_control import (
    AvatarAction,
    AvatarActionGateway,
    AvatarExecution,
)
from chiyuri_ai.permissions import ActionRisk, PermissionPolicy


@dataclass
class FakeAvatar:
    capabilities: frozenset[str] = frozenset({"avatar.point", "desktop.click"})
    calls: list = field(default_factory=list)

    def execute_action(self, action_id, parameters, request_id):
        self.calls.append((action_id, parameters, request_id))
        return AvatarExecution(request_id, "completed", "Avatar action completed")


def test_action_is_executed_only_by_avatar_runtime():
    avatar = FakeAvatar()
    gateway = AvatarActionGateway(avatar, PermissionPolicy({"desktop.control"}))
    result = gateway.execute(
        AvatarAction(
            "desktop.click",
            "Click a visible button",
            ActionRisk.REVERSIBLE,
            "desktop.control",
            parameters={"x": 100, "y": 200},
        )
    )
    assert result.status == "completed"
    assert avatar.calls[0][0] == "desktop.click"


def test_unknown_avatar_capability_is_not_executed():
    avatar = FakeAvatar(capabilities=frozenset({"avatar.point"}))
    gateway = AvatarActionGateway(avatar, PermissionPolicy({"desktop.control"}))
    result = gateway.execute(
        AvatarAction("desktop.click", "Click", ActionRisk.REVERSIBLE, "desktop.control")
    )
    assert result.status == "unsupported"
    assert avatar.calls == []


def test_sensitive_action_waits_for_explicit_approval():
    avatar = FakeAvatar()
    gateway = AvatarActionGateway(avatar, PermissionPolicy({"desktop.control"}))
    action = AvatarAction(
        "desktop.click",
        "Confirm a purchase",
        ActionRisk.EXTERNAL,
        "desktop.control",
    )
    result = gateway.execute(action)
    assert result.status == "approval_required"
    assert avatar.calls == []
    result = gateway.execute(action, approved=True)
    assert result.status == "completed"
    assert len(avatar.calls) == 1


def test_missing_permission_scope_denies_even_if_approved():
    avatar = FakeAvatar()
    gateway = AvatarActionGateway(avatar, PermissionPolicy())
    result = gateway.execute(
        AvatarAction("desktop.click", "Click", ActionRisk.REVERSIBLE, "desktop.control"),
        approved=True,
    )
    assert result.status == "denied"
    assert avatar.calls == []


def test_invalid_risk_and_non_boolean_approval_are_rejected():
    avatar = FakeAvatar()
    gateway = AvatarActionGateway(avatar, PermissionPolicy({"desktop.control"}))
    action = AvatarAction("desktop.click", "Click", "external", "desktop.control")
    with pytest.raises(ValueError, match="risk"):
        gateway.execute(action)
    valid = AvatarAction("desktop.click", "Click", ActionRisk.REVERSIBLE, "desktop.control")
    with pytest.raises(ValueError, match="boolean"):
        gateway.execute(valid, approved="yes")
    assert avatar.calls == []


def test_avatar_action_parameters_are_bounded_json():
    with pytest.raises(ValueError, match="valid JSON"):
        AvatarAction(
            "desktop.click",
            "Click",
            ActionRisk.REVERSIBLE,
            "desktop.control",
            parameters={"bad": object()},
        ).validate()
