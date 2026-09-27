package com.simplecityapps.provider.jellyfin.http

import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.networking.createHttpClient
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Retrofit's `@HEAD` probe never asked for JSON; ContentNegotiation must not add
 * `Accept: application/json` to a request that isn't fetching a JSON body (#585).
 */
class JellyfinTranscodeServiceTest {
    private val server = FixtureServer("jellyfin")
    private val service = JellyfinTranscodeService(createHttpClient(server.engine))

    @Test
    fun `the transcode probe doesn't ask for JSON`() = runTest {
        server.respond("/stream", code = 200, method = "HEAD")

        service.contentType("http://fixture.server/stream")

        server.requestsTo("/stream").single().headers["Accept"] shouldBe "*/*"
    }

    @Test
    fun `a failed probe returns no content type`() = runTest {
        server.respond("/stream", code = 404, method = "HEAD")

        service.contentType("http://fixture.server/stream").shouldBeNull()
    }
}
