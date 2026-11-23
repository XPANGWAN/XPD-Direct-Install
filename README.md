# XPD Direct Install

Native Android application for sending PlayStation 4 PKG files over a local network.

## Current implementation — 0.9.6

- Native black-and-silver interface with XPD wolf mark.
- Add individual PS4 PKG files or recursively scan a selected folder.
- Validate PS4 CNT package headers and read package metadata where available.
- Search package titles, filenames, Title IDs and Content IDs.
- Pause/resume between package submissions and retry failed or pending transfers.
- Discover GoldHEN BinLoader on port 9090 first, with Remote Package Installer fallback.
- Send PS4 package metadata to the bundled GPL-3.0 DirectPackageInstaller installer payload through GoldHEN BinLoader on port 9090.
- Keep the phone HTTP file server on port 9898 available while the PS4 BGFT service downloads each PKG.
- Retain the Remote Package Installer HTTP path on port 12800 as a fallback.
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

- `9090` is GoldHEN BinLoader. XPD sends the bundled PS4 installer payload to this TCP port, then the payload connects back to the phone's temporary metadata service. XPD supplies each package URL, title, content ID, type, size, and optional icon. The PS4 BGFT service registers the download and fetches the PKG from the phone's HTTP server on port `9898`.
- `12800` is the Remote Package Installer HTTP interface. XPD retains it as a fallback when GoldHEN BinLoader is unavailable.
- The PKG file itself is never sent raw to port `9090`.
- The bundled installer payload is from DirectPackageInstaller under GPL-3.0. Its source provenance and license are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), and the license text is included in [licenses/GPL-3.0.txt](licenses/GPL-3.0.txt).

## Architecture

The Android-side XPD bridge serves selected PS4 PKG files over local HTTP with byte-range support. The GoldHEN payload receives the package metadata over a separate local TCP service and registers each download with the PS4 BGFT service. The Android app also retains its independent RPI `12800` transfer route.
 
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
