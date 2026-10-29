#!/usr/bin/env bash
# Pulls the tables the corpus needs out of the MusicBrainz dump and the ListenBrainz listens out of theirs.
set -euo pipefail

DATA="${SONG_MATCH_DATA:-$HOME/data/song-match}"
TABLES=(artist_credit recording track medium release release_group release_country release_unknown_country
    release_group_secondary_type release_group_secondary_type_join)

mkdir -p "$DATA/raw/mb/x" "$DATA/raw/lb/x"
tar -xjf "$DATA/raw/mb/mbdump.tar.bz2" -C "$DATA/raw/mb/x" "${TABLES[@]/#/mbdump/}"
zstd -dc "$DATA/raw/lb/listens.tar.zst" | tar -xf - -C "$DATA/raw/lb/x"
