package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Song

/**
 * Writes the favourite state of a remote provider's songs to its server, so a heart made here shows up in the server's
 * own apps (#497).
 *
 * [setFavourite] returns whether the server accepted the change. A network failure may also surface as an exception;
 * callers treat that the same as `false`.
 */
interface FavouriteWriter {
    fun handles(song: Song): Boolean

    /** Marks [song] as a favourite on the server, or clears it when [favourite] is false. */
    suspend fun setFavourite(
        song: Song,
        favourite: Boolean
    ): Boolean
}

/** Routes each call to the first of [writers] that handles the song; a song none handle is reported as not written. */
class AggregateFavouriteWriter(private val writers: Set<FavouriteWriter>) : FavouriteWriter {
    override fun handles(song: Song): Boolean = writerFor(song) != null

    override suspend fun setFavourite(
        song: Song,
        favourite: Boolean
    ): Boolean = writerFor(song)?.setFavourite(song, favourite) ?: false

    private fun writerFor(song: Song): FavouriteWriter? = writers.firstOrNull { it.handles(song) }
}
