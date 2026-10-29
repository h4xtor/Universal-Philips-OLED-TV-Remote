# H4XTOR Universal Philips OLED TV Remote

Native Android remote for Philips 65OLED865/12 (JointSpace API v6 / firmware API 6.4.0), optimized for Samsung Galaxy S24 Ultra.

## Project status
The Android source is being migrated from the existing PhilipsTVRemote2026 project. This repository currently contains the product specification and build workflow; **it does not yet contain a complete Android application**. Do not treat the workflow as evidence of a successful build until the Android project is committed.

## Product requirements
- Premium OLED-dark touch UI with large D-pad, power, volume, mute, channels, playback and source.
- Secure PIN pairing with Philips JointSpace v6; store credentials using Android Keystore; never commit credentials or disable TLS verification globally.
- Channel Matrix: fetch actual TV channel list, search and favorite channels, direct selection.
- App Launcher: discover actual TV apps, launch by valid firmware-supported intent; gracefully handle unsupported apps.
- Ambilight Lab: read supported styles/modes from TV and allow selecting supported modes; on/off and status.
- Smart Power: standby via API; Wake-on-LAN using user-confirmed MAC, directed/subnet broadcast when available, bounded retries and connection polling. Never claim WoL guaranteed; test on device from standby.
- Diagnostics: request logs with secrets redacted, status, API version, connectivity, endpoint capability probes.
- Offline-first LAN-only, no analytics or cloud account.
- Android build and lint in CI; release artifacts only when build passes.

## Hardware
- Philips 65OLED865/12 at 192.168.0.110 (configurable)
- Samsung Galaxy S24 Ultra

## Acceptance tests
1. Pair from phone and reconnect after app restart.
2. D-pad, Home, Back, Source, volume and mute work on real TV.
3. Channel list reflects TV and direct channel selection works.
4. Installed apps are discoverable and launch where supported.
5. Ambilight styles offered are the TV's actual supported styles.
6. Wake from standby succeeds on target TV/network or UI reports unsupported with diagnostics.
7. No hard-coded credentials, permissive TLS trust manager or leaked secrets.
8. GitHub Actions compiles APK and uploads build artifact.

## Source migration
Import the Android Studio source from the original `PhilipsTVRemote2026/android` project, then iterate without deleting working JointSpace behavior. The prior local v2.1 ZIP is a migration input, not a verified release.
