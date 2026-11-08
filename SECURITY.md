# Security policy

## Current security status

ChiyuriHell is an early development project, not a production-certified VPN. The current build can import and start a structurally validated WireGuard profile, but it has not yet passed physical-device leak tests or an independent security audit.

## Important ALL-mode limitation

The client routes both IPv4 and IPv6 default routes through WireGuard while the tunnel is active. This does **not** yet guarantee a system-wide kill switch if the tunnel stops or the process is terminated. Android's Always-on VPN and Block connections without VPN require a correctly implemented always-on lifecycle and persisted configuration; this project explicitly opts out of Always-on support until that work is complete. Do not treat the current build as fail-closed.

## Reporting vulnerabilities

Until a dedicated security contact is published, do not include private keys, live server credentials, personal browsing data, or other secrets in public issues. Report only reproducible technical details that are safe to disclose publicly.

## Security commitments under development

- No arbitrary public proxy is trusted by default.
- No browsing-history collection is planned.
- Server profiles must be authenticated and revocable.
- Tunnel status must be verified rather than assumed.
- IPv4, IPv6, DNS, reconnect, and fail-closed behavior must be tested before a public VPN release.
- Third-party dependencies and licenses must be reviewed before integration.

## Scope

This policy applies to the source code in this repository. It does not certify third-party servers, providers, networks, or user-imported profiles.
