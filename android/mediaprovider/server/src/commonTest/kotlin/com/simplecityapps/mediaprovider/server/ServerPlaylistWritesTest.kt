package com.simplecityapps.mediaprovider.server

import com.simplecityapps.mediaprovider.PlaylistWriteResult
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlin.test.Test

class ServerPlaylistWritesTest {
    private fun failure(status: HttpStatusCode) = NetworkResult.Failure(RemoteServiceHttpError(status)).toPlaylistWriteResult()

    @Test
    fun `a success carries its value`() {
        NetworkResult.Success("body").toPlaylistWriteResult { body -> body.length } shouldBe PlaylistWriteResult.Success(4)
    }

    @Test
    fun `a 404 means the playlist is gone`() {
        failure(HttpStatusCode.NotFound) shouldBe PlaylistWriteResult.PlaylistGone
    }

    @Test
    fun `a client error a later try can't get past is refused`() {
        failure(HttpStatusCode.Forbidden) shouldBe PlaylistWriteResult.Refused
        failure(HttpStatusCode.BadRequest) shouldBe PlaylistWriteResult.Refused
    }

    @Test
    fun `no connection - a server error - signed out - timed out or rate limited is tried again later`() {
        NetworkResult.Failure(Exception("offline")).toPlaylistWriteResult() shouldBe PlaylistWriteResult.Failed
        failure(HttpStatusCode.InternalServerError) shouldBe PlaylistWriteResult.Failed
        failure(HttpStatusCode.Unauthorized) shouldBe PlaylistWriteResult.Failed
        failure(HttpStatusCode.RequestTimeout) shouldBe PlaylistWriteResult.Failed
        failure(HttpStatusCode.TooManyRequests) shouldBe PlaylistWriteResult.Failed
    }
}
