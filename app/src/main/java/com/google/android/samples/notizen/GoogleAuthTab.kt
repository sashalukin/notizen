package com.google.android.samples.notizen

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import java.lang.ref.WeakReference

/** No application OTC or session transfer: complete the existing Auth.js flow in its original WebView. */
class GoogleAuthTab(
    private val activity: ComponentActivity,
    private val browserPackage: () -> String? = { findSupportedBrowser(activity) }
) {
    private var pending: Pending? = null
    private data class Pending(val view: WeakReference<WebView>, val state: String?)

    private val launcher = AuthTabIntent.registerActivityResultLauncher(activity) { result ->
        val attempt = pending
        pending = null // consume once, including cancellation and invalid results
        if (attempt == null) {
            message("Sign-in was interrupted. Please start again.")
            return@registerActivityResultLauncher
        }
        val view = attempt.view.get()
        if (view == null || !view.isAttachedToWindow || activity.isFinishing || activity.isDestroyed) {
            message("The sign-in page was closed. Please start again.")
            return@registerActivityResultLauncher
        }
        val uri = result.resultUri
        if (result.resultCode == AuthTabIntent.RESULT_OK && uri != null &&
            GoogleAuthContract.validCallback(uri, attempt.state)) {
            // The original WebView sends Auth.js's existing PKCE/nonce/state cookies.
            // Auth.js redeems the Google code and sets its session cookie directly here.
            view.loadUrl(uri.toString())
        } else {
            val reason = when (result.resultCode) {
                AuthTabIntent.RESULT_CANCELED -> "Sign-in cancelled."
                AuthTabIntent.RESULT_VERIFICATION_FAILED -> "Auth Tab domain verification failed. Check the app signing certificate in assetlinks.json."
                AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT -> "Auth Tab domain verification timed out. Please start again."
                else -> "Sign-in did not complete. Please start again."
            }
            message(reason)
            view.loadUrl("${GoogleAuthContract.ORIGIN}/signin")
        }
    }

    fun open(view: WebView, uri: Uri): Boolean {
        if (pending != null) {
            message("A Google sign-in is already in progress.")
            return true
        }
        if (!GoogleAuthContract.trustedPage(view.url) || !GoogleAuthContract.validAuthorization(uri)) {
            message("This Google sign-in request is not supported by the experiment.")
            return true // never fall back to the legacy three-endpoint flow
        }
        val browser = browserPackage()
        if (browser == null) {
            message("This experiment needs an Auth Tab-capable browser, such as Chrome 137 or newer.")
            view.loadUrl("${GoogleAuthContract.ORIGIN}/signin")
            return true
        }
        pending = Pending(WeakReference(view), uri.getQueryParameter("state"))
        try {
            CookieManager.getInstance().flush()
            AuthTabIntent.Builder().build().also { it.intent.setPackage(browser) }
                .launch(launcher, uri, GoogleAuthContract.HOST, GoogleAuthContract.CALLBACK_PATH)
        } catch (_: Exception) {
            pending = null
            message("Could not open Auth Tab. Please start again.")
            view.loadUrl("${GoogleAuthContract.ORIGIN}/signin")
        }
        return true
    }

    companion object {
        private fun findSupportedBrowser(activity: ComponentActivity): String? {
            val packages = activity.packageManager.queryIntentServices(
                Intent("android.support.customtabs.action.CustomTabsService"), 0
            ).map { it.serviceInfo.packageName }.distinct()
            val preferred = CustomTabsClient.getPackageName(activity, packages)
            return (listOfNotNull(preferred) + packages).distinct().firstOrNull {
                CustomTabsClient.isAuthTabSupported(activity, it)
            }
        }
    }

    fun clear() { pending = null }
    private fun message(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
}

/** Pure URI contract, also exercised in unit tests. Never log authorization or callback URLs. */
object GoogleAuthContract {
    const val ORIGIN = "https://notizen.dev"
    const val HOST = "notizen.dev"
    const val CALLBACK_PATH = "/api/auth/callback/google"

    private fun httpsHost(uri: Uri, host: String): Boolean = uri.scheme == "https" &&
        uri.host == host && uri.userInfo == null && uri.port in listOf(-1, 443) && uri.fragment == null

    fun trustedPage(value: String?): Boolean = value != null &&
        runCatching { httpsHost(Uri.parse(value), HOST) }.getOrDefault(false)

    fun isGoogleAuthorization(uri: Uri): Boolean = httpsHost(uri, "accounts.google.com") &&
        uri.path in setOf("/o/oauth2/v2/auth", "/o/oauth2/auth")

    fun validAuthorization(uri: Uri): Boolean = isGoogleAuthorization(uri) &&
        uri.getQueryParameters("redirect_uri") == listOf("$ORIGIN$CALLBACK_PATH") &&
        uri.getQueryParameters("response_type") == listOf("code") &&
        uri.getQueryParameters("code_challenge_method") == listOf("S256") &&
        uri.getQueryParameters("code_challenge").singleOrNull()?.matches(Regex("[A-Za-z0-9_-]{43}")) == true &&
        uri.getQueryParameters("client_id").singleOrNull()?.isNotBlank() == true &&
        uri.getQueryParameters("state").size <= 1

    fun validCallback(uri: Uri, expectedState: String?): Boolean = httpsHost(uri, HOST) &&
        uri.encodedPath == CALLBACK_PATH && uri.getQueryParameters("error").isEmpty() &&
        uri.getQueryParameters("code").singleOrNull()?.isNotBlank() == true &&
        (if (expectedState == null) uri.getQueryParameters("state").isEmpty()
         else uri.getQueryParameters("state") == listOf(expectedState))
}
