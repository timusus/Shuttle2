# Spike: KTagLib through MediaStore content URIs (#370)

Question: can the local provider discover audio with MediaStore (`READ_MEDIA_AUDIO` on API 33+,
`READ_EXTERNAL_STORAGE` below) and read every tag with KTagLib from a file descriptor on each
`content://media/...` URI, so users no longer pick SAF folders? The first run's code (a debug-only
broadcast receiver plus a debug activity for the write consent) was deleted after the run. A
second run (b, below) changed `TaglibMediaProvider` itself to discover through MediaStore and
measured it against the SAF walk it replaces. This doc records what both runs found.

## Verdict

**Go for API 30+, conditional on one run below API 30.** On API 36 and 37, reading tags through a
MediaStore file descriptor works for all 5 formats tested. It handles non-ASCII tags, embedded
artwork, ReplayGain and a secondary (SD card) volume, with zero failures over 1,007 files. Tag
writes work through the same URI after a batch `MediaStore.createWriteRequest` consent. API 23–35
is **untested**, because `remote-emu.sh` only offers the API 36 ATD and API 37 images. Before the
provider change lands, run the same checks on API 29 and on one of API 23–28.

## Method

- A debug receiver queried `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`. It opened each row with
  `contentResolver.openFileDescriptor(uri, "r")` and `detachFd()`, then called
  `KTagLib.getMetadata(fd, displayName)` and `KTagLib.getArtwork` on a second fd. It logged a JSON
  summary: count, failures by exception, per-file timings, and each field that differed from
  MediaStore's own columns.
- Fixtures were generated with ffmpeg: mp3 (ID3v2.3, cover, ReplayGain, `Jóga (Ünïcode)`/`Björk`),
  FLAC (cover, ReplayGain, Japanese title, artist and album, disc 2), m4a (AAC, cover,
  `Ça plane pour moi`), Ogg Vorbis (ReplayGain, Greek title) and Opus (Cyrillic tags). Each file
  is 8 s long and the cover is 933 bytes. For timing, 200 copies of each (1,000 files) sat in a
  second folder.
- Lanes: API 36 `google_atd` and API 37 `google_apis`, both x86_64 on the WSL box (KVM).

## Results

| Check | API 36 | API 37 |
|---|---|---|
| Read tags via MediaStore fd, 5 formats | pass, 0 failures (1,007 files) | pass, 0 failures |
| Non-ASCII title, artist and album (Latin-1, CJK, Greek, Cyrillic) | correct | correct |
| Embedded artwork (mp3, FLAC, m4a) | 933 bytes each, as written | same |
| ReplayGain (mp3, FLAC, Ogg) | read | read |
| Secondary volume (virtual SD via `sm set-virtual-disk`) | found and read | not run |
| `.nomedia` folder | not discoverable (see below) | not discoverable or readable |
| Write without consent | `RecoverableSecurityException` | same |
| Write after `createWriteRequest` | pass, 5/5 formats | pass, 5/5 formats |

**Timing** (single thread, on the emulator):

| Step | Warm cache | Cold (`drop_caches`) |
|---|---|---|
| MediaStore cursor, 1,000 rows | 28 ms | 34 ms |
| Open fd + `getMetadata`, mean per file | 2.2 ms | 3.2 ms (p50 2.9, max 17) |
| `getArtwork` on a second fd, mean per file | 1.7 ms | 1.8 ms |

This works out to about **3.2 s per 1,000 files** for tags on one thread, or about 32 s for a
10k library. The import already runs `concurrentMap` over (cores − 1) threads, which should
divide that. The fixtures are short files with small covers, and a desktop CPU is faster than a
phone, so expect a real phone to be 3–5x slower (an estimate, not measured). The per-file cost
(open an fd, parse with TagLib) is the same code path `TaglibMediaProvider` runs today. The change
replaces the `DocumentFile` tree walk (one binder call per child for the name, type and
timestamps) with one cursor query, so it can't be slower than today. The SAF walk wasn't
benchmarked side by side.

**MediaStore vs TagLib.** Title, artist, album, album artist, genre, track, disc (MediaStore
stores `TRACK` as `disc * 1000 + track`) and duration all matched for the 5 fixtures. The one
difference: MediaStore's `YEAR` is null for FLAC, Ogg and Opus (a Vorbis comment `DATE`) on both
APIs, while TagLib reads it. MediaStore has no multi-value artists or genres, totals, lyrics,
grouping or ReplayGain, which is why TagLib is still needed.

## Unindexed files (`.nomedia`)

A file in a folder containing `.nomedia` gets a `Files` row with `media_type=0`. It never
appears in `Audio.Media`, and an explicit `scan_file` on it doesn't change that. On API 37 a
direct `java.io.File` open is denied (`EACCES`) and FUSE hides it from the folder listing, even
with `READ_MEDIA_AUDIO`. A file that landed *before* the `.nomedia` was created kept a stale
audio row, and stayed readable by path until something rescanned it (seen on API 36). So
MediaStore rows can be stale in both directions. Formats MediaStore doesn't recognise get the
same `media_type=0` treatment (expected, not tested; `.opus` is recognised as `audio/ogg` on
36/37).

**An optional SAF "Add folder" is enough to cover these.** A tree grant reads through the
document provider, not FUSE's media-type check. That is expected, since it's how today's provider
already reads everything, but it wasn't re-tested here. SD cards don't need it on API 30+.
`MediaStore.getExternalVolumeNames` returned `external_primary` and `04b9-1208`, and one query on
`EXTERNAL_CONTENT_URI` (`"external"`) returned rows from both volumes, with `VOLUME_NAME`
telling them apart. Files on the SD card read the same way.

## Write path (tag editor, and by extension Delete)

- On API 30+, `openFileDescriptor(uri, "rw")` on a MediaStore URI the app didn't create throws
  `RecoverableSecurityException`. `MediaStore.createWriteRequest(resolver, uris)` shows one system
  dialog for the whole batch ("Allow … to modify 5 audio files?"). After Allow, `"rw"` plus
  `KTagLib.writeMetadata` round-trips on all 5 formats, and artwork and ReplayGain survive.
- The request **must be launched from an Activity** (`startIntentSenderForResult` or
  `ActivityResultContracts.StartIntentSenderForResult`). Launched from a receiver or app context,
  `PermissionActivity` fails in `resolveCallingAppInfo` and never shows. Write only after the
  result callback; a write sent right after the tap raced the grant and failed.
- The grant doesn't survive a force-stop or a reinstall. Request it per edit session, right before
  writing. Don't store it.
- Not tested: API 29 (`RecoverableSecurityException.userAction`, one file at a time), API 23–28
  (plain file access, which needs `WRITE_EXTERNAL_STORAGE`; the manifest only declares
  `READ_EXTERNAL_STORAGE` up to 32), `MANAGE_MEDIA` (API 31+, skips the dialog after a special
  access grant), `createDeleteRequest`, and whether MediaStore re-reads a row after an fd write.
- **Bug found:** `KTagLib.writeMetadata` (1.6.2) stores non-ASCII values double-encoded
  (`écrit` → `Ã©crit`) in every format, and `ffprobe` confirms it on disk. This hits today's SAF
  tag editor too. Filed as #388; fix it before shipping any tag-editor path.

## Design implications

1. Discovery: one query on `Audio.Media.EXTERNAL_CONTENT_URI` (all volumes on API 29+) selecting
   `_ID`, `DATA`/`RELATIVE_PATH`, `VOLUME_NAME`, `SIZE`, `DATE_MODIFIED` and `MIME_TYPE`. Skip
   unchanged files by (path, size, mtime), as `withReplayGainTags` does already.
2. Tags: KTagLib for every field, via `getAudioFile(fd, …)` reused as is. MediaStore columns serve
   only as a fallback for unreadable files. Don't read artwork during the scan; Glide already
   loads it lazily from the URI.
3. Identity: MediaStore ids aren't stable across a MediaStore rebuild or a volume reformat. Keep
   path, size and mtime as the match key for migration (spike 3) and play counts, and store the
   content URI for opening the file.
4. Sources: "This device" needs only the runtime permission. "Add folder" (SAF) is an optional
   extra for `.nomedia` and unrecognised files; excludes become path-prefix filters on
   `DATA`/`RELATIVE_PATH`. Wire the (currently dead) MediaStore `ContentObserver` for incremental
   imports.
5. Tag editor: on API 30+ a batch `createWriteRequest` from the editor's Activity or Fragment
   result launcher, then write; below 30, per spike 2 once tested. Blocked on #388 for non-ASCII.

## Open

- Run on API 29 and API 23–28. Needs a lower-API image on the WSL box, or a real device, plus a
  real SD-card device to satisfy the issue's pass bar.
- Benchmark against today's SAF walk on the same 10k library, on a phone.
- Tag parity with the existing TagLib provider on multi-value artists and genres wasn't covered
  (the fixtures carried single values).

## Run b: the provider change

**Go, for API 30+.** `TaglibMediaProvider` no longer walks SAF trees to find songs. It queries
`Audio.Media.EXTERNAL_CONTENT_URI` (`IS_MUSIC=1 OR IS_PODCAST=1`, as the MediaStore provider
does), filters rows by folder, opens each row's content URI with `openFileDescriptor(uri, "r")`
and hands the detached fd to `KTagLib.getAudioFile` on the same `concurrentMap` as before. Folder
art near each file comes from `FolderImageReader`, as in the MediaStore provider. The SAF walk's
image collection (`DocumentNodeTree.imageNodes`) and `leavesWithFolderImages` were deleted. m3u
import still walks the SAF trees.

### What changed for stored songs

- `Song.path` is now the MediaStore `DATA` file path (the MediaStore provider already stores
  that), not a SAF document URI. Playback, artwork and m3u matching already handle file paths.
- Users who had the MediaStore provider keep their song identities. Users who had the S2 provider
  get new paths; spike 3 (below, #414) moves their songs to the new paths in place, so play counts
  and per-song excludes survive.

### Folder filters (#207 and the exclude list)

- `FolderFilter(includes, excludes)`: no includes means every folder; an exclude beats an include;
  prefix match on whole folder names, case-insensitive.
- Includes: the #207 persisted SAF tree grants, mapped to paths (`primary:Music` →
  `/storage/emulated/0/Music`, `home:` → `…/Documents`, `<UUID>:x` → `/storage/<UUID>/x`). Trees
  from other document providers have no path and are ignored. So an existing user's folder choice
  keeps working, and a user with no grants gets the whole device.
- Excludes: nothing feeds them yet. Settings > Sources (#379) will. The per-song exclude list
  (`blacklisted`, keyed by path) stays per song.

### Emulator results

The fixture was the seeded `library` (97 files) plus 5 hand-made files (mp3, FLAC, m4a, Ogg,
Opus with Latin-1, CJK, Greek and Cyrillic tags, embedded covers and ReplayGain), a `.wv` file
and a `.nomedia` folder, all under `/sdcard/Music/s2-seed`.

| Check | API 36 ATD | API 37 google_apis |
|---|---|---|
| Files read, failures | 102 of 102, 0 | 102 of 102, 0 |
| Tags vs the SAF build (every stored column) | identical except m4a MIME | same as API 36 |
| Non-ASCII, ReplayGain, track/disc, year | identical | identical |
| Embedded artwork | not checked in run b | shows in the Albums tab |
| Playback of an imported album | not run | plays |
| `.nomedia` folder | skipped | skipped |
| `.wv` (MediaStore type 0) | skipped | skipped |
| Include filter from a SAF grant | `Download` file excluded | same |
| Import, SAF walk (old build, same grant) | 915–1,217 ms | 2,138–2,424 ms |
| Import, MediaStore path | 232–274 ms | 267–309 ms |

- The only tag difference: m4a's MIME type is `audio/mp4` from MediaStore; SAF reported
  `audio/mpeg`. The new value is the right one.
- `.nomedia` songs were in the old SAF import and aren't in the new one. `.wv` was skipped by
  both (the SAF walk filters by extension and MIME too).
- The first API 37 run, with no grant and a cold start, took 1.3–1.6 s. With a warm cache and the
  same grant as the SAF runs, it took 0.27–0.31 s, which suggests host load on the shared box.
  Either way it beat the SAF walk.
- A fresh install with no SAF grant imports every audio file on the device, with no folder
  picker, and no m3u playlists (m3u import still needs a grant).
- Not run: secondary volume (the first run read a virtual SD card on API 36), anything below
  API 30, a 10k-track library, a real phone. These are in `docs/testing/device-checks.md`.

### Still open after run b

- m3u import without a SAF grant: read playlists from MediaStore `Files` rows or scan the
  included folders by path.
- An optional SAF "Add folder" for `.nomedia` and unrecognised formats.
- Tag editing and Delete for file-path songs need `createWriteRequest` (spike 2); today's editor
  expects SAF document URIs.
- Folder art on API 33+ for S2 songs: `FolderImageReader` can't list shared images there, and the
  MediaStore thumbnail fallback in the image loader only covers the MediaStore provider.
- `IS_MUSIC` leaves out audiobooks and recordings, and rows MediaStore hasn't scanned fully.

## Spike 3: keeping S2-provider users' history (#414)

**Done.** 1.0.10 (schema 40) stored each S2-provider song under its SAF document URI, for example
`content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FA%2Fb.mp3`.
Play counts, last played, the exclude flag, playlist entries (Favorites is a playlist) and the
saved queue all hang off the song's row id; there are no ratings.

**Where it runs: the importer, not a Room migration.** Matching needs MediaStore's rows, which a
migration can't query (it runs on whatever thread first opens the database, possibly before the
audio permission is granted). So `MediaImporter` asks each provider for `remapLegacySongs` before
the diff, applies the result with `SongRepository.remapPaths` in one transaction, and diffs against
the moved songs. The diff then updates those rows instead of deleting them. No schema change.

**No persisted flag.** The old identity is its own guard: `TaglibMediaProvider` only queries
MediaStore while some song still has a document URI as its path, and the first import after the
upgrade leaves none (matched songs move, unmatched ones are removed as missing, as before). An
interrupted run is safe at every point: the remap is one transaction, and if it or the scan fails,
nothing is diffed, so the old rows wait for the next import. A remap that throws fails the import
for that provider rather than letting the diff delete the songs. Without the audio permission
(1.0.10's S2 provider didn't need it) the MediaStore query fails, so nothing moves and nothing is
lost until the user grants it.

**Matching** (`LegacySafSongs`):

- The document id gives the volume and relative path: `primary:` is the primary volume, `home:` its
  Documents folder, any other root a secondary volume id (`04B9-1208`). The MediaStore file on the
  same volume at the same relative path (case-insensitive) is the match. Downloads documents use
  `raw:<path>` (matched by path) or `msf:<id>` (matched by id, confirmed by size, date and duration,
  since MediaStore ids change after a rebuild).
- Size, last modified (within 2 s) and duration (within 2 s) decide only when the path is ambiguous:
  the old volume id isn't mounted but the relative path exists on another volume (a reformatted SD
  card), or the id is opaque (another document provider). Exactly one file must fit, or the song is
  unmatched.
- Overlapping folder grants made the old scanner store one file twice. The most played row keeps
  the file; the others' playlist entries move to it, and the import removes them.
- A remap onto a path another row already holds (for example the MediaStore provider's row for the
  same file, since paths are unique across providers) is skipped.

**Unmatched:** files deleted, moved or renamed since the last 1.0.10 scan; files MediaStore doesn't
index (`.nomedia` folders, unrecognised formats); files outside the new folder filter. They lose
their history when the import removes them, as any missing file does.

**Tests:** `LegacySafSongsTest` (primary, SD card, `home:`, ambiguous, unmatched, already-migrated,
duplicates, Downloads ids) and `LegacySafSongsImportTest`, which builds a schema-40 database,
migrates it and runs `MediaImporter` over a fake MediaStore listing.
