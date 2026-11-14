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
The project is developed in `main` in feature stages. **No public release is planned until every applicable item below is implemented, audited and tested.** The tag workflow also requires a `RELEASE_READY` file containing exactly `READY`; it will reject release tags until the final audit explicitly creates that marker. A final installable release is signed with a stable Android keystore supplied through the GitHub Actions secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`. See [DEVICE_TEST_GUIDE.md](DEVICE_TEST_GUIDE.md) for SM-P619 testing instructions, [RELEASE_SIGNING.md](RELEASE_SIGNING.md) for signing setup, [FINAL_AUDIT.md](FINAL_AUDIT.md) for the mandatory pre-release checklist and [PLATFORM_LIMITATIONS.md](PLATFORM_LIMITATIONS.md) for documented Android/archive boundaries.

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
- [x] Live status indicators
  - [x] Battery, charging, network, clock and date indicators
- [ ] Final portrait/landscape layout validation on SM-P619

### Stage 0.2 — Multitasking
- [x] Android system task-stack control documented as unavailable; UsageStats recent package history and Alt+Tab provided
  - [x] Usage Access-based recent-app list and Alt+Tab switching (package history, not Android task-stack control)
  - [x] Launcher-local recent launch history (last 8 apps)
  - [x] Last-launched app marker on the taskbar (not a claim of current foreground state)
- [x] Three persistent workspaces with independently managed shortcuts and customizable workspace names
- [x] Built-in window-like panels; Android Back closes the active panel or Start menu; external windows remain Android/Samsung-limited
  - [x] File explorer and appearance panels can remain open together with focus highlighting
  - [x] Adjustable built-in panel width, minimized/restored from the taskbar
  - [x] Taskbar shortcuts can reopen/focus the built-in panels
- [x] Window focus, minimize/restore and close behavior for supported in-app panels
- [x] Tablet-oriented split layouts and keyboard/pointer interaction; dual panels require at least 1024 dp and automatically narrow to preserve desktop workspace width
  - [x] Keyboard shortcuts: Ctrl+Space (Start), Ctrl+Alt+F (Files), Ctrl+Alt+S (Settings), Ctrl+1/2/3 (workspaces), Alt+Tab (recent launcher apps)
- [x] Clearly report Android/firmware limitations rather than simulate unsupported OS-level window control

### Stage 0.3 — File manager
- [x] Browse a user-selected shared-storage folder using Android's Storage Access Framework, with breadcrumb, parent/root navigation and manual refresh
- [x] Search and sort entries by name, modification date, size or file type
- [x] Open supported files with installed Android apps
- [x] Basic file operations: create folders, rename files/folders and confirm recursive deletion using granted SAF permissions
- [x] Copy/move files and folders recursively using SAF, with 1,000-entry, 64-level and 512 MiB limits
- [x] Progress feedback and action lockout during long file/archive operations
- [x] Advanced permission/error recovery for common SAF provider failures
  - [x] Detect expired SAF folder grants and request folder selection again
  - [x] Detect read-only folders and disable write operations
- [x] ZIP, RAR and 7Z extraction with entry-count and expanded-size limits
- [x] Optional archive password field; memory caps for 7Z and RAR extraction
- [x] Archive preview/listing before extraction
- [x] Unsupported legacy/strong-encryption archive cases documented as explicit limitations (RAR1.4 multi-volume, ZIP strong encryption, unsupported 7z codecs)
  - [x] RAR4 .rar/.r00/.r01 and RAR5 .partN.rar multi-volume sets when all parts are available
  - [x] Password-protected ZIP extraction (AES and traditional ZIP encryption)
  - [x] Archive extraction preserves safe relative folder structure and rejects traversal paths; see THIRD_PARTY_NOTICES.md for limits
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
- [x] Root-only controls intentionally excluded; no root requirement for core launcher
- [x] Keep the launcher fully usable without root

### Explicit exclusions
- Windows OS emulation, EXE/Wine and the previously discussed Winlator stage are excluded.
- Android APK is the only target package.
- Third-party archive-library licenses and restrictions are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and `licenses/`.
- A feature that Android or the target Samsung firmware cannot safely provide must be documented as a platform limitation, not falsely marked complete.

## Current development gate

The applicable feature roadmap is implemented. The remaining roadmap item is final portrait/landscape validation on the target SM-P619; automated lint/tests, APK verification and the full release audit are still required before publication.

## Release status

No public release is being published during feature development. Pushes to `main` may produce temporary CI debug artifacts for build verification; they are not releases. A stable signed APK and GitHub Release will be created only after the applicable roadmap items and [FINAL_AUDIT.md](FINAL_AUDIT.md) checks pass. Release notes will be curated to list user-visible changes only, without CI/signing/test boilerplate. The tag workflow rejects publication while either the roadmap or final audit contains unchecked items.

## Build
GitHub Actions builds a debug APK on pushes to `main` and manual workflow dispatch.
Artifact: `ChiyuriDesktop-debug`.

## Important limitations
This is a launcher, not a Windows emulator or replacement OS. The three workspaces separate launcher desktop shortcuts, but Android does not expose isolated external-app task spaces to ordinary launchers. System-level task switching and arbitrary external app window management remain limited by Android and Samsung firmware. Chiyuri Desktop can request adjacent/split-screen launches and provide its own multi-panel workspace, but cannot force unsupported windowing behavior.

## Install
Download the APK artifact from the Actions run, install it, press Home and select **Chiyuri Desktop**. To return to Samsung's launcher, select One UI Home in Android's default Home app settings.
