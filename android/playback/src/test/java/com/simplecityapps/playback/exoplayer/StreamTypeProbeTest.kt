package com.simplecityapps.playback.exoplayer

import android.net.Uri
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StreamTypeProbeTest {
    private val server = FakeStreamServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun probe(path: String) = StreamTypeProbe.probe(DefaultHttpDataSource.Factory().createDataSource(), Uri.parse(server.baseUrl + path))

    @Test
    fun `extensionless remote url with a wildcard or missing mime type needs a probe`() {
        StreamTypeProbe.needsProbe(Uri.parse("https://emby.example/emby/Audio/1/universal?api_key=x"), "Audio/*") shouldBe true
        StreamTypeProbe.needsProbe(Uri.parse("http://192.168.1.2:8096/Audio/1/universal?ApiKey=x"), null) shouldBe true
    }

    @Test
    fun `local files, content uris, known mime types and file extensions skip the probe`() {
        StreamTypeProbe.needsProbe(Uri.parse("file:///storage/emulated/0/Music/a.mp3"), "audio/mpeg") shouldBe false
        StreamTypeProbe.needsProbe(Uri.parse("file:///storage/emulated/0/Music/noext"), "Audio/*") shouldBe false
        StreamTypeProbe.needsProbe(Uri.parse("content://media/external/audio/media/12"), null) shouldBe false
        StreamTypeProbe.needsProbe(Uri.parse("https://server.example/Audio/1/universal"), "audio/mpeg") shouldBe false
        StreamTypeProbe.needsProbe(Uri.parse("https://plex.example/library/parts/1/2/file.flac?X-Plex-Token=x"), "Audio/*") shouldBe false
        StreamTypeProbe.needsProbe(Uri.parse("https://server.example/stream/master.m3u8"), null) shouldBe false
    }

    @Test
    fun `detects hls from the content type`() {
        StreamTypeProbe.detect("application/vnd.apple.mpegurl", ByteArray(0)) shouldBe StreamType.Hls
        StreamTypeProbe.detect("Application/X-MpegURL; charset=utf-8", ByteArray(0)) shouldBe StreamType.Hls
    }

    @Test
    fun `detects hls from the body when the content type does not say`() {
        StreamTypeProbe.detect("application/octet-stream", "#EXTM3U\n".toByteArray()) shouldBe StreamType.Hls
        StreamTypeProbe.detect(null, "﻿#EXTM3U\n".toByteArray()) shouldBe StreamType.Hls
    }

    @Test
    fun `anything else is progressive`() {
        StreamTypeProbe.detect("audio/mpeg", "ID3\u0004".toByteArray()) shouldBe StreamType.Progressive
        StreamTypeProbe.detect(null, "#EXT".toByteArray()) shouldBe StreamType.Progressive
    }

    @Test
    fun `probe reads a transcoded stream as hls`() {
        probe("/Audio/1/hls") shouldBe StreamType.Hls
    }

    @Test
    fun `probe sniffs an unlabelled playlist`() {
        probe("/Audio/1/unlabelled-hls") shouldBe StreamType.Hls
    }

    @Test
    fun `probe reads a direct stream as progressive`() {
        probe("/Audio/1/direct") shouldBe StreamType.Progressive
    }

    @Test
    fun `probe surfaces http errors`() {
        shouldThrow<HttpDataSource.InvalidResponseCodeException> { probe("/Audio/1/rejected") }
    }
}
