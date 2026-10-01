# Android cookie-handoff example

Pair `example/webview-oauth` with the same branch in `sashalukin/Notizen-website`.
The first commit removes the old Android handoff. The second reimplements it;
ordinary website authentication is a prerequisite, not part of this rebuild.

## Files and flow

- `MainActivity.kt` installs `NotizenAuth` before page load, opens Custom Tabs,
  handles cold/warm return intents, and routes completion to the initiating tab.
  Stable tab IDs/URLs are saved across Activity recreation; old WebViews are not
  retained. Popup sign-in returns to its owning main tab.
- `OAuthHandoff.kt` holds one attempt in a ViewModel, generates S256 PKCE,
  validates the exact callback, performs native HTTPS exchange without redirects,
  checks the complete session-cookie set, removes stale session chunks, waits for
  every cookie callback, flushes, and signals navigation. No custom cookie timer
  or redemption retry queue is used. Network requests have ordinary finite timeouts.
- `AndroidManifest.xml` declares the `notizen://auth` callback. Custom schemes do
  not use `autoVerify`; the backend targets this package explicitly and PKCE
  protects redemption.

The web app chooses the native bridge for its sign-in button. This avoids relying
on `shouldOverrideUrlLoading()` to intercept NextAuth's POST-based sign-in.
The bridge uses WebViewCompat's exact HTTPS origin allowlist plus a main-frame
check; it exposes only a sign-in action, never session credentials.
It requires a WebView supporting `WEB_MESSAGE_LISTENER`.

Activity recreation preserves the pending verifier through the ViewModel. Process
death intentionally does not: a callback without its verifier asks for fresh
sign-in. Returning from the browser alone is not treated as cancellation; the
user can cancel in the app. Cookie writes finish before another attempt starts.

## Checks and limits

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
`OAuthHandoffTest` covers the RFC PKCE vector, callback rejection, cookie preflight,
asynchronous cookie sequencing, cancellation, and missing verifier. Existing
notification tests remain enabled.

Build, unit tests, and lint pass. **No physical device or emulator was connected;
real Google sign-in, the permission-free Custom Tab handoff, actual CookieManager
persistence, and tap/rotation behavior still require device verification.**

This is a private reference branch, not a deployed release. The example uses the
new backend/frontend `otc` contract; installing its APK against the unchanged
live website does not validate the implementation. Do not deploy one side alone.
