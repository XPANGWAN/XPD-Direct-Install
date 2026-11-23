# XPD Direct Install

Android companion app concept for selecting PS4 PKG files and preparing local-network transfers to a compatible PS4 receiver.

## Current implementation

- Native Android app scaffold (Kotlin).
- Dark black-and-silver interface.
- PS4 IP and receiver-port fields.
- Android document picker for selecting PKG files.
- GitHub Actions workflow for building a debug APK.

## Not implemented yet

- PS4 receiver discovery and connection test.
- PKG transfer protocol.
- Remote installation and installation status.

These features are intentionally not advertised as working until the receiver protocol, port, and behavior are verified against a compatible PS4 setup. Selecting a PKG file does not send or install it.

## Build

The **Android APK** workflow builds with JDK 17 and Gradle 8.9. Download the artifact named `xpd-direct-install-debug-apk` from a successful workflow run.

## Requirements and limitations

A compatible receiver/service must be running on the PS4, and both devices must be reachable on the same local network. This app does not bypass PS4 signing, licensing, firmware, or installation restrictions.
