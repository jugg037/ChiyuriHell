"""Application entry point and desktop chat shell."""

from __future__ import annotations

import html
import logging
import sys
from pathlib import Path

from PySide6.QtCore import QThread, QTimer, Signal
from PySide6.QtWidgets import (
    QApplication,
    QFileDialog,
    QHBoxLayout,
    QInputDialog,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QTextBrowser,
    QVBoxLayout,
    QWidget,
)

from chiyuri_ai import __version__
from chiyuri_ai.avatar_manager import AvatarManagerDialog
from chiyuri_ai.diagnostics import DiagnosticCenter
from chiyuri_ai.llama_cpp import LlamaCppServerAdapter
from chiyuri_ai.memory import ConversationMemory
from chiyuri_ai.model_library_ui import ModelLibraryDialog
from chiyuri_ai.models import Capability, GenerationRequest, ModelRouter
from chiyuri_ai.personality import (
    PersonalityProfile,
    PersonalityService,
    PersonalityStore,
)
from chiyuri_ai.provider_ui import ProviderManagerDialog
from chiyuri_ai.providers import OpenAICompatibleAdapter, ProviderStore
from chiyuri_ai.research import InternetResearchService, SearxNGSearchProvider
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
            logging.getLogger(__name__).exception("Chat worker failed")
            self.failed.emit(str(exc))


class WebResearchWorker(QThread):
    """Searches the web and grounds one chat response in labelled source results."""

    completed = Signal(str, str, object)
    failed = Signal(str)

    def __init__(
        self,
        service: ConversationService,
        conversation_id: str,
        endpoint: str,
        query: str,
    ):
        super().__init__()
        self.service = service
        self.conversation_id = conversation_id
        self.endpoint = endpoint
        self.query = query

    def run(self) -> None:
        try:
            research = InternetResearchService(
                [SearxNGSearchProvider(self.endpoint)]
            )
            results = research.search(self.query, limit=8)
            context = research.to_context(results)
            turn = self.service.send(
                self.conversation_id, self.query, extra_context=context
            )
            self.completed.emit(turn.assistant_message, turn.model_id, results)
        except Exception as exc:  # noqa: BLE001 - surface network and model errors
            logging.getLogger(__name__).exception("Web research worker failed")
            self.failed.emit(str(exc))


class PersonaWorker(QThread):
    """Creates a profile from a natural-language prompt without freezing Qt."""

    completed = Signal(str, str)
    failed = Signal(str)

    def __init__(self, store: PersonalityStore, router: ModelRouter, prompt: str):
        super().__init__()
        self.store = store
        self.router = router
        self.prompt = prompt

    def run(self) -> None:
        try:
            service = PersonalityService(self.store)
            profile = service.create_from_prompt(
                self.prompt,
                lambda design_prompt: self.router.generate(
                    GenerationRequest(Capability.CHAT, design_prompt)
                ).output,
            )
            self.completed.emit(profile.profile_id, profile.name)
        except Exception as exc:  # noqa: BLE001 - report model/profile errors in the UI
            logging.getLogger(__name__).exception("Personality worker failed")
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

    def __init__(self, paths: StoragePaths, diagnostics: DiagnosticCenter) -> None:
        super().__init__()
        self.paths = paths
        self.diagnostics = diagnostics
        self.memory = ConversationMemory(paths.root / "Memory" / "conversations.sqlite3")
        self.personality_store = PersonalityStore(paths.root / "Personas")
        self.provider_store = ProviderStore(paths.providers)
        self.chat_adapter = LlamaCppServerAdapter()
        self.router = ModelRouter()
        self.router.register(self.chat_adapter)
        self._load_provider_adapters()
        preferred_model = self.memory.get_setting("preferred_chat_model")
        if preferred_model:
            try:
                self.router.set_preferred(preferred_model)
            except ValueError:
                self.memory.set_setting("preferred_chat_model", self.chat_adapter.model_id)

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
        self.persona_worker: PersonaWorker | None = None
        self.web_worker: WebResearchWorker | None = None
        self.monitor = ResourceMonitor()
        self.status_worker: StatusWorker | None = None

        self.setWindowTitle(f"Chiyuri AI · {__version__}")
        self.resize(1120, 760)
        self.setMinimumSize(820, 600)

        self.chat = QTextBrowser()
        self.chat.setObjectName("chat")
        self.chat.setOpenExternalLinks(True)
        self.chat.append(
            "<h2>Chiyuri AI</h2>"
            "<p>Локальный диалоговый интерфейс готов. "
            "Подключи llama.cpp server или облачную модель в менеджере ИИ-провайдеров.</p>"
        )
        self.input = QLineEdit()
        self.input.setPlaceholderText("Напиши сообщение…")
        self.input.returnPressed.connect(self.send_message)
        self.send_button = QPushButton("Отправить")
        self.persona_button = QPushButton("Настроить характер")
        self.web_button = QPushButton("Поиск в интернете")
        self.provider_button = QPushButton("ИИ-провайдеры")
        self.model_library_button = QPushButton("Библиотека моделей")
        self.avatar_button = QPushButton("Аватар")
        self.diagnostics_button = QPushButton("Диагностика")
        self.self_check_button = QPushButton("Самопроверка")
        self.persona_button.clicked.connect(self.create_personality)
        self.web_button.clicked.connect(self.search_web)
        self.provider_button.clicked.connect(self.manage_providers)
        self.model_library_button.clicked.connect(self.open_model_library)
        self.avatar_button.clicked.connect(self.open_avatar_manager)
        self.diagnostics_button.clicked.connect(self.export_diagnostics)
        self.self_check_button.clicked.connect(self.run_diagnostics_check)
        self.send_button.clicked.connect(self.send_message)

        input_row = QHBoxLayout()
        input_row.addWidget(self.input, 1)
        input_row.addWidget(self.web_button)
        input_row.addWidget(self.persona_button)
        input_row.addWidget(self.provider_button)
        input_row.addWidget(self.model_library_button)
        input_row.addWidget(self.avatar_button)
        input_row.addWidget(self.diagnostics_button)
        input_row.addWidget(self.self_check_button)
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

    def _load_provider_adapters(self) -> None:
        for config in self.provider_store.list_configs():
            try:
                self.router.register(OpenAICompatibleAdapter(config))
            except (TypeError, ValueError) as exc:
                self.statusBar().showMessage(
                    f"Провайдер «{config.name}» пропущен: {exc}", 10000
                )

    def open_model_library(self) -> None:
        dialog = ModelLibraryDialog(self.paths.models, self)
        dialog.exec()

    def open_avatar_manager(self) -> None:
        dialog = AvatarManagerDialog(self.paths.avatars, self)
        dialog.exec()

    def run_diagnostics_check(self) -> None:
        checks = self.diagnostics.run_self_check()
        failed = [item for item in checks if item["status"] != "ok"]
        lines = [
            f"{'✓' if item['status'] == 'ok' else '⚠'} "
            f"{item['name']}: {item['detail']}"
            for item in checks
        ]
        self.chat.append(
            "<p><b>Самопроверка Chiyuri AI:</b></p><pre>"
            + html.escape("\n".join(lines))
            + "</pre>"
        )
        if failed:
            self.statusBar().showMessage(
                f"Самопроверка завершена: предупреждений {len(failed)} из {len(checks)}",
                12000,
            )
            self.diagnostics.logger.warning(
                "Self-check reported %s warning(s): %s",
                len(failed),
                ", ".join(item["name"] for item in failed),
            )
        else:
            self.statusBar().showMessage(
                f"Самопроверка пройдена: {len(checks)} проверок", 8000
            )
            self.diagnostics.logger.info("Self-check passed: %s checks", len(checks))

    def export_diagnostics(self) -> None:
        destination, _ = QFileDialog.getSaveFileName(
            self,
            "Сохранить диагностический отчёт Chiyuri",
            str(self.paths.root / "Diagnostics" / "Chiyuri-diagnostics.zip"),
            "ZIP archive (*.zip)",
        )
        if not destination:
            return
        target = Path(destination)
        if target.suffix.casefold() != ".zip":
            target = target.with_suffix(".zip")
        try:
            saved = self.diagnostics.export_bundle(target)
        except (OSError, ValueError) as exc:
            QMessageBox.warning(self, "Не удалось создать отчёт", str(exc))
            return
        QMessageBox.information(
            self,
            "Диагностика готова",
            "Архив создан. Он содержит технические сведения и журналы ошибок, "
            "но не содержит историю диалогов или файлы моделей. "
            f"\n\n{saved}\n\nПрикрепи архив в разговоре для анализа.",
        )

    def manage_providers(self) -> None:
        dialog = ProviderManagerDialog(
            self.provider_store,
            self.chat_adapter.model_id,
            self.router.preferred_model_id,
            self,
        )
        dialog.exec()
        self.router = ModelRouter()
        self.router.register(self.chat_adapter)
        self._load_provider_adapters()
        try:
            self.router.set_preferred(dialog.preferred_model_id)
        except ValueError:
            self.router.set_preferred(self.chat_adapter.model_id)
        self.memory.set_setting("preferred_chat_model", self.router.preferred_model_id or "")
        self.service.router = self.router
        self.statusBar().showMessage("Список ИИ-провайдеров обновлён", 8000)

    def search_web(self) -> None:
        if self.worker is not None and self.worker.isRunning():
            self.statusBar().showMessage("Дождись завершения текущего ответа")
            return
        if self.web_worker is not None and self.web_worker.isRunning():
            return
        endpoint = self.memory.get_setting("searxng_endpoint")
        if not endpoint:
            endpoint, accepted = QInputDialog.getText(
                self,
                "Подключение поиска",
                "Адрес твоего SearXNG (например, https://search.example):",
            )
            if not accepted or not endpoint.strip():
                return
            endpoint = endpoint.strip()
            try:
                SearxNGSearchProvider(endpoint)
            except ValueError as exc:
                self.statusBar().showMessage(str(exc), 10000)
                return
            self.memory.set_setting("searxng_endpoint", endpoint)
        query, accepted = QInputDialog.getText(
            self, "Поиск в интернете", "Что нужно найти?"
        )
        if not accepted or not query.strip():
            return
        self.chat.append(
            f'<p style="color:#ff7898"><b>Ты · веб-поиск:</b> '
            f"{html.escape(query)}</p>"
        )
        self.web_button.setEnabled(False)
        self.send_button.setEnabled(False)
        self.statusBar().showMessage("Ищу источники и готовлю ответ…")
        self.web_worker = WebResearchWorker(
            self.service, self.conversation_id, endpoint, query.strip()
        )
        self.web_worker.completed.connect(self.on_web_reply)
        self.web_worker.failed.connect(self.on_web_error)
        self.web_worker.finished.connect(self.on_web_finished)
        self.web_worker.start()

    def on_web_reply(self, answer: str, model_id: str, results: object) -> None:
        self.on_reply(answer, model_id)
        sources = results if isinstance(results, list) else []
        if not sources:
            self.chat.append("<p><i>Поиск не вернул источников.</i></p>")
            return
        lines = ["<p><b>Источники:</b></p><ol>"]
        for item in sources:
            title = html.escape(item.title)
            url = html.escape(item.url, quote=True)
            lines.append(f'<li><a href="{url}">{title}</a></li>')
        lines.append("</ol>")
        self.chat.append("".join(lines))

    def on_web_error(self, error: str) -> None:
        self.chat.append(
            '<p style="color:#ff9b9b"><b>Ошибка веб-поиска:</b> '
            f"{html.escape(error)}</p>"
        )
        self.statusBar().showMessage("Веб-поиск не выполнен", 10000)

    def on_web_finished(self) -> None:
        self.web_button.setEnabled(True)
        self.send_button.setEnabled(True)

    def create_personality(self) -> None:
        if self.worker is not None and self.worker.isRunning():
            self.statusBar().showMessage("Дождись завершения текущего ответа")
            return
        if self.persona_worker is not None and self.persona_worker.isRunning():
            return
        prompt, accepted = QInputDialog.getMultiLineText(
            self,
            "Personality Studio",
            "Опиши характер Чиюри обычным русским языком:",
        )
        if not accepted or not prompt.strip():
            return
        self.persona_button.setEnabled(False)
        self.statusBar().showMessage("Формирую профиль личности…")
        self.persona_worker = PersonaWorker(self.personality_store, self.router, prompt)
        self.persona_worker.completed.connect(self.on_personality_created)
        self.persona_worker.failed.connect(self.on_personality_error)
        self.persona_worker.finished.connect(
            lambda: self.persona_button.setEnabled(True)
        )
        self.persona_worker.start()

    def on_personality_created(self, profile_id: str, profile_name: str) -> None:
        self.active_personality = self.personality_store.load(profile_id)
        self.memory.set_setting("active_personality", profile_id)
        self.service.system_prompt_provider = self.active_personality.to_prompt
        self.statusBar().showMessage(
            f"Активирована личность: {profile_name}", 10000
        )
        self.chat.append(
            f'<p style="color:#ff7898"><b>Чиюри:</b> '
            f"Мой новый профиль «{html.escape(profile_name)}» сохранён и активирован.</p>"
        )

    def on_personality_error(self, error: str) -> None:
        self.statusBar().showMessage(f"Не удалось создать профиль: {error}", 12000)

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
        self.statusBar().showMessage("Нет доступного ИИ-провайдера")

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
    diagnostics = DiagnosticCenter(paths.root / "Diagnostics")
    diagnostics.install_exception_hooks()
    app = QApplication(sys.argv)
    app.setApplicationName("Chiyuri AI")
    window = MainWindow(paths, diagnostics)
    window.show()
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
