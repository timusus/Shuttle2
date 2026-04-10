# iTunes/Apple Music Sync Implementation

## Overview

This document describes the implementation of Phase 1 of iTunes/Apple Music sync support for Shuttle2, addressing [GitHub Issue #107](https://github.com/timusus/Shuttle2/issues/107).

## Implementation Status: Phase 1 Complete ✓

### What Was Implemented

#### 1. Star Rating Support ✓

**Database Layer:**
- Added `rating` column to `songs` table (INT, 0-5 scale, default 0)
- Created database migration `MIGRATION_40_41` to add the rating column
- Updated `MediaDatabase` version from 40 to 41
- Files modified:
  - `android/data/src/main/kotlin/com/simplecityapps/shuttle/model/Song.kt`
  - `android/mediaprovider/local/src/main/java/com/simplecityapps/localmediaprovider/local/data/room/entity/SongData.kt`
  - `android/mediaprovider/local/src/main/java/com/simplecityapps/localmediaprovider/local/data/room/database/MediaDatabase.kt`
  - `android/mediaprovider/local/src/main/java/com/simplecityapps/localmediaprovider/local/data/room/migrations/MIGRATION_40_41.kt` (new file)

**Repository Layer:**
- Added `setRating(song: Song, rating: Int)` method to `SongRepository` interface
- Implemented rating update in `LocalSongRepository`
- Added `updateRating(id: Long, rating: Int)` DAO method
- Files modified:
  - `android/mediaprovider/core/src/main/java/com/simplecityapps/mediaprovider/repository/songs/SongRepository.kt`
  - `android/mediaprovider/local/src/main/java/com/simplecityapps/localmediaprovider/local/repository/LocalSongRepository.kt`
  - `android/mediaprovider/local/src/main/java/com/simplecityapps/localmediaprovider/local/data/room/dao/SongDataDao.kt`

**UI Layer:**
- Added rating display to Song Info dialog
- Shows visual star rating (★★★☆☆) with numeric value
- Displays "Not rated" for unrated songs
- Files modified:
  - `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/songinfo/SongInfoDialogFragment.kt`
  - `android/app/src/main/res/values/strings_song_info.xml`

#### 2. MediaStore Rating Import ✓

- Reads existing ratings from Android's MediaStore database
- Converts MediaStore ratings (0-100 scale) to Shuttle's 0-5 star scale
- Automatic import when scanning media library
- Files modified:
  - `android/mediaprovider/local/src/main/java/com/simplecityapps/localmediaprovider/local/provider/mediastore/MediaStoreMediaProvider.kt`

**Conversion Scale:**
```kotlin
MediaStore (0-100) -> Shuttle (0-5)
0           -> 0 stars (unrated)
1-20        -> 1 star
21-40       -> 2 stars
41-60       -> 3 stars
61-80       -> 4 stars
81-100      -> 5 stars
```

#### 3. M3U Playlist Export ✓

- Created `PlaylistExporter` utility class
- Supports extended M3U format with metadata
- Compatible with iTunes playlist import
- Supports both absolute and relative file paths
- Files created:
  - `android/mediaprovider/core/src/main/java/com/simplecityapps/mediaprovider/PlaylistExporter.kt`

**M3U Format:**
```m3u
#EXTM3U
#PLAYLIST:My Playlist

#EXTINF:245,Artist Name - Song Title
/path/to/song1.mp3
#EXTINF:180,Another Artist - Another Song
/path/to/song2.mp3
```

**Usage Example:**
```kotlin
val exporter = PlaylistExporter(playlistRepository)
val outputFile = File("/storage/emulated/0/Music/MyPlaylist.m3u")
exporter.exportToM3U(playlist, outputFile)
```

### What's NOT Yet Implemented (Future Phases)

#### Phase 2: Rating Edit UI (Pending)

**Needed:**
- Interactive rating widget (tap to rate)
- Add rating option to song context menus
- Quick rating from Now Playing screen
- Batch rating for multiple songs

**Suggested Locations:**
- Song detail/info dialog (make rating tappable)
- Long-press menu on song items
- Now Playing screen toolbar/overflow menu
- Multi-select mode in song lists

#### Phase 3: Playlist Export UI (Pending)

**Needed:**
- Export button in playlist detail screen
- File picker for choosing export location
- Progress indicator for export
- Success/error notifications
- Share playlist via M3U file

#### Phase 4: Desktop Sync Application (Major Future Work)

This is the core iTunes sync functionality mentioned in the original issue. It would require:

**Desktop Application:**
- Cross-platform desktop app (Windows/macOS/Linux)
- USB device detection and communication
- Apple Music/iTunes library integration
- Bidirectional sync protocol
- Rating synchronization
- Playlist synchronization

**Android Side:**
- USB connection handling
- Sync service for background operations
- Conflict resolution for ratings/playlists
- Sync status UI

**Technologies to Consider:**
- Desktop: Electron, Qt, or native Swift/C++
- Communication: ADB, MTP, or custom USB protocol
- Apple Music API: AppleScript (macOS) or iTunes COM (Windows)
- Data format: JSON or Protocol Buffers for sync data

#### Phase 5: Apple Music API Integration (Future)

**For Full Cloud Sync:**
- Apple ID authentication
- iCloud Music Library access
- CloudKit integration
- OAuth token management
- Network sync instead of USB-only

## Database Schema

### Song Table (Updated)

```sql
CREATE TABLE songs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT,
    track INTEGER,
    disc INTEGER,
    duration INTEGER NOT NULL,
    year INTEGER,
    genres TEXT NOT NULL,
    path TEXT NOT NULL,
    albumArtist TEXT,
    artists TEXT NOT NULL,
    album TEXT,
    size INTEGER NOT NULL,
    mimeType TEXT NOT NULL,
    lastModified INTEGER NOT NULL,
    playbackPosition INTEGER NOT NULL,
    playCount INTEGER NOT NULL,
    rating INTEGER NOT NULL DEFAULT 0,  -- NEW FIELD
    lastPlayed INTEGER,
    lastCompleted INTEGER,
    blacklisted INTEGER NOT NULL,
    externalId TEXT,
    mediaProvider TEXT NOT NULL,
    replayGainTrack REAL,
    replayGainAlbum REAL,
    lyrics TEXT,
    grouping TEXT,
    bitRate INTEGER,
    bitDepth INTEGER,
    sampleRate INTEGER,
    channelCount INTEGER
);

CREATE UNIQUE INDEX index_songs_path ON songs(path);
```

## API Reference

### SongRepository

```kotlin
interface SongRepository {
    // ... existing methods ...

    /**
     * Set the rating for a song
     * @param song The song to rate
     * @param rating Rating value (0-5, where 0 = unrated)
     */
    suspend fun setRating(song: Song, rating: Int)
}
```

### PlaylistExporter

```kotlin
class PlaylistExporter(private val playlistRepository: PlaylistRepository) {
    /**
     * Export a playlist to M3U format
     * @param playlist The playlist to export
     * @param outputFile The output file location
     * @param useRelativePaths Use relative paths instead of absolute
     * @throws IOException if writing fails
     */
    suspend fun exportToM3U(
        playlist: Playlist,
        outputFile: File,
        useRelativePaths: Boolean = false
    )

    /**
     * Export a playlist to M3U8 format (UTF-8 encoded M3U)
     */
    suspend fun exportToM3U8(
        playlist: Playlist,
        outputFile: File,
        useRelativePaths: Boolean = false
    )

    /**
     * Generate M3U content as a string
     * @return The M3U content
     */
    suspend fun generateM3UContent(
        playlist: Playlist,
        useRelativePaths: Boolean = false
    ): String
}
```

## Testing Recommendations

### Manual Testing

1. **Rating Import:**
   - Use a file manager app to rate some audio files in Android's MediaStore
   - Trigger a media scan in Shuttle2
   - Verify ratings appear in Song Info dialog

2. **Rating Display:**
   - Open Song Info for rated songs
   - Verify star display matches expected rating
   - Verify unrated songs show "Not rated"

3. **M3U Export:**
   - Use `PlaylistExporter` to export a playlist
   - Verify M3U file format is correct
   - Import the M3U file into iTunes/Apple Music
   - Verify songs and metadata appear correctly

### Unit Tests (Recommended)

```kotlin
class PlaylistExporterTest {
    @Test
    fun testM3UFormat() {
        // Verify M3U header and song entries
    }

    @Test
    fun testRelativePaths() {
        // Verify path conversion works
    }
}

class SongRepositoryTest {
    @Test
    fun testSetRating() {
        // Verify rating updates correctly
    }

    @Test
    fun testRatingBounds() {
        // Verify 0-5 constraint is enforced
    }
}
```

## Migration Path for Users

### From iSyncr/Rocket Player

Users previously using iSyncr with Rocket Player can:

1. **Import existing ratings:**
   - If ratings are stored in MediaStore, they'll import automatically
   - If ratings are in Rocket Player's database, manual migration needed

2. **Export playlists from Shuttle:**
   - Use M3U export to create iTunes-compatible playlists
   - Import M3U files into iTunes/Apple Music

3. **Manual sync workflow (until desktop app exists):**
   - Export playlists as M3U from Shuttle
   - Transfer M3U files to computer
   - Import into iTunes/Apple Music
   - Manual rating updates via MediaStore-compatible apps

## Contributing

To complete the full iTunes sync vision:

1. **UI Contributors:**
   - Implement rating edit widgets
   - Add playlist export UI
   - Design sync status screens

2. **Desktop Developers:**
   - Create cross-platform desktop sync app
   - Implement iTunes/Apple Music integration
   - Design sync protocol

3. **Protocol Designers:**
   - Define bidirectional sync format
   - Handle conflict resolution
   - Design efficient delta sync

## Resources

- [M3U Format Specification](https://en.wikipedia.org/wiki/M3U)
- [Android MediaStore Documentation](https://developer.android.com/reference/android/provider/MediaStore.Audio)
- [iTunes Library Access](https://developer.apple.com/documentation/ituneslibrary)
- [AppleScript for iTunes](https://developer.apple.com/library/archive/documentation/AppleScript/Conceptual/AppleScriptLangGuide/)

## License

This implementation follows Shuttle2's existing license (GNU General Public License v3.0).

## Questions?

For questions or issues related to this implementation, please comment on:
- GitHub Issue: https://github.com/timusus/Shuttle2/issues/107
