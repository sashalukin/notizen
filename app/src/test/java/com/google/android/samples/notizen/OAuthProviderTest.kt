package com.google.android.samples.notizen

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OAuthProviderTest {
    @Test fun matchesOnlyExactProviderOriginsAndPaths() {
        assertEquals(OAuthProvider.CLEVER, OAuthProvider.fromAuthorization(Uri.parse("https://clever.com/oauth/authorize?client_id=test")))
        assertEquals(OAuthProvider.GOOGLE, OAuthProvider.fromAuthorization(Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")))
        for (value in listOf("https://clever.com.evil.test/oauth/authorize", "https://clever.com@evil.test/oauth/authorize", "http://clever.com/oauth/authorize", "https://clever.com/oauth/authorize/extra")) {
            assertNull(OAuthProvider.fromAuthorization(Uri.parse(value)))
        }
    }
    @Test fun onlyKnownFirstPartySignInRoutesSelectProvider() {
        assertEquals(OAuthProvider.CLEVER, OAuthProvider.fromSignIn(Uri.parse("https://notizen.dev/api/auth/signin/clever")))
        assertNull(OAuthProvider.fromSignIn(Uri.parse("https://evil.test/api/auth/signin/clever")))
        assertNull(OAuthProvider.fromSignIn(Uri.parse("https://notizen.dev/api/auth/signin/unknown")))
    }
}
