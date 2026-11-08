# ChiyuriHell architecture (initial)

## Product decisions

- Platform: Android 10+ (API 29+).
- Routing intent: ALL device traffic through one VPN tunnel.
- Server policy: MIXED, combining project-operated nodes and vetted community/provider nodes.
- The client must fail closed: it must never claim protection when the tunnel is absent or routing is incomplete.

## Planned components

1. Android UI and connection state machine.
2. Android VpnService lifecycle adapter.
3. Reviewed tunnel backend adapter (selection pending security and license review).
4. Signed server-profile catalog and profile verifier.
5. Routing policy: IPv4, IPv6, DNS, and private/local network behavior.
6. Diagnostics that do not upload browsing history.
7. Security and build verification.

## Connection state machine

DISCONNECTED -> PREPARING -> CONNECTING -> CONNECTED

Any failure transitions to ERROR or DISCONNECTED. CONNECTED is permitted only after the backend confirms tunnel establishment and route/DNS checks pass. Reconnects must re-verify the profile and tunnel. User-visible state must never be inferred from a button press.

## Server profile requirements

A profile is eligible only when it has:
- a stable identifier and version;
- a named operator and contact or project page;
- explicit authorization for public use;
- documented jurisdiction and retention policy;
- authenticated transport configuration;
- expiry/revocation metadata and integrity verification.

A reachability test is not a trust assessment. Unknown public proxies are excluded from automatic selection.

## Full-tunnel requirements

Before enabling ALL mode, tests must cover:
- IPv4 default route;
- IPv6 default route or explicit IPv6 blocking;
- DNS routing and leak checks;
- network changes and captive portals;
- tunnel process crash and reconnect;
- boot/restart behavior if enabled;
- Android battery/background restrictions;
- fail-closed behavior during every transition.

## Open decisions

- Select tunnel backend only after reviewing maintenance, Android integration, license, and threat model.
- Decide whether the server catalog is signed by a project key and how key rotation/revocation works.
- Decide how user-supplied profiles are isolated from curated community profiles.
