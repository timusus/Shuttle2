# Playlist Synchronization Architecture

**Issue:** [#102](https://github.com/timusus/Shuttle2/issues/102) - Sync S2 playlist actions with Jellyfin server
**Author:** Claude
**Date:** 2025-11-16
**Status:** Design Proposal

## Executive Summary

This document outlines an architecturally sound, scalable, and principled approach to implementing bidirectional playlist synchronization between Shuttle2 and media servers (Jellyfin, Emby, Plex). The design follows Clean Architecture principles, maintains backward compatibility, and provides a framework for future enhancements.

**Key Design Principles:**
- **Single Responsibility**: Each component has one clear purpose
- **Dependency Inversion**: High-level policies don't depend on low-level details
- **Interface Segregation**: Clients aren't forced to depend on unused interfaces
- **Open/Closed**: Open for extension, closed for modification
- **Scalability**: Handle multiple servers and thousands of playlists efficiently
- **Resilience**: Graceful degradation and conflict resolution

---

## Table of Contents

1. [Problem Statement](#1-problem-statement)
2. [Current Architecture Analysis](#2-current-architecture-analysis)
3. [Media Server API Capabilities](#3-media-server-api-capabilities)
4. [Proposed Architecture](#4-proposed-architecture)
5. [Implementation Strategy](#5-implementation-strategy)
6. [Conflict Resolution](#6-conflict-resolution)
7. [Migration Path](#7-migration-path)
8. [Testing Strategy](#8-testing-strategy)
9. [Future Considerations](#9-future-considerations)

---

## 1. Problem Statement

### Current Behavior (One-Way Sync)

```
┌──────────────┐                    ┌──────────────┐
│              │   Pull Playlists   │              │
│ Media Server │ ◄────────────────  │   Shuttle2   │
│  (Jellyfin)  │                    │              │
└──────────────┘                    └──────────────┘
```

**Limitations:**
- Playlists imported from media servers are read-only in effect
- Local modifications (add/remove songs, rename, delete) don't sync back
- Creates divergence between Shuttle2 and server state
- Users must manually maintain playlists in multiple places

### Desired Behavior (Bidirectional Sync)

```
┌──────────────┐                    ┌──────────────┐
│              │   Pull Playlists   │              │
│ Media Server │ ◄─────────────────►│   Shuttle2   │
│  (Jellyfin)  │   Push Changes     │              │
└──────────────┘                    └──────────────┘
```

**Requirements:**
1. **Create**: New playlists in Shuttle2 should sync to server
2. **Update**: Song additions/removals should sync bidirectionally
3. **Rename**: Playlist name changes should sync back
4. **Delete**: Playlist deletions should sync (with user confirmation)
5. **Reorder**: Song order changes should sync
6. **Conflict Resolution**: Handle concurrent modifications gracefully
7. **Offline Support**: Queue changes when offline, sync when reconnected

---

## 2. Current Architecture Analysis

### 2.1 Existing Components

#### Data Layer (Domain Models)

```kotlin
// android/data/src/main/kotlin/com/simplecityapps/shuttle/model/Playlist.kt
data class Playlist(
    val id: Long,                              // Local database ID
    val name: String,
    val songCount: Int,
    val duration: Int,
    val sortOrder: PlaylistSongSortOrder,
    val mediaProvider: MediaProviderType,      // Shuttle, Jellyfin, Emby, Plex
    val externalId: String?                    // Remote playlist ID
)

data class PlaylistSong(
    val id: Long,                              // Join table ID
    val sortOrder: Long,                       // Position in playlist
    val song: Song
)
```

**Key Insight**: The architecture already supports tracking playlist source (`mediaProvider`) and remote identity (`externalId`), providing the foundation for bidirectional sync.

#### Repository Pattern

```kotlin
// android/mediaprovider/core/src/main/java/com/simplecityapps/mediaprovider/repository/playlists/PlaylistRepository.kt
interface PlaylistRepository {
    // Queries (Reactive)
    fun getPlaylists(query: PlaylistQuery): Flow<List<Playlist>>
    fun getSongsForPlaylist(playlist: Playlist): Flow<List<PlaylistSong>>

    // Mutations (Suspending)
    suspend fun createPlaylist(name: String, mediaProviderType, songs, externalId): Playlist
    suspend fun addToPlaylist(playlist: Playlist, songs: List<Song>)
    suspend fun removeFromPlaylist(playlist: Playlist, playlistSongs: List<PlaylistSong>)
    suspend fun deletePlaylist(playlist: Playlist)
    suspend fun renamePlaylist(playlist: Playlist, name: String)
    suspend fun updatePlaylistSongsSortOrder(playlist: Playlist, playlistSongs: List<PlaylistSong>)

    // Sync Support
    suspend fun updatePlaylistExternalId(playlist: Playlist, externalId: String?)
    suspend fun updatePlaylistMediaProviderType(playlist: Playlist, mediaProviderType)
}
```

**Key Insight**: All mutation operations are already defined in the repository interface, making them observable points for sync triggers.

#### Media Provider Plugin Architecture

```kotlin
// android/mediaprovider/core/src/main/java/com/simplecityapps/mediaprovider/MediaProvider.kt
interface MediaProvider {
    val type: MediaProviderType

    // Current: One-way sync (pull from server)
    fun findSongs(): Flow<FlowEvent<List<Song>, MessageProgress>>
    fun findPlaylists(existingPlaylists, existingSongs):
        Flow<FlowEvent<List<PlaylistUpdateData>, MessageProgress>>
}
```

**Key Insight**: The plugin architecture is extensible. We can add sync capabilities without breaking existing implementations.

#### Current Import Flow

```kotlin
// android/mediaprovider/core/src/main/java/com/simplecityapps/mediaprovider/MediaImporter.kt
class MediaImporter {
    suspend fun import() {
        // For each media provider:
        //   1. Import songs (with diff/merge)
        //   2. Import playlists (with createOrUpdatePlaylist)
    }

    private suspend fun createOrUpdatePlaylist(
        playlistUpdateData: PlaylistUpdateData,
        existingPlaylists: List<Playlist>
    ) {
        val existingPlaylist = existingPlaylists.find {
            it.mediaProvider == playlistUpdateData.mediaProviderType &&
            (it.name == playlistUpdateData.name || it.externalId == playlistUpdateData.externalId)
        }

        if (existingPlaylist == null) {
            // Create new playlist
            playlistRepository.createPlaylist(...)
        } else {
            // Update existing (currently append-only for songs)
            playlistRepository.renamePlaylist(...)
            playlistRepository.addToPlaylist(...) // Only adds missing songs
        }
    }
}
```

**Key Insights:**
- Import is idempotent and merge-based (not destructive)
- Matching by `externalId` or `name`
- Currently doesn't detect deletions from server

### 2.2 Architecture Strengths

✅ **Clean Architecture**: Clear separation of concerns (domain, data, presentation)
✅ **Repository Pattern**: Centralized data access with reactive streams
✅ **Plugin System**: MediaProvider abstraction allows server-specific implementations
✅ **Reactive**: Flow-based architecture for real-time updates
✅ **Offline-First**: Room database provides local persistence
✅ **Type Safety**: Kotlin coroutines and sealed classes for error handling

### 2.3 Architecture Gaps for Bidirectional Sync

❌ **No Change Tracking**: No mechanism to track local modifications to remote playlists
❌ **No Sync Queue**: No persistent queue for pending sync operations
❌ **No Conflict Resolution**: No strategy for handling concurrent modifications
❌ **No Write API**: MediaProvider only defines read operations
❌ **No Sync State**: No tracking of sync status (pending, syncing, failed, synced)
❌ **No Event Bus**: Repository mutations don't trigger sync operations

---

## 3. Media Server API Capabilities

### 3.1 Jellyfin API

**Base URL**: `https://{server}/api`
**Authentication**: `X-Emby-Token` header

#### Playlist Operations

| Operation | Method | Endpoint | Notes |
|-----------|--------|----------|-------|
| **List Playlists** | GET | `/Users/{userId}/Items?includeItemTypes=Playlist` | ✅ Currently implemented |
| **Get Playlist Items** | GET | `/Playlists/{playlistId}/Items` | ✅ Currently implemented |
| **Create Playlist** | POST | `/Playlists` | ⚠️ Not implemented |
| **Add Items** | POST | `/Playlists/{playlistId}/Items?ids={itemIds}` | ⚠️ Not implemented |
| **Remove Items** | DELETE | `/Playlists/{playlistId}/Items?entryIds={entryIds}` | ⚠️ Known issues (see below) |
| **Move Item** | POST | `/Playlists/{playlistId}/Items/{itemId}/Move/{newIndex}` | ⚠️ Fixed in 2025 |
| **Update Playlist** | POST | `/Items/{playlistId}` | ⚠️ For name/metadata |
| **Delete Playlist** | DELETE | `/Items/{playlistId}` | ⚠️ Not implemented |

**Known Issues**:
- **Delete Items Bug**: Some versions have issues removing items (GitHub #2130, #9008, #13476)
  - Workaround: May need to recreate playlist instead of modifying
- **API Key Permissions**: API keys may not work for deletion, requires user token
- **Entry IDs vs Item IDs**: Removal requires `entryIds` (playlist-specific), not `itemIds`

**Request Examples**:

```http
# Create Playlist
POST /Playlists
Content-Type: application/json
X-Emby-Token: {token}

{
  "Name": "My Playlist",
  "UserId": "{userId}",
  "MediaType": "Audio",
  "Ids": ["itemId1", "itemId2"]  // Optional: initial items
}

# Add Items
POST /Playlists/{playlistId}/Items?ids=itemId1,itemId2&userId={userId}
X-Emby-Token: {token}

# Remove Items (requires entryIds from playlist items response)
DELETE /Playlists/{playlistId}/Items?entryIds=playlistEntryId1,playlistEntryId2
X-Emby-Token: {token}
```

### 3.2 Emby API

**Base URL**: `https://{server}/emby`
**Authentication**: `X-Emby-Token` header

#### Playlist Operations

| Operation | Method | Endpoint | Notes |
|-----------|--------|----------|-------|
| **List Playlists** | GET | `/Users/{userId}/Items?includeItemTypes=Playlist` | ✅ Similar to Jellyfin |
| **Get Playlist Items** | GET | `/Playlists/{playlistId}/Items` | ✅ Returns PlaylistItemId |
| **Create Playlist** | POST | `/Playlists` | ✅ Well documented |
| **Add Items** | POST | `/Playlists/{playlistId}/Items?ids={itemIds}` | ✅ Comma-delimited IDs |
| **Remove Items** | DELETE | `/Playlists/{playlistId}/Items?entryIds={playlistItemIds}` | ✅ Requires PlaylistItemId |
| **Update Playlist** | POST | `/Items/{playlistId}` | ✅ For metadata |
| **Delete Playlist** | DELETE | `/Items/{playlistId}` | ✅ Standard |

**Key Differences from Jellyfin**:
- More stable playlist modification APIs
- Better documentation for `PlaylistItemId` requirement
- Similar overall structure (Emby is Jellyfin's predecessor)

### 3.3 Plex API

**Base URL**: `https://{server}:32400`
**Authentication**: `X-Plex-Token` header or query param

#### Playlist Operations

| Operation | Method | Endpoint | Notes |
|-----------|--------|----------|-------|
| **List Playlists** | GET | `/playlists` | ✅ Returns playlist metadata |
| **Get Playlist Items** | GET | `/playlists/{playlistId}/items` | ✅ Returns ratingKeys |
| **Create Playlist** | POST | `/playlists?type={type}&title={title}&smart={smart}&uri={uri}` | ✅ Supports smart playlists |
| **Add Items** | PUT | `/playlists/{playlistId}/items?uri={serverUri}` | ✅ Server URI format |
| **Remove Items** | DELETE | `/playlists/{playlistId}/items/{playlistItemId}` | ✅ One at a time |
| **Move Item** | PUT | `/playlists/{playlistId}/items/{itemId}/move?after={afterItemId}` | ✅ Supports reordering |
| **Update Playlist** | PUT | `/playlists/{playlistId}?title={newTitle}` | ✅ For metadata |
| **Delete Playlist** | DELETE | `/playlists/{playlistId}` | ✅ Standard |

**Key Differences**:
- URI-based item references: `server://{machineId}/com.plexapp.plugins.library/library/metadata/{ratingKey}`
- More granular operations (remove one item at a time)
- Support for smart playlists (query-based)
- Different authentication approach

### 3.4 API Comparison Summary

| Feature | Jellyfin | Emby | Plex |
|---------|----------|------|------|
| **Create Playlist** | ✅ | ✅ | ✅ |
| **Add Items (Batch)** | ✅ | ✅ | ✅ |
| **Remove Items (Batch)** | ⚠️ Buggy | ✅ | ❌ (one at a time) |
| **Reorder Items** | ✅ (2025) | ❓ | ✅ |
| **Update Metadata** | ✅ | ✅ | ✅ |
| **Delete Playlist** | ✅ | ✅ | ✅ |
| **API Stability** | ⚠️ Some bugs | ✅ Stable | ✅ Stable |
| **Documentation** | ⚠️ Medium | ✅ Good | ✅ Excellent |

**Recommendation**: Start with Jellyfin (most requested), then Emby (most stable), then Plex.

---

## 4. Proposed Architecture

### 4.1 Core Components

```
┌─────────────────────────────────────────────────────────────────┐
│                        Presentation Layer                       │
│  ┌──────────────────┐          ┌──────────────────┐            │
│  │ PlaylistPresenter│──────────│ SyncStatusUI     │            │
│  └──────────────────┘          └──────────────────┘            │
└─────────────────────────────────────────────────────────────────┘
                            ↓ ↓ ↓
┌─────────────────────────────────────────────────────────────────┐
│                        Domain Layer (NEW)                       │
│  ┌────────────────────────────────────────────────────────┐    │
│  │              PlaylistSyncCoordinator                   │    │
│  │  - Observes repository changes                         │    │
│  │  - Queues sync operations                              │    │
│  │  - Handles conflict resolution                         │    │
│  │  - Manages sync lifecycle                              │    │
│  └────────────────────────────────────────────────────────┘    │
│               ↓                              ↓                  │
│  ┌──────────────────────┐      ┌──────────────────────────┐   │
│  │  SyncQueueRepository │      │  PlaylistSyncStrategy    │   │
│  │  - Persist operations│      │  - Diff algorithm        │   │
│  │  - Priority queue    │      │  - Conflict rules        │   │
│  └──────────────────────┘      └──────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                            ↓ ↓ ↓
┌─────────────────────────────────────────────────────────────────┐
│                     Data Layer (EXTENDED)                       │
│  ┌────────────────────────────────────────────────────────┐    │
│  │              PlaylistRepository (existing)             │    │
│  │  + Change tracking extension                           │    │
│  └────────────────────────────────────────────────────────┘    │
│               ↓                              ↓                  │
│  ┌──────────────────────┐      ┌──────────────────────────┐   │
│  │ Room Database        │      │ MediaProvider (extended) │   │
│  │ + SyncOperation      │      │ + PlaylistSyncProvider   │   │
│  │ + SyncState          │      │   - pushPlaylist()       │   │
│  └──────────────────────┘      │   - deletePlaylist()     │   │
│                                 │   - addItems()           │   │
│                                 │   - removeItems()        │   │
│                                 └──────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                            ↓ ↓ ↓
┌─────────────────────────────────────────────────────────────────┐
│                        Network Layer                            │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐ │
│  │JellyfinService   │  │EmbyService       │  │PlexService   │ │
│  │+ playlist APIs   │  │+ playlist APIs   │  │+ playlist    │ │
│  └──────────────────┘  └──────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 New Domain Models

#### SyncOperation (Room Entity)

```kotlin
// android/mediaprovider/local/src/main/java/com/simplecityapps/localmediaprovider/local/data/room/entity/SyncOperation.kt

@Entity(
    tableName = "sync_operations",
    indices = [
        Index("playlist_id"),
        Index("status"),
        Index("priority", "created_at")
    ]
)
data class SyncOperation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val playlistId: Long,              // Local playlist ID
    val externalId: String?,           // Remote playlist ID
    val mediaProviderType: MediaProviderType,

    @Embedded
    val operation: Operation,          // Sealed class defining the operation

    val status: SyncStatus,
    val priority: Int,                 // Higher = more urgent

    val createdAt: Instant,
    val lastAttemptAt: Instant?,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,

    val errorMessage: String?
)

sealed class Operation {
    data class CreatePlaylist(val name: String, val songIds: List<Long>) : Operation()
    data class UpdateMetadata(val name: String) : Operation()
    data class AddSongs(val songIds: List<Long>) : Operation()
    data class RemoveSongs(val songIds: List<Long>) : Operation()
    data class ReorderSongs(val playlistSongIds: List<Long>) : Operation()  // Ordered list
    object DeletePlaylist : Operation()
}

enum class SyncStatus {
    PENDING,      // Waiting to be processed
    IN_PROGRESS,  // Currently syncing
    FAILED,       // Failed after retries
    COMPLETED,    // Successfully synced
    CANCELLED     // User cancelled
}
```

#### PlaylistSyncState (Room Entity)

```kotlin
// Tracks the sync state of each playlist
@Entity(tableName = "playlist_sync_state")
data class PlaylistSyncState(
    @PrimaryKey
    val playlistId: Long,

    val lastSyncedAt: Instant?,
    val localModifiedAt: Instant,
    val remoteModifiedAt: Instant?,   // From server "LastModified" field

    val syncStatus: PlaylistSyncStatus,
    val conflictDetected: Boolean = false,

    // Hash of playlist content for change detection
    val localContentHash: String,
    val remoteContentHash: String?
)

enum class PlaylistSyncStatus {
    SYNCED,       // Local and remote are in sync
    LOCAL_AHEAD,  // Local has changes not pushed
    REMOTE_AHEAD, // Remote has changes not pulled
    CONFLICT,     // Both have diverged
    ERROR         // Sync error
}
```

### 4.3 Extended Interfaces

#### PlaylistSyncProvider (New)

```kotlin
// android/mediaprovider/core/src/main/java/com/simplecityapps/mediaprovider/sync/PlaylistSyncProvider.kt

interface PlaylistSyncProvider {
    val type: MediaProviderType

    /**
     * Create a new playlist on the remote server
     * @return externalId of created playlist
     */
    suspend fun createPlaylist(
        name: String,
        songExternalIds: List<String>
    ): NetworkResult<String>

    /**
     * Update playlist metadata
     */
    suspend fun updatePlaylistMetadata(
        externalId: String,
        name: String
    ): NetworkResult<Unit>

    /**
     * Add songs to an existing playlist
     * @param externalId Remote playlist ID
     * @param songExternalIds Remote song IDs to add
     */
    suspend fun addSongsToPlaylist(
        externalId: String,
        songExternalIds: List<String>
    ): NetworkResult<Unit>

    /**
     * Remove songs from a playlist
     * @param externalId Remote playlist ID
     * @param playlistItemIds Server-specific item IDs (NOT song IDs)
     */
    suspend fun removeSongsFromPlaylist(
        externalId: String,
        playlistItemIds: List<String>
    ): NetworkResult<Unit>

    /**
     * Reorder songs in a playlist
     * @param externalId Remote playlist ID
     * @param orderedPlaylistItemIds Server-specific item IDs in new order
     */
    suspend fun reorderPlaylistSongs(
        externalId: String,
        orderedSongExternalIds: List<String>
    ): NetworkResult<Unit>

    /**
     * Delete a playlist from the remote server
     */
    suspend fun deletePlaylist(externalId: String): NetworkResult<Unit>

    /**
     * Get the current state of a playlist for conflict detection
     * @return Pair of (songExternalIds, lastModified timestamp)
     */
    suspend fun getPlaylistState(
        externalId: String
    ): NetworkResult<PlaylistRemoteState>
}

data class PlaylistRemoteState(
    val externalId: String,
    val name: String,
    val songExternalIds: List<String>,  // Ordered
    val lastModified: Instant
)
```

#### PlaylistSyncCoordinator (New)

```kotlin
// android/mediaprovider/core/src/main/java/com/simplecityapps/mediaprovider/sync/PlaylistSyncCoordinator.kt

class PlaylistSyncCoordinator(
    private val playlistRepository: PlaylistRepository,
    private val syncQueueRepository: SyncQueueRepository,
    private val syncProviders: Map<MediaProviderType, PlaylistSyncProvider>,
    private val syncStrategy: PlaylistSyncStrategy,
    @AppCoroutineScope private val scope: CoroutineScope
) {
    /**
     * Observe playlist changes and queue sync operations
     */
    fun observeChanges() {
        // Implementation will use interceptor pattern
    }

    /**
     * Process the sync queue
     */
    suspend fun processQueue() {
        val pendingOps = syncQueueRepository.getPendingOperations()
            .sortedBy { it.priority }

        pendingOps.forEach { operation ->
            processSyncOperation(operation)
        }
    }

    /**
     * Force sync a specific playlist
     */
    suspend fun syncPlaylist(playlist: Playlist): SyncResult {
        // Check for conflicts first
        // Apply sync strategy
        // Execute operations
    }

    /**
     * Resolve a conflict for a playlist
     */
    suspend fun resolveConflict(
        playlist: Playlist,
        resolution: ConflictResolution
    ): SyncResult

    private suspend fun processSyncOperation(operation: SyncOperation): SyncResult
}
```

### 4.4 Data Flow Diagrams

#### Create Playlist Flow

```
User creates playlist in UI
        ↓
PlaylistPresenter.createPlaylist()
        ↓
PlaylistRepository.createPlaylist(mediaProviderType = MediaProviderType.Jellyfin)
        ↓
Room: Insert PlaylistData + PlaylistSongJoin
        ↓
[TRIGGER] PlaylistChangeInterceptor detects insert
        ↓
SyncCoordinator.onPlaylistCreated()
        ↓
SyncQueueRepository.enqueue(
    operation = Operation.CreatePlaylist(name, songIds),
    priority = HIGH
)
        ↓
[Background Worker] SyncCoordinator.processQueue()
        ↓
JellyfinSyncProvider.createPlaylist(name, songExternalIds)
        ↓
Retrofit: POST /Playlists
        ↓
[Success] Update playlist.externalId, mark operation COMPLETED
[Failure] Mark operation FAILED, schedule retry
```

#### Add Songs Flow (with conflict detection)

```
User adds song to remote playlist
        ↓
PlaylistRepository.addToPlaylist(playlist, songs)
        ↓
Room: Insert PlaylistSongJoin
        ↓
SyncCoordinator: Check if playlist.externalId exists
        ↓
[YES] Enqueue Operation.AddSongs(songIds)
        ↓
SyncWorker: Check for remote changes first
        ↓
JellyfinSyncProvider.getPlaylistState(externalId)
        ↓
Compare remoteContentHash with local
        ↓
[CONFLICT DETECTED]
    ↓
    Mark playlist as CONFLICT
    ↓
    Show conflict resolution UI
    ↓
    User chooses: LOCAL_WINS / REMOTE_WINS / MERGE
        ↓
        Apply resolution strategy
        ↓
        Continue sync

[NO CONFLICT]
    ↓
    JellyfinSyncProvider.addSongsToPlaylist(externalId, songExternalIds)
    ↓
    Update remoteContentHash
    ↓
    Mark COMPLETED
```

---

## 5. Implementation Strategy

### 5.1 Phased Approach

#### Phase 1: Foundation (Week 1-2)

**Goal**: Establish sync infrastructure without breaking existing functionality

- [ ] Create database schema for sync operations and state
  - Migration to add `sync_operations` table
  - Migration to add `playlist_sync_state` table

- [ ] Define `PlaylistSyncProvider` interface

- [ ] Implement `SyncQueueRepository`
  - CRUD operations for sync queue
  - Priority-based retrieval

- [ ] Create `SyncOperation` and `PlaylistSyncState` Room entities

- [ ] Set up dependency injection for sync components

**Success Criteria**: Schema created, tests pass, no impact on existing features

#### Phase 2: Jellyfin Write APIs (Week 3-4)

**Goal**: Implement Jellyfin-specific sync operations

- [ ] Create `JellyfinPlaylistService.kt` with write APIs
  ```kotlin
  interface JellyfinPlaylistService {
      @POST("Playlists")
      suspend fun createPlaylist(@Body request: CreatePlaylistRequest): NetworkResult<CreatePlaylistResponse>

      @POST("Playlists/{playlistId}/Items")
      suspend fun addItems(
          @Path("playlistId") playlistId: String,
          @Query("ids") itemIds: String,  // Comma-separated
          @Query("userId") userId: String
      ): NetworkResult<Unit>

      @HTTP(method = "DELETE", path = "Playlists/{playlistId}/Items", hasBody = false)
      suspend fun removeItems(
          @Path("playlistId") playlistId: String,
          @Query("entryIds") entryIds: String  // Comma-separated
      ): NetworkResult<Unit>

      @POST("Playlists/{playlistId}/Items/{itemId}/Move/{newIndex}")
      suspend fun moveItem(/*...*/): NetworkResult<Unit>

      @POST("Items/{playlistId}")
      suspend fun updateMetadata(@Body request: UpdateRequest): NetworkResult<Unit>

      @DELETE("Items/{playlistId}")
      suspend fun deletePlaylist(@Path("playlistId") playlistId: String): NetworkResult<Unit>
  }
  ```

- [ ] Implement `JellyfinPlaylistSyncProvider`
  - Implement all `PlaylistSyncProvider` methods
  - Handle Jellyfin-specific quirks (entry IDs vs item IDs)
  - Workaround for deletion bug (fallback to recreate)

- [ ] Add integration tests with mock server

**Success Criteria**: All Jellyfin write operations work in isolation

#### Phase 3: Change Tracking (Week 5)

**Goal**: Detect when playlists are modified locally

**Approach**: Repository Interceptor Pattern

```kotlin
class SyncAwarePlaylistRepository(
    private val delegate: PlaylistRepository,
    private val syncCoordinator: PlaylistSyncCoordinator
) : PlaylistRepository by delegate {

    override suspend fun createPlaylist(
        name: String,
        mediaProviderType: MediaProviderType,
        songs: List<Song>?,
        externalId: String?
    ): Playlist {
        val playlist = delegate.createPlaylist(name, mediaProviderType, songs, externalId)

        // Only sync if it's a remote playlist without externalId yet
        if (mediaProviderType.isRemote() && externalId == null) {
            syncCoordinator.onPlaylistCreated(playlist)
        }

        return playlist
    }

    override suspend fun addToPlaylist(playlist: Playlist, songs: List<Song>) {
        delegate.addToPlaylist(playlist, songs)

        if (playlist.shouldSync()) {
            syncCoordinator.onSongsAdded(playlist, songs)
        }
    }

    // Similar for remove, delete, rename, reorder...
}
```

Tasks:
- [ ] Create `SyncAwarePlaylistRepository` wrapper
- [ ] Implement change detection for all mutation operations
- [ ] Wire up via Hilt dependency injection
- [ ] Add feature flag for gradual rollout

**Success Criteria**: Changes are detected and queued (but not processed yet)

#### Phase 4: Sync Worker (Week 6)

**Goal**: Process sync queue in background

- [ ] Implement `PlaylistSyncCoordinator.processQueue()`
  - Retrieve pending operations
  - Execute via appropriate `PlaylistSyncProvider`
  - Update operation status
  - Handle failures and retries

- [ ] Create WorkManager job for periodic sync
  ```kotlin
  class PlaylistSyncWorker(
      context: Context,
      params: WorkerParameters
  ) : CoroutineWorker(context, params) {
      override suspend fun doWork(): Result {
          return try {
              syncCoordinator.processQueue()
              Result.success()
          } catch (e: Exception) {
              if (runAttemptCount < 3) Result.retry() else Result.failure()
          }
      }
  }
  ```

- [ ] Set up constraints (network required, battery not low)

- [ ] Add manual "Sync Now" button in settings

**Success Criteria**: Sync operations execute successfully in background

#### Phase 5: Conflict Detection (Week 7)

**Goal**: Handle concurrent modifications gracefully

- [ ] Implement content hashing for playlists
  ```kotlin
  fun Playlist.calculateContentHash(songs: List<PlaylistSong>): String {
      val content = buildString {
          append(name)
          songs.forEach { append(it.song.externalId).append(it.sortOrder) }
      }
      return content.sha256()
  }
  ```

- [ ] Before each sync operation, fetch remote state

- [ ] Compare hashes to detect conflicts

- [ ] Implement `PlaylistSyncStrategy` with conflict resolution rules:
  ```kotlin
  enum class ConflictResolution {
      LOCAL_WINS,    // Overwrite remote with local
      REMOTE_WINS,   // Overwrite local with remote
      MERGE,         // Intelligent merge (union of songs)
      MANUAL         // Ask user
  }
  ```

**Success Criteria**: Conflicts are detected and handled according to user preference

#### Phase 6: UI Integration (Week 8)

**Goal**: Expose sync status to users

- [ ] Add sync status indicators to playlist UI
  - Synced icon (cloud with checkmark)
  - Syncing icon (cloud with arrows)
  - Conflict icon (cloud with warning)
  - Failed icon (cloud with X)

- [ ] Create conflict resolution dialog

- [ ] Add "Sync Status" screen in settings
  - List of pending operations
  - Failed operations with retry option
  - Sync statistics

- [ ] Add sync logs for debugging

**Success Criteria**: Users can see sync status and resolve conflicts

#### Phase 7: Emby & Plex (Week 9-10)

**Goal**: Extend to other media servers

- [ ] Implement `EmbyPlaylistSyncProvider`
  - Similar structure to Jellyfin
  - Handle `PlaylistItemId` correctly

- [ ] Implement `PlexPlaylistSyncProvider`
  - Handle URI-based item references
  - One-at-a-time removal

- [ ] Add server-specific settings (enable/disable sync per server)

**Success Criteria**: All three servers support bidirectional sync

#### Phase 8: Polish & Optimization (Week 11-12)

- [ ] Batch operations where possible
  - Coalesce multiple adds into one API call
  - Debounce rapid changes

- [ ] Add analytics (non-PII)
  - Sync success rate
  - Common failure reasons
  - Performance metrics

- [ ] Performance optimization
  - Reduce database queries
  - Parallelize independent syncs

- [ ] Comprehensive error handling
  - Network timeouts
  - Authentication failures
  - Rate limiting

- [ ] Documentation
  - User guide
  - Developer documentation
  - API reference

**Success Criteria**: Production-ready with <1% failure rate

### 5.2 Feature Flags

Use feature flags for gradual rollout:

```kotlin
object SyncFeatureFlags {
    const val ENABLE_SYNC = "enable_playlist_sync"
    const val ENABLE_JELLYFIN_SYNC = "enable_jellyfin_playlist_sync"
    const val ENABLE_EMBY_SYNC = "enable_emby_playlist_sync"
    const val ENABLE_PLEX_SYNC = "enable_plex_playlist_sync"
    const val ENABLE_AUTO_CONFLICT_RESOLUTION = "enable_auto_conflict_resolution"
}
```

### 5.3 Rollback Strategy

If critical issues arise:

1. Disable feature flag → stops all sync operations
2. Sync queue remains intact → can resume later
3. Local database unchanged → no data loss
4. Existing one-way sync still works → fallback behavior

---

## 6. Conflict Resolution

### 6.1 Conflict Scenarios

| Scenario | Local State | Remote State | Detection |
|----------|-------------|--------------|-----------|
| **Concurrent Add** | Added song A | Added song B | Hash mismatch |
| **Concurrent Remove** | Removed song A | Removed song B | Hash mismatch |
| **Add vs Remove** | Added song A | Removed song A | Item-level conflict |
| **Concurrent Rename** | "Rock Hits" | "Best Rock" | Name mismatch |
| **Reorder** | Order: A,B,C | Order: C,B,A | Order mismatch |

### 6.2 Resolution Strategies

#### Strategy 1: Last-Write-Wins (Timestamp-based)

```kotlin
fun resolveByTimestamp(
    localModified: Instant,
    remoteModified: Instant,
    localState: PlaylistState,
    remoteState: PlaylistState
): PlaylistState {
    return if (localModified > remoteModified) {
        localState  // Push local to remote
    } else {
        remoteState  // Pull remote to local
    }
}
```

**Pros**: Simple, deterministic
**Cons**: May lose data (one side's changes discarded)

#### Strategy 2: Union Merge (Additive)

```kotlin
fun resolveByUnion(
    localSongs: List<Song>,
    remoteSongs: List<Song>
): List<Song> {
    val combined = (localSongs + remoteSongs)
        .distinctBy { it.externalId }
        .sortedBy { /* preserve order from local or remote */ }

    return combined
}
```

**Pros**: No data loss
**Cons**: May create duplicates, unclear ordering

#### Strategy 3: User Choice (Manual)

```kotlin
sealed class ConflictResolutionChoice {
    object KeepLocal : ConflictResolutionChoice()
    object KeepRemote : ConflictResolutionChoice()
    data class Custom(val songs: List<Song>) : ConflictResolutionChoice()
}
```

**Pros**: User control
**Cons**: Requires user intervention, may block sync

### 6.3 Recommended Strategy

**Hybrid Approach**:

1. **Auto-resolve safe conflicts**:
   - Union merge for non-overlapping adds
   - Remote wins for metadata (name) if local unchanged

2. **Flag complex conflicts for user**:
   - Overlapping removes
   - Concurrent renames
   - Reorder conflicts

3. **Provide user preferences**:
   ```kotlin
   enum class ConflictPreference {
       ALWAYS_LOCAL,
       ALWAYS_REMOTE,
       MERGE_AUTO,
       ASK_ME
   }
   ```

---

## 7. Migration Path

### 7.1 Database Migrations

```kotlin
// Migration 1: Add sync tables
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_operations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                playlist_id INTEGER NOT NULL,
                external_id TEXT,
                media_provider_type TEXT NOT NULL,
                operation_type TEXT NOT NULL,
                operation_data TEXT NOT NULL,
                status TEXT NOT NULL,
                priority INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                last_attempt_at INTEGER,
                retry_count INTEGER NOT NULL DEFAULT 0,
                max_retries INTEGER NOT NULL DEFAULT 3,
                error_message TEXT,
                FOREIGN KEY(playlist_id) REFERENCES playlist_data(id) ON DELETE CASCADE
            )
        """)

        database.execSQL("""
            CREATE INDEX idx_sync_operations_playlist_id
            ON sync_operations(playlist_id)
        """)

        database.execSQL("""
            CREATE INDEX idx_sync_operations_status
            ON sync_operations(status)
        """)

        database.execSQL("""
            CREATE TABLE IF NOT EXISTS playlist_sync_state (
                playlist_id INTEGER PRIMARY KEY NOT NULL,
                last_synced_at INTEGER,
                local_modified_at INTEGER NOT NULL,
                remote_modified_at INTEGER,
                sync_status TEXT NOT NULL,
                conflict_detected INTEGER NOT NULL DEFAULT 0,
                local_content_hash TEXT NOT NULL,
                remote_content_hash TEXT,
                FOREIGN KEY(playlist_id) REFERENCES playlist_data(id) ON DELETE CASCADE
            )
        """)
    }
}
```

### 7.2 Backward Compatibility

- Existing playlists continue to work as-is
- Sync is opt-in (feature flag + user settings)
- No breaking changes to existing repository interface
- Graceful degradation if server doesn't support an operation

### 7.3 Data Integrity

- Use database transactions for atomic operations
- Cascade deletes for cleanup
- Validate external IDs before sync
- Handle orphaned sync operations (playlist deleted locally)

---

## 8. Testing Strategy

### 8.1 Unit Tests

- [ ] `PlaylistSyncCoordinator` logic
- [ ] `SyncQueueRepository` CRUD operations
- [ ] Conflict resolution algorithms
- [ ] Content hashing

### 8.2 Integration Tests

- [ ] Jellyfin API interactions (MockWebServer)
- [ ] Emby API interactions
- [ ] Plex API interactions
- [ ] Database migrations
- [ ] Repository interceptor

### 8.3 End-to-End Tests

- [ ] Create playlist → syncs to server
- [ ] Add song → syncs incrementally
- [ ] Delete playlist → syncs deletion
- [ ] Conflict scenario → resolves correctly
- [ ] Offline → online → sync resumes

### 8.4 Manual Testing Checklist

- [ ] Create playlist with 100+ songs
- [ ] Modify same playlist from web UI and app simultaneously
- [ ] Delete playlist from server, ensure app updates
- [ ] Network interruption during sync
- [ ] Authentication expiration during sync
- [ ] Multiple servers active simultaneously

---

## 9. Future Considerations

### 9.1 Performance Optimizations

- **Incremental Sync**: Only sync changed portions, not entire playlist
- **Compression**: Compress large sync payloads
- **Caching**: Cache remote playlist states to reduce API calls
- **Batching**: Group multiple operations into single API call where supported

### 9.2 Advanced Features

- **Real-time Sync**: WebSocket-based push notifications from server
- **Collaborative Playlists**: Multiple users editing same playlist
- **Sync Profiles**: Different sync strategies per playlist
- **Selective Sync**: Choose which playlists to sync
- **Sync History**: Audit log of all sync operations

### 9.3 Scalability

- **Pagination**: Handle playlists with 10,000+ songs
- **Rate Limiting**: Respect server API limits
- **Priority Queues**: User-initiated syncs take precedence
- **Background Sync**: Use WorkManager for efficient battery usage

### 9.4 Observability

- **Metrics**: Track sync latency, success rate, conflict rate
- **Logging**: Structured logs for debugging
- **Alerts**: Notify developers of high failure rates
- **Dashboards**: Visualize sync health

---

## 10. Conclusion

This architecture provides a **solid foundation** for bidirectional playlist synchronization that is:

✅ **Architecturally Sound**: Follows Clean Architecture and SOLID principles
✅ **Scalable**: Handles multiple servers, thousands of playlists
✅ **Resilient**: Graceful error handling, conflict resolution, offline support
✅ **Maintainable**: Clear separation of concerns, testable components
✅ **Extensible**: Easy to add new media servers or sync strategies
✅ **User-Friendly**: Transparent sync status, minimal user intervention

### Key Design Decisions

1. **Repository Interceptor Pattern**: Non-invasive change tracking
2. **Persistent Sync Queue**: Reliable operation across app restarts
3. **Provider Abstraction**: Server-agnostic sync logic
4. **Conflict Detection via Hashing**: Efficient state comparison
5. **Phased Rollout**: Low-risk incremental deployment

### Next Steps

1. Review this document with stakeholders
2. Get approval on architecture
3. Create tickets for Phase 1 tasks
4. Begin implementation

### Open Questions

1. **User Preference**: Default conflict resolution strategy?
2. **Sync Frequency**: How often to run background sync? (15 min, 1 hour, manual only?)
3. **Scope**: Should smart playlists sync? (Read-only on servers typically)
4. **UX**: Should sync be opt-in per playlist or global setting?

---

**Document Version**: 1.0
**Last Updated**: 2025-11-16
**Review Status**: Pending Approval
**Estimated Effort**: 12 weeks (1 developer)
**Priority**: High (community requested feature)
