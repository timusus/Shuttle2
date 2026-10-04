package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.mediaprovider.losslessBitDepth

/**
 * The bit depth of a file TagLib scanned: TagLib's bits-per-sample, kept only for a lossless format. TagLib doesn't
 * name the codec, so it is taken from the file extension; an unknown extension, a lossy format or a depth of 0 is null.
 * M4A/MP4 is null too: AAC and ALAC share the extension and TagLib reports 16 for both.
 */
fun taglibBitDepth(
    fileName: String,
    bitsPerSample: Int?
): Int? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val codec = if (extension == "aif" || extension == "aifc") "aiff" else extension
    return losslessBitDepth(codec, bitsPerSample)
}
