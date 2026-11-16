import pytest

from chiyuri_ai.network_access import (
    DeviceRegistry,
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
