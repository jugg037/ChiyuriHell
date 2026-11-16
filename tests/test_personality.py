import json

import pytest

from chiyuri_ai.personality import PersonalityProfile, PersonalityService, PersonalityStore


def test_profile_prompt_and_round_trip(tmp_path):
    store = PersonalityStore(tmp_path / "personas")
    profile = PersonalityProfile(
        profile_id="chiyuri",
        name="Чиюри",
        description="Живая и любознательная",
        traits={"юмор": 75},
        speaking_style="Разговорный русский",
    )
    store.save(profile)

    loaded = store.load("chiyuri")
    assert loaded == profile
    assert "юмор: 75/100" in loaded.to_prompt()


def test_create_profile_from_russian_prompt(tmp_path):
    store = PersonalityStore(tmp_path / "personas")
    service = PersonalityService(store)
    response = {
        "profile_id": "warm",
        "name": "Чиюри",
        "description": "Дружелюбная",
        "traits": {"юмор": 80},
        "speaking_style": "Неформально",
        "values": ["Честность"],
        "boundaries": [],
        "system_prompt": "",
    }

    profile = service.create_from_prompt(
        "Сделай дружелюбной и с хорошим чувством юмора",
        lambda prompt: json.dumps(response, ensure_ascii=False),
    )

    assert profile.name == "Чиюри"
    assert store.list_profiles() == ["warm"]


def test_reject_invalid_profile_data(tmp_path):
    with pytest.raises(ValueError):
        PersonalityProfile("bad", "Чиюри", "Описание", {"юмор": 101})
