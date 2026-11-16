import json

import pytest

from chiyuri_ai.avatar_protocol import AvatarMessage, MAX_MESSAGE_BYTES


def test_avatar_protocol_round_trip():
    original = AvatarMessage.create("avatar.play_action", {"action": "wave"}, "req_1")
    assert AvatarMessage.from_json(original.to_json()) == original


def test_avatar_protocol_rejects_unknown_messages_and_versions():
    raw = json.dumps({
        "message_type": "execute.shell", "payload": {}, "request_id": "req_1", "version": 1,
    })
    with pytest.raises(ValueError, match="Unsupported avatar message type"):
        AvatarMessage.from_json(raw)
    with pytest.raises(ValueError, match="Unsupported avatar protocol version"):
        AvatarMessage("avatar.hello", {}, "req_1", version=99).to_json()


def test_avatar_protocol_rejects_oversized_payload():
    raw = json.dumps({
        "message_type": "avatar.state", "payload": {"blob": "x" * MAX_MESSAGE_BYTES},
        "request_id": "req_1", "version": 1,
    })
    with pytest.raises(ValueError, match="size limit"):
        AvatarMessage.from_json(raw)


def test_avatar_protocol_rejects_malformed_shape():
    with pytest.raises(ValueError, match="invalid shape"):
        AvatarMessage.from_json('{"message_type":"avatar.hello"}')
