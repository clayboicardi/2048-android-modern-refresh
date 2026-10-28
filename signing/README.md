# CI sideload signing key

1. The key is `signing/ci-sideload.p12` (store type PKCS12), with the alias `ci-sideload` and the store and key password `game2048-ci-sideload`.
2. This key is intentionally public. Anyone can sign an APK with it. It exists only so that every CI-built APK installs over the previous one.
3. It is never used for Google Play or any other store. Real release signing is out of scope until the repository owner adds secrets.
4. Do not register this key or the package name with Android developer verification.
5. Regenerate it with `./scripts/signing/generate-ci-sideload-keystore.sh`. The script refuses to overwrite an existing key. A new key changes `signing/ci-sideload.sha256` and breaks update-in-place of installed CI builds.
