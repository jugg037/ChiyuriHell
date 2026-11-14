# Chiyuri Desktop

Windows 10-inspired Android launcher with Chiyuri black/crimson styling.

**Target device:** Samsung Galaxy Tab S6 Lite SM-P619  
**Initial version:** 0.1.0  
**Platform:** Android APK only (no EXE/Wine support planned)

## Implemented foundation
- Windows-inspired desktop and bottom taskbar
- Start menu with installed Android app list and search
- Launch installed Android apps
- Pin and unpin apps on the taskbar with long-press; pins persist after restart
- Horizontally scrollable taskbar for many pinned apps
- Clock, date and battery indicator
- Dark black/crimson theme
- Portrait and landscape responsive layout

## Development roadmap — release is deliberately postponed
The project is developed in `main` in feature stages. **No public release is planned until every applicable item below is implemented, audited and tested.**

### Stage 0.1 — Desktop shell
- [x] Desktop, Start menu, app search and Android app launching
- [x] Persistent taskbar app pinning and unpinning
- [ ] Taskbar polish, active-app indication and app switching
- [x] Desktop shortcuts and shortcut management
- [x] File explorer entry point and folder navigation via Android Storage Access Framework
- [ ] Wallpaper selection, scaling and theme customization
  - [x] Custom image wallpaper and three built-in dark/crimson background presets
  - [x] Wallpaper scaling modes (fill/fit/stretch)
- [ ] Reliable status indicators and orientation/layout polish

### Stage 0.2 — Multitasking
- [ ] System recent-task switching (restricted by Android)
  - [x] Launcher-local recent launch history (last 8 apps)
  - [x] Last-launched app marker on the taskbar (not a claim of current foreground state)
- [ ] Multi-window workspace and window-like app panels where Android/Samsung APIs permit
- [ ] Window focus, minimize/restore and close behavior for supported in-app panels
- [ ] Tablet-oriented split layouts and keyboard/mouse interaction
- [ ] Clearly report Android/firmware limitations rather than simulate unsupported OS-level window control

### Stage 0.3 — File manager
- [x] Browse a user-selected shared-storage folder using Android's Storage Access Framework
- [x] Open supported files with installed Android apps; create folders and delete files
- [ ] File operations with Android permission handling
- [ ] Archive browsing and extraction for ZIP, RAR and 7Z where compatible libraries and licenses permit
- [ ] Error handling for inaccessible, protected and unsupported files

### Stage 0.5 — System tools and advanced settings
- [x] Basic device dashboard (model, Android version, available RAM, battery)
- [ ] Live resource and battery monitoring using public Android APIs
- [ ] Launcher behavior, appearance and interaction settings
- [ ] Optional controls only where Android permissions and Samsung firmware allow them

### Stage 0.6 — Privileged features (conditional)
- [ ] Evaluate root-only capabilities separately
- [ ] Implement only if explicitly supported, safely detectable and optional
- [ ] Keep the launcher fully usable without root

### Explicit exclusions
- Windows OS emulation, EXE/Wine and the previously discussed Winlator stage are excluded.
- Android APK is the only target package.
- A feature that Android or the target Samsung firmware cannot safely provide must be documented as a platform limitation, not falsely marked complete.

## Build
GitHub Actions builds a debug APK on pushes to `main` and manual workflow dispatch.
Artifact: `ChiyuriDesktop-debug`.

## Important limitations
This is a launcher, not a Windows emulator or replacement OS. It does not yet provide true independent virtual desktops, arbitrary resizable app windows, or privileged system controls. Those features will be developed only where Android APIs and Samsung firmware permit them.

## Install
Download the APK artifact from the Actions run, install it, press Home and select **Chiyuri Desktop**. To return to Samsung's launcher, select One UI Home in Android's default Home app settings.
