import pytest

from chiyuri_ai.permissions import (
    ActionRequest, ActionRisk, PermissionDecision, PermissionPolicy,
)


def test_read_only_and_reversible_actions_are_allowed_with_scope():
    policy = PermissionPolicy({"desktop.read", "desktop.window"})
    read = ActionRequest("read-window", "Read active window", ActionRisk.READ_ONLY, "desktop.read")
    move = ActionRequest("move-window", "Move a window", ActionRisk.REVERSIBLE, "desktop.window")
    assert policy.evaluate(read).decision is PermissionDecision.ALLOW
    assert policy.evaluate(move).decision is PermissionDecision.ALLOW


@pytest.mark.parametrize("risk", [
    ActionRisk.SENSITIVE, ActionRisk.DESTRUCTIVE, ActionRisk.EXTERNAL,
])
def test_sensitive_actions_require_explicit_approval(risk: ActionRisk):
    policy = PermissionPolicy({"files.write"})
    request = ActionRequest("action", "Do something", risk, "files.write")
    assert policy.evaluate(request).decision is PermissionDecision.REQUIRE_APPROVAL


def test_missing_scope_is_denied_even_for_read_only_action():
    request = ActionRequest("read", "Read file", ActionRisk.READ_ONLY, "files.read")
    assert PermissionPolicy().evaluate(request).decision is PermissionDecision.DENY


def test_incomplete_action_is_denied():
    request = ActionRequest("", "", ActionRisk.READ_ONLY, "desktop.read")
    assert PermissionPolicy({"desktop.read"}).evaluate(request).decision is PermissionDecision.DENY
