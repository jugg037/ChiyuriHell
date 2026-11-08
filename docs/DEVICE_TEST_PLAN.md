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

## VPN lifecycle

1. Grant Android's VPN permission and connect to the authorized test endpoint.
2. Confirm the UI reports only **VPN-туннель активен** after the backend reports UP.
3. Disconnect normally and confirm the state returns to disconnected.
4. Switch between Wi-Fi and mobile data while connected; record whether the tunnel reconnects.
5. Revoke VPN permission and verify the app handles the failure without claiming protection.
6. Stop the VPN process/service and observe whether Android falls back to the underlying network.

## Security limitations and required evidence

- This build does not yet provide a verified system-wide kill switch.
- Do not enable Always-on VPN or **Block connections without VPN** for this app until service restart/recovery behavior is implemented and verified.
- A visible UP state is not proof of a successful WireGuard handshake or leak-free routing.
- DNS, IPv4, IPv6, network-switch, server-loss, process-death, and reboot tests are still required.
- Record Android version, device model, network type, app build SHA-256, and the exact test step. Never include private keys, endpoint credentials, or browsing history.
