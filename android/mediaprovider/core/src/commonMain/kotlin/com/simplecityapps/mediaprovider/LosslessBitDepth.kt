package com.simplecityapps.mediaprovider

// Names as Jellyfin and Emby report them (ffmpeg's codec names) plus common aliases; any "pcm_*" is lossless too
private val losslessCodecs =
    setOf(
        "flac",
        "alac",
        "wav",
        "pcm",
        "aiff",
        "ape",
        "wavpack",
        "wv",
        "tta",
        "mlp",
        "truehd",
        "wmalossless",
        "dsd",
        "dsf",
        "dff",
        "dsd_lsbf",
        "dsd_msbf",
        "dsd_lsbf_planar",
        "dsd_msbf_planar"
    )

/** Whether [codec] (a codec name such as "flac" or "pcm_s24le") is lossless, so its bit depth means something. */
fun isLosslessCodec(codec: String?): Boolean {
    val name = codec?.trim()?.lowercase() ?: return false
    return name in losslessCodecs || name.startsWith("pcm_")
}

/**
 * The bit depth worth keeping for a stream encoded with [codec]. A lossy codec (MP3, AAC, Opus) has none, even when
 * the source reports its decoder's 16 or 32, so it is null; so is a missing or non-positive depth.
 */
fun losslessBitDepth(
    codec: String?,
    bitDepth: Int?
): Int? = bitDepth?.takeIf { it > 0 && isLosslessCodec(codec) }
