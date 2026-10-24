# SPDX-License-Identifier: AGPL-3.0-or-later
# One entry point for the monorepo. Mobile only: Kotlin Multiplatform for Android and iOS.
.PHONY: help sync check icons android release test ios ipaI I  linux

help:  ## List targets
	@grep -E '^[a-z-]+:.*##' $(MAKEFILE_LIST) | sed -E 's/:.*## /\t/'

sync:  ## Regenerate tokens, logo, pins and certificate copies from brand/ and certs/
	python3 tools/sync.py

check:  ## What CI runs: generated files in step with their sources
	python3 tools/sync.py --check

icons:  ## Render the app icons (Android adaptive + monochrome and iOS 1024) from the official mark
	python3 tools/icons.py

android:  ## Debug APK -> mobile/androidApp/build/outputs/apk/debug
	cd mobile && ./gradlew :androidApp:assembleDebug

release:  ## Release APK. Fails unless pinning is configured (verifyPinning).
	cd mobile && ./gradlew :androidApp:assembleRelease

test:  ## Shared Kotlin unit tests on the JVM
	cd mobile && ./gradlew :shared:testDebugUnitTest

ios:  ## macOS only: generate the Xcode project and open it
	cd mobile/iosApp && xcodegen && open iosApp.xcodeproj

ipa:  ## macOS only: unsigned IPA for AltStore -> dist/CodebergMobile.ipa
	sh tools/build-ipa.sh
linux: ## Build for Linux Mobile platforms, e.g Ubuntu Touch, Nura etc etc.
	cd mobile && ./gradlew :linuxMobileApp:createDistributable
	
