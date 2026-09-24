package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.s2

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VersionedGlideUrlTest {
    @Test
    fun `cache key is stable for the same url and version`() {
        VersionedGlideUrl("https://example.com/artwork", "v1").cacheKey shouldBe
            VersionedGlideUrl("https://example.com/artwork", "v1").cacheKey
    }

    @Test
    fun `cache key changes when the version changes`() {
        VersionedGlideUrl("https://example.com/artwork", "v1").cacheKey shouldNotBe
            VersionedGlideUrl("https://example.com/artwork", "v2").cacheKey
    }

    @Test
    fun `cache key keeps the unversioned url form when there is no version`() {
        VersionedGlideUrl("https://example.com/artwork", null).cacheKey shouldBe "https://example.com/artwork"
    }

    @Test
    fun `the fetched url is unchanged by the version`() {
        VersionedGlideUrl("https://example.com/artwork", "v1").toStringUrl() shouldBe "https://example.com/artwork"
    }
}
