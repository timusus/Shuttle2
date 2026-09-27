package com.simplecityapps.trial

import com.simplecityapps.networking.S2Json
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okhttp3.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromoCodeServiceTest {
    @Test
    fun `getPromoCode sends a GET with the email query param, Basic Auth and decodes the response`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = S2Json.encodeToString(PromoCode.serializer(), PromoCode(promoCode = "WELCOME10")),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = createHttpClient(engine) {
            defaultRequest {
                header(HttpHeaders.Authorization, Credentials.basic("s2", "aEqRKgkCbqALjEm9Eg7e7Qi5"))
            }
        }
        val service = PromoCodeService(client)

        val result = service.getPromoCode("someone@example.com")

        val request = engine.requestHistory.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("${S2_API_BASE_URL}v1/promo_code", request.url.toString().substringBefore("?"))
        assertEquals("someone@example.com", request.url.parameters["email"])
        assertEquals(Credentials.basic("s2", "aEqRKgkCbqALjEm9Eg7e7Qi5"), request.headers[HttpHeaders.Authorization])
        assertTrue(result is NetworkResult.Success)
        assertEquals("WELCOME10", (result as NetworkResult.Success).body.promoCode)
    }
}
