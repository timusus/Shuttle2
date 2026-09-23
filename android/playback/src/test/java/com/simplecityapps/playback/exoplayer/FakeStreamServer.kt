package com.simplecityapps.playback.exoplayer

import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.atomic.AtomicInteger

/**
 * A local HTTP server standing in for Jellyfin/Emby's `/Audio/{id}/universal` endpoint:
 * `/hls` answers like a transcode (HLS playlist), `/unlabelled-hls` sends a playlist as
 * octet-stream, `/direct` direct-streams an mp3, `/unauthorized` rejects the token.
 */
class FakeStreamServer : NanoHTTPD("127.0.0.1", 0) {
    val requestCount = AtomicInteger()

    val baseUrl: String get() = "http://127.0.0.1:$listeningPort"

    override fun serve(session: IHTTPSession): Response {
        requestCount.incrementAndGet()
        return when (session.uri) {
            "/Audio/1/hls" -> newFixedLengthResponse(Response.Status.OK, "application/vnd.apple.mpegurl; charset=utf-8", PLAYLIST)
            "/Audio/1/unlabelled-hls" -> newFixedLengthResponse(Response.Status.OK, "application/octet-stream", "﻿" + PLAYLIST)
            "/Audio/1/direct" -> newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, "audio/mpeg", "ID3\u0004\u0000 fake mp3 frames")
            else -> newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Unauthorized")
        }
    }

    companion object {
        const val PLAYLIST = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n#EXT-X-PLAYLIST-TYPE:VOD\n" +
            "#EXTINF:6.0,\nhls/segment0.ts\n#EXT-X-ENDLIST\n"
    }
}
