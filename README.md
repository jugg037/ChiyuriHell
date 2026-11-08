# ChiyuriHell

ChiyuriHell is an open-source Android project intended to provide privacy-respecting access to legitimate internet resources when networks are unreliable or access is restricted.

**Target:** Android 10 (API 29) and newer  
**Routing:** ALL — full-device VPN tunnel  
**Server policy:** MIXED — project-operated infrastructure plus vetted community/provider nodes

## Current status

This repository contains the initial Android application foundation and a GitHub Actions debug build. It is **not yet a working VPN client**. The connect control is deliberately disabled until a real tunnel backend, authenticated server profiles, leak protection, and fail-closed routing are implemented and tested.

## Principles

- No undisclosed traffic logging or sale of browsing data.
- No anonymous public proxy is trusted by default.
- Server operators, jurisdictions, retention policies, and transport security must be documented.
- Profiles must be authenticated and integrity-checked.
- The app must not report a connection unless the tunnel and routing have been verified.
- DNS and IPv6 leak behavior, reconnect handling, and kill-switch behavior require explicit tests.
- Third-party components must be reviewed for security, maintenance status, and license compatibility.

## Planned milestones

1. Android foundation and reproducible CI build.
2. Integrate a maintained VPN backend with a documented license and threat model.
3. Add authenticated profile import and a curated server catalog.
4. Implement full-tunnel IPv4/IPv6 routing, DNS handling, reconnect behavior, and fail-closed protection.
5. Add diagnostics and network-quality checks.
6. Security review, device testing, and signed release builds.

## Building

GitHub Actions builds a debug APK on pushes and pull requests to main. Locally, with JDK 17 and Gradle 8.9 installed:

    gradle assembleDebug

The APK is produced at app/build/outputs/apk/debug/app-debug.apk.

## Server safety

ChiyuriHell will not silently scrape, bundle, or rotate arbitrary public proxy endpoints. Community nodes must have a documented operator, explicit permission for public use, a published privacy policy, and a way to revoke compromised profiles. A successful ping is not proof that a server is trustworthy.

## License

The project license will be finalized after the initial dependency and contribution policy review. Do not assume that all planned third-party components share the same license.
