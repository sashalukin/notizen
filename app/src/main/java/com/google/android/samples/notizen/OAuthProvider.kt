package com.google.android.samples.notizen

import android.net.Uri

enum class OAuthProvider(val id: String, val displayName: String, val host: String, val paths: Set<String>) {
    GOOGLE("google", "Google", "accounts.google.com", setOf("/o/oauth2/v2/auth", "/o/oauth2/auth")),
    CLEVER("clever", "Clever", "clever.com", setOf("/oauth/authorize")),
    DISCORD("discord", "Discord", "discord.com", setOf("/oauth2/authorize", "/api/oauth2/authorize"));

    val callbackPath: String get() = "/api/auth/callback/$id"

    companion object {
        fun secureHost(uri: Uri, host: String): Boolean = uri.scheme == "https" &&
            uri.host == host && uri.userInfo == null && uri.port in listOf(-1, 443) && uri.fragment == null

        fun fromAuthorization(uri: Uri): OAuthProvider? = entries.firstOrNull {
            secureHost(uri, it.host) && uri.encodedPath in it.paths
        }

        fun fromSignIn(uri: Uri): OAuthProvider? = entries.firstOrNull {
            secureHost(uri, "notizen.dev") && uri.encodedPath == "/api/auth/signin/${it.id}"
        }
    }
}
