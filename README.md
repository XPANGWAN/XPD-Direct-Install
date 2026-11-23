# XPD Direct Install

Native Android application for sending PlayStation PKG files over a local network. The project is being rebuilt toward the practical workflow of [Loopayeh/pkg-sender](https://github.com/Loopayeh/pkg-sender), with Android-specific storage, network and lifecycle handling.

## Current implementation — 0.9.0

- Native black-and-silver Android interface.
- Add individual files or recursively scan a selected folder for `.pkg` files.
- Validate PS4 CNT and PS5 FIH package signatures; file extension case does not determine validity.
- Read PS4 `param.sfo` and PS5 `param.json` metadata where available: title, Title ID, Content ID, category and version.
- Extract an embedded PNG package icon when present.
- Search by title, filename, Title ID or Content ID; filter BASE/UPDATE/DLC; queue cards show covers and progress, with remove and reorder controls.
- Discover compatible local endpoints on Wi-Fi: RPI/receiver port 12800 and GoldHEN ports 9090, 9021 and 9020.
- PS4 RPI request path on port 12800.
- PS4 GoldHEN path: payload injection, callback listener, package metadata struct and BGFT manifest endpoint.
- Local phone HTTP server on port 9898 supports GET, HEAD and byte-range requests.
- Foreground data-sync service keeps the local file server alive when the Activity is backgrounded; a Stop File Server control ends it explicitly.
- Selected file URIs and console settings are persisted when Android grants access.

## Compatibility and honest status

- **PS4 + RPI:** request path is implemented; real-console testing is still required.
- **PS4 + GoldHEN:** payload/callback/manifest path is implemented; real-console testing across GoldHEN versions and network configurations is still required.
- **PS5:** PS5 package metadata is recognized, but the PS5 receiver payload and PS5 install workflow are not integrated yet. The app blocks sending PS5 PKGs through the PS4 installer path.
- Family grouping, pause/reorder/retry controls and a full library search/filter interface remain to be completed.
- A successful HTTP response or payload callback means the receiver accepted the request; it does not prove that installation completed. Confirm the result on the console.

## Build

The Android Actions workflow builds a debug APK on every push to `main`. Download the `xpd-direct-install-debug-apk` artifact from a successful run.

A signed release APK is built only when these GitHub Actions secrets are configured:

- `XPD_KEYSTORE_BASE64`
- `XPD_KEYSTORE_PASSWORD`
- `XPD_KEY_ALIAS`
- `XPD_KEY_PASSWORD`

Keep the keystore private and backed up. Upgradeable releases must keep application ID `com.xpd.directinstall` and the same signing key, with a higher version code.

## Third-party code and payload

The GoldHEN installer payload in `app/src/main/assets/ps4_dpi_payload.bin.b64` is from [Loopayeh/pkg-sender](https://github.com/Loopayeh/pkg-sender) and is distributed under its MIT license. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The Android metadata parser, queue UI, local server and integration code are XPD implementations.

## Safety and requirements

- Use only with a PlayStation console and network you own or are authorized to administer.
- A compatible PS4 RPI service or GoldHEN Payload Server must already be running.
- Keep the phone and console reachable on the same local network; the phone must accept the console's callback connection for GoldHEN.
- This project does not bypass console signing, licensing, firmware or installation restrictions.
