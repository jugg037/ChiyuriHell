# ChiyuriHell device verification plan

Run these checks on Android 10 or newer with a test WireGuard endpoint whose operator has explicitly authorized use. Do not use a production/private profile for screenshots or issue reports.

## Encrypted profile storage

1. Install a fresh debug APK and import a structurally valid dual-stack ALL-mode profile.
2. Confirm the app reports that the profile was validated and saved.
3. Force-stop the app from Android Settings, then launch it again.
4. Confirm the saved profile is restored and the Connect button is available.
5. Confirm the app does not automatically reconnect after relaunch.
6. Disconnect the tunnel, use **Удалить сохранённый профиль**, force-stop and relaunch.
7. Confirm the app asks for a profile again and the old profile is not restored.
8. Import a replacement profile and confirm it replaces the previous saved profile.

The profile file is app-private and contains AES-256-GCM ciphertext. The key is held by Android Keystore. Never copy the profile file, private key, or raw .conf contents into bug reports.

## Android Always-on recovery (experimental; physical-device verification required)

1. Import and save a valid dual-stack ALL-mode test profile.
2. Connect once manually and verify normal connect/disconnect behavior.
3. Open Android Settings → Network & internet → VPN → ChiyuriHell.
4. Enable **Always-on VPN**. Enable **Block connections without VPN** only on a test device and only after confirming that the profile connects successfully.
5. Reboot the device. After Android has started, verify that the VPN service starts and the app's tunnel is restored without opening the Activity.
6. With the tunnel active, force-stop the app only if Android permits it; separately test an OS process kill and observe whether Always-on restarts the service.
7. Disable Wi-Fi / switch between Wi-Fi and mobile data. Record whether the tunnel reconnects and whether traffic remains blocked during interruption.
8. Test a deliberately unreachable authorized test endpoint. With lockdown enabled, confirm that non-VPN traffic remains blocked; disable lockdown from Android Settings if recovery fails.
9. Open the app and confirm its status reflects the actual backend state or displays the recovery error.

**Important:** The app now advertises Always-on support and implements WireGuard GoBackend's system-start callback, but this lifecycle has not yet been verified on a physical device. This is not yet a certified Kill Switch. Android's **Block connections without VPN** is a separate system lockdown option; Always-on alone does not prevent fallback to the underlying network. Do not enable lockdown on your everyday device until the tests above pass.

## VPN lifecycle

1. Grant Android's VPN permission and connect to the authorized test endpoint.
2. Confirm the UI reports only **VPN-туннель активен** after the WireGuard backend reports UP.
3. Disconnect normally and confirm the state returns to disconnected.
4. Switch between Wi-Fi and mobile data while connected; record whether the tunnel reconnects.
5. Revoke VPN permission and verify the app handles the failure without claiming protection.
6. Stop the VPN process/service and observe whether Android falls back to the underlying network.

## Security limitations and required evidence

- Always-on callback and profile restore are implemented, but no physical-device lifecycle test has been completed.
- A visible UP state is not proof of a successful WireGuard handshake or leak-free routing.
- DNS, IPv4, IPv6, network-switch, server-loss, process-death, and reboot tests are still required.
- Record Android version, device model, network type, app build SHA-256, and the exact test step. Never include private keys, endpoint credentials, or browsing history.
