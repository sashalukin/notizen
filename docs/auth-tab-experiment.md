# Auth Tab without application handoff endpoints

Branch: `experiment/auth-tab-no-handoff` in both repositories.

## What changes

1. The existing website calls `signIn('google')` inside WebView. Auth.js handles CSRF and creates its PKCE cookie there.
2. Android intercepts only the resulting main-frame Google authorization URL. It validates the app origin, provider host/path, existing Google callback URI, and S256 challenge.
3. An Auth Tab-capable browser shows Google sign-in. Auth Tab is configured to capture `https://notizen.dev/api/auth/callback/google`.
4. Instead of letting the browser execute that callback, Auth Tab returns its URI to Android.
5. Android validates the result, consumes the pending attempt, and loads the URI into the same originating WebView. That WebView already has the Auth.js PKCE/state/nonce cookies that were created at initiation.
6. The unchanged Auth.js callback exchanges Google's authorization code server-side, creates the normal session cookie directly in WebView, and redirects to `/notes`.

There is no application OTC, cookie copying, native token exchange, new JS bridge, or call to `/android-signin`, `/api/android-callback`, or `/api/exchange`. Google's provider authorization code and Auth.js PKCE are still required. Provider secrets remain on the existing server.

This specifically depends on Auth.js's redirect-based authorization-code flow. It is not a universal replacement for every provider or browser-SDK flow. Capturing a callback is not itself authentication; the existing Auth.js handler must finish successfully in the original cookie store.

## HTTPS verification and device test

Use Chrome 137+ or another browser reporting Auth Tab support. The app explicitly rejects unsupported browsers; no ordinary Custom Tab fallback is enabled in this experiment.

Auth Tab verifies the package and signing certificate against `https://notizen.dev/.well-known/assetlinks.json`. The association-only website commit `714a96b6e95f2d8858bbd02bcbfc3d6e0c7d1562` preserves the existing certificate and adds the local experiment certificate. It changes only that public association file, not any auth handler. A build signed with another certificate will need its own association; copying the source does not copy signing identity. No signing key is committed.

For a phone test, run this Android branch with an associated signing key and an Auth Tab-capable browser. Start from the app's signed-out page, tap Google sign-in, select an account, and verify that the tab closes and `/notes` is authenticated. Also test cancellation and a second attempt. No manual Open Notizen link is part of the flow. Do not log callback URLs, provider codes, cookies, or tokens.

One login attempt is allowed at a time. Cancellation, malformed results, verification failure, or launch failure resets the attempt. Activity/process recreation or closing the originating WebView requires restarting sign-in. Pending state is deliberately not serialized. A repeated ActivityResult is ignored. Existing Auth.js error pages handle an expired or rejected provider code.

## Automated verification

- Android source compilation, unit tests, and lint.
- URI validation tests: exact callback, state matching, duplicate parameters, scheme/host/path spoofing, and provider request validation.
- ActivityResult tests: original WebView receives the callback; repeated results are ignored; cancellation permits restarting.
- The paired website's `tests/auth-tab.test.mjs` runs the installed Auth.js Google provider through real CSRF/PKCE processing and JWT session issuance, with only the external Google service mocked. It proves the original cookie store can finish login and an empty cookie store cannot; replay is rejected.
- Website production build and regression tests, including a disposable local PostgreSQL database.

Initial implementation was tested without a connected device. The user subsequently confirmed successful real Google login in an emulator after upgrading Chrome and associating the local signing certificate. Other browsers/providers remain unverified. No APK was generated on the development server.

## Staying signed in

Auth.js uses its existing persistent session cookie with a default 30-day lifetime. Android now flushes WebView cookies after first-party page loads and when the activity stops, including refreshed sessions and logout changes. This saves the cookie already issued by the normal callback; it does not copy Auth Tab cookies, extend session lifetime, or replay the Google callback on restart.

The user reported another sign-in prompt after reopening; the missing explicit post-login flush is a suspected cause, not a device-confirmed diagnosis. The backend regression test verifies a fresh cookie jar can authenticate using only the unexpired persistent session cookie. Verify the Android change by signing in, opening notes, closing the app, then launching it from its icon. Confirm logout stays logged out too. Uninstalling, clearing app data, or wiping emulator data removes the stored session and requires login again.

## Restore the pre-experiment code

Both GitHub repositories have tag `backup/pre-auth-tab-2026-10-04`.

- Website source: `00858e0861e36057fe92bdb197eb1b205d590095`.
- Android source: `077933a88b9e588c8acc20c2b933966accf936d5`.
- Pre-experiment Cloud Run revision: `notizen-00051-yum`, initially receiving 100% traffic.
- Its immutable image: `us-central1-docker.pkg.dev/main-tokenizer-485420-h8/notizen/notizen@sha256:8b2e5963f86138fcfd2cd1894543a1302a1b0442ecb6a41c4b9a878eee02e234`.

The archived Cloud Build source matched every included tracked file from the website commit; only `.gitignore` was omitted by the build uploader. Uncommitted local deployment notes were not deployed and remain outside the public recovery tag.

To roll back the website, route the `notizen` Cloud Run service in `us-central1`, project `main-tokenizer-485420-h8`, to `notizen-00051-yum`, or rebuild the saved source commit. Android rollback requires rebuilding/installing the saved source with the same signing identity. These are source/deployment recovery points, not backups of user notes, uploaded files, or secret values.

The website experiment branch removes the old three routes to prove independence. The association-only live update intentionally retains those routes for older Android clients. Do not deploy the route-removal commit as a general rollout while such clients still depend on it.

## Clever extension

The experiment also recognizes `https://clever.com/oauth/authorize` and captures
`https://notizen.dev/api/auth/callback/clever`. The pending attempt is bound to the
selected provider and its state, so a Google callback cannot finish a Clever attempt.
Clever uses Auth.js state cookies and server-side client authentication, rather than
assuming support for Google's PKCE flow. The originating WebView retains the state
cookie; the callback finishes through the existing Auth.js route with no OTC handoff.
Google's PKCE checks remain unchanged.

Clever developer credentials and a sandbox district/test user must be configured on
the website before the Clever button appears. Real Clever login is not yet tested.
