# Release Process

## Versioning (semantic)

- `v1.0.0` — initial / major
- `v1.1.0` — new features
- `v1.1.1` — bug fixes

## Checklist

1. Update `versionName` (+ `versionCode`) in `android/app/build.gradle.kts`.
2. Update `CHANGELOG.md` with a `## [x.y.z] — date` section (release notes are
   extracted from it automatically).
3. Merge to `main` with green CI (`.github/workflows/ci.yml`).
4. Tag and push:
   ```bash
   git tag v1.1.0
   git push origin v1.1.0
   ```
5. The `Release` workflow (`.github/workflows/release.yml`):
   - verifies the tag matches `versionName` (mismatch aborts),
   - runs all unit tests (failure aborts — no broken releases),
   - builds APK (debug + release) and AAB,
   - creates the GitHub Release with notes from `CHANGELOG.md` and attaches:
     - `MedTech-Review-Hub-vX.Y.Z-debug.apk` — always installable (debug-signed),
     - `MedTech-Review-Hub-vX.Y.Z-release.apk` (or `-release-unsigned.apk` when no
       signing secrets are configured),
     - `MedTech-Review-Hub-vX.Y.Z.aab` (Play Store bundle).
6. Verify the release page lists the APK asset before announcing it.

## APK signing (secrets — never committed)

Configure these repository secrets (`Settings → Secrets → Actions`):

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | base64 of your `.keystore`/`.jks` file |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |

Encode the keystore with:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Clipboard
```

Without these secrets, CI still publishes an installable **debug** APK and an
unsigned release APK (clearly labeled) so users are never left without a download.

Generate a keystore locally (kept out of git by `.gitignore`):
```bash
keytool -genkey -v -keystore release.keystore -alias medtech -keyalg RSA -keysize 2048 -validity 10000
```

## Updating users

Users on older versions get an in-app notification (GitHub Releases API) with the
release notes and a download link. Updates preserve all local user data (Room /
local storage are untouched by sideloading an update over the same applicationId).
If the app is later published on Google Play, Play-distributed installs use the
Play update mechanism instead.
