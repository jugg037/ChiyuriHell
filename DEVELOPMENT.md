# Chiyuri Desktop — development notes

Device target: Samsung Galaxy Tab S6 Lite SM-P619 (ARM64).

Design:
- Windows 10-inspired layout.
- Chiyuri black and blood-crimson palette.
- Extended multitasking is a roadmap goal; do not claim full floating-window support until implemented and tested.
- Android APK applications only. No EXE/MSI compatibility layer.

Build:
- JDK 17
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Compile SDK 35
- Min SDK 26

First milestone: installable launcher prototype with Start menu, taskbar, app discovery/launching, clock/date and battery indicator.
