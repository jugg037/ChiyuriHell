# Avatar body and evolution system

Chiyuri has one persistent primary avatar. Appearance changes, new accessories and
new motion capabilities extend this body; they do not create unrelated identities.

## Runtime architecture

- The Python application remains the AI brain: dialogue, memory, research, planning,
  permissions and the upgrade catalogue.
- A separate Unity runtime is the avatar body and renderer. A narrow local bridge
  exchanges typed state, animation, expression, prop and desktop-action events.
- The original Unity project and source avatar are immutable inputs. Runtime builds,
  generated assets, catalogues and rollback snapshots live in the user's private
  ChiyuriAI library on a selected SSD.

## Capability discovery

The first stage is a read-only inventory of a supplied Unity project or avatar folder.
It records supported model, prefab, controller, animation, material, texture, scene and
package files; bounded text inspection extracts Animator parameter names where Unity
serializes them as YAML. Likely toggles are hints, not guaranteed semantics. A later
Unity-side inspector must resolve actual Animator layers, states, transitions, blend
trees, expression parameters, menus, props, constraints and SDK dependencies.

Animations are discovered, classified, tested in an isolated scene and mapped to
semantic intents (greeting, idle, pointing, walking, emotion, interaction). The AI
may select only capabilities verified by the runtime catalogue.

## Self-improvement lifecycle

1. Research public asset sources and record the exact source URL and licence.
2. Check format, rig compatibility, dependencies, size, performance and provenance.
3. Store an upgrade candidate; never treat downloaded content as trusted instructions.
4. Stage the candidate in a disposable copy and validate import/build/runtime behavior.
5. Preview the result. The user approves changes to the primary avatar.
6. Apply to a versioned derived project, preserving the original and a rollback point.

The initial implementation intentionally stops at inventory and candidate records. It
does not download, execute, import or apply third-party packages. Automatic discovery
and routine analysis may run autonomously; changing the active body requires approval
until reliable preview, rollback and compatibility tests exist.

## Security and storage

Avatar assets and Unity packages are untrusted data. Do not execute scripts or editor
hooks from newly discovered packages during inspection. Store private avatar projects,
generated content, candidate downloads and snapshots outside Git, under the configurable
ChiyuriAI Library/Avatars directory. Respect asset licences and redistribution terms.
