"""Local-first diagnostics, bounded rotating logs and privacy-filtered support bundles."""

from __future__ import annotations

import json
import logging
import logging.handlers
import platform
import re
import sqlite3
import sys
import threading
import zipfile
from datetime import UTC, datetime
from importlib import metadata, util
from pathlib import Path
from tempfile import NamedTemporaryFile
from typing import Any

import psutil

from chiyuri_ai import __version__

MAX_LOG_BYTES = 2 * 1024 * 1024
MAX_ARCHIVE_BYTES = 8 * 1024 * 1024
_SECRET_PATTERNS = (
    re.compile(r"(?i)(bearer\s+)[A-Za-z0-9._~+/-]{8,}"),
    re.compile(r"(?i)(gh[pousr]_[A-Za-z0-9_]{12,})"),
    re.compile(r"(?i)(sk-[A-Za-z0-9_-]{12,})"),
    re.compile(
        r"(?i)(api[_-]?(?:key|token)|access[_-]?token|refresh[_-]?token|token|password|passwd|secret|authorization)"
        r"(\s*[=:]\s*)([\"']?)[^\s,;\"']+"
    ),
    re.compile(r"(?i)([?&](?:token|key|password|secret)=)[^&\s]+"),
)


def redact_text(value: str) -> str:
    """Remove common credentials and the current user's home path from diagnostic text."""
    result = str(value)
    for pattern in _SECRET_PATTERNS:
        if pattern.groups >= 3:
            result = pattern.sub(r"\1\2[REDACTED]", result)
        elif pattern.groups == 2:
            result = pattern.sub(r"\1[REDACTED]", result)
        else:
            result = pattern.sub("[REDACTED]", result)
    try:
        home = str(Path.home())
        if home and home != "/":
            result = result.replace(home, "~")
    except OSError:
        pass
    return result


class RedactingFormatter(logging.Formatter):
    """Applies credential scrubbing after formatting the full log record."""

    def format(self, record: logging.LogRecord) -> str:
        return redact_text(super().format(record))


class DiagnosticCenter:
    """Owns application logs and exports an explicit, privacy-bounded support archive."""

    def __init__(self, directory: Path) -> None:
        self.directory = directory
        self.directory.mkdir(parents=True, exist_ok=True)
        self.log_path = self.directory / "chiyuri.log"
        self.logger = logging.getLogger("chiyuri_ai")
        self.logger.setLevel(logging.INFO)
        self.logger.propagate = False
        for existing in list(self.logger.handlers):
            if getattr(existing, "_chiyuri_diagnostics", False):
                existing_path = Path(existing.baseFilename).resolve()
                if existing_path != self.log_path.resolve():
                    self.logger.removeHandler(existing)
                    existing.close()
        has_handler = any(
            getattr(handler, "_chiyuri_diagnostics", False)
            for handler in self.logger.handlers
        )
        if not has_handler:
            handler = logging.handlers.RotatingFileHandler(
                self.log_path,
                maxBytes=MAX_LOG_BYTES,
                backupCount=3,
                encoding="utf-8",
            )
            handler.setFormatter(
                RedactingFormatter("%(asctime)s %(levelname)s %(name)s: %(message)s")
            )
            handler._chiyuri_diagnostics = True  # type: ignore[attr-defined]
            self.logger.addHandler(handler)
        self.logger.info("Diagnostics initialized for Chiyuri AI %s", __version__)
        self._hooks_installed = False

    def run_self_check(self) -> list[dict[str, str]]:
        """Run safe local checks without reading conversations, credentials or model files."""
        checks: list[dict[str, str]] = []

        def add(name: str, ok: bool, detail: str) -> None:
            checks.append(
                {
                    "name": name,
                    "status": "ok" if ok else "warning",
                    "detail": redact_text(detail)[:300],
                }
            )

        add(
            "python_version",
            sys.version_info >= (3, 11),
            f"Python {platform.python_version()}",
        )
        add(
            "log_handler",
            any(
                getattr(handler, "_chiyuri_diagnostics", False)
                for handler in self.logger.handlers
            ),
            "Файловый журнал подключён"
            if any(
                getattr(handler, "_chiyuri_diagnostics", False)
                for handler in self.logger.handlers
            )
            else "Файловый журнал не подключён",
        )
        try:
            with NamedTemporaryFile(dir=self.directory, prefix=".write-check-", delete=True):
                pass
            add("diagnostics_storage", True, "Каталог диагностики доступен для записи")
        except OSError as exc:
            add("diagnostics_storage", False, f"Нет доступа к каталогу: {type(exc).__name__}")

        try:
            with sqlite3.connect(":memory:") as connection:
                connection.execute("CREATE VIRTUAL TABLE health_fts USING fts5(content)")
                connection.execute("INSERT INTO health_fts(content) VALUES ('health check')")
                row = connection.execute(
                    "SELECT count(*) FROM health_fts WHERE health_fts MATCH 'health'"
                ).fetchone()
            add("sqlite_fts5", bool(row and row[0] == 1), "SQLite FTS5 доступен")
        except sqlite3.Error as exc:
            add("sqlite_fts5", False, f"SQLite FTS5 недоступен: {type(exc).__name__}")

        for module_name in ("PySide6", "psutil", "huggingface_hub"):
            try:
                available = util.find_spec(module_name) is not None
            except (ImportError, ValueError):
                available = False
            add(
                f"dependency_{module_name.casefold()}",
                available,
                f"{module_name}: {'найден' if available else 'не найден'}",
            )
        return checks

    def install_exception_hooks(self) -> None:
        """Record otherwise-unhandled main-thread and Python worker-thread exceptions."""
        if self._hooks_installed:
            return
        self._hooks_installed = True
        previous_sys_hook = sys.excepthook

        def handle_exception(exc_type: type[BaseException], exc: BaseException, tb: Any) -> None:
            self.logger.critical(
                "Unhandled application exception",
                exc_info=(exc_type, exc, tb),
            )
            previous_sys_hook(exc_type, exc, tb)

        sys.excepthook = handle_exception

        previous_thread_hook = threading.excepthook

        def handle_thread_exception(args: threading.ExceptHookArgs) -> None:
            self.logger.critical(
                "Unhandled thread exception",
                exc_info=(args.exc_type, args.exc_value, args.exc_traceback),
            )
            previous_thread_hook(args)

        threading.excepthook = handle_thread_exception

    @staticmethod
    def _installed_packages() -> dict[str, str]:
        wanted = {
            "chiyuri-ai",
            "pyside6",
            "psutil",
            "huggingface-hub",
            "llama-cpp-python",
            "torch",
            "transformers",
            "pyinstaller",
        }
        found: dict[str, str] = {}
        for package in metadata.distributions():
            name = package.metadata.get("Name", "").casefold()
            if name in wanted:
                found[name] = package.version
        return dict(sorted(found.items()))

    def build_report(self) -> dict[str, Any]:
        """Return technical environment details without conversations, secrets or file paths."""
        memory = psutil.virtual_memory()
        report: dict[str, Any] = {
            "schema_version": 1,
            "generated_utc": datetime.now(UTC).isoformat(),
            "application": {"name": "Chiyuri AI", "version": __version__},
            "runtime": {
                "python": platform.python_version(),
                "implementation": platform.python_implementation(),
                "platform": platform.platform(),
                "system": platform.system(),
                "release": platform.release(),
                "architecture": platform.machine(),
            },
            "resources": {
                "ram_total_bytes": memory.total,
                "ram_available_bytes": memory.available,
                "cpu_count": psutil.cpu_count(logical=True),
            },
            "packages": self._installed_packages(),
            "health_checks": self.run_self_check(),
            "privacy": {
                "conversation_history_included": False,
                "model_files_included": False,
                "provider_credentials_included": False,
                "environment_variables_included": False,
                "log_content_redacted": True,
            },
        }
        return report

    def export_bundle(self, destination: Path) -> Path:
        """Create a bounded ZIP containing environment metadata and sanitized app logs."""
        target = destination.expanduser()
        target.parent.mkdir(parents=True, exist_ok=True)
        report_bytes = json.dumps(
            self.build_report(), ensure_ascii=False, indent=2
        ).encode("utf-8")
        logs: list[tuple[str, bytes]] = []
        for index in range(4):
            path = self.log_path if index == 0 else self.log_path.with_name(
                f"{self.log_path.name}.{index}"
            )
            try:
                if not path.is_file():
                    continue
                payload = path.read_bytes()
            except OSError:
                continue
            if len(payload) > MAX_LOG_BYTES:
                payload = payload[-MAX_LOG_BYTES:]
            decoded = payload.decode("utf-8", errors="replace")
            safe_lines = [redact_text(line) for line in decoded.splitlines()]
            logs.append((f"logs/{path.name}", ("\n".join(safe_lines) + "\n").encode("utf-8")))
        total_size = len(report_bytes) + sum(len(payload) for _, payload in logs)
        if total_size > MAX_ARCHIVE_BYTES:
            raise ValueError("Диагностический отчёт превышает установленный лимит размера.")
        temporary = target.with_suffix(target.suffix + ".tmp")
        try:
            with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("diagnostics.json", report_bytes)
                archive.writestr(
                    "README.txt",
                    (
                        "Chiyuri AI diagnostic bundle.\n"
                        "Contains environment metadata and sanitized application logs.\n"
                        "Does not contain chat history, model weights, provider credentials "
                        "or environment variables.\n"
                        "Review the archive before sharing it.\n"
                    ),
                )
                for name, payload in logs:
                    archive.writestr(name, payload)
            temporary.replace(target)
        except Exception:
            temporary.unlink(missing_ok=True)
            raise
        self.logger.info("Diagnostic bundle exported: %s", target.name)
        return target
