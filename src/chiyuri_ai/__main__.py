"""Application entry point."""

from __future__ import annotations

import sys

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QApplication, QLabel, QMainWindow

from chiyuri_ai import __version__
from chiyuri_ai.storage import StoragePaths


class MainWindow(QMainWindow):
    """Minimal gothic-themed shell; feature panels will be added incrementally."""

    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle(f"Chiyuri AI · {__version__}")
        self.resize(1100, 720)
        self.setMinimumSize(800, 560)

        label = QLabel(
            "Chiyuri AI\n\nЛокальный интеллект. Единая система.\n"
            "Ядро проекта подготовлено — подключение движков впереди."
        )
        label.setObjectName("welcome")
        label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.setCentralWidget(label)
        self.setStyleSheet(
            """
            QMainWindow { background: #100b0e; }
            QLabel#welcome {
                color: #f2e9ed;
                background: #180e13;
                border: 1px solid #6f172e;
                border-radius: 14px;
                margin: 28px;
                padding: 32px;
                font-size: 20px;
            }
            """
        )


def main() -> int:
    """Start the desktop application."""
    StoragePaths.from_environment().create()
    app = QApplication(sys.argv)
    app.setApplicationName("Chiyuri AI")
    window = MainWindow()
    window.show()
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
