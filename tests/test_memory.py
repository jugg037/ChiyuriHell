from chiyuri_ai.memory import ConversationMemory


def test_conversation_round_trip_and_full_text_search(tmp_path):
    memory = ConversationMemory(tmp_path / "memory.sqlite3")
    conversation_id = memory.create_conversation("Тест")

    memory.add_message(conversation_id, "user", "Привет, Chiyuri")
    memory.add_message(conversation_id, "assistant", "Привет! Я на месте.")

    messages = memory.get_messages(conversation_id)
    assert [item["role"] for item in messages] == ["user", "assistant"]
    assert "Привет" in messages[0]["content"]
    assert memory.search("Chiyuri")[0]["conversation_id"] == conversation_id


def test_memory_rejects_invalid_role_and_empty_content(tmp_path):
    memory = ConversationMemory(tmp_path / "memory.sqlite3")
    conversation_id = memory.create_conversation()

    try:
        memory.add_message(conversation_id, "admin", "bad")
    except ValueError:
        pass
    else:
        raise AssertionError("invalid role should fail")

    try:
        memory.add_message(conversation_id, "user", " ")
    except ValueError:
        pass
    else:
        raise AssertionError("empty content should fail")
