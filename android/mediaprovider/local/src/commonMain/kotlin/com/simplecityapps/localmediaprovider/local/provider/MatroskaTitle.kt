package com.simplecityapps.localmediaprovider.local.provider

/**
 * The title of a Matroska file (.mka, .mkv, .webm), from the start of the file [head]. ffmpeg writes it to the segment
 * info (Segment > Info > Title), not as a tag, so TagLib's property map leaves it out (#523) and KTagLib exposes nothing
 * else. Returns null when the head holds no title: none is set, or the info lies past what [head] covers.
 */
fun matroskaTitle(head: ByteArray): String? {
    var position = 0

    // The element ID at [position], marker bits kept; moves past it
    fun readId(): Long? {
        val first = head.getOrNull(position)?.toInt()?.and(0xFF) ?: return null
        val length = first.countLeadingZeroBits() - 24 + 1
        if (length !in 1..4 || position + length > head.size) return null
        var id = 0L
        repeat(length) { id = (id shl 8) or (head[position + it].toLong() and 0xFF) }
        position += length
        return id
    }

    // The element size at [position], -1 for unknown; moves past it. Null when malformed or cut off.
    fun readSize(): Long? {
        val first = head.getOrNull(position)?.toInt()?.and(0xFF) ?: return null
        val length = first.countLeadingZeroBits() - 24 + 1
        if (length !in 1..8 || position + length > head.size) return null
        var size = (first and (0xFF shr length)).toLong()
        var allOnes = size == (0xFF shr length).toLong()
        for (index in 1 until length) {
            val byte = head[position + index].toLong() and 0xFF
            allOnes = allOnes && byte == 0xFFL
            size = (size shl 8) or byte
        }
        position += length
        return if (allOnes) -1 else size
    }

    // Walks the children of the master element ending at [end], descending into the ones in [descend]
    fun find(end: Int, descend: Set<Long>): String? {
        while (position < end) {
            val id = readId() ?: return null
            val size = readSize() ?: return null
            if (id == CLUSTER) return null
            if (id in descend) {
                val childEnd = if (size < 0 || position + size > end) end else (position + size).toInt()
                find(childEnd, if (id == SEGMENT) setOf(INFO) else emptySet())?.let { return it }
                position = childEnd
                continue
            }
            if (size < 0 || position + size > head.size) return null
            if (id == TITLE) return head.decodeToString(position, position + size.toInt()).trim('\u0000', ' ').ifEmpty { null }
            position += size.toInt()
        }
        return null
    }

    return find(head.size, setOf(SEGMENT))
}

private const val SEGMENT = 0x18538067L
private const val INFO = 0x1549A966L
private const val CLUSTER = 0x1F43B675L
private const val TITLE = 0x7BA9L
