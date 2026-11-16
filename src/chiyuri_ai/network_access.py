"""Permission-aware internet research and local-device connection primitives."""

from __future__ import annotations

import ipaddress
import socket
from dataclasses import dataclass, field
from enum import StrEnum
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, Request, build_opener
from uuid import uuid4


class NetworkPermission(StrEnum):
    DISCOVER = "discover"
    READ = "read"
    CONTROL = "control"


class NetworkAccessDenied(PermissionError):
    """Raised when an internet or device operation lacks an explicit grant."""


@dataclass(frozen=True, slots=True)
class NetworkDevice:
    device_id: str
    name: str
    endpoint: str
    protocol: str
    permissions: frozenset[NetworkPermission] = field(default_factory=frozenset)


class DeviceRegistry:
    """Explicitly enrolled LAN devices; deliberately does not scan networks itself."""

    def __init__(self) -> None:
        self._devices: dict[str, NetworkDevice] = {}

    def enroll(
        self,
        name: str,
        endpoint: str,
        protocol: str,
        permissions: set[NetworkPermission] | frozenset[NetworkPermission],
    ) -> NetworkDevice:
        if not name.strip() or not protocol.strip():
            raise ValueError("Device name and protocol are required")
        device = NetworkDevice(
            device_id=str(uuid4()),
            name=name.strip(),
            endpoint=endpoint.strip(),
            protocol=protocol.strip().lower(),
            permissions=frozenset(permissions),
        )
        self._devices[device.device_id] = device
        return device

    def get(self, device_id: str, permission: NetworkPermission) -> NetworkDevice:
        device = self._devices[device_id]
        if permission not in device.permissions:
            raise NetworkAccessDenied(
                f"Device '{device.name}' has no '{permission.value}' permission"
            )
        return device

    def revoke(self, device_id: str, permission: NetworkPermission) -> NetworkDevice:
        device = self._devices[device_id]
        updated = NetworkDevice(
            device.device_id,
            device.name,
            device.endpoint,
            device.protocol,
            device.permissions - {permission},
        )
        self._devices[device_id] = updated
        return updated

    def list_devices(self) -> list[NetworkDevice]:
        return list(self._devices.values())


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class SafeInternetClient:
    """Small bounded HTTP reader; only explicitly approved public hosts are reachable."""

    def __init__(
        self,
        approved_hosts: set[str] | frozenset[str],
        timeout_seconds: float = 12,
        max_bytes: int = 2_000_000,
    ) -> None:
        self.approved_hosts = frozenset(host.lower().rstrip(".") for host in approved_hosts)
        self.timeout_seconds = timeout_seconds
        self.max_bytes = max_bytes
        self._opener = build_opener(_NoRedirect)

    @staticmethod
    def _is_public_host(host: str) -> bool:
        try:
            addresses = [ipaddress.ip_address(host)]
        except ValueError:
            try:
                addresses = [
                    ipaddress.ip_address(item[4][0])
                    for item in socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)
                ]
            except OSError:
                return False
        return bool(addresses) and all(address.is_global for address in addresses)

    def get_text(self, url: str) -> str:
        from urllib.parse import urlsplit

        parsed = urlsplit(url)
        host = (parsed.hostname or "").lower().rstrip(".")
        if parsed.scheme != "https" or host not in self.approved_hosts:
            raise NetworkAccessDenied("Only approved HTTPS hosts are allowed")
        if not self._is_public_host(host):
            raise NetworkAccessDenied("Private, local and non-public network targets are blocked")
        if parsed.username or parsed.password or parsed.port not in (None, 443):
            raise NetworkAccessDenied("Credentials and non-standard ports are not allowed")
        request = Request(url, headers={"User-Agent": "ChiyuriAI/0.1", "Accept": "text/*"})
        try:
            with self._opener.open(request, timeout=self.timeout_seconds) as response:
                content_type = response.headers.get_content_type()
                if not (content_type.startswith("text/") or content_type == "application/json"):
                    raise ValueError(f"Unsupported response content type: {content_type}")
                payload = response.read(self.max_bytes + 1)
        except (HTTPError, URLError, TimeoutError, OSError) as exc:
            raise RuntimeError(f"Internet request failed: {exc}") from exc
        if len(payload) > self.max_bytes:
            raise ValueError("Internet response exceeded the configured size limit")
        return payload.decode("utf-8", errors="replace")


class DeviceConnector:
    """Protocol boundary for SSH, HTTP APIs, SMB and media-server integrations."""

    def read(self, device: NetworkDevice, resource: str) -> str:
        raise NotImplementedError

    def control(self, device: NetworkDevice, action: str, payload: dict[str, object]) -> str:
        raise NotImplementedError


class NetworkActionService:
    """Checks grants before handing an operation to a protocol-specific connector."""

    def __init__(self, devices: DeviceRegistry, connectors: dict[str, DeviceConnector]) -> None:
        self.devices = devices
        self.connectors = connectors

    def read(self, device_id: str, resource: str) -> str:
        device = self.devices.get(device_id, NetworkPermission.READ)
        connector = self._connector(device)
        return connector.read(device, resource)

    def control(
        self,
        device_id: str,
        action: str,
        payload: dict[str, object],
        confirmed: bool = False,
    ) -> str:
        device = self.devices.get(device_id, NetworkPermission.CONTROL)
        if not confirmed:
            raise NetworkAccessDenied("Control action requires explicit user confirmation")
        connector = self._connector(device)
        return connector.control(device, action, payload)

    def _connector(self, device: NetworkDevice) -> DeviceConnector:
        try:
            return self.connectors[device.protocol]
        except KeyError as exc:
            raise RuntimeError(f"No connector installed for protocol '{device.protocol}'") from exc
