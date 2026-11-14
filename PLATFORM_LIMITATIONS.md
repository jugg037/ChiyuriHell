# Platform limitations and explicit non-goals

These boundaries are deliberate. Chiyuri Desktop must remain a normal, non-root Android launcher and must not pretend to have operating-system privileges that Android does not grant.

## Android task switching and external windows
- Ordinary third-party launchers cannot enumerate, reorder, close or move the complete Android system task stack. Chiyuri provides its own recent-app history, UsageStats-based recent package list, Alt+Tab shortcut and taskbar launching instead.
- Launching another app adjacent or into split screen is only a request to Android. The system and Samsung firmware decide whether that activity can enter multi-window mode.
- The built-in Chiyuri panels support focus, adjustable width, minimize, restore and close. They are launcher-owned panels, not arbitrary resizable windows wrapping other apps.

## Archive format boundaries
- Supported core formats: ZIP, RAR and 7z, including password-protected ZIP AES/traditional encryption, password-protected RAR/7z, RAR4 .r00-style volumes and RAR5 .partN.rar sets.
- RAR 1.4 multi-volume extraction is outside the current Junrar implementation.
- ZIP strong encryption, split ZIP volumes and codecs not supported by the bundled 7z libraries are not promised.
- Resource limits are intentional: 512 MiB source archive/volume set, 256 MiB expanded output, 1,000 entries, bounded folder depth and bounded codec memory.

## Root and privileged system controls
- Root is not required for any core launcher feature.
- Root-only system modifications are not included. The app can detect/report root availability, but does not alter protected system settings or firmware.
- Wi-Fi, Bluetooth, display, sound, battery and location shortcuts open Android settings; Android remains responsible for applying those changes.

## Device validation
- Static repository checks do not replace testing on the target Samsung Galaxy Tab S6 Lite SM-P619.
- Portrait/landscape behavior, Samsung split-screen behavior, keyboard shortcuts, SAF providers, archive samples and stable APK updates must be checked using the final debug build before creating the stable release.