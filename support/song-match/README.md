# Song-match corpus

Labelled pairs of song copies (two sets of plain tags, same song or not) for tuning and pinning `SongCopyMatcher`
(`android/domain`), the rule that decides whether two sources hold one song of one album. The data stays out of the
repo, in `~/data/song-match` (`SONG_MATCH_DATA` overrides); only the scripts and a 2k-pair fixture are checked in.

## Sources

| Source | What it gives | Licence |
| --- | --- | --- |
| [MusicBrainz full export](https://data.metabrainz.org/pub/musicbrainz/data/fullexport/), `mbdump.tar.bz2` (7 GB) | Releases, mediums, tracks, recordings: the answer key | CC0 (core tables) |
| [ListenBrainz incremental dump](https://data.metabrainz.org/pub/musicbrainz/listenbrainz/incremental/), listens `.tar.zst` (335 MB) | Real file tags next to the recording and release MBIDs players read from them: the noise model and the seed albums | CC0 |
| [AcoustID daily updates](https://data.acoustid.org/), fingerprint, track_fingerprint, track_mbid (small) | File lengths submitted per recording: a cross-check of the duration jitter only | CC BY-SA 3.0 |

Nothing from AcoustID reaches the pairs or the fixture, so both stay CC0.

## Pipeline

Python 3 with DuckDB; run every script with `-I`, since it reads downloaded data.

```bash
python3 -m venv ~/data/song-match/venv && ~/data/song-match/venv/bin/pip install duckdb
support/song-match/download.sh            # resumable; MB_DUMP, LB_DUMP, ACOUSTID_DAYS pick other dumps
support/song-match/extract.sh             # only the tables the corpus needs
~/data/song-match/venv/bin/python -I support/song-match/prepare.py
~/data/song-match/venv/bin/python -I support/song-match/make_pairs.py ~/data/song-match \
    android/domain/src/commonTest/resources/song-copy/pairs.tsv
./gradlew :android:domain:evaluateSongCopyMatcher   # -Pcorpus=<tsv> for another file
```

- `prepare.py` loads the ListenBrainz listens that carry a recording MBID and the MusicBrainz subset seeded by their
  releases, widened with what makes hard negatives: the other releases of each release group, other albums with the
  same title, compilations holding the same recordings and same-titled recordings by the same artist
  (`work/song-match.duckdb`).
- `make_pairs.py` writes `corpus/pairs.tsv` (and `corpus/noise_model.json`). Each pair renders two MusicBrainz tracks
  as tags with noise measured from ListenBrainz (case, diacritics, featuring credits, remaster suffixes, missing disc,
  year, duration jitter). `same` means one recording on one release group; categories prefixed `ambiguous_` are pairs
  MusicBrainz can't settle (likely unmerged duplicate recordings) and stay out of the headline rates. The
  `*unknown_album*`, `neg_untagged_rip` and `neg_generic_*` categories render both copies with placeholder or generic
  tags ("Unknown Album", "Greatest Hits", no album artist), where only title and duration separate songs. The optional
  second argument writes the fixture: an equal share of each category, at most 2,000 pairs (licence note beside it).
- The evaluation prints wrong- and missed-merge rates per matcher variant, a per-category table and m/u weights per
  field. `SongCopyFixtureTest` pins the default rule's merges per category on the fixture.

## Chosen rule

On the 2026-10 corpus (201,000 pairs), the default rule merged 2.97% of different-song pairs and missed 33.0% of
same-song pairs. It blocks on album, disc and track, treats placeholder albums as untagged, and requires equal titles
with edition qualifiers dropped, lengths within 2 s and the same version markers. Clean and explicit editions make up
most of the wrong merges, since their tags are identical; without them the rate is 0.13%. Most missed merges are copies
with no usable album (placeholder albums, a moved track) or a length beyond 2 s. Gating on artist cut 0.01 points of
wrong merges and cost 1.4 points of recall, because real artist tags vary.
