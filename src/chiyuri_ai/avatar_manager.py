"""Desktop manager for inspecting a Unity avatar project with Chiyuri."""
from __future__ import annotations

import shutil
import subprocess
from importlib.resources import files
from pathlib import Path

from PySide6.QtCore import QThread, Signal
from PySide6.QtWidgets import (
    QDialog,
    QFileDialog,
    QHBoxLayout,
    QLabel,
    QMessageBox,
    QPushButton,
    QTextBrowser,
    QVBoxLayout,
)

from chiyuri_ai.avatar import AvatarProjectScanner

INSPECTOR_RELATIVE_PATH = Path("Assets/Editor/ChiyuriAI/ChiyuriAvatarInspector.cs")


def validate_unity_project(root: Path) -> Path:
    """Return a resolved Unity project root or reject an unrelated directory."""
    resolved = root.expanduser().resolve(strict=True)
    if not resolved.is_dir() or not (resolved / "Assets").is_dir():
        raise ValueError("Выбранная папка не похожа на Unity-проект: нет каталога Assets.")
    if not (resolved / "ProjectSettings").is_dir():
        raise ValueError("В выбранной папке нет ProjectSettings. Укажи корень Unity-проекта.")
    return resolved


def install_inspector(root: Path) -> Path:
    """Install the bundled read-only inspector, preserving any previous copy."""
    project = validate_unity_project(root)
    destination = project / INSPECTOR_RELATIVE_PATH
    destination.parent.mkdir(parents=True, exist_ok=True)
    source = files("chiyuri_ai").joinpath("resources/ChiyuriAvatarInspector.cs")
    payload = source.read_bytes()
    if not payload or len(payload) > 256 * 1024:
        raise ValueError("Встроенный инспектор отсутствует или имеет недопустимый размер.")
    if destination.exists():
        backup = destination.with_suffix(destination.suffix + ".chiyuri-backup")
        if backup.exists():
            raise FileExistsError(f"Резервная копия уже существует: {backup}")
        shutil.copy2(destination, backup)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    temporary.write_bytes(payload)
    temporary.replace(destination)
    return destination


def launch_unity(editor_executable: Path, project_root: Path) -> subprocess.Popen[bytes]:
    """Launch a user-selected Unity Editor executable for the selected project."""
    editor = editor_executable.expanduser().resolve(strict=True)
    project = validate_unity_project(project_root)
    if not editor.is_file() or editor.suffix.casefold() != ".exe":
        raise ValueError("Нужно выбрать исполняемый файл Unity Editor (.exe).")
    return subprocess.Popen(
        [str(editor), "-projectPath", str(project)],
        close_fds=True,
    )


class AvatarScanWorker(QThread):
    completed = Signal(object)
    failed = Signal(str)

    def __init__(self, project_root: Path):
        super().__init__()
        self.project_root = project_root

    def run(self) -> None:
        try:
            self.completed.emit(AvatarProjectScanner().scan(self.project_root))
        except Exception as exc:  # noqa: BLE001 - report scan errors in the UI
            self.failed.emit(str(exc))


class AvatarManagerDialog(QDialog):
    """Guided Windows UI for selecting, inspecting and opening an avatar project."""

    def __init__(self, inventory_directory: Path, parent=None):
        super().__init__(parent)
        self.inventory_directory = inventory_directory
        self.project_root: Path | None = None
        self.editor_executable: Path | None = None
        self.worker: AvatarScanWorker | None = None
        self.setWindowTitle("Chiyuri · Менеджер аватара")
        self.resize(760, 560)

        self.project_label = QLabel("Unity-проект не выбран")
        self.project_label.setWordWrap(True)
        self.editor_label = QLabel("Unity Editor не выбран")
        self.report = QTextBrowser()
        self.report.setOpenExternalLinks(False)

        choose_project = QPushButton("Выбрать Unity-проект")
        choose_editor = QPushButton("Выбрать Unity Editor")
        scan_button = QPushButton("Проверить файлы")
        install_button = QPushButton("Установить инспектор")
        launch_button = QPushButton("Открыть проект в Unity")
        choose_project.clicked.connect(self.choose_project)
        choose_editor.clicked.connect(self.choose_editor)
        scan_button.clicked.connect(self.scan_project)
        install_button.clicked.connect(self.install)
        launch_button.clicked.connect(self.launch)

        project_row = QHBoxLayout()
        project_row.addWidget(choose_project)
        project_row.addWidget(scan_button)
        editor_row = QHBoxLayout()
        editor_row.addWidget(choose_editor)
        editor_row.addWidget(launch_button)

        layout = QVBoxLayout(self)
        layout.addWidget(QLabel("<b>Проект аватара</b>"))
        layout.addWidget(self.project_label)
        layout.addLayout(project_row)
        layout.addWidget(QLabel("<b>Unity Editor</b>"))
        layout.addWidget(self.editor_label)
        layout.addLayout(editor_row)
        layout.addWidget(install_button)
        layout.addWidget(self.report, 1)
        self.setStyleSheet(
            "QDialog { background:#100b0e; color:#f2e9ed; }"
            "QTextBrowser { background:#180e13; border:1px solid #6f172e; "
            "border-radius:8px; padding:10px; }"
            "QPushButton { background:#8e1838; color:white; border:0; "
            "border-radius:8px; padding:9px 14px; }"
            "QPushButton:hover { background:#b5224b; }"
        )
        self.report.setPlainText(
            "Выбери корневую папку Unity-проекта.\n"
            "Проверка файлов работает в режиме чтения и не изменяет исходные ассеты.\n\n"
            "Установка инспектора добавит один служебный .cs-файл в Assets/Editor/ChiyuriAI. "
            "Перед этим программа запросит подтверждение."
        )

    def choose_project(self) -> None:
        selected = QFileDialog.getExistingDirectory(self, "Выбери корень Unity-проекта")
        if not selected:
            return
        try:
            self.project_root = validate_unity_project(Path(selected))
        except (OSError, ValueError) as exc:
            QMessageBox.warning(self, "Неверный проект", str(exc))
            return
        self.project_label.setText(str(self.project_root))
        self.report.append("\nПроект выбран. Можно выполнить проверку.")

    def choose_editor(self) -> None:
        selected, _ = QFileDialog.getOpenFileName(
            self, "Выбери Unity Editor", "", "Unity Editor (Unity.exe)"
        )
        if selected:
            self.editor_executable = Path(selected)
            self.editor_label.setText(str(self.editor_executable))

    def scan_project(self) -> None:
        if self.project_root is None:
            QMessageBox.information(self, "Chiyuri", "Сначала выбери Unity-проект.")
            return
        if self.worker is not None and self.worker.isRunning():
            return
        self.report.setPlainText("Проверяю файлы проекта…")
        self.worker = AvatarScanWorker(self.project_root)
        self.worker.completed.connect(self.show_inventory)
        self.worker.failed.connect(lambda error: self.report.setPlainText(f"Ошибка: {error}"))
        self.worker.start()

    def show_inventory(self, inventory: object) -> None:
        assets = getattr(inventory, "assets", [])
        animations = getattr(inventory, "animation_files", [])
        controllers = getattr(inventory, "animator_controllers", [])
        parameters = getattr(inventory, "animator_parameters", [])
        toggles = getattr(inventory, "likely_toggles", [])
        warnings = getattr(inventory, "warnings", [])
        lines = [
            "Проверка завершена (только чтение).",
            f"Файлов ресурсов: {len(assets)}",
            f"Анимаций: {len(animations)}",
            f"Animator Controllers: {len(controllers)}",
            f"Параметров Animator: {len(parameters)}",
            "",
            "Кандидаты переключателей:",
            *(f" • {item}" for item in toggles[:100]),
            "",
            "Предупреждения:",
            *(f" • {item}" for item in warnings[:50]),
        ]
        self.report.setPlainText("\n".join(lines))
        self.inventory_directory.mkdir(parents=True, exist_ok=True)
        from chiyuri_ai.avatar import AvatarEvolutionStore

        saved = AvatarEvolutionStore(self.inventory_directory).save_inventory(inventory)
        self.report.append(f"\n\nОтчёт сохранён: {saved}")

    def install(self) -> None:
        if self.project_root is None:
            QMessageBox.information(self, "Chiyuri", "Сначала выбери Unity-проект.")
            return
        answer = QMessageBox.question(
            self,
            "Установить инспектор?",
            "В проект будет добавлен служебный файл ChiyuriAvatarInspector.cs. "
            "Существующий файл будет сохранён в резервную копию. "
            "Исходные модели, сцены и настройки аватара не изменяются. Продолжить?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            QMessageBox.StandardButton.No,
        )
        if answer != QMessageBox.StandardButton.Yes:
            return
        try:
            installed = install_inspector(self.project_root)
        except (OSError, ValueError) as exc:
            QMessageBox.critical(self, "Ошибка установки", str(exc))
            return
        QMessageBox.information(self, "Готово", f"Инспектор установлен:\n{installed}")

    def launch(self) -> None:
        if self.project_root is None or self.editor_executable is None:
            QMessageBox.information(
                self, "Chiyuri", "Выбери Unity-проект и исполняемый файл Unity Editor."
            )
            return
        try:
            launch_unity(self.editor_executable, self.project_root)
        except (OSError, ValueError) as exc:
            QMessageBox.critical(self, "Не удалось запустить Unity", str(exc))
            return
        self.report.append("\nUnity Editor запущен для выбранного проекта.")
