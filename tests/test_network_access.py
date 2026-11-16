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
