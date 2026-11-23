# XPD Direct Install

Native Android companion for sending PS4 PKG files over a local Wi-Fi network to a compatible Remote Package Installer (RPI).

## Current app status — 0.4.0

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
