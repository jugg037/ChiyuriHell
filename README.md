# Chiyuri Desktop

Windows 10-inspired Android launcher with Chiyuri black/crimson styling.

**Target device:** Samsung Galaxy Tab S6 Lite SM-P619  
**Initial version:** 0.1.0  
**Platform:** Android APK only (no EXE/Wine support planned)

## Prototype features
- Windows-inspired desktop and bottom taskbar
- Start menu with installed Android app list and search
- Launch installed Android apps
- Clock, date and battery indicator
- Dark black/crimson theme
- Portrait and landscape responsive layout

## Build
GitHub Actions builds a debug APK on pushes to `main` and manual workflow dispatch.
Artifact: `ChiyuriDesktop-debug`.

## Important limitations
This is a launcher, not a Windows emulator or replacement OS. It does not yet provide true independent virtual desktops, arbitrary resizable app windows, or privileged system controls. Those features will be developed only where Android APIs and Samsung firmware permit them.

## Install
Download the APK artifact from the Actions run, install it, press Home and select **Chiyuri Desktop**. To return to Samsung's launcher, select One UI Home in Android's default Home app settings.
