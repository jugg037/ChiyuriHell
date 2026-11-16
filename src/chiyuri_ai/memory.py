"""Persistent local conversation memory backed by SQLite."""

from __future__ import annotations

import sqlite3
from datetime import UTC, datetime
from pathlib import Path
from uuid import uuid4


class ConversationMemory:
    """Small, transactional message store with full-text search."""

    def __init__(self, database_path: Path) -> None:
        self.database_path = database_path
        self.database_path.parent.mkdir(parents=True, exist_ok=True)
        self._initialize()

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.database_path, timeout=10)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA journal_mode = WAL")
        return connection

    def _initialize(self) -> None:
        with self._connect() as db:
            db.executescript(
                """
                CREATE TABLE IF NOT EXISTS conversations (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS messages (
                    id TEXT PRIMARY KEY,
                    conversation_id TEXT NOT NULL REFERENCES conversations(id)
                        ON DELETE CASCADE,
                    role TEXT NOT NULL CHECK(role IN ('system', 'user', 'assistant', 'tool')),
                    content TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_messages_conversation
                    ON messages(conversation_id, created_at);
                CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
                    message_id UNINDEXED, conversation_id UNINDEXED, content
                );
                CREATE TRIGGER IF NOT EXISTS messages_after_insert
                AFTER INSERT ON messages BEGIN
                    INSERT INTO messages_fts(message_id, conversation_id, content)
                    VALUES (new.id, new.conversation_id, new.content);
                END;
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                """
            )

    @staticmethod
    def _now() -> str:
        return datetime.now(UTC).isoformat()

    def create_conversation(self, title: str = "Новый диалог") -> str:
        conversation_id = str(uuid4())
        now = self._now()
        with self._connect() as db:
            db.execute(
                "INSERT INTO conversations(id, title, created_at, updated_at) "
                "VALUES (?, ?, ?, ?)",
                (conversation_id, title.strip() or "Новый диалог", now, now),
            )
        return conversation_id

    def add_message(self, conversation_id: str, role: str, content: str) -> str:
        if role not in {"system", "user", "assistant", "tool"}:
            raise ValueError(f"Unsupported message role: {role}")
        if not content.strip():
            raise ValueError("Message content cannot be empty")
        message_id = str(uuid4())
        now = self._now()
        with self._connect() as db:
            exists = db.execute(
                "SELECT 1 FROM conversations WHERE id = ?", (conversation_id,)
            ).fetchone()
            if exists is None:
                raise KeyError(f"Unknown conversation: {conversation_id}")
            db.execute(
                "INSERT INTO messages(id, conversation_id, role, content, created_at) "
                "VALUES (?, ?, ?, ?, ?)",
                (message_id, conversation_id, role, content, now),
            )
            db.execute(
                "UPDATE conversations SET updated_at = ? WHERE id = ?",
                (now, conversation_id),
            )
        return message_id

    def get_messages(self, conversation_id: str, limit: int = 100) -> list[dict[str, str]]:
        if limit < 1:
            raise ValueError("limit must be positive")
        with self._connect() as db:
            rows = db.execute(
                "SELECT role, content, created_at FROM messages "
                "WHERE conversation_id = ? ORDER BY created_at, rowid LIMIT ?",
                (conversation_id, limit),
            ).fetchall()
        return [dict(row) for row in rows]

    def search(self, query: str, limit: int = 20) -> list[dict[str, str]]:
        if not query.strip():
            return []
        with self._connect() as db:
            rows = db.execute(
                """
                SELECT m.id, m.conversation_id, m.role, m.content, m.created_at
                FROM messages_fts f
                JOIN messages m ON m.id = f.message_id
                WHERE messages_fts MATCH ?
                ORDER BY rank LIMIT ?
                """,
                (query, limit),
            ).fetchall()
        return [dict(row) for row in rows]

    def set_setting(self, key: str, value: str) -> None:
        with self._connect() as db:
            db.execute(
                """
                INSERT INTO settings(key, value, updated_at) VALUES (?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET
                    value = excluded.value, updated_at = excluded.updated_at
                """,
                (key, value, self._now()),
            )

    def get_setting(self, key: str) -> str | None:
        with self._connect() as db:
            row = db.execute("SELECT value FROM settings WHERE key = ?", (key,)).fetchone()
        return None if row is None else str(row["value"])
