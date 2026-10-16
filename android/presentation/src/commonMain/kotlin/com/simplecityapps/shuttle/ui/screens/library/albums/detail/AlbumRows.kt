package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import com.simplecityapps.shuttle.model.AlbumItem
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.WorkBlock
import com.simplecityapps.shuttle.model.albumCreditsLine
import com.simplecityapps.shuttle.model.composerDisplayName
import com.simplecityapps.shuttle.model.composerName
import com.simplecityapps.shuttle.model.groupWorks
import com.simplecityapps.shuttle.model.isClassical
import com.simplecityapps.shuttle.model.movementLabel
import com.simplecityapps.shuttle.ui.text.PluralKey
import com.simplecityapps.shuttle.ui.text.UiText

/** What tapping a work header does: a long work folds and unfolds, a short one plays. */
enum class WorkTap { ToggleCollapse, Play }

/**
 * One row of a classical album page; [key] is stable across collapse and playback changes. A row that plays a song
 * carries its [Movement.playIndex] in the album's songs, so the screen needn't search for it.
 */
sealed interface AlbumRow {
    val key: String

    /** Shown where the composer changes between works, as on a recital. */
    data class ComposerHeading(val name: String, val firstSongId: Long) : AlbumRow {
        override val key get() = "composer-$firstSongId"
    }

    /** [detail] is the key, catalogue number and arrangement; [count] is "4 movements"; [songs] are what playing the work plays. */
    data class WorkHeader(
        val title: String,
        val detail: String?,
        val count: UiText,
        val durationMs: Long,
        val credits: String?,
        val tap: WorkTap,
        val collapsed: Boolean,
        val songs: List<Song>,
        val playing: Boolean,
    ) : AlbumRow {
        override val key get() = "work-${songs.first().id}"

        val collapsible: Boolean get() = tap == WorkTap.ToggleCollapse
    }

    data class Movement(val song: Song, val numeral: String?, val name: String, val playing: Boolean, val playIndex: Int) : AlbumRow {
        override val key get() = "movement-${song.id}"
    }

    data class ActHeader(val label: String, val firstSongId: Long) : AlbumRow {
        override val key get() = "act-$firstSongId"
    }

    /** [inWork] is a disc change in the middle of a work, drawn with the work's movements. */
    data class DiscDivider(val disc: Int, val firstSongId: Long, val inWork: Boolean = false) : AlbumRow {
        override val key get() = "disc-$firstSongId"
    }

    /** A song outside any work; [title] is null when the song has no name. */
    data class Track(val song: Song, val title: String?, val playing: Boolean, val playIndex: Int) : AlbumRow {
        override val key get() = "song-${song.id}"
    }
}

/** What the classical album page shows for an album's songs; null from [buildAlbumRows] when the album has no work blocks. */
data class AlbumRows(
    val rows: List<AlbumRow>,
    /** The one composer of every song on the album, for the header's eyebrow; null on a recital or a mixed album. */
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

    fun Song.shownComposer(): String? = composerDisplayName()?.takeUnless { shown -> shown.equals(albumArtist, ignoreCase = true) || composerName()?.name.equals(albumArtist, ignoreCase = true) }

    fun WorkBlock.composerName(): String? = this.songs.firstOrNull { it.composerName() != null }?.shownComposer()

    val composers = blocks.mapNotNull { it.composerName() }.distinct()
    val headings = composers.size > 1
    val multiDisc = songs.mapTo(HashSet()) { it.disc ?: 1 }.size > 1
    val playIndexes = songs.withIndex().associate { (index, song) -> song.id to index }

    val rows = mutableListOf<AlbumRow>()
    var lastComposer: String? = null
    var lastDisc: Int? = null

    // The album's first disc needs no heading above a work; its movements are already the page's top.
    fun discDivider(song: Song, beforeWork: Boolean) {
        val disc = song.disc ?: 1
        if (multiDisc && disc != lastDisc && !(beforeWork && rows.isEmpty())) rows += AlbumRow.DiscDivider(disc, song.id)
        lastDisc = disc
    }

    items.forEach { item ->
        when (item) {
            is AlbumItem.Track -> {
                discDivider(item.song, beforeWork = false)
                val song = item.song
                rows += AlbumRow.Track(song, if (song.isClassical) song.movementLabel() else song.name, song.id == currentSongId, playIndexes.getValue(song.id))
            }

            is AlbumItem.Work -> {
                val block = item.block
                val first = block.songs.first()
                if (headings) {
                    block.composerName()?.takeIf { it != lastComposer }?.let { rows += AlbumRow.ComposerHeading(it, first.id) }
                    lastComposer = block.composerName()
                }
                discDivider(first, beforeWork = true)
                val collapsed = block.collapsedByDefault != (first.id in toggled)
                rows += AlbumRow.WorkHeader(
                    title = block.title.core,
                    detail = listOfNotNull(
                        block.title.nickname?.let { "“$it”" },
                        block.title.key,
                        block.title.catalogue,
                        block.title.arrangement?.let { "arr. $it" },
                    ).joinToString(" · ").ifEmpty { null },
                    count = UiText.Plural(
                        when (block.numbering.unit) {
                            "parts" -> PluralKey.WORK_PARTS
                            "songs" -> PluralKey.WORK_SONGS
                            else -> PluralKey.WORK_MOVEMENTS
                        },
                        block.movementCount,
                    ),
                    durationMs = block.durationMs,
                    credits = block.credits,
                    tap = if (block.collapsedByDefault) WorkTap.ToggleCollapse else WorkTap.Play,
                    collapsed = collapsed,
                    songs = block.songs,
                    playing = block.songs.any { it.id == currentSongId },
                )
                if (!collapsed) {
                    block.movements.forEachIndexed { index, movement ->
                        block.discBreaks.firstOrNull { it.beforeIndex == index }?.let { rows += AlbumRow.DiscDivider(it.disc, movement.song.id, inWork = true) }
                        block.acts.firstOrNull { it.movements.first == index }?.label?.let { rows += AlbumRow.ActHeader(it, movement.song.id) }
                        rows += AlbumRow.Movement(movement.song, movement.numeral, movement.name, movement.song.id == currentSongId, playIndexes.getValue(movement.song.id))
                    }
                }
                lastDisc = block.songs.last().disc ?: 1
            }
        }
    }
    // Bonus tracks carry no composer tag, so they don't break "one composer"; the album artist being that composer doesn't hide it.
    val tagged = songs.mapNotNull { it.composerDisplayName() }
    val eyebrow = tagged.firstOrNull()?.takeIf { composer -> tagged.all { it == composer } }
    return AlbumRows(rows, eyebrow = eyebrow, credits = albumCreditsLine(songs))
}
