# Favourites sync (#497)

Favourite is a per-song flag (`songs.favouritedAt`, Room v46), local only until this work lands
server sync for it. Design and owner decisions: issue #497 (spike comments, 2026-09-26 and
2026-09-27) and #506 for the smart-playlist groundwork it builds on.

## Outbox contract (slice 1, Room v48)

`pending_favourites` is the local outbox for a favourite/unfavourite made on a remote-provider
song (Jellyfin, Emby, Plex — `MediaProviderType.remote`). Local songs (Shuttle, MediaStore) never
enqueue.

| Column | Meaning |
|---|---|
| `songId` (PK) | The local song row. Foreign key to `songs.id`, `ON DELETE CASCADE` — a row is removed with its song. |
| `mediaProvider` | Copied from the song at write time, so a later push knows which server API to call. |
| `externalId` | The server's item id for the song, copied at write time so the writer doesn't need to look the song back up. |
| `favourite` | The desired state to push: `true` to favourite, `false` to unfavourite. |
| `changedAt` | When the toggle was made locally. |

One row per song: a later toggle before the row is flushed replaces it (`INSERT OR REPLACE`), so
only the latest desired state is ever sent — a favourite immediately followed by an unfavourite
collapses to a single pending "unfavourite" row rather than two queued operations.

The write happens inside `SongDataDao.setFavourite`, in the same `@Transaction` as the
`songs.favouritedAt` write it already makes (including the #564 undo path, which restores the
song's original `favouritedAt` rather than stamping now) — so the outbox can never disagree with
the column it describes.

## Remaining slices

1. **Writers.** A `FavouriteWriter` interface in `mediaprovider:core` (`handles(song)`,
   `suspend fun set(song, favourite): Result` — Ok/Gone/Retry), modelled on `PlaybackReporter`,
   with Jellyfin/Emby/Plex implementations and an aggregate. A `FavouriteSender` in `app`,
   modelled on `PlaybackReportSender`, drains `pending_favourites` on app start, on the table
   changing, and at the start of each library import; a 2xx or 404 deletes the row, anything else
   is retried on the next trigger.
2. **Pull + merge.** Each provider's `toSong()` maps the server's favourite (Jellyfin/Emby
   `UserData.IsFavorite`, Plex `userRating == 10`) into `favouritedAt`. The merge in
   `insertUpdateAndDelete` runs for remote-provider updates only: a pending local toggle wins over
   the server value; otherwise the server wins. `favouritedAt` for a server favourite keeps an
   existing local timestamp, or uses Plex's `lastRatedAt` / the sync time for Jellyfin and Emby.
3. **(Optional) UI.** A heart on song rows and in the actions sheet, plus a settings/empty-state
   line noting favourites sync to the server, and device checks in
   `docs/testing/device-checks.md`.

Favourites always sync — no toggle, no Pro gate (owner decision, 2026-09-27).
