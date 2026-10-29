#!/usr/bin/env bash
# Downloads the public dumps the song-match corpus is built from into ~/data/song-match/raw (about 8 GB).
# Resumable: rerun after an interrupted transfer. See README.md for sources and licences.
set -euo pipefail

DATA="${SONG_MATCH_DATA:-$HOME/data/song-match}"
MB_DUMP="${MB_DUMP:-20261028-002228}"
LB_DUMP="${LB_DUMP:-listenbrainz-dump-2750-20261029-000002-incremental}"
ACOUSTID_DAYS="${ACOUSTID_DAYS:-2026-10-20 2026-10-21 2026-10-22 2026-10-23 2026-10-24 2026-10-25 2026-10-26}"

mkdir -p "$DATA/raw/mb" "$DATA/raw/lb" "$DATA/raw/acoustid"

fetch() { curl -fL --retry 5 -C - -o "$2" "$1"; }

# ListenBrainz: one daily incremental listens dump (CC0).
fetch "https://data.metabrainz.org/pub/musicbrainz/listenbrainz/incremental/$LB_DUMP/${LB_DUMP/listenbrainz-dump/listenbrainz-listens-dump}.tar.zst" \
    "$DATA/raw/lb/listens.tar.zst"

# AcoustID: a week of daily updates; fingerprints carry the file length, track_mbid maps them to recordings (CC BY-SA 3.0).
for day in $ACOUSTID_DAYS; do
    month="${day:0:7}"
    for table in fingerprint track_fingerprint track_mbid; do
        fetch "https://data.acoustid.org/${day:0:4}/$month/$day-$table-update.jsonl.gz" "$DATA/raw/acoustid/$day-$table.jsonl.gz"
    done
done

# MusicBrainz: the full core dump (CC0 tables); extract.sh pulls only the tables the corpus needs.
fetch "https://data.metabrainz.org/pub/musicbrainz/data/fullexport/$MB_DUMP/mbdump.tar.bz2" "$DATA/raw/mb/mbdump.tar.bz2"
