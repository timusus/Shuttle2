package com.simplecityapps.playback.lyrics

import io.ktor.client.HttpClient

/** The HTTP client [AppOnlineLyrics] reaches LRCLIB with; each platform's DI module supplies it (Android backs it with the app's OkHttpClient). */
class LrclibHttp(val client: HttpClient)
