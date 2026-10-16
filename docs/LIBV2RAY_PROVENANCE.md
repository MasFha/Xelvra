# libv2ray AAR Source Provenance

## Distributed binary

- File: `app/libs/libv2ray.aar`
- SHA-256: `bb71290a2239e8e3f47f5bb31ccf0db0ceb90960ce8ac14015560dbf562d74bb`
- Architectures: `arm64-v8a`, `armeabi-v7a`
- Embedded Go toolchain: `go1.27.1`

## Identified upstream components

### AndroidLibXrayLite

- Repository: https://github.com/2dust/AndroidLibXrayLite
- License: LGPL-3.0
- Candidate source commit: `d0c6c4ae1b09c912070c8288bd0dbcc2e492ac29`

The candidate commit matches the Xray-core and
golang.org/x/mobile versions embedded in the AAR.

This is not proof of byte-for-byte reproducibility.

### Xray-core

- Repository: https://github.com/XTLS/Xray-core
- License: MPL-2.0
- Version: `v1.260327.1-0.20260908222543-52a412d9e2f5`

### golang.org/x/mobile

- Version: `v0.0.0-20260908204917-8b95e45f8d3e`

## Upstream build procedure

Upstream requires Go, gomobile, JDK, Android SDK
and Android NDK.

Its workflow also generates and copies assets.

Build command documented upstream:

    gomobile bind -v -androidapi 24 -trimpath -ldflags='-s -w -buildid= -checklinkname=0' ./

Source reference:

https://github.com/2dust/AndroidLibXrayLite/tree/d0c6c4ae1b09c912070c8288bd0dbcc2e492ac29

## Verification limits

Not yet established:

1. Exact original AAR build environment.
2. Byte-for-byte binary reproducibility.
3. All transitive dependencies and generated assets.
4. Full LGPL-3.0 and MPL-2.0 distribution compliance,
   including applicable source availability, notices,
   and library modification or replacement requirements.

This document does not assert full license compliance.

See `LICENSE` and `THIRD_PARTY_NOTICES.md`.

## Access to candidate upstream source

Upstream source:
https://github.com/2dust/AndroidLibXrayLite

Candidate revision:
d0c6c4ae1b09c912070c8288bd0dbcc2e492ac29

Retrieval commands:

    git clone https://github.com/2dust/AndroidLibXrayLite.git
    cd AndroidLibXrayLite
    git checkout --detach d0c6c4ae1b09c912070c8288bd0dbcc2e492ac29

This candidate matches identified dependency versions.
It is not verified as the exact source of the bundled AAR.

This reference alone does not establish compliance with
LGPL-3.0, MPL-2.0, or other applicable licenses.
