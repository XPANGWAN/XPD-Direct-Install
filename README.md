# XPD Direct Install

Native Android PKG sender for a local PlayStation network. XPD is being rebuilt toward the workflow of PKG Sender while keeping Android-specific storage, network and lifecycle handling.

## Current app status — 0.9.0

- Native black-and-silver Android interface with separated console, package queue and transfer sections.
- Reads PS4 CNT and PS5 FIH package signatures; it no longer rejects PS5 FIH files at selection.
- Extracts available PS4 `param.sfo` or PS5 `param.json` metadata: title, Title ID, Content ID, category and version.
- Extracts a package icon when it is present as a PNG entry.
- Shows platform, title, version, Title ID, size and served progress in individual queue cards.
- Uses Android's document picker without restrictive MIME filters.
- Automatic local Wi-Fi discovery for RPI/PKG Receiver and GoldHEN endpoints (12800, 9090, 9021, 9020).\n- PS4 RPI sends one package request at a time to port 12800.\n- GoldHEN path includes the upstream MIT-licensed DPI payload, callback listener, PS4 package metadata struct and BGFT manifest endpoint on phone port 9898.\n- Phone file server on port 9898 supports GET, HEAD and byte ranges.

## Rebuild roadmap and current limits
- PS4 RPI: basic install-request path exists; test against a real console is still required.
- PS4 GoldHEN: payload injection and callback/manifest flow are implemented; requires testing with a real PS4/GoldHEN version and network setup.
- PS5: package metadata parsing exists; the PS5 receiver payload and install workflow are not yet integrated.
- Family grouping (base/update/DLC), queue pause/reorder/retry and a true foreground background-transfer service remain in development.
- A parsed PKG header does not guarantee that every encrypted, split, malformed or future package variant is fully supported.

- Native Kotlin Android application with a black-and-silver interface.
- Manual PS4 IPv4 address and receiver port.
- RPI probe on port 12800 using its documented GET /api response.
- GoldHEN status probe on port 9090 using GET /status.
- Multiple .pkg document selection through Android's system picker.
- Local HTTP file server on the phone (port 8080), with GET, HEAD and byte-range support.
- RPI install request uses POST /api/install on port 12800 only.
- Incremental byte accounting across range requests.
- Remembers the console address, port and selected document URIs when Android grants persistent access.

## Important protocol limitations

RPI on port 12800 and GoldHEN Payload Server on port 9090 are different protocols.

This build can detect a GoldHEN status endpoint, but **does not yet implement GoldHEN payload injection or its callback/manifest transfer flow**. Selecting port 9090 will not send an RPI JSON request to it. GoldHEN installation is therefore not advertised as supported yet.

RPI must be installed and running on the PS4. The app's HTTP 2xx response is not proof that the package finished installing; confirm the result on the console.

## Build

The **Android APK** GitHub Actions workflow builds a debug APK on every push to `main`. Download `xpd-direct-install-debug-apk` from a successful run.

A signed release APK is built only when these GitHub Actions repository secrets are configured:

- `XPD_KEYSTORE_BASE64`
- `XPD_KEYSTORE_PASSWORD`
- `XPD_KEY_ALIAS`
- `XPD_KEY_PASSWORD`

Keep the keystore private and back it up securely. Every upgradeable release must use the same application ID (`com.xpd.directinstall`) and the same signing key, with a higher version code. Debug APKs are not guaranteed to update over a release APK or a debug APK signed with a different key.

## Requirements and limitations

- Android 6.0 or newer.
- Phone and PS4 must be reachable on the same local network.
- Keep the app open while the console downloads from the phone's HTTP server.
- A compatible, already-running RPI service is required for installation.
- This project does not bypass PS4 signing, licensing, firmware, or installation restrictions.

## License and credits

XPD Direct Install's original Android code is maintained in this repository. No third-party receiver payload is bundled in this build. If upstream code or binaries are incorporated later, their original license notices and contributor credits must be retained and modifications identified.
