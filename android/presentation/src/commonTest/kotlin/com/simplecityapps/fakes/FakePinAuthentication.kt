package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.server.AccountServer
import com.simplecityapps.mediaprovider.server.PinAuthentication
import com.simplecityapps.mediaprovider.server.ServerConnection
import com.simplecityapps.mediaprovider.server.SignInPin

/**
 * A PIN sign-in whose PIN is approved once [approveAfterChecks] checks have seen it waiting (never, if null), and whose
 * account has [servers]. [signedInTo] names the server signed in now, by id.
 */
class FakePinAuthentication : PinAuthentication {
    var pin = SignInPin(id = 1, code = "ABCD", authUrl = "https://app.plex.tv/auth#?code=ABCD", linkUrl = "https://plex.tv/link", expiresInSeconds = 900)
    var createFailure: Throwable? = null
    var checkFailure: Throwable? = null
    var serversFailure: Throwable? = null
    var connectFailure: Throwable? = null
    var approveAfterChecks: Int? = 0
    var servers = listOf(server("home", "Home"))
    var signedInTo: String? = null
    var checkCount = 0
    val serversRequestedWith = mutableListOf<String>()
    val connected = mutableListOf<AccountServer>()

    override suspend fun createPin(): Result<SignInPin> = createFailure?.let { Result.failure(it) } ?: Result.success(pin)

    override suspend fun checkPin(pin: SignInPin): Result<String?> {
        checkFailure?.let { return Result.failure(it) }
        val approved = approveAfterChecks?.let { checkCount >= it } ?: false
        checkCount++
        return Result.success(if (approved) ACCOUNT_TOKEN else null)
    }

    override suspend fun servers(accountToken: String): Result<List<AccountServer>> {
        serversRequestedWith += accountToken
        return serversFailure?.let { Result.failure(it) } ?: Result.success(servers)
    }

    override fun isSignedInTo(server: AccountServer): Boolean = server.id == signedInTo

    override suspend fun connect(server: AccountServer): Result<Unit> {
        connectFailure?.let { return Result.failure(it) }
        connected += server
        signedInTo = server.id
        return Result.success(Unit)
    }

    companion object {
        const val ACCOUNT_TOKEN = "account-token"

        fun server(id: String, name: String, owned: Boolean = true) = AccountServer(
            id = id,
            name = name,
            owned = owned,
            accessToken = "$id-token",
            connections = listOf(ServerConnection("https://$id.example:32400")),
        )
    }
}
