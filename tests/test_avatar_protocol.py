import json

import pytest

from chiyuri_ai.avatar_protocol import MAX_MESSAGE_BYTES, AvatarMessage


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


@pytest.mark.parametrize("bad_type", [[], {}, None, 3])
def test_avatar_protocol_rejects_non_string_message_type(bad_type):
    raw = json.dumps({
        "message_type": bad_type, "payload": {}, "request_id": "req_1", "version": 1,
    })
    with pytest.raises(ValueError, match="Unsupported avatar message type"):
        AvatarMessage.from_json(raw)


@pytest.mark.parametrize("bad_version", [True, "1", 1.0, None])
def test_avatar_protocol_rejects_non_integer_version(bad_version):
    raw = json.dumps({
        "message_type": "avatar.hello", "payload": {}, "request_id": "req_1",
        "version": bad_version,
    })
    with pytest.raises(ValueError, match="Unsupported avatar protocol version"):
        AvatarMessage.from_json(raw)


def test_avatar_protocol_rejects_non_object_payload_and_request_id():
    for payload, request_id in [([], "req_1"), ({}, []), ({}, None)]:
        raw = json.dumps({
            "message_type": "avatar.hello", "payload": payload,
            "request_id": request_id, "version": 1,
        })
        with pytest.raises(ValueError):
            AvatarMessage.from_json(raw)
