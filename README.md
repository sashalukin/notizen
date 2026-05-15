# Notizen: Android WebView-based App

Notizen demonstrates how to seamlessly wrap a web application into a high-performance native Android application. It serves as a robust baseline template for web developers and engineers who want to test and showcase their web apps natively.

These are some of the core APIs and patterns demonstrated:

- **[Android WebView API](https://developer.android.com/reference/android/webkit/WebView)**: Advanced custom web clients supporting multi-window/popup handling, custom file download interception, and offline connection state tracking.
- **[Jetpack Compose](https://developer.android.com/jetpack/androidx/releases/compose)**: Fully declarative UI layers and dynamic tab state rendering.

## App Overview

The primary interaction centers around the `WebView`. Developers can configure the app as a clean, immersive single-page viewer or toggle a multi-tabbed experience.

Here are the main features included out-of-the-box:

- **Single-Line Entry Point**: Configured via a highly descriptive entry point directly inside `MainActivity.kt`.
- **Multi-Window / Popup Support**: Full interception of `window.open()` and `target="_blank"` navigation requests natively rendered inside Android dialog overlays.
- **Native Download Hooking**: Integration with the native Android `DownloadManager` to support file downloads.

## Quickstart Configuration

To adapt this template for your own web application, open `MainActivity.kt` and customize the entry point located inside `onCreate()`:

```kotlin
// Pass your website URL and choose whether to enable tabbed navigation.
showWebsite(url = "https://yourwebsite.com", enableTabs = true)
```

## License

```
Copyright 2026 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on effective License.
See the License for the specific language governing permissions and
limitations under the License.
```
