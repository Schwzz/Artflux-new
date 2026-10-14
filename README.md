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
  * Manual custom source configuration and quick-apply templates (Safebooru, Danbooru, Moebooru).

---

## 🛠️ Tech Stack & Architecture

* **UI**: 100% [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 (M3).
* **Architecture**: MVVM with unidirectional data flow (Kotlin Coroutines, Flow, StateFlow).
* **Image & Media Loading**: [Coil](https://coil-kt.github.io/coil/) (with animated GIF support) and [Media3 ExoPlayer](https://developer.android.com/guide/topics/media/media3).
* **Network**: [OkHttp 4](https://square.github.io/okhttp/) & [Retrofit](https://square.github.io/retrofit/).
* **Testing**: [Robolectric](https://robolectric.org/) JVM unit testing suite.

---

## 🚀 Building & Running

### Prerequisites
* JDK 17 or higher
* Android SDK (API 36 / Android 14+ recommended)

### Build Commands

```bash
# Clone the repository
git clone https://github.com/Schwzz/Artflux.git
cd Artflux

# Copy environment template if needed
cp .env.example .env

# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🧪 CI/CD

Continuous integration is automated via GitHub Actions (`.github/workflows/android.yml`), running unit tests and building debug APK artifacts on every push or pull request to `main` and `master`.
