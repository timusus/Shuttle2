package com.simplecityapps.localmediaprovider.local.provider.taglib

/** MediaStore's audio rows in memory, counting each kind of read. */
internal class FakeMediaStore : MediaStoreAudioSource {
    private val rows = linkedMapOf<Long, MediaStoreAudioRow>()
    var version: String? = "v1"
    var unreadable = false

    // Rows removed between the generations query and reading them again
    var vanishOnRead: Set<Long> = emptySet()
    var wholeReads = 0
    var wholeReadsWithGeneration = 0
    var generationReads = 0
    val changedAfter = mutableListOf<Long>()
    val changedAfterReads get() = changedAfter.size
    val idReads = mutableListOf<Set<Long>>()

    fun put(vararg rows: MediaStoreAudioRow) = rows.forEach { row -> this.rows[row.file.id] = row }

    override fun version(): String? = version

    override fun rows(withGeneration: Boolean): List<MediaStoreAudioRow>? {
        if (unreadable) return null
        wholeReads++
        if (withGeneration) wholeReadsWithGeneration++
        return rows.values.map { row -> if (withGeneration) row else row.copy(generation = 0) }
    }

    override fun generations(): Map<Long, Long>? {
        if (unreadable) return null
        generationReads++
        return rows.mapValues { (_, row) -> row.generation }
    }

    override fun rowsChangedAfter(generation: Long): List<MediaStoreAudioRow>? {
        if (unreadable) return null
        changedAfter += generation
        return rows.values.filter { row -> row.generation > generation && row.file.id !in vanishOnRead }
    }

    override fun rowsWithIds(ids: Collection<Long>): List<MediaStoreAudioRow>? {
        if (unreadable) return null
        idReads += ids.toSet()
        return ids.mapNotNull { id -> rows[id]?.takeIf { id !in vanishOnRead } }
    }
}
