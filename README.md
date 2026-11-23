# XPD Direct Install

Native Android application for sending PlayStation PKG files over a local network. The project follows the practical workflow of [Loopayeh/pkg-sender](https://github.com/Loopayeh/pkg-sender), with an Android-native interface, storage access and transfer lifecycle.

## Current implementation — 0.9.2

- Native black-and-silver interface with XPD wolf mark.
- Add individual PKG files or recursively scan a selected folder; .pkg extension matching is case-insensitive and the container signature is validated.
- Read PS4 CNT and PS5 FIH package metadata where available, including title, Title ID, Content ID, category and version; extract embedded PNG icons.
- Search by title, filename, Title ID or Content ID; filter BASE/UPDATE/DLC; group related entries by Title ID count; remove and reorder queue entries.
- Pause/resume between package submissions and retry the failed/pending portion of the queue.
- Discover compatible receivers on the local Wi-Fi network.
- PS4: send through Remote Package Installer on port 12800 or GoldHEN payload server on 9090/9021/9020.
- PS5: upload the bundled pkg-receiver.elf through Payload Manager (default web port 8084), launch it, then send PS5 PKG files to the receiver on port 12800.
- Local phone HTTP server on port 9898 supports GET, HEAD and byte-range requests.
- Foreground data-sync service and bounded CPU wake lock keep the local file server alive when the Activity is backgrounded; Stop File Server shuts it down.
- Selected file URIs and console settings are persisted when Android grants access.

## Transfer workflow

1. Keep the phone and console on the same reachable LAN.
2. Select PKG files or scan a folder.
3. For PS4, enable RPI or GoldHEN on the console. For PS5, start Payload Manager and use INSTALL PS5 RECEIVER first.
4. Enter the console IP, select the matching receiver port, and start the queue.
5. A successful receiver response means the package request was accepted; it is not a guarantee that the console completed installation.

## Build

The Android Actions workflow builds a debug APK on every push to main. Download the xpd-direct-install-debug-apk artifact from a successful run.

A signed release APK is built only when these GitHub Actions secrets are configured:

- XPD_KEYSTORE_BASE64
- XPD_KEYSTORE_PASSWORD
- XPD_KEY_ALIAS
- XPD_KEY_PASSWORD

Keep the keystore private and backed up. Upgradeable releases must keep application ID com.xpd.directinstall and the same signing key, with a higher version code.

## Third-party notices

- The PS4 GoldHEN installer payload and PS5 pkg-receiver.elf are from [Loopayeh/pkg-sender](https://github.com/Loopayeh/pkg-sender), under its MIT license. The PS5 receiver asset is bundled as Base64 text and decoded at runtime. See THIRD_PARTY_NOTICES.md.
- XPD's Kotlin UI, package metadata reader, local HTTP server and Android integration are maintained in this repository.

## Requirements and limitations

- A compatible receiver must already be available on the console; the PS5 receiver can be deployed by the app through Payload Manager.
- The phone and console must be reachable on the same local network. GoldHEN also needs to connect back to the phone's callback listener.
- This project does not bypass console signing, licensing, firmware or installation restrictions.
