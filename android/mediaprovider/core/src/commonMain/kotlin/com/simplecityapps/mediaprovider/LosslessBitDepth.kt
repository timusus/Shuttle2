package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.isLosslessCodec

/**
 * The bit depth worth keeping for a stream encoded with [codec]. A lossy codec (MP3, AAC, Opus) has none, even when
 * the source reports its decoder's 16 or 32, so it is null; so is a missing or non-positive depth.
 */
fun losslessBitDepth(
    codec: String?,
    bitDepth: Int?
): Int? = bitDepth?.takeIf { it > 0 && isLosslessCodec(codec) }
