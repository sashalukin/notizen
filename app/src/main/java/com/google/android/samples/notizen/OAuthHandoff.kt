package com.google.android.samples.notizen

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

object OAuthContract {
    const val ORIGIN = "https://notizen.dev"
    const val COOKIE = "__Secure-authjs.session-token"
    fun verifier(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun callbackCode(intent: Intent): String? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (uri.scheme != "notizen" || uri.host != "auth" || !uri.path.isNullOrEmpty() ||
            uri.port != -1 || uri.userInfo != null || uri.fragment != null) return null
        val values = uri.getQueryParameters("otc")
        return values.singleOrNull()?.takeIf { Regex("[A-Za-z0-9_-]{43}").matches(it) }
    }

    // Preflight before touching any existing cookies. Accept only our session and full chunk sets.
    fun sessionCookies(headers: List<String>): List<String> {
        require(headers.isNotEmpty()) { "Missing session cookies" }
        val names = headers.map { header ->
            require(header.length <= 4096 && !header.contains('\r') && !header.contains('\n'))
            val parts = header.split(';').map { it.trim() }
            val pair = parts.first().split('=', limit = 2)
            require(pair.size == 2 && pair[1].isNotEmpty())
            require(pair[0] == COOKIE || Regex("${Regex.escape(COOKIE)}\\.[0-9]+").matches(pair[0]))
            require(parts.any { it.equals("Secure", true) } && parts.any { it.equals("HttpOnly", true) })
            require(parts.any { it.equals("Path=/", true) } && parts.none { it.startsWith("Domain=", true) })
            pair[0]
        }
        require(names.distinct().size == names.size)
        if (COOKIE in names) require(names.size == 1)
        else require(names.toSet() == names.indices.map { "$COOKIE.$it" }.toSet())
        return headers
    }

    fun isSessionName(name: String) = name == COOKIE || Regex("${Regex.escape(COOKIE)}\\.[0-9]+").matches(name)

    fun exchange(otc: String, verifier: String): List<String> {
        val connection = URL("$ORIGIN/api/exchange").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("otc", otc).put("code_verifier", verifier).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == 200) { "Sign-in expired or failed" }
            val cookies = connection.headerFields.filterKeys { it?.equals("Set-Cookie", true) == true }
                .values.flatten()
            return sessionCookies(cookies)
        } finally { connection.disconnect() }
    }
}

// Retains data and in-flight work through rotation; never retains Activity/WebView references.
class OAuthState : ViewModel() {
    data class Attempt(val id: String, val verifier: String, val tabId: String)
    var pending by mutableStateOf<Attempt?>(null)
        private set
    var phase by mutableStateOf("waiting")
        private set
    var error by mutableStateOf<String?>(null)
    var readyTabId by mutableStateOf<String?>(null)
        private set

    fun begin(tabId: String): Uri? {
        if (pending != null || readyTabId != null) return null
        val attempt = Attempt(UUID.randomUUID().toString(), OAuthContract.verifier(), tabId)
        pending = attempt
        error = null
        phase = "waiting"
        return Uri.parse("${OAuthContract.ORIGIN}/android-signin").buildUpon()
            .appendQueryParameter("code_challenge", OAuthContract.challenge(attempt.verifier)).build()
    }

    fun cancel() {
        // CookieManager writes cannot be cancelled: finish these before accepting another attempt.
        if (phase != "installing") pending = null
    }

    fun launchFailed() { pending = null; error = "Could not open the browser. Please try signing in again." }

    fun receive(intent: Intent) {
        val otc = OAuthContract.callbackCode(intent) ?: return
        val attempt = pending
        if (attempt == null) { error = "Sign-in was interrupted. Please start again."; return }
        if (phase != "waiting") return
        phase = "exchanging"
        viewModelScope.launch {
            try {
                val headers = withContext(Dispatchers.IO) { OAuthContract.exchange(otc, attempt.verifier) }
                if (pending?.id != attempt.id) return@launch
                phase = "installing"
                val manager = CookieManager.getInstance()
                manager.setAcceptCookie(true)
                // Remove stale chunks from a previous account before installing the complete new set.
                val old = manager.getCookie(OAuthContract.ORIGIN).orEmpty().split(';')
                    .map { it.trim().substringBefore('=') }.filter(OAuthContract::isSessionName)
                    .map { "$it=; Max-Age=0; Path=/; Secure; HttpOnly" }
                installCookies(old + headers, { cookie, done ->
                    manager.setCookie(OAuthContract.ORIGIN, cookie, done)
                }) { success ->
                    if (pending?.id == attempt.id) {
                        if (success) {
                            try {
                                manager.flush()
                                readyTabId = attempt.tabId
                            } catch (_: Exception) { error = "Could not persist the session. Please sign in again." }
                        } else error = "Could not store the session. Please sign in again."
                        pending = null
                        phase = "waiting"
                    }
                }
            } catch (_: Exception) {
                if (pending?.id == attempt.id) {
                    pending = null
                    phase = "waiting"
                    error = "Sign-in failed or expired. Please start again."
                }
            }
        }
    }

    fun consumed() { readyTabId = null }
}

// Sequential callback chain; no sleep, cookie timer, or retry queue.
internal fun installCookies(
    cookies: List<String>,
    write: (String, (Boolean) -> Unit) -> Unit,
    complete: (Boolean) -> Unit
) {
    if (cookies.isEmpty()) { complete(false); return }
    fun next(index: Int) {
        if (index == cookies.size) { complete(true); return }
        try {
            write(cookies[index]) { accepted -> if (accepted) next(index + 1) else complete(false) }
        } catch (_: Exception) { complete(false) }
    }
    next(0)
}

fun installOAuthBridge(view: WebView, start: () -> Unit) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
    WebViewCompat.addWebMessageListener(view, "NotizenAuth", setOf(OAuthContract.ORIGIN)) {
            _, message, origin, mainFrame, _ ->
        if (mainFrame && origin.toString() == OAuthContract.ORIGIN && message.data.orEmpty().length <= 256) {
            try { if (JSONObject(message.data.orEmpty()).optString("type") == "SIGN_IN") start() }
            catch (_: Exception) { /* Reject malformed bridge input. */ }
        }
    }
}
