package com.simplecityapps.provider.subsonic

import kotlin.random.Random

/** What a Subsonic request is signed with. */
sealed interface SubsonicCredentials {
    /** A username and password, sent as a salted token (`t = md5(password + salt)`), or as the password itself where the server refuses tokens. */
    data class Token(
        val username: String,
        val password: String
    ) : SubsonicCredentials

    /** OpenSubsonic's `apiKeyAuthentication`: a key the user made on the server, sent instead of a username. */
    data class ApiKey(val key: String) : SubsonicCredentials
}

/**
 * [credentials] as a request's query parameters. A token is salted afresh for every request; [sendPassword] sends the
 * password hex-encoded (`p=enc:...`) instead, for a server that answered error 41 (token auth not supported, e.g. LDAP).
 */
data class SubsonicAuth(
    val credentials: SubsonicCredentials,
    val sendPassword: Boolean = false
) {
    fun parameters(): List<Pair<String, String>> = when (credentials) {
        is SubsonicCredentials.Token ->
            if (sendPassword) {
                listOf("u" to credentials.username, "p" to "enc:" + credentials.password.encodeToByteArray().toHex())
            } else {
                val salt = Random.nextBytes(SALT_BYTES).toHex()
                listOf("u" to credentials.username, "t" to md5Hex(credentials.password + salt), "s" to salt)
            }

        is SubsonicCredentials.ApiKey -> listOf("apiKey" to credentials.key)
    }

    private companion object {
        const val SALT_BYTES = 8
    }
}

/** The names of the query parameters that carry credentials, so a URL can be told apart from them. */
val SUBSONIC_CREDENTIAL_PARAMETERS = setOf("u", "t", "s", "p", "apiKey")
