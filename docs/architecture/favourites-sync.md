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
| `favourite` | The desired state to push: `true` to favourite, `false` to unfavourite. |
| `changedAt` | When the toggle was made locally. |

One row per song: a later toggle before the row is flushed replaces it (`INSERT OR REPLACE`), so
only the latest desired state is ever sent — a favourite immediately followed by an unfavourite
collapses to a single pending "unfavourite" row rather than two queued operations.

The write happens inside `SongDataDao.setFavourite`, in the same `@Transaction` as the
`songs.favouritedAt` write it already makes (including the #564 undo path, which restores the
song's original `favouritedAt` rather than stamping now) — so the outbox can never disagree with
the column it describes.

## Pull and merge (Room v53)

Each provider's `toSong()` maps the server's favourite into `favouritedAt`: Jellyfin and Emby
`UserData.IsFavorite` (the items listing asks for user data), at the sync's start time since the
server keeps none; Plex `userRating == 10`, at `lastRatedAt`. `SongDataDao.insertUpdateAndDelete`
merges it for remote-provider updates in the same transaction: a song with a `pending_favourites`
row keeps its local value; otherwise the server wins, and a server favourite keeps an existing
local timestamp. Inserts take the server value as is. MediaStore and TagLib songs are never
touched. v53 dropped `pending_favourites.mediaProvider` and `externalId`, which nothing read.

A favourite change doesn't move what the incremental listing filters on (Jellyfin/Emby
`DateLastSaved`: user data lives apart from the item; Plex `updatedAt`), so each incremental pass
also fetches the favourites alone (Jellyfin/Emby `Filters=IsFavorite`, Plex `userRating=10`) and
`withFavouriteChanges` (`mediaprovider:server`) adds the stored songs whose favourite differs
from that set. If the fetch fails the pass goes on without it; the next one fetches the whole set
again. A full pass reads every song's favourite from the listing itself.

## Remaining slices

1. **(Optional) UI.** A heart on song rows and in the actions sheet, plus a settings/empty-state
   line noting favourites sync to the server, and device checks in
   `docs/testing/device-checks.md`.

Favourites always sync — no toggle, no Pro gate (owner decision, 2026-09-27).
