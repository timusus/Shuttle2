#!/usr/bin/env bash
# Pulls the tables the corpus needs out of the MusicBrainz dump and the ListenBrainz listens out of theirs.
set -euo pipefail

DATA="${SONG_MATCH_DATA:-$HOME/data/song-match}"
TABLES=(artist_credit recording track medium release release_group release_country
    release_unknown_country release_group_secondary_type release_group_secondary_type_join)
# The dump is one bzip2 stream of ~30 GB; pbzip2 decompresses it on several cores when it's there
BUNZIP=$(command -v pbzip2 || command -v bzip2)

mkdir -p "$DATA/raw/mb/x" "$DATA/raw/lb/x"
if [ ! -s "$DATA/raw/mb/x/mbdump/track" ]; then
    "$BUNZIP" -dc "$DATA/raw/mb/mbdump.tar.bz2" | tar -xf - -C "$DATA/raw/mb/x" SCHEMA_SEQUENCE "${TABLES[@]/#/mbdump/}"
fi
if ! ls "$DATA"/raw/lb/x/*/listens/*/*.listens >/dev/null 2>&1; then
    zstd -dc "$DATA/raw/lb/listens.tar.zst" | tar -xf - -C "$DATA/raw/lb/x"
fi
