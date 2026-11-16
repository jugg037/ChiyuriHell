"""In-app Hugging Face GGUF catalog and downloader."""
from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import QThread, Signal
from PySide6.QtWidgets import (
    QComboBox,
    QDialog,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QMessageBox,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from chiyuri_ai.huggingface import (
    HubModel,
    HuggingFaceCatalog,
    HuggingFaceModelDownloader,
)


class CatalogWorker(QThread):
    completed = Signal(object)
    failed = Signal(str)

    def __init__(self, query: str, author: str) -> None:
        super().__init__()
        self.query = query
        self.author = author

    def run(self) -> None:
        try:
            self.completed.emit(HuggingFaceCatalog().search(self.query, self.author))
        except Exception as exc:  # noqa: BLE001 - show provider/network failures in the UI
            self.failed.emit(str(exc))


class FilesWorker(QThread):
    completed = Signal(object)
    failed = Signal(str)

    def __init__(self, repo_id: str) -> None:
        super().__init__()
        self.repo_id = repo_id

    def run(self) -> None:
        try:
            self.completed.emit(HuggingFaceCatalog().list_gguf_files(self.repo_id))
        except Exception as exc:  # noqa: BLE001
            self.failed.emit(str(exc))


class DownloadWorker(QThread):
    completed = Signal(str)
    failed = Signal(str)

    def __init__(self, models_directory: Path, repo_id: str, filename: str) -> None:
        super().__init__()
        self.models_directory = models_directory
        self.repo_id = repo_id
        self.filename = filename

    def run(self) -> None:
        try:
            path = HuggingFaceModelDownloader(self.models_directory).download(
                self.repo_id, self.filename
            )
            self.completed.emit(str(path))
        except Exception as exc:  # noqa: BLE001
            self.failed.emit(str(exc))


class ModelLibraryDialog(QDialog):
    """Searches Hugging Face, prioritises huihui-ai and downloads selected GGUF files."""

    def __init__(self, models_directory: Path, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.models_directory = models_directory
        self.models: list[HubModel] = []
        self.setWindowTitle("Библиотека моделей · Hugging Face")
        self.setMinimumSize(760, 560)

        self.query = QLineEdit()
        self.query.setPlaceholderText("Поиск моделей по названию или описанию…")
        self.author = QComboBox()
        self.author.addItem("Приоритет: huihui-ai", "huihui-ai")
        self.author.addItem("Все авторы", "")
        self.search_button = QPushButton("Найти модели")
        self.model_list = QListWidget()
        self.file_list = QListWidget()
        self.details = QLabel(
            "В каталоге отображаются публичные модели. Для первого этапа доступны "
            "загрузка отдельных файлов GGUF; запуск выбранной модели будет подключён "
            "к локальному движку отдельным следующим этапом."
        )
        self.details.setWordWrap(True)
        self.download_button = QPushButton("Скачать выбранный GGUF")
        self.download_button.setEnabled(False)
        self.status = QLabel("По умолчанию поиск ограничен автором huihui-ai.")
        row = QHBoxLayout()
        row.addWidget(self.query, 1)
        row.addWidget(self.author)
        row.addWidget(self.search_button)
        layout = QVBoxLayout(self)
        layout.addLayout(row)
        layout.addWidget(QLabel("Модели"))
        layout.addWidget(self.model_list, 3)
        layout.addWidget(QLabel("Файлы GGUF выбранной модели"))
        layout.addWidget(self.file_list, 2)
        layout.addWidget(self.details)
        layout.addWidget(self.status)
        layout.addWidget(self.download_button)

        self.search_button.clicked.connect(self.search)
        self.query.returnPressed.connect(self.search)
        self.model_list.currentRowChanged.connect(self.load_files)
        self.file_list.currentRowChanged.connect(self._update_download_button)
        self.download_button.clicked.connect(self.download_selected)
        self.catalog_worker: CatalogWorker | None = None
        self.files_worker: FilesWorker | None = None
        self.download_worker: DownloadWorker | None = None
        self.search()

    def search(self) -> None:
        if self.catalog_worker is not None and self.catalog_worker.isRunning():
            return
        self.search_button.setEnabled(False)
        self.status.setText("Ищу модели на Hugging Face…")
        self.catalog_worker = CatalogWorker(
            self.query.text().strip(), str(self.author.currentData() or "")
        )
        self.catalog_worker.completed.connect(self._show_models)
        self.catalog_worker.failed.connect(self._show_error)
        self.catalog_worker.finished.connect(lambda: self.search_button.setEnabled(True))
        self.catalog_worker.start()

    def _show_models(self, models: object) -> None:
        self.models = models if isinstance(models, list) else []
        self.model_list.clear()
        self.file_list.clear()
        for model in self.models:
            priority = "★ " if model.repo_id.lower().startswith("huihui-ai/") else ""
            self.model_list.addItem(
                f"{priority}{model.repo_id}  ·  загрузок: {model.downloads:,}"
            )
        self.status.setText(f"Найдено моделей: {len(self.models)}")
        if not self.models:
            self.details.setText("Ничего не найдено. Попробуй изменить запрос или автора.")

    def load_files(self, row: int) -> None:
        if not 0 <= row < len(self.models):
            self.file_list.clear()
            self._update_download_button()
            return
        if self.files_worker is not None and self.files_worker.isRunning():
            return
        model = self.models[row]
        self.status.setText(f"Получаю список GGUF-файлов: {model.repo_id}")
        self.files_worker = FilesWorker(model.repo_id)
        self.files_worker.completed.connect(self._show_files)
        self.files_worker.failed.connect(self._show_error)
        self.files_worker.start()

    def _show_files(self, files: object) -> None:
        self.file_list.clear()
        for item in files if isinstance(files, list) else []:
            size = (
                f"{item.size_bytes / (1024**3):.2f} ГБ"
                if item.size_bytes is not None
                else "размер неизвестен"
            )
            self.file_list.addItem(f"{item.filename}  ·  {size}")
            self.file_list.item(self.file_list.count() - 1).setData(256, item.filename)
        self.status.setText(f"GGUF-файлов найдено: {self.file_list.count()}")
        self._update_download_button()

    def _update_download_button(self, _row: int = -1) -> None:
        self.download_button.setEnabled(
            self.file_list.currentItem() is not None
            and (self.download_worker is None or not self.download_worker.isRunning())
        )

    def download_selected(self) -> None:
        model_row = self.model_list.currentRow()
        item = self.file_list.currentItem()
        if not 0 <= model_row < len(self.models) or item is None:
            return
        filename = item.data(256)
        model = self.models[model_row]
        answer = QMessageBox.question(
            self,
            "Подтверждение загрузки",
            f"Скачать {filename} из {model.repo_id} в папку Models?",
        )
        if answer != QMessageBox.StandardButton.Yes:
            return
        self.download_button.setEnabled(False)
        self.status.setText("Загрузка модели началась. Большие файлы могут скачиваться долго…")
        self.download_worker = DownloadWorker(self.models_directory, model.repo_id, filename)
        self.download_worker.completed.connect(self._download_complete)
        self.download_worker.failed.connect(self._show_error)
        self.download_worker.finished.connect(self._update_download_button)
        self.download_worker.start()

    def _download_complete(self, path: str) -> None:
        self.status.setText("Загрузка завершена.")
        self.details.setText(f"Файл сохранён:
{path}")

    def _show_error(self, error: str) -> None:
        self.status.setText("Операция не выполнена.")
        QMessageBox.warning(self, "Hugging Face", error)
