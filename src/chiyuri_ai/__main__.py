"""Application entry point and desktop chat shell."""

from __future__ import annotations

import html
import sys

from PySide6.QtCore import QThread, QTimer, Signal
from PySide6.QtWidgets import (
    QApplication,
    QHBoxLayout,
    QLineEdit,
    QMainWindow,
    QPushButton,
    QTextBrowser,
    QVBoxLayout,
    QWidget,
)

from chiyuri_ai import __version__
from chiyuri_ai.llama_cpp import LlamaCppServerAdapter
from chiyuri_ai.memory import ConversationMemory
from chiyuri_ai.models import ModelRouter
from chiyuri_ai.personality import PersonalityProfile, PersonalityStore
from chiyuri_ai.resources import ResourceMonitor
from chiyuri_ai.service import ConversationService
from chiyuri_ai.storage import StoragePaths


class ChatWorker(QThread):
    """Runs model inference away from the Qt UI thread."""

    completed = Signal(str, str)
    failed = Signal(str)

    def __init__(self, service: ConversationService, conversation_id: str, message: str):
        super().__init__()
        self.service = service
        self.conversation_id = conversation_id
        self.message = message

    def run(self) -> None:
        try:
            turn = self.service.send(self.conversation_id, self.message)
            self.completed.emit(turn.assistant_message, turn.model_id)
        except Exception as exc:  # noqa: BLE001 - surface backend failures in the chat
            self.failed.emit(str(exc))


class StatusWorker(QThread):
    """Collects hardware telemetry and backend health without freezing the UI."""

    completed = Signal(str)

    def __init__(self, adapter: LlamaCppServerAdapter, monitor: ResourceMonitor):
        super().__init__()
        self.adapter = adapter
        self.monitor = monitor

    def run(self) -> None:
        snapshot = self.monitor.snapshot()
        ram_gb = snapshot.ram_available_bytes / (1024**3)
        gpu = (
            f" · {snapshot.gpu_name}: "
            f"{snapshot.vram_free_bytes / (1024**3):.1f} GB VRAM свободно"
            if snapshot.gpu_name and snapshot.vram_free_bytes is not None
            else " · NVIDIA GPU не обнаружен"
        )
        engine = "llama.cpp: подключён" if self.adapter.is_available() else (
            "llama.cpp: не подключён"
        )
        self.completed.emit(f"{engine} · RAM свободно: {ram_gb:.1f} GB{gpu}")


class MainWindow(QMainWindow):
    """First usable local chat interface with persistent history."""

    def __init__(self, paths: StoragePaths) -> None:
        super().__init__()
        self.paths = paths
        self.memory = ConversationMemory(paths.root / "Memory" / "conversations.sqlite3")
        self.router = ModelRouter()
        self.chat_adapter = LlamaCppServerAdapter()
        self.router.register(self.chat_adapter)
        self.personality_store = PersonalityStore(paths.root / "Personas")
        active_id = self.memory.get_setting("active_personality")
        if not self.personality_store.list_profiles():
            self.personality_store.save(
                PersonalityProfile(
                    profile_id="chiyuri",
                    name="Чиюри",
                    description="Любознательный, внимательный и самостоятельный ИИ-помощник.",
                    traits={"общительность": 65, "юмор": 45, "инициативность": 70},
                    speaking_style="Естественный русский язык, живое и ясное общение.",
                    values=["Честность", "Полезность", "Уважение к пользователю"],
                    boundaries=["Не выдумывать результаты действий и возможности инструментов"],
                )
            )
        profiles = self.personality_store.list_profiles()
        if active_id not in profiles:
            active_id = profiles[0]
            self.memory.set_setting("active_personality", active_id)
        self.active_personality = self.personality_store.load(active_id)
        self.service = ConversationService(
            self.memory,
            self.router,
            system_prompt_provider=self.active_personality.to_prompt,
        )
        self.conversation_id = self.service.new_conversation()
        self.worker: ChatWorker | None = None
        self.monitor = ResourceMonitor()
        self.status_worker: StatusWorker | None = None

        self.setWindowTitle(f"Chiyuri AI · {__version__}")
        self.resize(1120, 760)
        self.setMinimumSize(820, 600)

        self.chat = QTextBrowser()
        self.chat.setObjectName("chat")
        self.chat.setOpenExternalLinks(False)
        self.chat.append(
            "<h2>Chiyuri AI</h2>"
            "<p>Локальный диалоговый интерфейс готов. "
            "Подключи llama.cpp server, чтобы начать общение.</p>"
        )
        self.input = QLineEdit()
        self.input.setPlaceholderText("Напиши сообщение…")
        self.input.returnPressed.connect(self.send_message)
        self.send_button = QPushButton("Отправить")
        self.send_button.clicked.connect(self.send_message)

        input_row = QHBoxLayout()
        input_row.addWidget(self.input, 1)
        input_row.addWidget(self.send_button)

        layout = QVBoxLayout()
        layout.addWidget(self.chat, 1)
        layout.addLayout(input_row)
        central = QWidget()
        central.setLayout(layout)
        self.setCentralWidget(central)
        self.statusBar().showMessage("Проверка локального движка…")
        self.setStyleSheet(
            """
            QMainWindow, QWidget { background: #100b0e; color: #f2e9ed; }
            QTextBrowser {
                background: #180e13; color: #f2e9ed;
                border: 1px solid #6f172e; border-radius: 12px;
                padding: 14px; font-size: 14px;
            }
            QLineEdit {
                background: #21141a; color: #ffffff;
                border: 1px solid #84213e; border-radius: 9px;
                padding: 12px; font-size: 14px;
            }
            QPushButton {
                background: #8e1838; color: white; border: 0;
                border-radius: 9px; padding: 11px 20px; font-weight: bold;
            }
            QPushButton:hover { background: #b5224b; }
            QPushButton:disabled { background: #493039; color: #b9aab0; }
            """
        )

        self.status_timer = QTimer(self)
        self.status_timer.timeout.connect(self.update_status)
        self.status_timer.start(5000)
        self.update_status()

    def send_message(self) -> None:
        message = self.input.text().strip()
        if not message or (self.worker is not None and self.worker.isRunning()):
            return
        self.input.clear()
        self.chat.append(
            f'<p style="color:#ff7898"><b>Ты:</b> {html.escape(message)}</p>'
        )
        self.send_button.setEnabled(False)
        self.statusBar().showMessage("Chiyuri думает…")
        self.worker = ChatWorker(self.service, self.conversation_id, message)
        self.worker.completed.connect(self.on_reply)
        self.worker.failed.connect(self.on_error)
        self.worker.finished.connect(lambda: self.send_button.setEnabled(True))
        self.worker.start()

    def on_reply(self, answer: str, model_id: str) -> None:
        self.chat.append(
            f'<p><b style="color:#ff7898">Chiyuri:</b> {html.escape(answer)}</p>'
        )
        self.statusBar().showMessage(f"Модель: {model_id}", 8000)

    def on_error(self, error: str) -> None:
        self.chat.append(
            '<p style="color:#ff9b9b"><b>Ошибка движка:</b> '
            f"{html.escape(error)}</p>"
        )
        self.statusBar().showMessage("Локальная модель недоступна")

    def update_status(self) -> None:
        if self.status_worker is not None and self.status_worker.isRunning():
            return
        self.status_worker = StatusWorker(self.chat_adapter, self.monitor)
        self.status_worker.completed.connect(self.statusBar().showMessage)
        self.status_worker.start()


def main() -> int:
    """Start the desktop application."""
    paths = StoragePaths.from_environment()
    paths.create()
    app = QApplication(sys.argv)
    app.setApplicationName("Chiyuri AI")
    window = MainWindow(paths)
    window.show()
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
