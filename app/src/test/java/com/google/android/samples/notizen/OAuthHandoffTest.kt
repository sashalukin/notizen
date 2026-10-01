package com.google.android.samples.notizen

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OAuthHandoffTest {
    private val otc = "a".repeat(43)
    private fun intent(url: String = "notizen://auth?otc=$otc") = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    private fun cookie(name: String = OAuthContract.COOKIE) = "$name=example; Path=/; Secure; HttpOnly; SameSite=Lax"

    @Test fun pkceMatchesRfcVectorAndRandomValuesHaveCorrectEncoding() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", OAuthContract.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val values = (1..100).map { OAuthContract.verifier() }
        assertEquals(100, values.distinct().size)
        assertTrue(values.all { Regex("[A-Za-z0-9_-]{43}").matches(it) })
    }
    @Test fun callbackValidationRejectsConfusedTargetsAndDuplicateParameters() {
        assertEquals(otc, OAuthContract.callbackCode(intent()))
        for (url in listOf("https://auth?otc=$otc", "notizen://auth.evil?otc=$otc", "notizen://auth/other?otc=$otc", "notizen://user@auth?otc=$otc", "notizen://auth?otc=$otc&otc=$otc", "notizen://auth?otc=short")) {
            assertNull(OAuthContract.callbackCode(intent(url)))
        }
        assertNull(OAuthContract.callbackCode(intent().setAction(Intent.ACTION_SEND)))
    }
    @Test fun sessionPreflightRejectsMissingIncompleteAndUnsafeCookies() {
        assertEquals(1, OAuthContract.sessionCookies(listOf(cookie())).size)
        assertEquals(2, OAuthContract.sessionCookies(listOf(cookie("${OAuthContract.COOKIE}.0"), cookie("${OAuthContract.COOKIE}.1"))).size)
        for (cookies in listOf(emptyList(), listOf(cookie("unrelated")), listOf(cookie("${OAuthContract.COOKIE}.1")), listOf(cookie(), cookie()), listOf(cookie().replace("Secure; ", "")), listOf(cookie() + "; Domain=evil.example"))) {
            assertThrows(IllegalArgumentException::class.java) { OAuthContract.sessionCookies(cookies) }
        }
    }
    @Test fun cookieChainWaitsForEveryCallbackAndStopsOnRejection() {
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        val submitted = mutableListOf<String>()
        var result: Boolean? = null
        installCookies(listOf("one", "two"), { cookie, done -> submitted.add(cookie); callbacks.add(done) }) { result = it }
        assertEquals(listOf("one"), submitted); assertNull(result)
        callbacks[0](true)
        assertEquals(listOf("one", "two"), submitted); assertNull(result)
        callbacks[1](true); assertEquals(true, result)
        installCookies(listOf("one", "two"), { _, done -> done(false) }) { result = it }
        assertEquals(false, result)
    }
    @Test fun onePendingAttemptCancellationAndLostVerifier() {
        val state = OAuthState()
        assertNotNull(state.begin("tab-1"))
        val original = state.pending
        assertNull(state.begin("tab-2")); assertEquals(original, state.pending)
        state.cancel(); assertNull(state.pending)
        state.receive(intent()); assertNotNull(state.error)
        assertNotNull(state.begin("tab-2")); assertNull(state.error)
    }
}
