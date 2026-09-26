package com.simplecityapps.imageloading.glide.loader.remote.s2

import com.bumptech.glide.load.model.GlideUrl
import com.simplecityapps.imageloading.glide.loader.common.withArtworkVersion

/**
 * A [GlideUrl] whose disk-cache key also carries the model's artwork version. Without this, the S2
 * artwork API's URL (keyed only by artist/album name) never changes, so a disk-cache hit for it can mask
 * newly discovered local artwork after a rescan bumps the local loaders' versioned key. The fetched URL
 * itself is unaffected — only the cache key moves.
 */
class VersionedGlideUrl(
    url: String,
    artworkVersion: String?
) : GlideUrl(url) {
    private val cacheKey = super.getCacheKey().withArtworkVersion(artworkVersion)

    override fun getCacheKey(): String = cacheKey
}
