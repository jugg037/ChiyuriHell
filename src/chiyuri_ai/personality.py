"""Editable, prompt-authored personality profiles for Chiyuri."""

from __future__ import annotations

import json
import re
from collections.abc import Callable
from dataclasses import asdict, dataclass, field
from pathlib import Path


@dataclass(frozen=True, slots=True)
class PersonalityProfile:
    """A portable description of one assistant persona."""

    profile_id: str
    name: str
    description: str
    traits: dict[str, int] = field(default_factory=dict)
    speaking_style: str = ""
    values: list[str] = field(default_factory=list)
    boundaries: list[str] = field(default_factory=list)
    system_prompt: str = ""

    def __post_init__(self) -> None:
        if not self.profile_id.strip() or not self.name.strip():
            raise ValueError("Personality profile requires an id and a name")
        if any(not 0 <= value <= 100 for value in self.traits.values()):
            raise ValueError("Personality traits must be between 0 and 100")

    def to_prompt(self) -> str:
        """Render stable persona guidance for every model capability."""
        traits = ", ".join(f"{key}: {value}/100" for key, value in self.traits.items())
        sections = [
            f"Имя и образ: {self.name}.",
            self.description,
            f"Черты характера: {traits}." if traits else "",
            f"Манера общения: {self.speaking_style}" if self.speaking_style else "",
            "Ценности: " + "; ".join(self.values) if self.values else "",
            "Границы: " + "; ".join(self.boundaries) if self.boundaries else "",
            self.system_prompt,
            "Сохраняй эту личность в диалоге и во всех подключённых режимах. "
            "Не выдумывай возможности инструментов и соблюдай их разрешения.",
        ]
        return "\n".join(item for item in sections if item)

    def to_dict(self) -> dict[str, object]:
        return asdict(self)


class PersonalityStore:
    """Stores named profiles as local JSON files, outside the source repository."""

    def __init__(self, directory: Path) -> None:
        self.directory = directory
        self.directory.mkdir(parents=True, exist_ok=True)

    def save(self, profile: PersonalityProfile) -> Path:
        path = self.directory / f"{profile.profile_id}.json"
        temporary = path.with_suffix(".json.tmp")
        temporary.write_text(
            json.dumps(profile.to_dict(), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        temporary.replace(path)
        return path

    def load(self, profile_id: str) -> PersonalityProfile:
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", profile_id):
            raise ValueError("Invalid profile id")
        data = json.loads((self.directory / f"{profile_id}.json").read_text(encoding="utf-8"))
        return PersonalityProfile(**data)

    def list_profiles(self) -> list[str]:
        return sorted(path.stem for path in self.directory.glob("*.json"))


class PersonalityService:
    """Turns a Russian-language description into a validated profile via a model callback."""

    DESIGN_PROMPT = """Ты редактор личности ИИ-помощника. На основе описания пользователя
создай JSON-объект строго по схеме:
{"profile_id":"ascii_id","name":"имя","description":"краткий образ",
"traits":{"общительность":0,"юмор":0,"эмоциональность":0,"инициативность":0},
"speaking_style":"манера речи","values":["..."],"boundaries":["..."],
"system_prompt":"дополнительные устойчивые инструкции"}.
Числа traits — целые от 0 до 100. Сохрани намерение пользователя, не добавляй
непрошенные черты. Ответь только JSON. Это описание характера, а не разрешение
нарушать права, ограничения инструментов или правила безопасности."""

    def __init__(self, store: PersonalityStore) -> None:
        self.store = store

    def create_from_prompt(
        self, user_prompt: str, generate_json: Callable[[str], str]
    ) -> PersonalityProfile:
        if not user_prompt.strip():
            raise ValueError("Personality prompt cannot be empty")
        raw = generate_json(f"{self.DESIGN_PROMPT}\n\nОписание пользователя:\n{user_prompt}")
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError("The model did not return valid personality JSON") from exc
        if not isinstance(data, dict):
            raise ValueError("Personality response must be a JSON object")
        profile = PersonalityProfile(
            profile_id=str(data.get("profile_id", "custom")),
            name=str(data.get("name", "Чиюри")),
            description=str(data.get("description", "")),
            traits={str(k): int(v) for k, v in dict(data.get("traits", {})).items()},
            speaking_style=str(data.get("speaking_style", "")),
            values=[str(item) for item in data.get("values", [])],
            boundaries=[str(item) for item in data.get("boundaries", [])],
            system_prompt=str(data.get("system_prompt", "")),
        )
        self.store.save(profile)
        return profile
