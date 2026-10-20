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
    @Test fun classLinkUsesExactOriginAndFirstPartyRoute() {
        assertEquals(OAuthProvider.CLASSLINK, OAuthProvider.fromAuthorization(Uri.parse("https://launchpad.classlink.com/oauth2/v2/auth?client_id=test")))
        assertEquals(OAuthProvider.CLASSLINK, OAuthProvider.fromSignIn(Uri.parse("https://notizen.dev/api/auth/signin/classlink")))
        for (url in listOf("https://launchpad.classlink.com.evil.test/oauth2/v2/auth", "http://launchpad.classlink.com/oauth2/v2/auth", "https://launchpad.classlink.com/oauth2/v2/auth/extra", "https://launchpad.classlink.com:444/oauth2/v2/auth")) {
            assertNull(OAuthProvider.fromAuthorization(Uri.parse(url)))
        }
    }
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
