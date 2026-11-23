# Third-party notices

## DirectPackageInstaller PS4 installer payload

XPD Direct Install bundles the prebuilt PS4 GoldHEN installer payload from:

- Project: DirectPackageInstaller
- Upstream: https://github.com/marcussacana/DirectPackageInstaller
- Source revision: ca7bade66737fede3b0e7bad73b665e0a4d4ff39
- License: GNU General Public License v3.0 (GPL-3.0-only)
- Upstream payload path: `DirectPackageInstaller/DirectPackageInstaller/payload`

The payload is distributed unmodified. XPD's Android-side bridge, package metadata exchange, local HTTP serving, and UI integration are separate XPD code. The payload receives package metadata from the phone and uses the PS4 BGFT services to register package downloads.

The complete upstream source corresponding to the bundled payload is available at:
https://github.com/marcussacana/DirectPackageInstaller/tree/ca7bade66737fede3b0e7bad73b665e0a4d4ff39/Payload

The GPL-3.0 license text is included at [licenses/GPL-3.0.txt](licenses/GPL-3.0.txt). Any redistribution or modification of this bundled component must comply with GPL-3.0, including source-code availability and preservation of applicable notices.
