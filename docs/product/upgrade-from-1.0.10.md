# Upgrading from 1.0.10

What a user of the legacy Play build (1.0.10, commit `2c586573c`, database schema 40) keeps when the rewrite
installs over it. Status: **kept** (unchanged), **migrated** (converted, tested), **lost by design**, **BROKEN**
(filed), **untested**.

## Library database

| Data | Status | Notes |
|---|---|---|
| Database, schema 40 → 57 | migrated | Every migration is additive; `LegacyReleaseUpgradeTest` opens a 1.0.10 database through the whole chain. |
| Songs, ids, exclude flags | migrated | The first import keys songs by path and keeps their ids. 1.0.10 SAF songs (`content://` paths) are remapped to file paths by id first (`LegacySafSongsImportTest`). MediaStore, `jellyfin://item/`, `emby://item/` and `plex://` paths are unchanged. |
| Playlists | migrated | Kept with their entries. A legacy SAF song the remap can't match is deleted on the first import, with its playlist entries. |
| Favourites | migrated | The Favorites playlist becomes `favouritedAt` flags (45 → 46); pending remote favourites move to `pending_favourites` (52 → 53). |
| Play counts, last played | kept | Columns on the song row, which keeps its id. |
| Play history (`play_events`) | **BROKEN** for SAF users (#1023) | Seeded from `lastCompleted` (48 → 49) with the legacy path; the remap rewrites only `songs.path`, and history joins on path. MediaStore and remote users are unaffected. |
| Resume points | **BROKEN** when a path is remapped (#1023) | Same path join as history. |
| Saved queue | untested (#1025) | Restored by song id, which survives the import. |
| Remote (Jellyfin/Emby/Plex) reimport over legacy rows | untested (#1025) | |
| Real `TaglibMediaProvider` remap, music permission declined | untested (#1025) | |

## Settings

| Data | Status | Notes |
|---|---|---|
| Preferences file, keys and types | kept | Same default prefs file and `encrypted_preferences`; reads tolerate a wrong type (`Setting.kt`). |
| Server credentials | kept | Same keys (`ServerCredentialStoreTest`). |
| Queue, EQ, replay gain, theme, tabs, sort orders | kept | `SettingsRepositoriesTest`, `GeneralPreferenceManagerTest`, `PlaybackPreferenceManagerTest`. |
| Widget dark mode, ignore playlist duplicates, trial and rating dialog flags | lost by design | |
| Rescan "Never" | lost by design | Becomes Daily (#771). |
| First-run setup | kept | Android has no onboarding (#379); the source setup flow is iOS-only. A 1.0.10 SAF user is asked for the music permission once on first launch. |

## Pro entitlement

| Legacy SKU | Status | Notes |
|---|---|---|
| `s2_iap_full_version`, `s2_iap_full_version_low` | migrated | Pro (legacy lifetime). |
| `s2_subscription_full_version_monthly`, `_yearly`, `_yearly_low` | migrated | Pro while the subscription is active. |
| All of the above, first launch before Play answers | **BROKEN** (#1024) | `Entitlement.Unknown`: server streaming refused until Play answers once. |
| A PENDING legacy purchase | lost by design (#1024) | 1.0.10 counted it as paid; the rewrite waits for PURCHASED. |
| 1.0.10 trial state | lost by design | Ignored (`EntitlementRepository`). |

Mapping covered by `EntitlementResolverTest` and `PurchaseEntitlementTest`; device restores are #940.

## Analytics and crash reporting

Current behaviour, pending the default decision in #481. `pref_crash_reporting` and `pref_firebase_analytics` keep
their keys but now default to on (1.0.10 defaulted off). A 1.0.10 user who toggled either keeps that choice; one who
never did gets both on, and `InstallDefaults` shows the one-time "analytics is now on" notice on the first launch
after the upgrade.

## Device check for Tim

1. On a spare Android device, install 1.0.10 from Play (uninstall any dev build first; the release build has the same
   application id).
2. In 1.0.10: add music via a SAF folder (Shuttle provider) and, if you can, a Jellyfin server. Play a few songs to
   the end, favourite two, make a playlist, exclude one song, change the theme, EQ and a sort order. Leave the queue
   mid-song. If you own a legacy SKU on that account, check Pro shows.
3. Sideload the rewrite's release build over it (`adb install -r`), first launch online.
4. Verify: library, playlists, exclude flag and play counts are all there; Favorites holds both songs
   (device-checks.md "Favorites" section); settings kept; the queue restores; Pro still shows; the analytics notice
   appears once.
5. History: Home's history-based shelves (Heavy Rotation, Rediscover) count the songs played in step 2. Expect SAF
   songs' history missing until #1023 is fixed.
6. Repeat 1–3 with Play unreachable at first launch but the server reachable (Wi-Fi with no internet, server on the
   LAN): Pro features other than streaming work; streaming from the server is refused until Play answers (#1024).
7. Legacy SKU restores: device-checks.md, "legacy SKU" items (#940).
