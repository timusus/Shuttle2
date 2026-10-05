package com.simplecityapps.localmediaprovider.local.provider

import java.io.File

private val MATROSKA_EXTENSIONS = setOf("mka", "mkv", "webm")

/**
 * The segment title of the Matroska file open as [fileDescriptor], for a file TagLib found no title in (see [matroskaTitle]);
 * null for any other file, or when it has none or can't be read. The file is opened afresh through /proc/self/fd, so the
 * descriptor's own read position, which TagLib uses, is left alone.
 */
fun matroskaTitleOf(
    fileDescriptor: Int,
    fileName: String
): String? {
    if (fileName.substringAfterLast('.', "").lowercase() !in MATROSKA_EXTENSIONS) return null
    return try {
        File("/proc/self/fd/$fileDescriptor").inputStream().use { stream ->
            val head = ByteArray(HEAD_SIZE)
            var read = 0
            while (read < head.size) {
                val count = stream.read(head, read, head.size - read)
                if (count < 0) break
                read += count
            }
            matroskaTitle(head.copyOf(read))
        }
    } catch (e: Exception) {
        null
    }
}

// The segment info follows the EBML header and seek head, a few KB in; cover a large attachments-first layout too
private const val HEAD_SIZE = 256 * 1024
