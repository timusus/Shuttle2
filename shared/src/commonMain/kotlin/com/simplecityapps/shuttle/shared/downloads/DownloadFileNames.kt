package com.simplecityapps.shuttle.shared.downloads

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A downloaded song's file name: its `Song.path`, URL-safe Base64 encoded so the name can be read back to the path it
 * belongs to, then the file's extension, which the engine's format probe can lean on. `jellyfin://item/42` with
 * extension `flac` is `amVsbHlmaW46Ly9pdGVtLzQy.flac`.
 */
@OptIn(ExperimentalEncodingApi::class)
object DownloadFileNames {
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    fun fileName(
        path: String,
        extension: String
    ): String = base64.encode(path.encodeToByteArray()) + "." + extension

    /** The `Song.path` a [fileName] belongs to, or null for a file that isn't one of ours. */
    fun path(fileName: String): String? = runCatching { base64.decode(fileName.substringBefore('.')).decodeToString() }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() && fileName.contains('.') }

    /** The extension a download of [mimeType] is saved with, or [fallback] for a type it doesn't know. */
    fun extension(
        mimeType: String,
        fallback: String?
    ): String = when (mimeType.substringBefore(';').trim().lowercase()) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac" -> "m4a"
        "audio/ogg", "audio/vorbis" -> "ogg"
        "audio/opus" -> "opus"
        "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
        "audio/aiff", "audio/x-aiff" -> "aiff"
        "audio/x-ms-wma" -> "wma"
        else -> fallback?.takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }?.lowercase() ?: "audio"
    }
}
