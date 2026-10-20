# Codeberg Mobile

A Kotlin Multiplatform client for [Codeberg](https://codeberg.org), built with Compose Multiplatform for Android and iOS.

Browse repositories, read source code and Markdown files, explore issues and pull requests, and use a native interface designed around Codeberg's visual identity. Codeberg Mobile aims to feel like a first-class part of the Codeberg ecosystem rather than a generic third-party wrapper: shared Kotlin code keeps the two platforms feature-aligned, while Compose Multiplatform provides a fully native rendering layer on each device.

## Security warning: install only trusted releases

> [!CAUTION]
> **Only install Codeberg Mobile from official project channels. Do not install builds from unofficial websites, third-party app stores, random download links, mirrors, or any other untrusted source.**

Codeberg Mobile runs with access to your Codeberg account. It stores authentication tokens, communicates with the Codeberg API on your behalf, and can read any repository content your account is authorized to see — including private repositories, issues, and pull request data. This makes the application a high-value target for tampered or malicious builds.

A modified build of this app could, among other things:

- steal your access token and use it to read or modify your repositories;
- exfiltrate private repository contents, issue discussions, or personal data;
- bypass certificate pinning or weaken network security settings, redirecting your traffic to an unauthorized server;
- alter commits, reviews, or release artifacts under your identity;
- silently persist access to your account even after you uninstall the app.

**Official project source:** [Codeberg Mobile on Codeberg](https://codeberg.org/)

**Official release downloads:** [Codeberg Mobile on GitHub Releases](https://github.com/)

> [!NOTE]
> These links currently point to the hosting platforms, not a verified project-specific repository or release page. Replace them with the exact project URLs before publishing this README.

Practical guidance before installing:

1. Download only release artifacts published by the project maintainers on the official channels listed above.
2. Prefer F-Droid, Google Play, or the App Store listings when they are maintained by the project itself; treat any other store listing as unofficial unless the project states otherwise.
3. Where checksums or signatures are provided with a release, verify them before installation. Note that a checksum downloaded from the same untrusted source as the binary is not independent proof of authenticity — verify it against a second official channel.
4. Never accept sideloaded builds (APKs, IPAs) shared through chat messages, forums, file-sharing sites, or issue threads.
5. If you find an unofficial distribution of this app, please report it to the project maintainers.

If you believe you have installed a tampered build, revoke all Codeberg access tokens associated with the app, change your password, and review your account's recent activity.

## Features

### Repository browsing

- Explore Codeberg repositories and browse their directory trees.
- Read source files and inspect individual file contents.
- View repository Markdown documents and rendered documentation.
- Browse issues and pull requests, with filtering support.
- Navigate repository content with a native, platform-appropriate interface.

### Markdown rendering

- Render Markdown with support for images, tables, and code blocks.
- Handle Markdown links with URL validation before opening them.
- Load remote images while avoiding unnecessary tracking resources where implemented.
- Provide a readable native document-viewing experience tuned for mobile screens.

### Native Android and iOS experience

- Built with Kotlin Multiplatform and Compose Multiplatform from a shared codebase where supported by the project architecture.
- Light and dark themes based on Codeberg's visual style and design tokens.
- Platform-appropriate interactions, gestures, and device integration on both platforms.
- Consistent behavior across devices, with UI that respects each platform's conventions.

### Local repository and file viewing

- Inspect local repository content through the `codeberg://localhost` scheme.
- Browse file blobs and read Markdown documents stored locally.
- View locally available content without treating it as a remote Codeberg repository.

### Credential protection

Codeberg Mobile treats authentication credentials as sensitive data and stores them using each platform's hardware-backed secure storage:

- **Android:** credentials are encrypted with AES-GCM using a key held in the Android Keystore.
- **iOS:** credentials are stored in the Keychain, configured for device-only accessibility and protection while the device is locked.
- **Biometric authentication:** fingerprint and Face ID unlock are supported where the device provides them.
- **Device authentication:** falls back to device credential authentication (PIN, pattern, or password) where available.

Security properties depend on the platform, the device's configuration, and the specific operations implemented by the application. Hardware-backed storage protects credentials at rest, but it does not by itself guarantee that every network request or in-app operation is secure — which is one more reason to only run builds you obtained from trusted sources.

## Platform support

| Platform | Status |
|---|---|
| Android | Kotlin Multiplatform / Compose Multiplatform |
| iOS | Kotlin Multiplatform / Compose Multiplatform |

**Minimum Android SDK:** API 26 (Android 8.0).

The current Android build configuration targets API 36 by default. Actual feature availability depends on the platform and device; some features may be limited on older devices or where the required hardware (such as a biometric sensor) is unavailable.

## Building from source

Building from source is the most transparent way to verify what the application does.
```make``` or ```gmake``` will show you the options you need to build the app.
Release signing credentials must be configured locally or through a secure build environment. Never commit private signing keys or passwords to the repository, and never accept a build from someone who asks you to use their signing credentials.

## Security and responsible disclosure

The security of this application matters to everyone who uses it. If you believe you have found a vulnerability:

- Report suspected vulnerabilities privately to the project maintainers **before** publishing details that could put users at risk.
- Allow the maintainers reasonable time to investigate and release a fix before public disclosure.
- Do not share access tokens, passwords, private repository contents, or signing credentials in public issues, even when demonstrating a vulnerability — redact or revoke any credentials used in a report.

## Contributing

Codeberg Mobile is licensed under the **GNU Affero General Public License v3.0 or later (AGPL-3.0-or-later)**; see the repository's license file for the full terms.

This project aims to keep its release process transparent and its code maintainable:

- Contributions should preserve readable source code.
- Do not introduce unnecessary obfuscation that makes community review and maintenance harder.
- Contributions and modifications must comply with the applicable AGPL-3.0-or-later license terms, including providing source code to users of distributed builds.
