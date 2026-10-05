package com.simplecityapps.shuttle.telemetry

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test

class TelemetryScrubberTest {
    private fun scrub(text: String) = TelemetryScrubber.scrub(text)

    @Test
    fun `urls go whole - query and all`() {
        scrub("GET https://music.example.com:8096/Users/AuthenticateByName?api_key=abc failed") shouldBe "GET <url> failed"
        scrub("Couldn't open content://media/external/audio/media/42") shouldBe "Couldn't open <url>"
        scrub("file:///var/mobile/Containers/Data/Application/X/Documents/a.flac") shouldBe "<url>"
    }

    @Test
    fun `credentials are redacted outside a url`() {
        scrub("retry user=sam token=s3cr3t&X-Plex-Token=xyz api_key=1") shouldBe
            "retry user=<redacted> token=<redacted>&X-Plex-Token=<redacted> api_key=<redacted>"
        scrub("userId=abc-123") shouldBe "userId=<redacted>"
        scrub("failed (token=abc) [pw=x]") shouldBe "failed (token=<redacted>) [pw=<redacted>]"
        scrub("query ?id=1&u=sam&t=26719a&s=c19b2d&p=enc:73") shouldBe "query ?id=1&u=<redacted>&t=<redacted>&s=<redacted>&p=<redacted>"
        scrub("a=1 s=2") shouldBe "a=1 s=2"
    }

    @Test
    fun `ip addresses go - with their port`() {
        scrub("Failed to connect to /192.168.1.20:8096") shouldBe "Failed to connect to /<ip>"
        scrub("route to fe80::1c2b:3cff:fe4d:5e6f lost") shouldBe "route to <ip> lost"
        scrub("2001:db8:85a3:0:0:8a2e:370:7334 down") shouldBe "<ip> down"
    }

    @Test
    fun `host names go - code names stay`() {
        scrub("Unable to resolve host \"jellyfin.example.com\": No address") shouldBe "Unable to resolve host \"<host>\": No address"
        scrub("nas.local:32400 refused") shouldBe "<host> refused"
        scrub("kotlin.IllegalStateException in com.simplecityapps.shuttle.playback.Queue") shouldBe
            "kotlin.IllegalStateException in com.simplecityapps.shuttle.playback.Queue"
    }

    @Test
    fun `host names go whatever their case`() {
        scrub("Tims-NAS.local refused") shouldBe "<host> refused"
        scrub("connect to MyServer.example.com:8096 timed out") shouldBe "connect to <host> timed out"
        scrub("SERVER.LOCAL down") shouldBe "<host> down"
        // A capitalised first label is never a package
        scrub("Platform.example.com down") shouldBe "<host> down"
    }

    @Test
    fun `single-label hosts go where the text says they are hosts`() {
        scrub("Failed to connect to homeserver/192.168.1.5:8096") shouldBe "Failed to connect to <host>/<ip>"
        scrub("homeserver/192.168.1.5:8096 refused") shouldBe "<host>/<ip> refused"
        scrub("Unable to resolve host \"homeserver\": No address associated with hostname") shouldBe
            "Unable to resolve host \"<host>\": No address associated with hostname"
        scrub("connect to Tims-NAS timed out") shouldBe "connect to <host> timed out"
        scrub("host=homeserver, hostname: nas") shouldBe "host=<host>, hostname: <host>"
        scrub("share //homeserver/music/x.flac gone") shouldNotContain "homeserver"
        scrub("//homeserver:8096 refused") shouldBe "//<host> refused"
        scrub("homeserver:32400 refused") shouldBe "<host> refused"
    }

    @Test
    fun `ordinary words - members and source locations stay`() {
        scrub("Failed to connect to the server") shouldBe "Failed to connect to the server"
        scrub("Unable to resolve host: no network") shouldBe "Unable to resolve host: no network"
        scrub("Fragment.onCreate threw at Queue.kt:42 (SettingsRoutes.kt)") shouldBe
            "Fragment.onCreate threw at Queue.kt:42 (SettingsRoutes.kt)"
        scrub("failed at line:42") shouldBe "failed at line:42"
    }

    @Test
    fun `file paths go`() {
        scrub("No such file /storage/emulated/0/Music/Artist/Song.mp3") shouldBe "No such file <path>"
        scrub("open /var/mobile/Containers/Data/Application/ABC/Documents failed") shouldBe "open <path> failed"
    }

    @Test
    fun `emails go`() {
        scrub("signed in as sam@example.com") shouldBe "signed in as <email>"
    }

    @Test
    fun `ordinary text - times - and status codes stay`() {
        val text = "Playback failed at 12:30:45 with HTTP 503 (attempt 2/3)"
        scrub(text) shouldBe text
        scrub("") shouldBe ""
    }

    @Test
    fun `nothing identifying survives a realistic breadcrumb`() {
        val crumb = "tag: JellyfinAuth, message: Failed https://10.0.0.5:8096/Items?userId=u1, throwable: " +
            "Unable to resolve host \"media.home.arpa\" for /Users/tim/Music/x.flac"
        val scrubbed = scrub(crumb)
        scrubbed shouldNotContain "10.0.0.5"
        scrubbed shouldNotContain "media.home.arpa"
        scrubbed shouldNotContain "/Users/tim"
        scrubbed shouldNotContain "u1"
    }
}
