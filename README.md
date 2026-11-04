<div align="center">

<img src="./.github/readme-icon.svg" alt="Oto Logo" width="80" height="80">

<h1>Oto (Android)</h1>

<p align="center">
  A fast, native music streaming client and local player for Android built with Kotlin and Jetpack Compose.
</p>

[![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg?style=flat-square)](./LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3-purple.svg?style=flat-square)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-green.svg?style=flat-square)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Audio-AndroidX%20Media3-orange.svg?style=flat-square)](https://developer.android.com/media/media3)
[![Android](https://img.shields.io/badge/Android-8.0%2B-teal.svg?style=flat-square)](https://developer.android.com)

</div>

> [!NOTE]
> **Oto Android** is the mobile companion to the [Oto Desktop](https://github.com/BranProHengker/oto) music player. Designed from the ground up for a fast, responsive, and privacy-respecting listening experience with zero accounts required.

---

## Features

- **Streaming & Offline**: Stream high-quality audio from YouTube and YouTube Music backend, or play offline local audio files stored on your device.
- **Pure Native UI**: Built with Jetpack Compose — modern Material 3 design, dynamic theme palette, smooth animations, and zero webview wrapper bloat.
- **Friction-Free Onboarding**: Skip setup instantly to jump straight to the player without forced account sign-ins or mandatory artist selections.
- **Audio Engine & Transitions**: Native playback powered by AndroidX Media3 (ExoPlayer), gapless playback, and configurable crossfade transitions.
- **Synchronized Lyrics**: Real-time scrolling LRC lyrics support with syllable sync and multiple lyrics providers (LRCLIB, KuGou, Musixmatch, etc.).
- **Discord Rich Presence (RPC)**: Live status reporting to Discord showing currently playing track, album art, and artist info.
- **Privacy & Lightweight**: Zero analytics, zero user data tracking, and no ads.

---

## Installation

Prebuilt APKs are available on the [GitHub Releases](https://github.com/BranProHengker/oto-android/releases/latest) page.

1. Download the latest `.apk` from [Releases](https://github.com/BranProHengker/oto-android/releases/latest).
   * **`arm64`** *(Recommended)*: Optimized for virtually all modern 64-bit Android smartphones.
   * **`universal`**: Compatible with all architectures.
2. Install the APK on your device (allow install from unknown sources if prompted).
3. Open **Oto** and start listening immediately.

---

## Building from Source

### Prerequisites

- **JDK 17 or JDK 21**
- **Android SDK** (API Level 36, Build-Tools)
- **ADB** (for debugging/installing via USB or Wi-Fi)

### Build with Gradle

```bash
# Clone the repository
git clone https://github.com/BranProHengker/oto-android.git
cd oto-android

# Build Debug APK (arm64)
./gradlew assembleArm64FossDebug

# Build & install directly to a connected Android device:
./gradlew installArm64FossDebug

# Build Release APK
./gradlew assembleArm64FossRelease
```

Compiled APKs will be located at:
`app/build/outputs/apk/arm64Foss/{debug,release}/`

---

## Credits & Upstream

Oto Android builds upon open-source foundations:

- [Oto](https://github.com/BranProHengker/oto) — Original desktop music player (Rust & GPUI).
- [Mochy-Music](https://github.com/realidkroo/Mochy-Music) — Base Android player framework and UI foundations.
- [ViviMusic](https://github.com/vivizzz007/vivi-music) — Upstream player architecture.
- [Metrolist](https://github.com/mostafaalagamy/Metrolist) — Upstream base player foundations.
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — YouTube stream extraction.
- [LRCLIB](https://github.com/tranxuanthang/lrclib) — Synchronized lyrics API provider.

---

## License

Oto is free software licensed under the [GNU General Public License version 3 or later](LICENSE).
