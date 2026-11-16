"""Append-only SQLite audit trail for tool and identity decisions."""
from __future__ import annotations

import json
import sqlite3
from contextlib import closing
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from uuid import uuid4

MAX_FIELD_LENGTH = 512
MAX_METADATA_BYTES = 16 * 1024
_SECRET_MARKERS = ("password", "passwd", "secret", "token", "api_key", "authorization", "credential")


def _safe_text(value: Any, *, limit: int = MAX_FIELD_LENGTH) -> str:
    return str(value).replace("\x00", "")[:limit]


def _sanitize(value: Any, *, key: str = "") -> Any:
    if any(marker in key.lower() for marker in _SECRET_MARKERS):
        return "[REDACTED]"
    if isinstance(value, dict):
        return {str(k)[:80]: _sanitize(v, key=str(k)) for k, v in list(value.items())[:100]}
    if isinstance(value, (list, tuple)):
        return [_sanitize(item) for item in value[:100]]
    if isinstance(value, (str, int, float, bool)) or value is None:
        return _safe_text(value) if isinstance(value, str) else value
    return _safe_text(repr(value))


@dataclass(frozen=True, slots=True)
class AuditEvent:
    event_id: str
    timestamp: str
    action_id: str
    request_id: str
    scope: str
    risk: str
    target: str
    description: str
    decision: str
    outcome: str
    metadata: dict[str, Any]


class AuditLog:
    """Append-only API; database triggers reject ordinary row mutation."""

    def __init__(self, database_path: Path | str) -> None:
        self.database_path = Path(database_path)
        self.database_path.parent.mkdir(parents=True, exist_ok=True)
        with closing(self._connect()) as connection:
            with connection:
                connection.execute(
                    """
                    CREATE TABLE IF NOT EXISTS audit_events (
                        event_id TEXT PRIMARY KEY,
                        timestamp TEXT NOT NULL,
                        action_id TEXT NOT NULL,
                        request_id TEXT NOT NULL,
                        scope TEXT NOT NULL,
                        risk TEXT NOT NULL,
                        target TEXT NOT NULL,
                        description TEXT NOT NULL,
                        decision TEXT NOT NULL,
                        outcome TEXT NOT NULL,
                        metadata_json TEXT NOT NULL
                    )
                    """
                )
                connection.executescript(
                    """
                    CREATE TRIGGER IF NOT EXISTS audit_no_update
                    BEFORE UPDATE ON audit_events
                    BEGIN SELECT RAISE(ABORT, 'audit log is append-only'); END;
                    CREATE TRIGGER IF NOT EXISTS audit_no_delete
                    BEFORE DELETE ON audit_events
                    BEGIN SELECT RAISE(ABORT, 'audit log is append-only'); END;
                    """
                )

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.database_path, timeout=10)
        connection.row_factory = sqlite3.Row
        return connection

    def append(
        self,
        *,
        action_id: str,
        request_id: str = "",
        scope: str = "",
        risk: str = "",
        target: str = "",
        description: str = "",
        decision: str = "",
        outcome: str = "",
        metadata: dict[str, Any] | None = None,
    ) -> AuditEvent:
        safe_metadata = _sanitize(metadata or {})
        metadata_json = json.dumps(safe_metadata, ensure_ascii=False, separators=(",", ":"))
        if len(metadata_json.encode("utf-8")) > MAX_METADATA_BYTES:
            metadata_json = json.dumps({"truncated": True, "reason": "metadata size limit"})
        event = AuditEvent(
            event_id=str(uuid4()),
            timestamp=datetime.now(UTC).isoformat(),
            action_id=_safe_text(action_id),
            request_id=_safe_text(request_id, limit=128),
            scope=_safe_text(scope),
            risk=_safe_text(risk),
            target=_safe_text(target),
            description=_safe_text(description),
            decision=_safe_text(decision),
            outcome=_safe_text(outcome),
            metadata=json.loads(metadata_json),
        )
        with closing(self._connect()) as connection:
            with connection:
                connection.execute(
                    """
                    INSERT INTO audit_events VALUES (
                        :event_id, :timestamp, :action_id, :request_id, :scope, :risk,
                        :target, :description, :decision, :outcome, :metadata_json
                    )
                    """,
                    {**asdict(event), "metadata_json": metadata_json},
                )
        return event

    def recent(self, limit: int = 100) -> list[AuditEvent]:
        bounded_limit = max(1, min(int(limit), 1000))
        with closing(self._connect()) as connection:
            rows = connection.execute(
                "SELECT * FROM audit_events ORDER BY rowid DESC LIMIT ?", (bounded_limit,)
            ).fetchall()
        return [
            AuditEvent(
                event_id=row["event_id"],
                timestamp=row["timestamp"],
                action_id=row["action_id"],
                request_id=row["request_id"],
                scope=row["scope"],
                risk=row["risk"],
                target=row["target"],
                description=row["description"],
                decision=row["decision"],
                outcome=row["outcome"],
                metadata=json.loads(row["metadata_json"]),
            )
            for row in reversed(rows)
        ]
