# XPD Direct Install

Native Android application for sending PlayStation 4 PKG files over a local network.

## Current implementation — 0.9.4

- Native black-and-silver interface with XPD wolf mark.
- Add individual PS4 PKG files or recursively scan a selected folder.
- Validate PS4 CNT package headers and read package metadata where available.
- Search package titles, filenames, Title IDs and Content IDs.
- Pause/resume between package submissions and retry failed or pending transfers.
- Discover PS4 receivers on the local Wi-Fi network.
- PS4: send through Remote Package Installer on port 12800 or GoldHEN payload server on 9090/9021/9020.
- GoldHEN installer integration sends the package manifest and metadata to the PS4 callback.
- Local phone HTTP server on port 9898 supports GET, HEAD and byte-range requests.
- Foreground data-sync service keeps the local file server available while the console downloads.
- Selected file URIs and console settings are persisted when Android grants access.

## Transfer workflow

1. Keep the phone and PS4 on the same reachable LAN.
2. Select PS4 PKG files or scan a folder.
3. Enable Remote Package Installer or GoldHEN on the PS4.
4. Enter the PS4 IP address and test the receiver.
5. Start the queue and keep the phone file server active while the PS4 downloads.
6. A successful receiver response means the package request was accepted; it does not guarantee installation completion.

## PS4 payload status

The current GoldHEN installer payload is retained temporarily so the existing PS4 installation path is not broken while an independently implemented replacement is developed and tested. It will only be removed after the replacement passes a real-console test.

## Build

The Android Actions workflow builds a debug APK on every push to main. Download the `xpd-direct-install-debug-apk` artifact from a successful run.

A signed release APK is built only when these GitHub Actions secrets are configured:

- XPD_KEYSTORE_BASE64
- XPD_KEYSTORE_PASSWORD
- XPD_KEY_ALIAS
- XPD_KEY_PASSWORD

Keep the keystore private and backed up. Upgradeable releases must keep application ID `com.xpd.directinstall` and the same signing key, with a higher version code.

## Third-party notices

The temporary PS4 GoldHEN installer payload is from [Loopayeh/pkg-sender](https://github.com/Loopayeh/pkg-sender), under its MIT license. See `THIRD_PARTY_NOTICES.md`.

XPD's Kotlin UI, package metadata reader, local HTTP server and Android integration are maintained in this repository.

## Requirements and limitations

- A compatible PS4 receiver must already be available on the console.
- The phone and PS4 must be reachable on the same local network.
- This project does not bypass console signing, licensing, firmware or installation restrictions.
