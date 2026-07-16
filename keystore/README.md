# TingXia signing keys

## `tingxia-debug.jks` (committed)

Fixed **debug/CI** keystore so every CI and local debug APK shares the same signature.
This allows cover-install of debug builds without uninstalling.

- storePassword / keyPassword: `tingxia-debug`
- alias: `tingxia-debug`
- applicationId for this key: `com.tingxia.app.debug` only

**Do not** use this key for Play Store / public release.

## Release keystore (not in git)

Provide via GitHub Secrets / local env:

- `TINGXIA_RELEASE_STORE_FILE`
- `TINGXIA_RELEASE_STORE_PASSWORD`
- `TINGXIA_RELEASE_KEY_ALIAS`
- `TINGXIA_RELEASE_KEY_PASSWORD`

If those are absent, `release` builds are left **unsigned** (no debug-key fallback).
