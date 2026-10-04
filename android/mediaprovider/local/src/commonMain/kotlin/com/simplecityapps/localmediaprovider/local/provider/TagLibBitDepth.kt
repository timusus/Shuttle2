package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.mediaprovider.losslessBitDepth

/**
 * The bit depth of a file TagLib scanned: TagLib's bits-per-sample, kept only when its [codec] (as ktaglib names it,
 * not the container, so an `.m4a` is "alac" or "aac") is lossless. A lossy codec such as AAC, an unknown codec or a
 * depth of 0 is null.
 */
fun taglibBitDepth(
    codec: String?,
    bitsPerSample: Int?
): Int? = losslessBitDepth(codec, bitsPerSample)
