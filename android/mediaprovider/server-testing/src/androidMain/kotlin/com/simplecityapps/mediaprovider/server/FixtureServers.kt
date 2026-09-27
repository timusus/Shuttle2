package com.simplecityapps.mediaprovider.server

import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/** A [FixtureServer] answering with the JSON fixtures in the test resources' [fixtureDir] (`src/test/resources/jellyfin`, say). */
fun FixtureServer(fixtureDir: String): FixtureServer = FixtureServer { name ->
    checkNotNull(FixtureServer::class.java.classLoader.getResource("$fixtureDir/$name")) { "No fixture $fixtureDir/$name" }.readText()
}

/**
 * An [OkHttpClient] whose calls this server answers, without touching the network, for the providers' Retrofit
 * services. Deleted once they move to Ktor (#585 step 3) and build their clients on [FixtureServer.engine].
 */
fun FixtureServer.okHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain -> answer(chain.request()) }
    .build()

private fun FixtureServer.answer(request: Request): Response = runBlocking {
    val response =
        client.request(request.url.toString()) {
            method = HttpMethod.parse(request.method)
            for ((name, value) in request.headers) {
                if (name !in HttpHeaders.UnsafeHeadersList) headers.append(name, value)
            }
            request.body?.let { body ->
                val buffer = Buffer().also(body::writeTo)
                setBody(ByteArrayContent(buffer.readByteArray(), body.contentType()?.let { ContentType.parse(it.toString()) }))
            }
        }
    val builder =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(response.status.value)
            .message(response.status.description)
    response.headers.forEach { name, values -> values.forEach { value -> builder.addHeader(name, value) } }
    builder.body(response.bodyAsBytes().toResponseBody(response.contentType()?.toString()?.toMediaTypeOrNull())).build()
}
