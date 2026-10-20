# Notizen: Android WebView-based App

Notizen demonstrates how to seamlessly wrap a web application into a high-performance native Android application. It serves as a robust baseline template for web developers and engineers who want to test and showcase their web apps natively.

These are some of the core APIs and patterns demonstrated:

- **[Android WebView API](https://developer.android.com/reference/android/webkit/WebView)**: Advanced custom web clients supporting multi-window/popup handling, custom file download interception, and offline connection state tracking.
- **[Jetpack Compose](https://developer.android.com/jetpack/androidx/releases/compose)**: Fully declarative UI layers and dynamic tab state rendering.

## Custom Tab sign-in

The `fix/restore-custom-tabs` branch restores browser-completed OAuth for Google and Discord.
The app opens `CustomTabsIntent`, receives a PKCE-bound one-time Notizen code through
`notizen://auth`, exchanges it at `/api/exchange`, waits for every cookie write and opens notes.
There is no Auth Tab API or browser-version/Digital Asset Links requirement for login.

Website counterpart: `sashalukin/Notizen-website`, branch `fix/restore-custom-tabs`.
See [Custom Tab flow](docs/custom-tab-login.md).

The former experiment remains on `experiment/auth-tab-no-handoff` (Android commit `550aa72`).
