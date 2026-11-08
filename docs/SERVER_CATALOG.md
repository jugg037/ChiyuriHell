# MIXED server catalog specification

## Purpose

The MIXED policy combines ChiyuriHell-operated infrastructure with community/provider nodes that have explicitly authorized public use. It does not mean importing random proxy lists or scraping endpoints from unrelated applications.

## Catalog entry fields

Each future catalog entry must provide:

- stable node ID and display name;
- transport type and supported client version;
- named operator and public project/contact page;
- jurisdiction and hosting provider;
- privacy policy and retention statement;
- explicit public-use authorization and terms;
- profile download URL over HTTPS;
- SHA-256 digest of the exact profile bytes;
- detached Ed25519 signature over those exact bytes;
- publication and expiry timestamps;
- revocation status or a signed revocation-list reference.

Private keys must never appear in catalog metadata or in repository commits. A WireGuard client profile contains a client private key, so profiles must be provisioned per user/device, not shared as one universal configuration.

## Trust and selection rules

1. A reachable endpoint is not automatically trusted.
2. An entry without a named operator, public-use permission, privacy statement, and expiry is rejected.
3. The profile digest and signature must be verified before parsing or import.
4. Expired or revoked entries are never offered for connection.
5. Health checks may report reachability, latency, and handshake success, but must not imply operator trust.
6. The client must show the operator and jurisdiction before the first connection.
7. Community entries are opt-in until a review process and signing-key rotation are implemented.
8. No catalog entry may silently replace a user's selected node.

## Signing model

The catalog publisher signs exact profile bytes with an Ed25519 key held only by the catalog maintainer. The Android application embeds only the corresponding public verification key. Key rotation requires a transition signed by the old key and the new key, or a separately documented recovery procedure. Never place a signing private key in this repository or in GitHub Actions logs.

## Current implementation status

This document defines requirements only. The app currently supports user-selected WireGuard .conf import with structural ALL-mode checks. It does not yet download profiles, verify catalog signatures, or provide a public server list.
