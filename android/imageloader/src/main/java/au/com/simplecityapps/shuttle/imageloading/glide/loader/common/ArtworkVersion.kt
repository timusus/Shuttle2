package au.com.simplecityapps.shuttle.imageloading.glide.loader.common

/**
 * Appends the provider's artwork version, so the key changes exactly when the artwork does. Without a version
 * (not yet reimported, or a provider with no signal) the key keeps its old form and existing cache entries stay valid.
 */
internal fun String.withArtworkVersion(artworkVersion: String?): String = if (artworkVersion == null) this else "${this}_$artworkVersion"
