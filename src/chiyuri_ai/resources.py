"""Best-effort host and NVIDIA GPU resource telemetry."""

from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass

import psutil


@dataclass(frozen=True, slots=True)
class ResourceSnapshot:
    cpu_percent: float
    ram_total_bytes: int
    ram_available_bytes: int
    gpu_name: str | None = None
    vram_total_bytes: int | None = None
    vram_free_bytes: int | None = None

    @property
    def ram_used_percent(self) -> float:
        if not self.ram_total_bytes:
            return 0.0
        used = self.ram_total_bytes - self.ram_available_bytes
        return used / self.ram_total_bytes * 100


class ResourceMonitor:
    """Reads system memory via psutil and GPU memory via nvidia-smi when present."""

    def snapshot(self) -> ResourceSnapshot:
        memory = psutil.virtual_memory()
        gpu = self._read_nvidia_smi()
        return ResourceSnapshot(
            cpu_percent=psutil.cpu_percent(interval=0.1),
            ram_total_bytes=memory.total,
            ram_available_bytes=memory.available,
            gpu_name=gpu[0] if gpu else None,
            vram_total_bytes=gpu[1] if gpu else None,
            vram_free_bytes=gpu[2] if gpu else None,
        )

    @staticmethod
    def _read_nvidia_smi() -> tuple[str, int, int] | None:
        executable = shutil.which("nvidia-smi")
        if executable is None:
            return None
        try:
            result = subprocess.run(
                [
                    executable,
                    "--query-gpu=name,memory.total,memory.free",
                    "--format=csv,noheader,nounits",
                ],
                capture_output=True,
                check=True,
                text=True,
                timeout=2,
            )
            first_line = result.stdout.strip().splitlines()[0]
            name, total_mib, free_mib = (part.strip() for part in first_line.split(",", 2))
            mib = 1024 * 1024
            return name, int(total_mib) * mib, int(free_mib) * mib
        except (OSError, subprocess.SubprocessError, IndexError, ValueError):
            return None
