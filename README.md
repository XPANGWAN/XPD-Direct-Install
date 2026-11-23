# XPD Direct Install

Native Android application for sending PlayStation 4 PKG files over a local network.

## Current implementation — 0.9.5

- Native black-and-silver interface with XPD wolf mark.
- Add individual PS4 PKG files or recursively scan a selected folder.
- Validate PS4 CNT package headers and read package metadata where available.
- Search package titles, filenames, Title IDs and Content IDs.
- Pause/resume between package submissions and retry failed or pending transfers.
- Discover PS4 Remote Package Installer on the local Wi-Fi network.
- Send PS4 PKG files through Remote Package Installer on port 12800.
- Local phone HTTP server on port 9898 supports GET, HEAD and byte-range requests.
- Foreground data-sync service keeps the local file server available while the console downloads.
- Selected file URIs and console settings are persisted when Android grants access.

## Transfer workflow

1. Keep the phone and PS4 on the same reachable LAN.
2. Enable Remote Package Installer on the PS4.
3. Select PS4 PKG files or scan a folder.
4. Enter the PS4 IP address and test the receiver.
5. Start the queue and keep the phone file server active while the PS4 downloads.
6. A successful receiver response means the package request was accepted; it does not guarantee installation completion.

## Port separation

- `12800` is the Remote Package Installer HTTP interface. XPD sends the package URL and metadata to its `/api/install` endpoint.
- `9090` is GoldHEN's BinLoader socket. It accepts a compatible binary payload; it is not an HTTP package-install endpoint and must not receive a raw `.pkg` file.
- Detecting an open BinLoader socket does not prove that a compatible XPD installer payload is loaded or that package installation is available.
- XPD currently does not ship an original PS4 installer payload, so the supported package-transfer path remains a separately running RPI receiver on port `12800`.

## Architecture

XPD uses the PS4 Remote Package Installer HTTP interface directly. It no longer bundles or injects a third-party PS4 or PS5 payload. A compatible RPI receiver must already be running on the PS4.

## Build

The Android Actions workflow builds a debug APK on every push to main. Download the `xpd-direct-install-debug-apk` artifact from a successful run.

A signed release APK is built only when these GitHub Actions secrets are configured:

- XPD_KEYSTORE_BASE64
- XPD_KEYSTORE_PASSWORD
- XPD_KEY_ALIAS
- XPD_KEY_PASSWORD

Keep the keystore private and backed up. Upgradeable releases must keep application ID `com.xpd.directinstall` and the same signing key, with a higher version code.

## Requirements and limitations

- A compatible PS4 Remote Package Installer receiver must already be available on the console.
- The phone and PS4 must be reachable on the same local network.
- This project does not bypass console signing, licensing, firmware or installation restrictions.
