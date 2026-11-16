"""Permission-aware internet research and local-device connection primitives."""

from __future__ import annotations

from http.client import HTTPException

import ipaddress
import json
from collections.abc import Callable
from dataclasses import dataclass, field
from enum import StrEnum
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
from uuid import uuid4

from chiyuri_ai.egress import EgressDenied, NetworkEgressPolicy, _PinnedHTTPSConnection


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


class SafeInternetClient:
    """Bounded HTTPS reader using exact allowlists and DNS-pinned connections."""

    def __init__(
        self,
        approved_hosts: set[str] | frozenset[str],
        timeout_seconds: float = 12,
        max_bytes: int = 2_000_000,
    ) -> None:
        self.egress_policy = NetworkEgressPolicy(approved_hosts)
        self.timeout_seconds = timeout_seconds
        self.max_bytes = max_bytes

    def get_text(
        self,
        url: str,
        *,
        contains_user_data: bool = False,
        user_approved: bool = False,
    ) -> str:
        try:
            endpoint = self.egress_policy.authorize(
                url,
                contains_user_data=contains_user_data,
                user_approved=user_approved,
            )
        except EgressDenied as exc:
            raise NetworkAccessDenied(str(exc)) from exc

        connection = _PinnedHTTPSConnection(
            endpoint.host,
            endpoint.port,
            endpoint.addresses,
            timeout=self.timeout_seconds,
        )
        try:
            connection.request(
                "GET",
                endpoint.request_target,
                headers={"User-Agent": "ChiyuriAI/0.1", "Accept": "text/*"},
            )
            response = connection.getresponse()
            if 300 <= response.status < 400:
                raise NetworkAccessDenied("HTTP redirects are blocked")
            if response.status >= 400:
                raise RuntimeError(f"Internet request returned HTTP {response.status}")
            content_type = response.getheader("Content-Type", "").split(";", 1)[0].lower()
            if not (content_type.startswith("text/") or content_type == "application/json"):
                raise ValueError(f"Unsupported response content type: {content_type}")
            payload = response.read(self.max_bytes + 1)
        except (OSError, TimeoutError, HTTPException) as exc:
            raise RuntimeError(f"Internet request failed: {exc}") from exc
        finally:
            connection.close()

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



class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class HTTPApiDeviceConnector(DeviceConnector):
    """Bounded REST connector for explicitly enrolled local devices and services.

    Reads use GET /<resource>. Controls use POST /actions/<action> and are gated
    by NetworkActionService's explicit confirmation requirement.
    """

    def __init__(
        self,
        timeout_seconds: float = 10,
        max_bytes: int = 1_000_000,
        auth_headers: Callable[[NetworkDevice], dict[str, str]] | None = None,
    ) -> None:
        self.timeout_seconds = timeout_seconds
        self.max_bytes = max_bytes
        self.auth_headers = auth_headers or (lambda _device: {})
        self._opener = build_opener(_NoRedirect)

    @staticmethod
    def _base_url(device: NetworkDevice) -> str:
        parsed = urlsplit(device.endpoint)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise ValueError("Device endpoint must be an absolute HTTP(S) URL")
        if parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError("Device endpoint cannot contain credentials, query or fragment")
        if parsed.scheme == "http":
            host = parsed.hostname.lower()
            try:
                address = ipaddress.ip_address(host)
                local = address.is_private or address.is_loopback or address.is_link_local
            except ValueError:
                local = host in {"localhost"} or host.endswith((".local", ".lan"))
            if not local:
                raise NetworkAccessDenied("Plain HTTP is allowed only for local devices")
        return device.endpoint.rstrip("/")

    @classmethod
    def _resource_url(cls, device: NetworkDevice, resource: str) -> str:
        if not resource.strip() or resource.startswith("//"):
            raise ValueError("A relative device resource path is required")
        parsed = urlsplit(resource)
        segments = resource.strip("/").split("/")
        if parsed.scheme or parsed.netloc or parsed.query or parsed.fragment:
            raise ValueError("Device resource must be a relative path without query or fragment")
        if any(segment in {".", ".."} for segment in segments):
            raise ValueError("Path traversal is not allowed")
        suffix = "/".join(quote(segment, safe="") for segment in segments)
        return f"{cls._base_url(device)}/{suffix}"

    def _request(self, device: NetworkDevice, path: str, data: bytes | None = None) -> str:
        headers = {"Accept": "application/json, text/plain", **self.auth_headers(device)}
        if data is not None:
            headers["Content-Type"] = "application/json"
        request = Request(
            path,
            data=data,
            headers=headers,
            method="POST" if data is not None else "GET",
        )
        try:
            with self._opener.open(request, timeout=self.timeout_seconds) as response:
                payload = response.read(self.max_bytes + 1)
        except (HTTPError, URLError, TimeoutError, OSError) as exc:
            raise RuntimeError(f"Device API request failed: {exc}") from exc
        if len(payload) > self.max_bytes:
            raise ValueError("Device API response exceeded the configured size limit")
        return payload.decode("utf-8", errors="replace") or "OK"

    def read(self, device: NetworkDevice, resource: str) -> str:
        return self._request(device, self._resource_url(device, resource))

    def control(self, device: NetworkDevice, action: str, payload: dict[str, object]) -> str:
        if not action.strip() or "/" in action or action in {".", ".."}:
            raise ValueError("A single safe action name is required")
        url = self._resource_url(device, f"actions/{action}")
        body = json.dumps(payload).encode("utf-8")
        return self._request(device, url, body)
