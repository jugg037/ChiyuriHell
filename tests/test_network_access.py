import socket

import pytest

from chiyuri_ai.network_access import (
    DeviceRegistry,
    HTTPApiDeviceConnector,
    NetworkAccessDenied,
    NetworkPermission,
    SafeInternetClient,
)


def test_device_permissions_can_be_revoked():
    registry = DeviceRegistry()
    device = registry.enroll(
        "NAS",
        "192.168.1.20",
        "http",
        {NetworkPermission.READ, NetworkPermission.CONTROL},
    )
    assert registry.get(device.device_id, NetworkPermission.READ) == device

    registry.revoke(device.device_id, NetworkPermission.CONTROL)
    with pytest.raises(NetworkAccessDenied):
        registry.get(device.device_id, NetworkPermission.CONTROL)


def test_control_requires_user_confirmation():
    from chiyuri_ai.network_access import NetworkActionService

    class Connector:
        def read(self, device, resource):
            return resource

        def control(self, device, action, payload):
            return action

    registry = DeviceRegistry()
    device = registry.enroll("PC", "192.168.1.10", "mock", {NetworkPermission.CONTROL})
    service = NetworkActionService(registry, {"mock": Connector()})

    with pytest.raises(NetworkAccessDenied):
        service.control(device.device_id, "restart", {})

    assert service.control(device.device_id, "restart", {}, confirmed=True) == "restart"


def test_internet_client_rejects_unapproved_hosts():
    client = SafeInternetClient({"example.com"})
    with pytest.raises(NetworkAccessDenied):
        client.get_text("https://not-approved.example/")



def test_http_connector_restricts_device_paths_and_plain_http_hosts():
    registry = DeviceRegistry()
    device = registry.enroll(
        "Home Assistant",
        "http://192.168.1.20:8123/api",
        "http",
        {NetworkPermission.READ, NetworkPermission.CONTROL},
    )
    connector = HTTPApiDeviceConnector()

    assert connector._resource_url(device, "states") == (
        "http://192.168.1.20:8123/api/states"
    )
    with pytest.raises(ValueError):
        connector._resource_url(device, "../secrets")
    with pytest.raises(ValueError):
        connector._resource_url(device, "https://attacker.example/")
    public_http = registry.enroll("Remote", "http://example.com/api", "http", set())
    with pytest.raises(NetworkAccessDenied):
        connector._resource_url(public_http, "status")


def test_egress_policy_uses_exact_hosts_and_pins_public_dns_results():
    from chiyuri_ai.egress import NetworkEgressPolicy

    calls = []

    def resolver(host, port, *, type):
        calls.append((host, port, type))
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port))]

    policy = NetworkEgressPolicy({"example.com"}, resolver=resolver)
    endpoint = policy.authorize("https://example.com/article?q=1")

    assert endpoint.host == "example.com"
    assert endpoint.request_target == "/article?q=1"
    assert endpoint.addresses == ("93.184.216.34",)
    assert calls == [("example.com", 443, socket.SOCK_STREAM)]


def test_egress_policy_rejects_private_or_mixed_dns_answers():
    from chiyuri_ai.egress import EgressDenied, NetworkEgressPolicy

    def resolver(_host, port, *, type):
        return [
            (socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port)),
            (socket.AF_INET, socket.SOCK_STREAM, 6, "", ("127.0.0.1", port)),
        ]

    policy = NetworkEgressPolicy({"example.com"}, resolver=resolver)
    with pytest.raises(EgressDenied):
        policy.authorize("https://example.com/")


def test_egress_policy_requires_approval_for_user_data():
    from chiyuri_ai.egress import EgressDenied, NetworkEgressPolicy

    def resolver(_host, port, *, type):
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port))]

    policy = NetworkEgressPolicy({"example.com"}, resolver=resolver)
    with pytest.raises(EgressDenied):
        policy.authorize("https://example.com/upload", contains_user_data=True)
    assert policy.authorize(
        "https://example.com/upload",
        contains_user_data=True,
        user_approved=True,
    ).host == "example.com"


def test_egress_policy_rejects_subdomains_non_https_and_nonstandard_ports():
    from chiyuri_ai.egress import EgressDenied, NetworkEgressPolicy

    policy = NetworkEgressPolicy({"example.com"})
    for url in (
        "https://sub.example.com/",
        "http://example.com/",
        "https://example.com:8443/",
        "https://user@example.com/",
    ):
        with pytest.raises(EgressDenied):
            policy.authorize(url)


def test_pinned_https_connection_uses_validated_ip_and_original_tls_hostname(monkeypatch):
    from chiyuri_ai.egress import _PinnedHTTPSConnection

    calls = []

    class FakeSocket:
        def close(self):
            pass

    class FakeContext:
        def wrap_socket(self, raw_socket, *, server_hostname):
            calls.append(("tls", server_hostname))
            return raw_socket

    def fake_create_connection(address, *, timeout, source_address):
        calls.append(("connect", address, timeout, source_address))
        return FakeSocket()

    monkeypatch.setattr("chiyuri_ai.egress.socket.create_connection", fake_create_connection)
    connection = _PinnedHTTPSConnection(
        "example.com",
        443,
        ("93.184.216.34",),
        timeout=5,
    )
    connection._context = FakeContext()
    connection.connect()

    assert calls == [
        ("connect", ("93.184.216.34", 443), 5, None),
        ("tls", "example.com"),
    ]
    connection.close()
