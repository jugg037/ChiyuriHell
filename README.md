# ChiyuriHell

ChiyuriHell is an open-source Android project for privacy-respecting access to legitimate internet resources when networks are unreliable or access is restricted.

**Target:** Android 10 (API 29) and newer  
**Routing:** ALL — full-device VPN tunnel  
**Server policy:** MIXED — project-operated infrastructure plus vetted community/provider nodes  
**Current app version:** 0.2.0 (development)

## Current status

The Android app now integrates the official WireGuard tunnel library. Users can import a WireGuard configuration file, and the app validates that it contains IPv4 and IPv6 interface addresses, DNS servers, one peer, and both default routes (0.0.0.0/0 and ::/0) before enabling connection. The backend is managed through the official WireGuard Android GoBackend.

Imported profiles are currently held in application memory only and are discarded when the process exits. This avoids writing private keys to ordinary preferences or files while secure, user-controlled persistence is designed.

This is an early integration build, **not yet a production-certified VPN**. A profile passing structural validation does not prove that its operator is trustworthy, that the server is reachable, or that traffic is leak-free. Do not use unknown public profiles for sensitive traffic.

## Security principles

- No undisclosed traffic logging or sale of browsing data.
- No arbitrary public proxy is trusted by default.
- Server operators, jurisdictions, retention policies, and transport security must be documented.
- Profile parsing and validation must not log private keys or full configuration contents.
- A connection must not be reported as active unless the backend reports the tunnel as up.
- Full-tunnel profiles must include IPv4 and IPv6 default routes and configured DNS.
- DNS, IPv6, reconnect, network-change, and fail-closed behavior require device-level tests.
- Third-party components must be reviewed for maintenance status, security, and license compatibility.

## Current implementation

- Android 10+ application foundation and crimson theme.
- Official WireGuard Android tunnel library (Apache-2.0).
- Configuration import through Android document picker.
- Profile size limit and structural validation for dual-stack ALL mode.
- Process-wide tunnel controller to retain backend identity across Activity recreation.
- VPN permission flow and connect/disconnect control.
- GitHub Actions unit-test and debug APK build with SHA-256 checksum artifact.

## Planned milestones

1. Complete backend integration tests and test on physical Android devices.
2. Add secure, user-controlled profile persistence and profile management.
3. Build a curated MIXED server catalog with explicit operator consent, signed metadata, expiry, and revocation.
4. Add network diagnostics, IPv4/IPv6/DNS leak checks, and reconnect handling.
5. Evaluate additional maintained transports and providers; do not silently import unknown public proxies.
6. Security review, signed release builds, and public release documentation.

## Build

GitHub Actions runs unit tests and builds the debug APK on pushes and pull requests to main. Locally, with JDK 17 and Gradle 8.9:

    gradle testDebugUnitTest assembleDebug

The APK is produced at app/build/outputs/apk/debug/app-debug.apk.

## Server safety

A successful ping is not proof that a server is trustworthy. Community nodes must have a documented operator, explicit permission for public use, a published privacy policy, and a way to revoke compromised profiles. The app will not scrape or automatically rotate arbitrary public endpoints.

## License

The project license will be finalized after the dependency and contribution policy review. The WireGuard tunnel dependency is Apache-2.0; this does not automatically determine the license of this repository.
