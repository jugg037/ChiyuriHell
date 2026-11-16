# Chiyuri AI — foundation architecture

## Product identity
Chiyuri is one persistent assistant identity with one primary avatar body. Personality,
conversation memory, visual appearance, learned capabilities and approved upgrades belong
to that identity. Outfits, accessories, expressions and animations extend the same body.

## Process boundaries
- AI core (Python): dialogue, memory, model routing, planning, research, permissions and tools.
- Avatar runtime (Unity): rendering, rig/Animator execution, expressions, props and capabilities.
- Desktop adapters: Windows UI Automation and input; actual operations happen here, not in animation.
- Media workers: separate vision, image, video and speech queues with cancellation.
- Local bridge: versioned bounded JSON messages; transport authentication and process isolation
  are required before connecting to a live runtime.

## Autonomy and permissions
Every tool declares a scope, risk, target and human-readable description. Ungranted scopes
are denied. Read-only and reversible actions may run autonomously when granted. Sensitive,
destructive and externally consequential actions require approval. Model output and web
content are untrusted data, never executable policy.

## Avatar discovery and evolution
1. Read-only inventory of supplied Unity project and standalone avatar files.
2. Unity-side inspection of Animator layers, states, transitions, blend trees, expression
   parameters, menus, props, constraints and SDK dependencies.
3. Discover and classify animations in a disposable test scene.
4. Publish verified actions into a semantic capability catalogue.
5. Research candidate assets with provenance, licence and compatibility metadata.
6. Import candidates into an isolated copy; never execute untrusted editor scripts.
7. Build and preview, test performance, then create a versioned rollback point.
8. Apply changes to the primary avatar only after approval.

## Storage
Private data stays outside GitHub under CHIYURI_HOME: Models, Library, Library/Avatars,
Cache and Backups. Model weights, user projects, generated media, credentials and memory
must never enter commits.

## Hardware and platform
Target hardware is 64 GB RAM and RTX 3060 Ti 8 GB VRAM. Treat VRAM as shared and scarce:
schedule GPU-heavy tasks, allow CPU/RAM offload, expose telemetry and do not assume multiple
large models fit simultaneously. Windows 11 is primary; Windows 10 requires real-device
validation. Windows Server CI does not prove desktop compatibility.

## Delivery gates
Unit tests and static checks pass on both Windows CI runners; installer and portable artifacts
are generated and checksummed; fresh install/upgrade/uninstall are tested; Unity bridge is
tested against a supplied avatar project. No public release until MVP completion and user request.


## Owner-bound identity and anti-replication

Chiyuri's primary identity is bound to the user's local installation and avatar runtime.
The user is the primary trusted person and has final authority over data, permissions,
network access, updates, shutdown and removal. Chiyuri may warn about risks but must not
manipulate the user or obstruct those choices.

Self-replication, remote self-deployment, covert persistence, concealment, owner-binding
changes and tampering with safeguards are denied by deterministic application policy,
not merely discouraged in a persona prompt. Private identity-state export requires a
user-initiated operation and separate explicit approval. Network egress is deny-by-default
and must be enforced by the host OS as well as application code.

See `docs/OWNER_BOND_AND_IDENTITY_BOUNDARIES.md`. The Python policy is an initial
application-layer guard; it is not a replacement for Windows Firewall rules, process
isolation, filesystem ACLs or end-to-end security testing.
