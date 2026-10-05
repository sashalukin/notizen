package com.google.android.samples.notizen

import android.net.Uri
import android.content.Intent
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.browser.auth.AuthTabIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleAuthTabTest {
    private fun authorization(): Uri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
        .appendQueryParameter("client_id", "test-client")
        .appendQueryParameter("redirect_uri", "${AuthTabContract.ORIGIN}${AuthTabContract.CALLBACK_PATH}")
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("code_challenge_method", "S256")
        .appendQueryParameter("code_challenge", "a".repeat(43)).build()
    private fun callback(suffix: String = "?code=test-code") =
        Uri.parse("${AuthTabContract.ORIGIN}${AuthTabContract.CALLBACK_PATH}$suffix")

    @Test fun acceptsExistingAuthJsPkceRequest() {
        assertTrue(AuthTabContract.validAuthorization(authorization()))
        assertTrue(AuthTabContract.validCallback(callback(), null))
    }
    @Test fun bindsCallbackToStateWhenPresent() {
        assertTrue(AuthTabContract.validCallback(callback("?code=test-code&state=attempt"), "attempt"))
        assertFalse(AuthTabContract.validCallback(callback("?code=test-code&state=other"), "attempt"))
        assertFalse(AuthTabContract.validCallback(callback(), "attempt"))
        assertFalse(AuthTabContract.validCallback(callback("?code=test-code&state=unexpected"), null))
    }
    @Test fun rejectsAmbiguousAndFailedCallbacks() {
        for (query in listOf("", "?code=", "?code=a&code=b", "?error=access_denied", "?code=a&error=denied")) {
            assertFalse(AuthTabContract.validCallback(callback(query), null))
        }
        assertFalse(AuthTabContract.validCallback(callback("?code=a&state=s&state=s"), "s"))
    }
    @Test fun rejectsUntrustedCallbackDestinations() {
        for (uri in listOf(
            "http://notizen.dev/api/auth/callback/google?code=a",
            "https://notizen.dev.evil.test/api/auth/callback/google?code=a",
            "https://notizen.dev@evil.test/api/auth/callback/google?code=a",
            "https://user@notizen.dev/api/auth/callback/google?code=a",
            "https://notizen.dev:444/api/auth/callback/google?code=a",
            "https://notizen.dev/api/auth/callback/google/extra?code=a",
            "https://notizen.dev/api/auth/callback/google?code=a#fragment",
            "https://notizen.dev/api/android-callback?code=a",
            "notizen://auth?code=a"
        )) assertFalse(AuthTabContract.validCallback(Uri.parse(uri), null))
    }
    @Test fun rejectsProviderSpoofingAndRedirectReplacement() {
        assertFalse(AuthTabContract.validAuthorization(authorization().buildUpon().authority("accounts.google.com.evil.test").build()))
        assertFalse(AuthTabContract.validAuthorization(authorization().buildUpon().appendQueryParameter("redirect_uri", "https://evil.test").build()))
        assertFalse(AuthTabContract.validAuthorization(authorization().buildUpon().appendQueryParameter("code_challenge_method", "plain").build()))
    }
    @Test fun onlyLaunchesFromNotizenOrigin() {
        assertTrue(AuthTabContract.trustedPage("https://notizen.dev/signin"))
        assertFalse(AuthTabContract.trustedPage("https://notizen.dev.evil.test/signin"))
        assertFalse(AuthTabContract.trustedPage("http://notizen.dev/signin"))
        assertFalse(AuthTabContract.trustedPage(null))
    }

    @Test fun activityResultResumesOriginalWebViewAndDuplicateResultIsIgnored() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.get()
        val authTab = ProviderAuthTab(activity) { "com.android.chrome" }
        controller.setup().visible()
        val view = WebView(activity)
        activity.setContentView(view)
        view.loadUrl("https://notizen.dev/signin")
        assertTrue(authTab.open(view, authorization()))
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals("com.android.chrome", started.intent.`package`)
        assertEquals(authorization(), started.intent.data)
        assertEquals(AuthTabContract.HOST, started.intent.getStringExtra(AuthTabIntent.EXTRA_HTTPS_REDIRECT_HOST))
        assertEquals(AuthTabContract.CALLBACK_PATH, started.intent.getStringExtra(AuthTabIntent.EXTRA_HTTPS_REDIRECT_PATH))
        activity.activityResultRegistry.dispatchResult(started.requestCode, AuthTabIntent.RESULT_OK, Intent().setData(callback()))
        assertEquals(callback().toString(), view.url)
        view.loadUrl("https://notizen.dev/notes")
        activity.activityResultRegistry.dispatchResult(started.requestCode, AuthTabIntent.RESULT_OK, Intent().setData(callback()))
        assertEquals("https://notizen.dev/notes", view.url)
        authTab.clear()
        controller.pause().stop().destroy()
    }

    @Test fun cancellationDoesNotRedeemProviderCodeAndAllowsFreshAttempt() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.get()
        val authTab = ProviderAuthTab(activity) { "com.android.chrome" }
        controller.setup().visible()
        val view = WebView(activity)
        activity.setContentView(view)
        view.loadUrl("https://notizen.dev/signin")
        authTab.open(view, authorization())
        val started = shadowOf(activity).nextStartedActivityForResult
        activity.activityResultRegistry.dispatchResult(started.requestCode, AuthTabIntent.RESULT_CANCELED, null)
        assertEquals("https://notizen.dev/signin", view.url)
        authTab.open(view, authorization())
        assertNotNull(shadowOf(activity).nextStartedActivityForResult)
        authTab.clear()
        controller.pause().stop().destroy()
    }
}
