# Signing keys

## CI sideload key (public)

1. The key is `signing/ci-sideload.p12` (store type PKCS12), with the alias `ci-sideload` and the store and key password `game2048-ci-sideload`.
2. This key is intentionally public. Anyone can sign an APK with it. It exists only so that every CI-built APK installs over the previous one.
3. It is never used for Google Play, GitHub Releases after 1.0.0 or any other distribution. Releases are signed with the private release key below (D-272).
4. Do not register this key or the package name with Android developer verification.
5. Regenerate it with `./scripts/signing/generate-ci-sideload-keystore.sh`. The script refuses to overwrite an existing key. A new key changes `signing/ci-sideload.sha256` and breaks update-in-place of installed CI builds.

## Release key (private, D-272)

The owner's private key signs every GitHub Release after 1.0.0. Only its public certificate digest is committed, as `signing/release.sha256` (lowercase hex SHA-256 of the DER certificate, one line). The keystore and its password exist only on the owner's machine, in the owner's offline backup and in the GitHub Actions secrets of the `release` environment. `.github/workflows/release.yml` fails before building when either secret or `signing/release.sha256` is missing.

| Item | Value |
|---|---|
| Keystore | PKCS12, alias `game2048-release`, one password for store and key (PKCS12 has no separate key password) |
| Key | RSA 4096, validity 10,000 days |
| Secret `GAME2048_RELEASE_KEYSTORE_BASE64` | The keystore file, base64 on one line |
| Secret `GAME2048_RELEASE_STORE_PASSWORD` | Its password |
| Gradle | `GAME2048_RELEASE_KEYSTORE` (a path) and `GAME2048_RELEASE_STORE_PASSWORD` switch the release build type to `signingConfigs.release`; release.yml sets them for one `assembleRelease` after the gate. Without them every build signs with `ciSideload` |
| Gate | `scripts/check-release-apk.sh`: the published APK's only signer is `signing/release.sha256`, and its entries equal the gated APK's byte for byte |

Generate the key once, on the owner's machine, outside any git checkout. keytool asks for the password; it never appears in the shell history.

```bash
keytool -genkeypair -v -keystore game2048-release.p12 -storetype PKCS12 \
  -alias game2048-release -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=2048 release key, O=clayboicardi"
keytool -exportcert -keystore game2048-release.p12 -storetype PKCS12 -alias game2048-release |
  sha256sum | cut -d' ' -f1 > release.sha256
```

Commit `release.sha256` as `signing/release.sha256`. Never commit the keystore: `.gitignore` ignores every `*.p12`, `*.jks` and `*.keystore` except `signing/ci-sideload.p12`. Losing the key or its password means no later release can update an installed one, so keep an offline backup of both.

Create the repository environment `release` first (Settings → Environments → New environment), and under "Deployment branches and tags" choose "Selected branches and tags" with the one rule `master`, so a workflow on any other branch or pull request cannot read its secrets. Then store them there, not as repository secrets:

```bash
base64 -w0 game2048-release.p12 |
  gh secret set GAME2048_RELEASE_KEYSTORE_BASE64 --env release -R clayboicardi/2048-android-modern-refresh
gh secret set GAME2048_RELEASE_STORE_PASSWORD --env release -R clayboicardi/2048-android-modern-refresh
```

The second command prompts for the value. On macOS, use `base64 -i game2048-release.p12` and `shasum -a 256` in place of `base64 -w0` and `sha256sum`.

A release-key APK cannot update an install signed with the CI sideload key (1.0.0 and every CI build), nor the reverse: switching means uninstalling, which deletes the local data unless Android backup restores it.
