# Chiyuri Desktop final release audit

Do not create RELEASE_READY or push a version tag until every applicable check below passes. A checkbox means the check was actually performed, not merely that code exists.

## Automated repository checks
- [ ] GitHub Actions Android lint completes successfully
- [ ] Unit tests complete successfully
- [ ] Debug APK builds and has a SHA-256 checksum
- [ ] Release APK builds using the stable signing key
- [ ] apksigner verifies the release APK
- [ ] Release versionName matches the Git tag and versionCode is incremented

## Target-device checks — Samsung Galaxy Tab S6 Lite SM-P619
- [ ] Install the debug build and select Chiyuri Desktop as the default Home app
- [ ] Return to One UI Home and switch back to Chiyuri Desktop
- [ ] Test portrait and landscape layouts, on-screen keyboard and hardware keyboard shortcuts
- [ ] Test Start menu, app search, app launch, empty search state and app-list refresh
- [ ] Test pin, unpin, reorder and persistence of taskbar applications
- [ ] Test all three workspaces, custom names and per-workspace desktop shortcuts
- [ ] Test recent-app list and Alt+Tab with and without Usage Access
- [ ] Test simultaneous built-in panels, focus, adjustable width, minimize, restore and close
- [ ] Test app drawer tile density and taskbar battery/date visibility preferences
- [ ] Test Android adjacent/split-screen launch on supported applications
- [ ] Grant Usage Access and verify foreground-app highlighting; repeat without the permission
- [ ] Test wallpaper presets, custom image, fit/crop/stretch, accent colors and preference persistence
- [ ] Test file access with read/write and read-only SAF providers
- [ ] Test file search and sorting by name/date/size
- [ ] Test progress indication and action lockout during long file/archive operations
- [ ] Test folder creation, rename, file/folder copy and move, recursive delete and failure recovery
- [ ] Test ZIP, RAR and 7Z listing and extraction, including password-protected ZIP/RAR/7Z, RAR4 .r00-style and RAR5 multipart sets, wrong passwords, nested folder paths, traversal-path rejection, duplicate names and resource limits
- [ ] Test Android system settings shortcuts for Wi-Fi, Bluetooth, display, sound, battery and location
- [ ] Test battery, charging, network, RAM, storage and Chiyuri process metrics
- [ ] Test root check without root and, if a rooted device is available, verify that it remains opt-in

## Publication checks
- [ ] Confirm release notes describe only the changes included in this release
- [ ] Confirm APK package name, versionName and versionCode
- [ ] Confirm the APK signature is stable and can update the previously installed build
- [ ] Confirm GitHub Release contains the APK and matching SHA-256 file
- [ ] Download the published APK and verify its checksum
- [ ] Confirm the published release is not marked as a prerelease

Only after all applicable checks pass should RELEASE_READY be created with the single line READY and the matching version tag be pushed.