"""Small Qt manager for explicitly configured AI providers."""

from __future__ import annotations

from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QDialog,
    QDialogButtonBox,
    QDoubleSpinBox,
    QFormLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QMessageBox,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from chiyuri_ai.providers import ProviderConfig, ProviderStore


class ProviderEditorDialog(QDialog):
    """Edits non-secret provider settings and an explicit data-sharing grant."""

    def __init__(self, parent: QWidget | None = None, config: ProviderConfig | None = None):
        super().__init__(parent)
        self.setWindowTitle("Подключение ИИ-провайдера")
        self.setMinimumWidth(520)
        self.provider_id = QLineEdit()
        self.name = QLineEdit()
        self.base_url = QLineEdit()
        self.model = QLineEdit()
        self.api_key_env = QLineEdit()
        self.enabled = QCheckBox("Провайдер включён")
        self.data_sharing = QCheckBox(
            "Разрешаю отправлять этому провайдеру сообщения и контекст переписки"
        )
        self.timeout = QDoubleSpinBox()
        self.timeout.setRange(1, 600)
        self.timeout.setDecimals(0)
        self.timeout.setSuffix(" сек.")
        form = QFormLayout()
        form.addRow("Идентификатор", self.provider_id)
        form.addRow("Название", self.name)
        form.addRow("HTTPS Base URL", self.base_url)
        form.addRow("Модель", self.model)
        form.addRow("Переменная API-ключа", self.api_key_env)
        form.addRow("Тайм-аут", self.timeout)
        form.addRow("", self.enabled)
        form.addRow("", self.data_sharing)
        note = QLabel(
            "Сам API-ключ сюда не вводится и не сохраняется. Укажи имя переменной "
            "окружения Windows, в которой задан ключ провайдера."
        )
        note.setWordWrap(True)
        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Save | QDialogButtonBox.StandardButton.Cancel
        )
        buttons.accepted.connect(self._save)
        buttons.rejected.connect(self.reject)
        layout = QVBoxLayout(self)
        layout.addLayout(form)
        layout.addWidget(note)
        layout.addWidget(buttons)
        if config is not None:
            self.provider_id.setText(config.provider_id)
            self.provider_id.setReadOnly(True)
            self.name.setText(config.name)
            self.base_url.setText(config.base_url)
            self.model.setText(config.model)
            self.api_key_env.setText(config.api_key_env)
            self.enabled.setChecked(config.enabled)
            self.data_sharing.setChecked(config.data_sharing_approved)
            self.timeout.setValue(config.timeout_seconds)
        else:
            self.enabled.setChecked(True)
            self.timeout.setValue(90)

    def _save(self) -> None:
        try:
            self.config = ProviderConfig(
                provider_id=self.provider_id.text().strip(),
                name=self.name.text().strip(),
                base_url=self.base_url.text().strip(),
                model=self.model.text().strip(),
                api_key_env=self.api_key_env.text().strip(),
                enabled=self.enabled.isChecked(),
                data_sharing_approved=self.data_sharing.isChecked(),
                timeout_seconds=self.timeout.value(),
            )
        except (TypeError, ValueError) as exc:
            QMessageBox.warning(self, "Проверь настройки", str(exc))
            return
        self.accept()


class ProviderManagerDialog(QDialog):
    """Adds, edits, removes and selects the preferred chat provider."""

    def __init__(
        self,
        store: ProviderStore,
        local_model_id: str,
        preferred_model_id: str | None = None,
        parent: QWidget | None = None,
    ) -> None:
        super().__init__(parent)
        self.store = store
        self.local_model_id = local_model_id
        self.preferred_model_id = preferred_model_id or local_model_id
        self.setWindowTitle("ИИ-провайдеры Чиюри")
        self.setMinimumSize(650, 430)
        self.provider_list = QListWidget()
        self.preferred = QComboBox()
        self.add_button = QPushButton("Добавить")
        self.edit_button = QPushButton("Изменить")
        self.remove_button = QPushButton("Удалить")
        buttons_row = QHBoxLayout()
        buttons_row.addWidget(self.add_button)
        buttons_row.addWidget(self.edit_button)
        buttons_row.addWidget(self.remove_button)
        form = QFormLayout()
        form.addRow("Основная модель для диалога", self.preferred)
        note = QLabel(
            "Чиюри сохраняет одну личность и память. Выбранная модель отвечает первой; "
            "если она недоступна или завершилась ошибкой, маршрутизатор пробует следующую."
        )
        note.setWordWrap(True)
        close = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
        close.rejected.connect(self.accept)
        layout = QVBoxLayout(self)
        layout.addWidget(self.provider_list, 1)
        layout.addLayout(buttons_row)
        layout.addLayout(form)
        layout.addWidget(note)
        layout.addWidget(close)
        self.add_button.clicked.connect(self.add_provider)
        self.edit_button.clicked.connect(self.edit_provider)
        self.remove_button.clicked.connect(self.remove_provider)
        self.provider_list.currentRowChanged.connect(self._update_buttons)
        self._refresh()

    def _refresh(self) -> None:
        self.provider_list.clear()
        self.preferred.clear()
        self.preferred.addItem("Локальная модель (llama.cpp)", self.local_model_id)
        for config in self.store.list_configs():
            state = "включён" if config.enabled else "отключён"
            consent = " · передача разрешена" if config.data_sharing_approved else " · без согласия"
            self.provider_list.addItem(f"{config.name} — {config.model} ({state}{consent})")
            self.provider_list.item(self.provider_list.count() - 1).setData(
                Qt.ItemDataRole.UserRole, config.provider_id
            )
            self.preferred.addItem(
                f"{config.name} — {config.model}",
                f"{config.provider_id}:{config.model}",
            )
        index = self.preferred.findData(self.preferred_model_id)
        self.preferred.setCurrentIndex(index if index >= 0 else 0)
        self._update_buttons()

    def _selected_config(self) -> ProviderConfig | None:
        item = self.provider_list.currentItem()
        if item is None:
            return None
        provider_id = item.data(Qt.ItemDataRole.UserRole)
        return self.store.load(provider_id) if provider_id else None

    def _update_buttons(self, _row: int = -1) -> None:
        selected = self.provider_list.currentItem() is not None
        self.edit_button.setEnabled(selected)
        self.remove_button.setEnabled(selected)

    def add_provider(self) -> None:
        dialog = ProviderEditorDialog(self)
        if dialog.exec() == QDialog.DialogCode.Accepted:
            try:
                self.store.save(dialog.config)
            except OSError as exc:
                QMessageBox.critical(self, "Не удалось сохранить", str(exc))
                return
            self._refresh()

    def edit_provider(self) -> None:
        config = self._selected_config()
        if config is None:
            return
        dialog = ProviderEditorDialog(self, config)
        if dialog.exec() == QDialog.DialogCode.Accepted:
            try:
                self.store.save(dialog.config)
            except OSError as exc:
                QMessageBox.critical(self, "Не удалось сохранить", str(exc))
                return
            self._refresh()

    def remove_provider(self) -> None:
        config = self._selected_config()
        if config is None:
            return
        answer = QMessageBox.question(
            self,
            "Удалить провайдера",
            (
                f"Удалить подключение «{config.name}»? Сам аккаунт и ключ "
                "у провайдера не затрагиваются."
            ),
        )
        if answer == QMessageBox.StandardButton.Yes:
            self.store.delete(config.provider_id)
            self._refresh()

    def accept(self) -> None:
        self.preferred_model_id = self.preferred.currentData() or self.local_model_id
        super().accept()
