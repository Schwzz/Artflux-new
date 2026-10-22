# Artflux 🎨

**Artflux** is a modern, high-performance Android application built with Jetpack Compose and Material Design 3 for discovering, streaming, filtering, and curating anime illustrations, animated GIFs, and videos across multiple imageboards and REST APIs.

---

## ✨ Features

* **Multi-Source Discovery**:
  * **Safebooru**: Curated all-ages anime illustration repository with tag search and animated GIF discovery.
  * **Danbooru**: Premier anime imageboard with comprehensive tagging, video clips (`webm`/`mp4`), and 4-tier content ratings.
  * **Yande.re**: High-resolution anime artwork and scan archive with server-side rating filters.
  * **Custom API Engine**: Easily connect any booru-compatible or REST JSON API endpoint with custom query parameters and live testing.
* **Source-Aware Media & Rating Filters**:
  * **Media Types**: All Types, Images, GIFs, and Videos with server-side tag composition (e.g. `animated`, `webm`) and client-side fallback.
  * **Content Ratings**: Normalized into **All Ratings**, **Safe**, **Suggestive**, and **Adult** across diverse source standards (`g`/`s`/`q`/`e`).
* **Visual Experience**:
  * Dark obsidian aesthetic with neon indigo, cyan, and magenta accents.
  * Staggered/masonry grid with smooth infinite scrolling and pull-to-refresh.
  * Interactive Lightbox viewer with high-resolution zooming, video playback via Media3/ExoPlayer, metadata inspector, and image downloads.
* **Custom Source Engine**:
  * Manual source configuration, JSON import/export, diagnostics, and templates for additional sources.

---

## 🛠️ Tech Stack & Architecture

* **UI**: 100% [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 (M3).
* **Architecture**: MVVM with unidirectional data flow (Kotlin Coroutines, Flow, StateFlow).
* **Image & Media Loading**: [Coil](https://coil-kt.github.io/coil/) (with animated GIF support) and [Media3 ExoPlayer](https://developer.android.com/guide/topics/media/media3).
* **Network**: [OkHttp 4](https://square.github.io/okhttp/).
* **Testing**: [Robolectric](https://robolectric.org/) JVM unit testing suite.

---

## 🚀 Building & Running

### Prerequisites
* JDK 17 or higher (CI uses JDK 21)
* Android SDK Platform 36.1 for compilation; devices running Android 7.0 (API 24) or newer

### Build Commands

```bash
# Clone the repository
git clone https://github.com/Schwzz/Artflux-new.git
cd Artflux-new

# Copy environment template if needed
cp .env.example .env

# Run unit tests
./gradlew testDebugUnitTest

# Run Android lint
./gradlew lintDebug

# Assemble debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🧪 CI/CD

Continuous integration is automated via GitHub Actions (`.github/workflows/android.yml`), running unit tests and building debug APK artifacts on every push or pull request to `main` and `master`.


On Windows PowerShell, use `.\gradlew.bat` in place of `./gradlew`. Android Studio or Gradle creates the standard debug keystore automatically. No API key is required to build.

CI runs unit tests, Android lint, and a debug APK build. Each run uploads verification reports and, when successful, `artflux-debug-apk` from the Actions run page. Debug APKs are for testing; publishing installable updates requires a stable release signing key.

See [the repository review](docs/REPOSITORY_REVIEW.md) for the reliability changes, remaining improvements, and device verification checklist.
