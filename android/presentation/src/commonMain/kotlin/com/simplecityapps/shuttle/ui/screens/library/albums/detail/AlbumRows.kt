package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import com.simplecityapps.shuttle.model.AlbumItem
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.WorkBlock
import com.simplecityapps.shuttle.model.albumCreditsLine
import com.simplecityapps.shuttle.model.composerDisplayName
import com.simplecityapps.shuttle.model.composerName
import com.simplecityapps.shuttle.model.groupWorks

enum class WorkUnit { Movements, Parts, Songs }

/** One row of a classical album page; [key] is stable across collapse and playback changes. */
sealed interface AlbumRow {
    val key: String

    /** Shown where the composer changes between works, as on a recital. */
    data class ComposerHeading(val name: String, val firstSongId: Long) : AlbumRow {
        override val key get() = "composer-$firstSongId"
    }

    /** [detail] is the key, catalogue number and arrangement; [songs] are what playing the work plays. */
    data class WorkHeader(
        val title: String,
        val detail: String?,
        val unit: WorkUnit,
        val count: Int,
        val durationMs: Long,
        val credits: String?,
        val collapsible: Boolean,
        val collapsed: Boolean,
        val songs: List<Song>,
        val playing: Boolean,
    ) : AlbumRow {
        override val key get() = "work-${songs.first().id}"
    }

    data class Movement(val song: Song, val numeral: String?, val name: String, val playing: Boolean) : AlbumRow {
        override val key get() = "movement-${song.id}"
    }

    data class ActHeader(val label: String, val firstSongId: Long) : AlbumRow {
        override val key get() = "act-$firstSongId"
    }

    data class DiscDivider(val disc: Int, val firstSongId: Long) : AlbumRow {
        override val key get() = "disc-$firstSongId"
    }

    data class Track(val song: Song, val playing: Boolean) : AlbumRow {
        override val key get() = "song-${song.id}"
    }
}

/** What the classical album page shows for an album's songs; null from [buildAlbumRows] when the album has no work blocks. */
data class AlbumRows(
    val rows: List<AlbumRow>,
    /** The one composer of the whole album, for the header's eyebrow; null on a recital or when the composer is the album artist. */
    val eyebrow: String?,
    val credits: String?,
)

/**
 * Groups [songs] into works and flattens them to rows. [toggled] holds the works (by first song id) whose collapse state
 * the user flipped from the default.
 */
fun buildAlbumRows(songs: List<Song>, albumArtist: String?, currentSongId: Long?, toggled: Set<Long>): AlbumRows? {
    val items = groupWorks(songs)
    val blocks = items.filterIsInstance<AlbumItem.Work>().map { it.block }
    if (blocks.isEmpty()) return null

    fun WorkBlock.composerName(): String? = this.songs.firstOrNull { it.composerName() != null }?.let { song ->
        song.composerDisplayName()?.takeUnless { shown -> shown.equals(albumArtist, ignoreCase = true) || song.composerName()?.name.equals(albumArtist, ignoreCase = true) }
    }

    val composers = blocks.mapNotNull { it.composerName() }.distinct()
    val headings = composers.size > 1
    val multiDisc = songs.mapTo(HashSet()) { it.disc ?: 1 }.size > 1

    val rows = mutableListOf<AlbumRow>()
    var lastComposer: String? = null
    var lastDisc: Int? = null

    fun discDivider(song: Song) {
        val disc = song.disc ?: 1
        if (multiDisc && disc != lastDisc) rows += AlbumRow.DiscDivider(disc, song.id)
        lastDisc = disc
    }

    items.forEach { item ->
        when (item) {
            is AlbumItem.Track -> {
                discDivider(item.song)
                rows += AlbumRow.Track(item.song, item.song.id == currentSongId)
            }

            is AlbumItem.Work -> {
                val block = item.block
                val first = block.songs.first()
                if (headings) {
                    block.composerName()?.takeIf { it != lastComposer }?.let { rows += AlbumRow.ComposerHeading(it, first.id) }
                    lastComposer = block.composerName()
                }
                discDivider(first)
                val collapsed = block.collapsedByDefault != (first.id in toggled)
                rows += AlbumRow.WorkHeader(
                    title = block.title.core,
                    detail = listOfNotNull(
                        block.title.nickname?.let { "“$it”" },
                        block.title.key,
                        block.title.catalogue,
                        block.title.arrangement?.let { "arr. $it" },
                    ).joinToString(" · ").ifEmpty { null },
                    unit = when (block.numbering.unit) {
                        "parts" -> WorkUnit.Parts
                        "songs" -> WorkUnit.Songs
                        else -> WorkUnit.Movements
                    },
                    count = block.movementCount,
                    durationMs = block.durationMs,
                    credits = block.credits,
                    collapsible = block.collapsedByDefault,
                    collapsed = collapsed,
                    songs = block.songs,
                    playing = block.songs.any { it.id == currentSongId },
                )
                if (!collapsed) {
                    block.movements.forEachIndexed { index, movement ->
                        block.discBreaks.firstOrNull { it.beforeIndex == index }?.let { rows += AlbumRow.DiscDivider(it.disc, movement.song.id) }
                        block.acts.firstOrNull { it.movements.first == index }?.label?.let { rows += AlbumRow.ActHeader(it, movement.song.id) }
                        rows += AlbumRow.Movement(movement.song, movement.numeral, movement.name, movement.song.id == currentSongId)
                    }
                }
                lastDisc = block.songs.last().disc ?: 1
            }
        }
    }
    return AlbumRows(rows, eyebrow = composers.singleOrNull(), credits = albumCreditsLine(songs))
}
