package com.google.android.samples.notizen

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.browser.auth.AuthTabIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CleverAuthTabTest {
    private fun authorization(state: String? = "test-state"): Uri = Uri.parse("https://clever.com/oauth/authorize").buildUpon()
        .appendQueryParameter("client_id", "test-client")
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("redirect_uri", "https://notizen.dev/api/auth/callback/clever")
        .apply { if (state != null) appendQueryParameter("state", state) }.build()

    @Test fun cleverRequiresStateButDoesNotRequireGooglePkce() {
        assertTrue(AuthTabContract.validAuthorization(authorization()))
        assertFalse(AuthTabContract.validAuthorization(authorization(null)))
        assertFalse(AuthTabContract.validAuthorization(authorization("")))
        assertFalse(AuthTabContract.validAuthorization(authorization().buildUpon().appendQueryParameter("state", "duplicate").build()))
        assertFalse(AuthTabContract.validAuthorization(authorization().buildUpon().authority("clever.com.evil.test").build()))
        assertFalse(AuthTabContract.validAuthorization(authorization().buildUpon().appendQueryParameter("redirect_uri", "https://evil.test").build()))
    }

    @Test fun callbackIsBoundToBothProviderAndState() {
        val good = Uri.parse("https://notizen.dev/api/auth/callback/clever?code=test-code&state=test-state")
        assertTrue(AuthTabContract.validCallback(good, "test-state", OAuthProvider.CLEVER))
        assertFalse(AuthTabContract.validCallback(good, "different-state", OAuthProvider.CLEVER))
        assertFalse(AuthTabContract.validCallback(good, "test-state", OAuthProvider.GOOGLE))
        assertFalse(AuthTabContract.validCallback(Uri.parse("https://notizen.dev/api/auth/callback/clever?code=test-code"), null, OAuthProvider.CLEVER))
    }

    @Test fun resultReturnsToOriginalWebViewUsingCleverCallback() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.get()
        val auth = ProviderAuthTab(activity) { "com.android.chrome" }
        controller.setup().visible()
        val view = WebView(activity)
        activity.setContentView(view)
        view.loadUrl("https://notizen.dev/signin")
        auth.open(view, authorization())
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals("/api/auth/callback/clever", started.intent.getStringExtra(AuthTabIntent.EXTRA_HTTPS_REDIRECT_PATH))
        val callback = Uri.parse("https://notizen.dev/api/auth/callback/clever?code=test-code&state=test-state")
        activity.activityResultRegistry.dispatchResult(started.requestCode, AuthTabIntent.RESULT_OK, Intent().setData(callback))
        assertEquals(callback.toString(), view.url)
        auth.clear()
        controller.pause().stop().destroy()
    }
}
