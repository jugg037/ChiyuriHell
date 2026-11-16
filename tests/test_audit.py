import sqlite3

from chiyuri_ai.audit import AuditLog


def test_audit_log_persists_events_and_redacts_secrets(tmp_path):
    path = tmp_path / "logs" / "audit.sqlite3"
    log = AuditLog(path)
    event = log.append(
        action_id="browser.search",
        decision="allow",
        outcome="completed",
        metadata={"query": "weather", "api_token": "do-not-store"},
    )
    assert event.metadata["api_token"] == "[REDACTED]"
    loaded = AuditLog(path).recent()
    assert len(loaded) == 1
    assert loaded[0].event_id == event.event_id
    assert loaded[0].metadata["api_token"] == "[REDACTED]"


def test_audit_log_is_append_only_at_database_level(tmp_path):
    path = tmp_path / "audit.sqlite3"
    log = AuditLog(path)
    event = log.append(action_id="test", outcome="started")
    with sqlite3.connect(path) as connection:
        try:
            connection.execute(
                "UPDATE audit_events SET outcome='rewritten' WHERE event_id=?",
                (event.event_id,),
            )
        except sqlite3.IntegrityError as exc:
            assert "append-only" in str(exc)
        else:
            raise AssertionError("audit update unexpectedly succeeded")
        try:
            connection.execute("DELETE FROM audit_events WHERE event_id=?", (event.event_id,))
        except sqlite3.IntegrityError as exc:
            assert "append-only" in str(exc)
        else:
            raise AssertionError("audit delete unexpectedly succeeded")


def test_audit_metadata_is_bounded(tmp_path):
    log = AuditLog(tmp_path / "audit.sqlite3")
    event = log.append(action_id="test", metadata={"blob": "x" * 20000})
    assert event.metadata == {"truncated": True, "reason": "metadata size limit"}
