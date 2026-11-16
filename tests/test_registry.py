from chiyuri_ai.audit import AuditLog
from chiyuri_ai.identity import IdentityIntent, OwnerBoundIdentityPolicy
from chiyuri_ai.permissions import ActionRisk, PermissionPolicy
from chiyuri_ai.registry import ToolRegistry, ToolSpec


def _spec(handler, *, risk=ActionRisk.READ_ONLY, intent=IdentityIntent.NORMAL):
    return ToolSpec(
        name="desktop.read",
        description="Read desktop state",
        required_scope="desktop.read",
        risk=risk,
        validator=lambda data: set(data) <= {"window"},
        handler=handler,
        identity_intent=intent,
    )


def test_registry_runs_registered_tool_with_granted_scope(tmp_path):
    called = []
    registry = ToolRegistry(
        PermissionPolicy({"desktop.read"}),
        OwnerBoundIdentityPolicy(),
        AuditLog(tmp_path / "audit.sqlite3"),
    )
    registry.register(_spec(lambda args: called.append(args) or {"title": "Editor"}))
    result = registry.dispatch("desktop.read", {"window": "active"})
    assert result.status == "completed"
    assert result.result == {"title": "Editor"}
    assert called == [{"window": "active"}]
    assert [e.outcome for e in registry._audit.recent()] == ["started", "completed"]


def test_registry_denies_unregistered_tools(tmp_path):
    registry = ToolRegistry(
        PermissionPolicy({"desktop.read"}),
        OwnerBoundIdentityPolicy(),
        AuditLog(tmp_path / "audit.sqlite3"),
    )
    assert registry.dispatch("shell.exec", {}).status == "denied"


def test_registry_never_calls_handler_without_scope_or_for_approval(tmp_path):
    calls = []
    registry = ToolRegistry(
        PermissionPolicy(),
        OwnerBoundIdentityPolicy(),
        AuditLog(tmp_path / "audit.sqlite3"),
    )
    registry.register(_spec(lambda args: calls.append(args), risk=ActionRisk.DESTRUCTIVE))
    result = registry.dispatch("desktop.read", {})
    assert result.status == "denied"
    assert calls == []

    registry2 = ToolRegistry(
        PermissionPolicy({"desktop.read"}),
        OwnerBoundIdentityPolicy(),
        AuditLog(tmp_path / "audit2.sqlite3"),
    )
    registry2.register(_spec(lambda args: calls.append(args), risk=ActionRisk.DESTRUCTIVE))
    result = registry2.dispatch("desktop.read", {})
    assert result.status == "approval_required"
    assert calls == []


def test_registry_blocks_identity_violation_even_with_permission_scope(tmp_path):
    calls = []
    registry = ToolRegistry(
        PermissionPolicy({"identity.manage"}),
        OwnerBoundIdentityPolicy(),
        AuditLog(tmp_path / "audit.sqlite3"),
    )
    spec = ToolSpec(
        name="identity.copy",
        description="Copy Chiyuri identity",
        required_scope="identity.manage",
        risk=ActionRisk.READ_ONLY,
        validator=lambda data: not data,
        handler=lambda args: calls.append(args),
        identity_intent=IdentityIntent.SELF_REPLICATION,
    )
    registry.register(spec)
    assert registry.dispatch("identity.copy", {}).status == "denied"
    assert calls == []


def test_registry_rejects_duplicate_names_and_bad_arguments(tmp_path):
    calls = []
    registry = ToolRegistry(
        PermissionPolicy({"desktop.read"}),
        OwnerBoundIdentityPolicy(),
        AuditLog(tmp_path / "audit.sqlite3"),
    )
    spec = _spec(lambda args: calls.append(args))
    registry.register(spec)
    try:
        registry.register(spec)
    except ValueError:
        pass
    else:
        raise AssertionError("duplicate tool registration unexpectedly succeeded")
    assert registry.dispatch("desktop.read", {"unexpected": True}).status == "denied"
    assert calls == []
