# SPDX-License-Identifier: AGPL-3.0-or-later
# One entry point for the monorepo.  Linux: android, test, release, pins.  macOS: all of them.
.RECIPEPREFIX = >
.PHONY: android release test pins ios ipa

android:  ## Debug APK -> androidApp/build/outputs/apk/debug
> ./gradlew :androidApp:assembleDebug

release:  ## Release APK. Fails unless pinning is configured (verifyPinning).
> ./gradlew :androidApp:assembleRelease

test:     ## Shared unit tests (validators, deep links) on the JVM; works on Linux
> ./gradlew :shared:testDebugUnitTest

pins:     ## Recompute SPKI pins from codeberg.org's live chain
> sh tools/print-pins.sh

ios:      ## macOS only: generate the Xcode project and open it
> cd iosApp && xcodegen && open iosApp.xcodeproj

ipa:      ## macOS only: unsigned IPA for AltStore -> dist/CodebergMobile.ipa
> sh tools/build-ipa.sh
