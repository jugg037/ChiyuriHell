# Chiyuri Desktop

Windows 10-inspired Android launcher with Chiyuri black/crimson styling.

**Target device:** Samsung Galaxy Tab S6 Lite SM-P619  
**Initial version:** 0.1.0  
**Platform:** Android APK only (no EXE/Wine support planned)

## Implemented foundation
- Windows-inspired desktop and bottom taskbar
- Chiyuri CP adaptive launcher icon
- Start menu with installed Android app list and search
- Launch installed Android apps
- Pin and unpin apps on the taskbar with long-press; pins persist after restart
- Horizontally scrollable taskbar for many pinned apps
- Clock, date and battery indicator
- Dark black/crimson theme
- Portrait and landscape responsive layout

## Development roadmap — release is deliberately postponed
The project is developed in `main` in feature stages. **No public release is planned until every applicable item below is implemented, audited and tested.** The tag workflow also requires a `RELEASE_READY` file containing exactly `READY`; it will reject release tags until the final audit explicitly creates that marker. A final installable release is signed with a stable Android keystore supplied through the GitHub Actions secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`. See [RELEASE_SIGNING.md](RELEASE_SIGNING.md) for setup instructions and [FINAL_AUDIT.md](FINAL_AUDIT.md) for the mandatory pre-release test checklist.

### Stage 0.1 — Desktop shell
- [x] Desktop, Start menu, app search and Android app launching
- [x] Persistent taskbar app pinning and unpinning
- [x] Ordered taskbar pins with left/right reordering from the Start menu
- [x] Taskbar foreground-app indication (optional Usage Access permission)
  - [x] Optional real foreground detection after the user grants Usage Access
  - [x] Recent launch history and last-launched marker
  - [x] Usage Access-based recent-app list in Start menu and Alt+Tab switching
  - [x] Adjacent/split-screen launch request for apps on supported Android/Samsung configurations
- [x] Desktop shortcuts and shortcut management
- [x] File explorer entry point and folder navigation via Android Storage Access Framework
- [x] Wallpaper selection, scaling and theme customization
  - [x] Custom image wallpaper and three built-in dark/crimson background presets
  - [x] Wallpaper scaling modes (fill/fit/stretch)
  - [x] Selectable blood-red, burgundy and scarlet accent colors
- [ ] Reliable status indicators and orientation/layout polish
  - [x] Live battery, charging, network, clock and date indicators

### Stage 0.2 — Multitasking
- [ ] System recent-task switching (restricted by Android)
  - [x] Launcher-local recent launch history (last 8 apps)
  - [x] Last-launched app marker on the taskbar (not a claim of current foreground state)
- [x] Launcher multi-workspace and built-in window-like panels; external windows remain Android/Samsung-limited
  - [x] File explorer and appearance panels can remain open together with focus highlighting
  - [x] Adjustable built-in panel width, minimized/restored from the taskbar
  - [x] Taskbar shortcuts can reopen/focus the built-in panels
- [x] Window focus, minimize/restore and close behavior for supported in-app panels
- [x] Tablet-oriented split layouts and keyboard/pointer interaction
  - [x] Keyboard shortcuts: Ctrl+Space (Start), Ctrl+Alt+F (Files), Ctrl+Alt+S (Settings), Ctrl+1/2/3 (workspaces), Alt+Tab (recent launcher apps)
- [x] Clearly report Android/firmware limitations rather than simulate unsupported OS-level window control

### Stage 0.3 — File manager
- [x] Browse a user-selected shared-storage folder using Android's Storage Access Framework
- [x] Open supported files with installed Android apps; create folders and delete files
- [x] Basic file operations: create folders, rename files/folders and confirm recursive deletion using granted SAF permissions
- [x] Copy/move files and folders recursively using SAF, with 1,000-entry, 64-level and 512 MiB limits
- [x] Confirmed recursive folder deletion with entry/depth limits
- [x] Advanced permission/error recovery for common SAF provider failures
  - [x] Detect expired SAF folder grants and request folder selection again
  - [x] Detect read-only folders and disable write operations
- [x] ZIP, RAR and 7Z extraction with entry-count and expanded-size limits
- [x] Optional archive password field; memory caps for 7Z and RAR extraction
- [x] Archive preview/listing before extraction
- [ ] Multi-volume RAR, ZIP strong encryption and broader codec compatibility
  - [x] Password-protected ZIP extraction (AES and traditional ZIP encryption)
  - Extracted archive paths are flattened into the selected folder; see THIRD_PARTY_NOTICES.md for format and resource limits
- [x] Error handling for inaccessible, protected and unsupported files (provider-specific edge cases remain subject to testing)

### Stage 0.5 — System tools and advanced settings
- [x] Basic device dashboard (model, Android version, available RAM, battery, storage and network)
- [x] Basic live resource and battery monitoring using public Android APIs
  - [x] Live battery state, available/used RAM, data-partition storage and network state
  - [x] Chiyuri process CPU and PSS memory metrics
- [x] Launcher behavior, appearance and interaction settings
  - [x] Persisted background presets, custom wallpaper image and fit/fill/stretch options
  - [x] Three persistent accent-color choices
  - [x] Configurable battery percentage and taskbar date visibility
  - [x] Adjustable app drawer tile density
  - [x] Shortcut to Android's default Home app settings
  - [x] Optional Usage Access setting for taskbar foreground highlighting
- [x] Optional shortcuts to Android Wi-Fi, Bluetooth, display, sound, battery and location settings; actual controls remain under Android

### Stage 0.6 — Privileged features (conditional)
- [x] Manual root-access check; no `su` command runs until the user explicitly taps the check button
- [x] Evaluate root-only capabilities separately; no root requirement is justified for the launcher core
- [ ] Implement optional root-only controls only if a concrete supported use case is approved
- [x] Keep the launcher fully usable without root

### Explicit exclusions
- Windows OS emulation, EXE/Wine and the previously discussed Winlator stage are excluded.
- Android APK is the only target package.
- Third-party archive-library licenses and restrictions are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and `licenses/`.
- A feature that Android or the target Samsung firmware cannot safely provide must be documented as a platform limitation, not falsely marked complete.

## Build
GitHub Actions builds a debug APK on pushes to `main` and manual workflow dispatch.
Artifact: `ChiyuriDesktop-debug`.

## Important limitations
This is a launcher, not a Windows emulator or replacement OS. The three workspaces separate launcher desktop shortcuts, but Android does not expose isolated external-app task spaces to ordinary launchers. System-level task switching and arbitrary external app window management remain limited by Android and Samsung firmware. Chiyuri Desktop can request adjacent/split-screen launches and provide its own multi-panel workspace, but cannot force unsupported windowing behavior.

## Install
Download the APK artifact from the Actions run, install it, press Home and select **Chiyuri Desktop**. To return to Samsung's launcher, select One UI Home in Android's default Home app settings.
