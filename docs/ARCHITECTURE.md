# ChiyuriHell architecture

## Product decisions

- Platform: Android 10+ (API 29+).
- Routing: ALL — full-device VPN tunnel.
- Server policy: MIXED — project-operated nodes plus vetted community/provider nodes.
- Failure policy: fail closed when Android lockdown is enabled; without lockdown, the OS may route traffic outside the VPN.

## Implemented: WireGuard first transport

The Android app integrates com.wireguard.android:tunnel:1.0.20260102 and its GoBackend. The library's own GoBackend.VpnService owns the TUN interface and userspace WireGuard engine. Do not add a second competing VPN service for this backend.

The process-wide WireGuardController keeps one backend and one stable Tunnel identity across Activity recreation. Imported profiles are encrypted at rest with AES-256-GCM; the key is generated and held by Android Keystore. AtomicFile protects replacement from partial writes. The app decrypts and revalidates the profile on launch.

A custom Application initializes GoBackend and registers its documented AlwaysOnCallback before Android can create GoBackend.VpnService directly. When Android starts the service in Always-on mode, the callback loads the encrypted profile, validates it again, and applies it to the backend on a worker thread. A recovery failure is surfaced in the Activity when opened. Manual launches still do not auto-connect unless Android starts the service in Always-on mode.

## ALL-mode profile policy

The initial importer accepts only structurally valid WireGuard profiles that have:
- both IPv4 and IPv6 interface addresses;
- at least one DNS server;
- exactly one peer;
- both 0.0.0.0/0 and ::/0 in AllowedIPs;
- no included-application or excluded-application filters;
- a peer endpoint.

Profiles missing any requirement are rejected before the connect control is enabled. This is a conservative first policy, not a substitute for packet-level leak tests or a trust assessment of the server operator.

## Connection lifecycle

DISCONNECTED -> VPN_PERMISSION -> CONNECTING -> CONNECTED

Failure returns to DISCONNECTED/ERROR. The UI only reports connected after the WireGuard backend reports Tunnel.State.UP. A permission denial must not leave the connect control disabled.

The app advertises Android Always-on support and registers the WireGuard GoBackend system-start callback. This implementation is experimental until reboot, process-death, network-switch, and server-loss tests pass on physical devices. Android's **Block connections without VPN** is a separate system lockdown setting. Always-on without lockdown can still permit fallback to the underlying network; do not describe the app as a verified system-wide Kill Switch until device tests confirm fail-closed behavior.

## Server profile requirements

A catalog entry is eligible only when it has:
- stable identifier and version;
- named operator and contact/project page;
- explicit authorization for public use;
- documented jurisdiction and retention policy;
- authenticated transport configuration;
- expiry/revocation metadata and integrity verification.

A reachability test is not a trust assessment. Unknown public proxies are excluded from automatic selection.

## Remaining full-tunnel requirements

Physical-device tests must cover:
- IPv4 and IPv6 default routes;
- DNS routing and leak behavior;
- network changes and captive portals;
- tunnel process crash and reconnect;
- VPN permission revocation;
- app process death and restart;
- Android reboot and Always-on service recovery;
- Android battery/background restrictions;
- behavior when the server stops responding;
- lockdown behavior with an unreachable endpoint.

## Open decisions

- Signed server catalog format, signing key rotation, and revocation.
- Additional transports only after maintenance, security, and license review.
- Privacy-preserving diagnostics and opt-in telemetry policy.
