# Notizen: Android WebView Template Repository

Notizen demonstrates how to seamlessly wrap a responsive website or web application into a high-performance native Android application. It serves as a robust baseline template for web developers and engineers who want to test and showcase their web apps natively with zero boilerplate configuration.

Leveraging a modern architecture, Notizen achieves complex native web integration while remaining fully customizable. These are some of the core APIs and patterns demonstrated:

- **[Jetpack Compose](https://developer.android.com/jetpack/androidx/releases/compose)**: Fully declarative UI layers and dynamic tab state rendering.
- **[Android WebView API](https://developer.android.com/reference/android/webkit/WebView)**: Advanced custom web clients supporting multi-window/popup handling, custom file download interception, and offline connection state tracking.
- **[Chrome Custom Tabs](https://developer.android.com/reference/androidx/browser/customtabs/CustomTabsIntent)**: Secure out-of-process authentication and deep-link callbacks without breaking session context.

> 💡 **Exemplar Design:** This sample is structured as a template repository. All advanced logic (such as deep-link OAuth interception) is pre-built but left cleanly commented out so developers can test their own URLs out-of-the-box immediately.

## App Overview

The primary interaction centers around the core `WebView` viewports. Developers can configure the app as a clean, immersive single-page viewer or toggle a robust multi-tabbed browsing experience.

Here are the main features included out-of-the-box:

- **Single-Line Entry Point**: Configured via a highly descriptive entry point directly inside `MainActivity.kt`.
- **Multi-Window / Popup Support**: Full interception of `window.open()` and `target="_blank"` navigation requests natively rendered inside Android dialog overlays.
- **Native Download Hooking**: Integration with the native Android `DownloadManager` to seamlessly pass cookies and User-Agent headers for protected downloads.
- **Resilient Connectivity Tracking**: Automatic detection of dropped connections with fully styled native retry interfaces.

## Quickstart Configuration

To adapt this template for your own web application, open `MainActivity.kt` and customize the entry point located inside `onCreate()`:

```kotlin
// Pass your website URL and choose whether to enable tabbed navigation.
showWebsite(url = "https://yourwebsite.com", enableTabs = true)
```

## Implementing Secure OAuth

Notizen showcases a full end-to-end Proof Key for Code Exchange (PKCE) OAuth workflow with deep-link validation. To prevent breaking default setups, this implementation is divided into **exactly 7 sequential commented sections**. 

To enable native Google Sign-In or custom OAuth bridging, follow the numbered checklist across the codebase:

1. **Section (1/7)**: Uncomment the cold-start deep-link handler in `onCreate()`.
2. **Section (2/7)**: Uncomment the runtime deep-link handler inside `onNewIntent()`.
3. **Section (3/7)**: Uncomment the private `handleDeepLink(...)` token exchange engine.
4. **Section (4/7)**: Uncomment the `companion object` holding runtime PKCE challenge states.
5. **Section (5/7)**: Uncomment the authentication URL interceptor inside `NotizenWebViewClient`.
6. **Section (6/7)**: Uncomment the native PKCE generation utility object.
7. **Section (7/7)**: Uncomment the custom intent filter within `AndroidManifest.xml`.

Requires a compatible token exchange endpoint on your backend server (demonstrated live on `https://notizen.dev`).

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
