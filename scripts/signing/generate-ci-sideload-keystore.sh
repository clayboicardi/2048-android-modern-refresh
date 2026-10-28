#!/usr/bin/env bash
# Generates the intentionally public CI sideload key (D-131). Run once; commit the outputs.
set -euo pipefail
cd "$(dirname "$0")/../.."
test ! -e signing/ci-sideload.p12 || { echo "signing/ci-sideload.p12 already exists"; exit 1; }
mkdir -p signing
keytool -genkeypair -v -keystore signing/ci-sideload.p12 -storetype PKCS12 \
  -storepass game2048-ci-sideload -keypass game2048-ci-sideload -alias ci-sideload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=2048 CI sideload key (public\; not for store uploads), O=clayboicardi"
keytool -exportcert -keystore signing/ci-sideload.p12 -storetype PKCS12 \
  -storepass game2048-ci-sideload -alias ci-sideload | sha256sum | cut -d' ' -f1 \
  > signing/ci-sideload.sha256
cat signing/ci-sideload.sha256
