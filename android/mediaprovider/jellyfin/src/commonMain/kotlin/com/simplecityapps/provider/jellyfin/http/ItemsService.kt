package com.simplecityapps.provider.jellyfin.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlin.time.Instant

/** Jellyfin's library queries: songs, playlists and their items, and a single item. */
class ItemsService(private val client: HttpClient) {
    suspend fun audioItems(
        url: String,
        authorization: String,
        userId: String,
        /** The music library to list the songs of. */
        parentId: String,
        limit: Int = 2500,
        startIndex: Int = 0,
        /** Only the songs saved on the server (added, or their metadata changed) at or after this time; all of them if null. */
        minDateLastSaved: Instant? = null
    ): NetworkResult<QueryResult> = items(
        url = "$url/Users/$userId/Items",
        authorization = authorization,
        itemTypes = "Audio",
        fields = "Genres,DateCreated,ProviderIds,MediaStreams",
        parentId = parentId,
        limit = limit,
        startIndex = startIndex,
        minDateLastSaved = minDateLastSaved,
        // Each song's UserData, which holds whether it's a favourite
        enableUserData = true,
        // A stable order (the server has no unique sort key to break ties), so a song is unlikely to slip between pages when the list is paged by offset
        sortBy = "DateCreated,SortName"
    )

    /**
     * The ids of the songs in the music library [parentId] (in every library the user has if null), with nothing else (no fields, user data or images): what the
     * server still holds, which an incremental sync removes the rest against. A [limit] of 1 and [startIndex] 0 reads the
     * [QueryResult.totalRecordCount] cheaply.
     */
    suspend fun audioIds(
        url: String,
        authorization: String,
        userId: String,
        parentId: String?,
        limit: Int = 2500,
        startIndex: Int = 0
    ): NetworkResult<QueryResult> = items(
        url = "$url/Users/$userId/Items",
        authorization = authorization,
        itemTypes = "Audio",
        parentId = parentId,
        limit = limit,
        startIndex = startIndex,
        enableUserData = false,
        enableImages = false,
        // The order the songs are listed in, so one is unlikely to slip between pages when the list is paged by offset
        sortBy = "DateCreated,SortName"
    )

    /** The user's libraries, each with its [Item.collectionType]: a sync reads the songs of all but those of kinds that hold no music. */
    suspend fun libraries(
        url: String,
        authorization: String,
        userId: String
    ): NetworkResult<QueryResult> = client.networkResult {
        get("$url/Users/$userId/Views") {
            // A library hidden from the user's home screen still holds their music; leaving it out would delete its songs
            parameter("IncludeHidden", true)
            header(HttpHeaders.Authorization, authorization)
        }
    }

    /**
     * The user's favourite songs, by id alone (no fields, no user data): an incremental sync's favourites list, since a
     * favourite toggled on the server doesn't change the song's DateLastSaved.
     */
    suspend fun favouriteAudioItems(
        url: String,
        authorization: String,
        userId: String,
        limit: Int = 2500,
        startIndex: Int = 0
    ): NetworkResult<QueryResult> = items(
        url = "$url/Users/$userId/Items",
        authorization = authorization,
        itemTypes = "Audio",
        limit = limit,
        startIndex = startIndex,
        filters = "IsFavorite",
        enableUserData = false,
        // A stable order (the server has no unique sort key to break ties), so a favourite is unlikely to slip between pages when the list is paged by offset
        sortBy = "DateCreated,SortName"
    )

    /**
     * The songs the user has played, most recently played first, read as [audioItems] reads them: a sync pages through
     * them until it reaches the plays it has already seen.
     */
    suspend fun playedAudioItems(
        url: String,
        authorization: String,
        userId: String,
        limit: Int,
        startIndex: Int
    ): NetworkResult<QueryResult> = items(
        url = "$url/Users/$userId/Items",
        authorization = authorization,
        itemTypes = "Audio",
        fields = "Genres,DateCreated,ProviderIds,MediaStreams",
        limit = limit,
        startIndex = startIndex,
        filters = "IsPlayed",
        enableUserData = true,
        sortBy = "DatePlayed,SortName",
        sortOrder = "Descending"
    )

    suspend fun playlists(
        url: String,
        authorization: String,
        userId: String,
        limit: Int = 2500,
        startIndex: Int = 0
    ): NetworkResult<QueryResult> = items(
        url = "$url/Users/$userId/Items",
        authorization = authorization,
        itemTypes = "Playlist",
        fields = "DateLastSaved,ChildCount",
        limit = limit,
        startIndex = startIndex
    )

    suspend fun playlistItems(
        url: String,
        authorization: String,
        playlistId: String,
        userId: String,
        limit: Int = 2500,
        startIndex: Int = 0
    ): NetworkResult<QueryResult> = items(
        url = "$url/Playlists/$playlistId/Items",
        authorization = authorization,
        itemTypes = "Audio",
        limit = limit,
        startIndex = startIndex,
        userId = userId
    )

    suspend fun item(
        url: String,
        authorization: String,
        userId: String,
        itemId: String
    ): NetworkResult<Item> = client.networkResult {
        get("$url/Users/$userId/Items/$itemId") {
            header(HttpHeaders.Authorization, authorization)
        }
    }

    private suspend fun items(
        url: String,
        authorization: String,
        itemTypes: String,
        fields: String? = null,
        limit: Int,
        startIndex: Int,
        userId: String? = null,
        parentId: String? = null,
        minDateLastSaved: Instant? = null,
        filters: String? = null,
        enableUserData: Boolean? = null,
        enableImages: Boolean? = null,
        sortBy: String? = null,
        sortOrder: String? = null
    ): NetworkResult<QueryResult> = client.networkResult {
        get(url) {
            header(HttpHeaders.Authorization, authorization)
            parameter("recursive", true)
            parameter("includeItemTypes", itemTypes)
            parameter("fields", fields)
            parameter("limit", limit)
            parameter("startIndex", startIndex)
            parameter("userId", userId)
            parameter("parentId", parentId)
            parameter("minDateLastSaved", minDateLastSaved?.toString())
            parameter("filters", filters)
            parameter("enableUserData", enableUserData)
            parameter("enableImages", enableImages)
            parameter("sortBy", sortBy)
            parameter("sortOrder", sortOrder)
        }
    }
}
