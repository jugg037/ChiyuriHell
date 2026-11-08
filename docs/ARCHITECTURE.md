# ChiyuriHell architecture

## Product decisions

- Platform: Android 10+ (API 29+).
- Routing: ALL — full-device VPN tunnel.
- Server policy: MIXED — project-operated nodes plus vetted community/provider nodes.
- Failure policy: fail closed; never claim protection if tunnel establishment fails.

## Implemented: WireGuard first transport

The Android app integrates com.wireguard.android:tunnel:1.0.20260102 and its GoBackend. The library's own GoBackend.VpnService owns the TUN interface and userspace WireGuard engine. Do not add a second competing VPN service for this backend.

The process-wide WireGuardController keeps one backend and one stable Tunnel identity across Activity recreation. Imported profiles are held in memory only; no private key is written to ordinary preferences or logs.

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

Failure returns to DISCONNECTED/ERROR. The UI only reports connected after the WireGuard backend reports Tunnel.State.UP. A permission denial must not leave the connect control disabled. Android Always-on VPN is explicitly disabled until secure profile persistence and the restart callback are implemented. The current build is not a system-wide kill switch: traffic may fall back to the underlying network after the VPN stops.

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
- Android battery/background restrictions;
- behavior when the server stops responding.

## Open decisions

- Secure, user-controlled profile persistence and key handling.
- Signed server catalog format, signing key rotation, and revocation.
- Additional transports only after maintenance, security, and license review.
- Privacy-preserving diagnostics and opt-in telemetry policy.
