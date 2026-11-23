# Third-party notices

## Android UI design reference

The XPD Android screen layout was independently reimplemented in Kotlin using the general interaction structure of [Loopayeh/pkg-sender](https://github.com/Loopayeh/pkg-sender), including its compact console card, package library, and primary send/status area. No upstream Android C# source was copied into this Kotlin UI. The upstream project is MIT-licensed; its copyright and license notice are retained below for the upstream payload components already used by XPD.

## PS5 package receiver

The file app/src/main/assets/pkg-receiver.elf.b64 contains the Base64-encoded droid/pkg-receiver.elf binary from [Loopayeh/pkg-sender](https://github.com/Loopayeh/pkg-sender), upstream commit 73804c41c079b1d32c60bf3fd555f90fba1af2e4 (blob 6131e39f7f2a41c18e247d7d902ab98018ac937c). It is distributed under the repository's MIT License and is decoded in memory before upload to the PS5 Payload Manager. The binary is not modified.

## PS4 DPI GoldHEN installer payload

The file `app/src/main/assets/ps4_dpi_payload.bin.b64` contains the `ps4_dpi_payload.bin` binary from [Loopayeh/pkg-sender](https://github.com/Loopayeh/pkg-sender), commit source `main` at the time of import. The binary is distributed under the MIT License. It is used unmodified; XPD's runtime patches only the callback address and port marker in memory before sending it to the console.

XPD's Kotlin PKG metadata reader and GoldHEN manifest/callback integration were implemented independently after consulting the upstream project's `PkgInfo.cs` and `Ps4Installer.cs` behavior. No upstream C# source file was copied. The upstream project is credited here and its MIT notice is retained.

MIT License

Copyright (c) 2026 Loopayeh

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

