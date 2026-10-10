#!/bin/sh
# SPDX-License-Identifier: AGPL-3.0-or-later
# Builds an UNSIGNED CodebergMobile.ipa (AltStore re-signs it when installing). Needs macOS, Xcode, xcodegen, JDK 17.
# UNVERIFIED: written without a Mac. Run it, then fix whatever Xcode reports.
set -eu
[ "$(uname)" = "Darwin" ] || { echo "iOS builds need macOS (Kotlin/Native for Apple targets and Xcode)." >&2; exit 1; }
command -v xcodegen >/dev/null || { echo "Install xcodegen: brew install xcodegen" >&2; exit 1; }

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/build/ipa"
rm -rf "$out"
mkdir -p "$out/Payload" "$root/dist"

(cd "$root/iosApp" && xcodegen)
xcodebuild archive \
  -project "$root/iosApp/iosApp.xcodeproj" -scheme iosApp -configuration Release \
  -sdk iphoneos -destination "generic/platform=iOS" \
  -archivePath "$out/iosApp.xcarchive" \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""

cp -R "$out/iosApp.xcarchive/Products/Applications/CodebergMobile.app" "$out/Payload/"
(cd "$out" && zip -qry "$root/dist/CodebergMobile.ipa" Payload)

echo "Built dist/CodebergMobile.ipa"
echo "sha256: $(shasum -a 256 "$root/dist/CodebergMobile.ipa" | cut -d' ' -f1)"
echo "size:   $(stat -f%z "$root/dist/CodebergMobile.ipa") bytes   (put both in dist/altstore-source.json)"
