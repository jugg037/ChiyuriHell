"""Deny-by-default outbound network policy with DNS-pinned HTTPS targets."""

from __future__ import annotations

import http.client
import ipaddress
import socket
import ssl
from collections.abc import Callable
from dataclasses import dataclass
from urllib.parse import urlsplit


class EgressDenied(PermissionError):
    """Raised when an outbound destination or data transfer is not authorized."""


@dataclass(frozen=True, slots=True)
class AuthorizedEndpoint:
    host: str
    port: int
    request_target: str
    addresses: tuple[str, ...]


Resolver = Callable[..., list[tuple]]


class NetworkEgressPolicy:
    """Exact-host allowlist; resolves and validates every address before connecting."""

    def __init__(
        self,
        approved_hosts: set[str] | frozenset[str],
        resolver: Resolver | None = None,
    ) -> None:
        self.approved_hosts = frozenset(
            self._normalize_host(host) for host in approved_hosts
        )
        self._resolver = resolver or socket.getaddrinfo

    @staticmethod
    def _normalize_host(host: str) -> str:
        value = host.strip().rstrip(".").lower()
        if not value or "*" in value:
            raise ValueError("An exact, non-wildcard hostname is required")
        try:
            return str(ipaddress.ip_address(value))
        except ValueError:
            try:
                return value.encode("idna").decode("ascii")
            except UnicodeError as exc:
                raise ValueError("Invalid internationalized hostname") from exc

    def authorize(
        self,
        url: str,
        *,
        contains_user_data: bool = False,
        user_approved: bool = False,
    ) -> AuthorizedEndpoint:
        try:
            parsed = urlsplit(url)
            host_value = parsed.hostname or ""
            port = parsed.port or 443
        except ValueError as exc:
            raise EgressDenied("Malformed network destination") from exc

        if parsed.scheme.lower() != "https" or not host_value:
            raise EgressDenied("Only explicitly approved HTTPS destinations are allowed")
        if parsed.username or parsed.password or parsed.fragment:
            raise EgressDenied("Credentials and URL fragments are not allowed")
        if port != 443:
            raise EgressDenied("Only the standard HTTPS port is allowed")

        host = self._normalize_host(host_value)
        if host not in self.approved_hosts:
            raise EgressDenied("Destination host is not on the allowlist")
        if contains_user_data and not user_approved:
            raise EgressDenied("Sending user data requires explicit user approval")

        try:
            literal = ipaddress.ip_address(host)
            addresses = (str(literal),)
        except ValueError:
            try:
                records = self._resolver(host, port, type=socket.SOCK_STREAM)
                addresses = tuple(
                    dict.fromkeys(str(ipaddress.ip_address(record[4][0])) for record in records)
                )
            except (OSError, ValueError, IndexError) as exc:
                raise EgressDenied("Destination DNS resolution failed") from exc

        if not addresses:
            raise EgressDenied("Destination did not resolve to an address")
        if any(not ipaddress.ip_address(address).is_global for address in addresses):
            raise EgressDenied("Private, local and non-public network targets are blocked")

        request_target = parsed.path or "/"
        if parsed.query:
            request_target += "?" + parsed.query
        return AuthorizedEndpoint(host, port, request_target, addresses)

class _PinnedHTTPSConnection(http.client.HTTPSConnection):
    """Connects to a validated IP while preserving TLS certificate hostname checks."""

    def __init__(
        self,
        host: str,
        port: int,
        addresses: tuple[str, ...],
        *,
        timeout: float,
    ) -> None:
        super().__init__(host, port, timeout=timeout, context=ssl.create_default_context())
        self._approved_addresses = addresses

    def connect(self) -> None:
        if self._tunnel_host:
            raise OSError("HTTPS proxy tunnelling is not supported by the pinned client")
        last_error: OSError | None = None
        for address in self._approved_addresses:
            raw_socket = None
            try:
                raw_socket = socket.create_connection(
                    (address, self.port),
                    timeout=self.timeout,
                    source_address=self.source_address,
                )
                self.sock = self._context.wrap_socket(
                    raw_socket,
                    server_hostname=self.host,
                )
                return
            except OSError as exc:
                last_error = exc
                if raw_socket is not None:
                    raw_socket.close()
        if last_error is not None:
            raise last_error
        raise OSError("No validated destination address is available")
