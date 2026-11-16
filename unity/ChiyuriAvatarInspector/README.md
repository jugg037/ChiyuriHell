# Chiyuri Unity avatar inspector

This is a read-only Unity Editor utility for collecting an avatar capability
inventory before connecting Chiyuri to the avatar.

## Install into an existing Unity project

1. Create the folder `Assets/Editor/ChiyuriAI` in a copy of the Unity project.
2. Copy `ChiyuriAvatarInspector.cs` from this repository into that folder.
3. Wait for Unity to compile the Editor script.
4. Select the avatar root GameObject in the Hierarchy.
5. Choose **Chiyuri → Avatar → Inspect Selected Avatar (Read Only)**.
6. Save the generated JSON inventory and provide it for review.

The report includes Animator parameters, layers, nested state names,
transitions, Blend Tree child motions, component type names and likely toggle
candidates. Candidates are only names for further review; they are not assumed
to be safe or executable actions.

## Safety and limitations

- The inspector does not modify, import, delete or reconfigure avatar assets.
- It does not run avatar animations or control the desktop.
- It does not yet decode VRChat expression menus or parameter assets. Component
  type names are included so a later SDK-aware inspector can identify them.
- The utility has not been compiled against the user's actual Unity/VRChat SDK
  project. Validate it in a disposable project copy first.
- Keep the original avatar project unchanged; use a separate working copy.
