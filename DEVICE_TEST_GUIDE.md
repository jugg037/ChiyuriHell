# Target-device test guide

Use this guide only after the latest `main` workflow has completed successfully and uploaded the `ChiyuriDesktop-debug` artifact. A debug artifact is a test build, not a public release.

## Install the test build
1. Open the repository's [GitHub Actions page](https://github.com/jugg037/ChiyuriHell/actions) on the Galaxy Tab S6 Lite SM-P619.
2. Open the newest successful **Android APK Build** run for `main`.
3. Download the `ChiyuriDesktop-debug` artifact and extract the APK from the downloaded ZIP.
4. Install the APK. If Android asks, allow the browser or file manager to install unknown apps.
5. Press Home and select **Chiyuri Desktop**. Choose **Always** only if you want it as the default launcher during testing.

## Test the launcher
- Rotate between portrait and landscape; check that desktop shortcuts, Start, panels and taskbar remain usable.
- Open Start, search for apps, launch one, return Home and check that the app list refreshes.
- Pin, unpin and reorder taskbar apps; restart the launcher and check that pins remain.
- Switch among all three workspaces, rename them, add/remove shortcuts and confirm each workspace keeps its own shortcuts.
- Test Alt+Tab with Usage Access enabled and disabled. Verify the Settings panel accurately reports permission state.
- Open File Explorer and Settings together in landscape. Test minimize, restore, close, Android Back, Escape and width adjustment.
- Change wallpaper, fit mode, accent, app tile density and taskbar indicators; restart and check persistence.
- Test SAF access to internal storage and SD card, read-only folders, search, sorting, create/rename/copy/move/delete, and return-to-root navigation.
- Test ZIP, RAR and 7z previews and extraction with safe sample archives, including encrypted ZIP/RAR/7z and multipart RAR sets where available.
- Test hardware keyboard shortcuts and the system settings links.

## Return to Samsung One UI Home
Use Chiyuri Desktop's **Choose default launcher** shortcut, or open Android Settings → Apps → Choose default apps → Home app → **One UI Home**.

## Before the first stable release
The debug APK uses Android's debug signing certificate. It cannot be updated in place by the first stable APK signed with the project's release keystore. Uninstall the debug build before installing the first stable release. Later stable releases must update in place using the same release key.

## Report test results
Record the workflow run link, Android version, the feature being tested, exact steps, expected result and actual result. For crashes, include a screenshot and Android crash details if available. Do not share signing keys, passwords or keystore files.