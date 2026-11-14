# Release signing setup

The release workflow intentionally refuses to publish until the final audit creates RELEASE_READY containing exactly READY.

A release must use the same private keystore for every version. If the signing key is lost or replaced, Android will refuse to install updates over the existing app.

## GitHub Actions secrets

In the repository, open Settings → Secrets and variables → Actions → New repository secret and add:

- ANDROID_KEYSTORE_BASE64 — the keystore file encoded as one Base64 string
- ANDROID_KEYSTORE_PASSWORD — keystore password
- ANDROID_KEY_ALIAS — alias of the signing key
- ANDROID_KEY_PASSWORD — key password

Do not commit the keystore or passwords to the repository. Keep an offline backup of the keystore and its credentials.

## Creating a key

If no existing stable Android signing key is available, create one once with keytool on a trusted device:

    keytool -genkeypair -v -keystore chiyuri-desktop-release.jks -alias chiyuri-desktop -keyalg RSA -keysize 4096 -validity 10000

Encode it for the ANDROID_KEYSTORE_BASE64 secret:

    base64 chiyuri-desktop-release.jks | tr -d '\n'

The release pipeline decodes that secret into the runner's temporary directory, signs the release APK, and publishes the APK plus SHA-256 checksum only after the release-readiness gate passes.

Do not create a new key for each version. If a key from another app is considered, first confirm that you own it and can retain it permanently; never assume a key is available just because another project used signed APKs.

## Android-only setup with Termux

The key can be created from the Android tablet; a desktop computer is not required.

1. Install Termux from a trusted official distribution and open it.
2. Install the Java key tools and Base64 utility:

       pkg update
       pkg install openjdk-17 coreutils

3. Create the keystore once:

       keytool -genkeypair -v -keystore "$HOME/chiyuri-desktop-release.jks" -alias chiyuri-desktop -keyalg RSA -keysize 4096 -validity 10000

4. Verify that the alias exists:

       keytool -list -v -keystore "$HOME/chiyuri-desktop-release.jks"

5. Create the single-line value for the GitHub secret:

       base64 "$HOME/chiyuri-desktop-release.jks" | tr -d '\n'

6. Copy the complete output into the GitHub Actions secret named ANDROID_KEYSTORE_BASE64. Add the alias and passwords as their separate secrets.

Keep the original .jks file in a secure offline backup. Do not paste the keystore, Base64 value or passwords into ChatGPT, issues, commits or release notes. The key is permanent: losing it prevents future APK updates from installing over this app.
