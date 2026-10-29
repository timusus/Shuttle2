"""Builds labelled song-copy pairs from work/song-match.duckdb (prepare.py) into corpus/pairs.tsv.

A pair is two plain-tag records (what SongCopyMatcher sees) with a label: `same` when both are the same MusicBrainz
recording on the same release group (one song of one album), else `different`. Each side is rendered as one source
would tag it: MusicBrainz's own strings with noise at the rates measured between ListenBrainz listens and MusicBrainz,
or a ListenBrainz listen's strings verbatim. Categories whose MusicBrainz label can't be trusted are prefixed
`ambiguous_` and kept out of the headline rates.

Usage: venv/bin/python -I make_pairs.py [data dir] [fixture path]
"""

import json
import os
import random
import re
import sys
import unicodedata

import duckdb

DATA = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/data/song-match")
FIXTURE = sys.argv[2] if len(sys.argv) > 2 else None
SEED = 1069
rng = random.Random(SEED)
db = duckdb.connect(f"{DATA}/work/song-match.duckdb")
q = db.execute

NORM = "regexp_replace(lower(strip_accents({0})), '[^a-z0-9]+', '', 'g')"
MARKER_RE = r"\b(live|re-?mix|mix|demo|acoustic|instrumental|edit|explicit|clean|unplugged|reprise|mono|karaoke|version)\b"
VERSION_COMMENT = r"(clean|explicit|edited|censored|radio)"
GENERIC = ["intro", "outro", "interlude", "untitled", "skit", "prelude", "hiddentrack", "silence", "reprise", "theme",
           "introduction", "overture", "finale", "epilogue", "prologue"]
GENERIC_ALBUM = ["greatesthits", "thebestof", "bestof", "hits", "live", "demo", "demos", "singles", "untitled", "ep",
                 "thecollection", "collection", "theverybestof", "anthology", "essentials", "theessential", "gold",
                 "unplugged", "acoustic", "remixes", "christmas", "selftitled", "liveinconcert", "rarities"]
PLACEHOLDER_ALBUM = ["Unknown Album", "Unknown Album", "Unknown Album", "Unknown", "[Unknown Album]", "<unknown>"]

q(f"""CREATE OR REPLACE TABLE tk AS
WITH rel_mediums AS (SELECT release, count(*) AS discs FROM sel_medium GROUP BY 1),
rg_year AS (SELECT r.rg, min(y.year) AS year FROM sel_release r JOIN release_year y ON y.release = r.id GROUP BY 1),
secondary AS (SELECT rg, bool_or(name = 'Compilation') AS compilation, bool_or(name = 'Live') AS live FROM rg_secondary GROUP BY 1)
SELECT row_number() OVER (ORDER BY t.medium, t.position) AS tid,
  t.recording AS rec, rc.gid AS rec_gid, rc.comment AS rec_comment, t.name AS title, tac.name AS artist,
  coalesce(t.length, rc.length) AS duration_ms, t.position AS track, m.position AS disc, rm.discs,
  r.id AS rel, r.gid AS rel_gid, r.name AS album, rac.name AS album_artist, r.rg, r.comment AS rel_comment,
  y.year, rgy.year AS rg_year,
  coalesce(s.compilation, false) OR rac.name = 'Various Artists' AS compilation, coalesce(s.live, false) AS live,
  {NORM.format('t.name')} AS ntitle, {NORM.format('r.name')} AS nalbum
FROM track t JOIN recording rc ON rc.id = t.recording JOIN sel_medium m ON m.id = t.medium
JOIN sel_release r ON r.id = m.release JOIN artist_credit tac ON tac.id = t.ac JOIN artist_credit rac ON rac.id = r.ac
JOIN rel_mediums rm ON rm.release = r.id
LEFT JOIN release_year y ON y.release = r.id LEFT JOIN rg_year rgy ON rgy.rg = r.rg LEFT JOIN secondary s ON s.rg = r.rg
WHERE t.name IS NOT NULL AND r.name IS NOT NULL""")

# --- The noise model: how a listener's player tags a track MusicBrainz knows, per listen with both MBIDs ---
lb_rows = q("""SELECT DISTINCT l.title, l.artist, l.album, l.duration_ms, l.track, l.disc, t.tid, t.title, t.artist, t.album,
  t.duration_ms, t.track, t.disc
FROM lb l JOIN tk t ON t.rec_gid = l.rec_gid AND t.rel_gid = l.rel_gid""").fetchall()


def fold(s):
    return "".join(c for c in unicodedata.normalize("NFKD", s) if not unicodedata.combining(c))


def loose(s):
    return re.sub(r"[^0-9a-z]+", "", fold(s).lower())


def rate(n, d):
    return n / d if d else 0.0


lb_by_tid = {}
diffs = []
stats = dict(n=0, title_exact=0, title_case=0, title_fold=0, title_loose=0, title_feat=0, title_remaster=0,
             album_exact=0, album_loose=0, album_edition=0, artist_exact=0, artist_feat=0, artist_amp=0,
             duration=0, track=0, track_agree=0, disc=0)
EDITION = re.compile(r"remaster|deluxe|expanded|edition|anniversary|bonus", re.I)
FEAT = re.compile(r"\b(feat\.?|ft\.|featuring)\s", re.I)
for (lt, la, lal, ld, ltn, ldn, tid, mt, ma, mal, md, mtn, mdn) in lb_rows:
    if not lt or not mt:
        continue
    lb_by_tid.setdefault(tid, []).append((lt, la, lal, ld, ltn, ldn))
    stats["n"] += 1
    stats["title_exact"] += lt == mt
    stats["title_case"] += lt != mt and lt.lower() == mt.lower()
    stats["title_fold"] += lt.lower() != mt.lower() and fold(lt).lower() == fold(mt).lower()
    stats["title_loose"] += loose(lt) == loose(mt)
    stats["title_feat"] += bool(FEAT.search(lt)) and not FEAT.search(mt)
    stats["title_remaster"] += bool(EDITION.search(lt)) and not EDITION.search(mt)
    stats["album_exact"] += lal == mal
    stats["album_loose"] += bool(lal) and loose(lal) == loose(mal)
    stats["album_edition"] += bool(lal) and bool(EDITION.search(lal)) and not EDITION.search(mal)
    stats["artist_exact"] += la == ma
    stats["artist_feat"] += bool(la) and bool(FEAT.search(ma or "")) and not FEAT.search(la)
    stats["artist_amp"] += bool(la) and ((" & " in la) != (" & " in (ma or "")))
    stats["track"] += ltn is not None
    stats["track_agree"] += ltn is not None and ltn == mtn
    stats["disc"] += ldn is not None
    if ld and md:
        stats["duration"] += 1
        diffs.append(ld - md)
n = stats["n"]
model = {k: rate(v, n) for k, v in stats.items() if k != "n"}
model["listens"] = n
model["track_agree"] = rate(stats["track_agree"], stats["track"])
abs_diffs = sorted(abs(d) for d in diffs)
model["duration_abs_diff_ms_percentiles"] = {p: abs_diffs[int(len(abs_diffs) * p / 100) - 1] for p in (50, 75, 90, 95, 99)} if abs_diffs else {}
# Beyond 20 s the listen is usually mapped to another version, not the same file timed differently
jitter = [d for d in diffs if abs(d) <= 20_000] or [0]
model["duration_beyond_20s"] = rate(len(diffs) - len(jitter), len(diffs))

acoustid = q("""SELECT quantile_cont(spread, [0.5, 0.75, 0.9, 0.95, 0.99]), count(*) FROM (
  SELECT rec_gid, max(length) - min(length) AS spread FROM acoustid_length GROUP BY 1 HAVING count(*) > 1)""").fetchone()
model["acoustid_length_spread_s_percentiles"] = dict(zip(["50", "75", "90", "95", "99"], acoustid[0] or []))
model["acoustid_recordings_with_2plus_files"] = acoustid[1]
os.makedirs(f"{DATA}/corpus", exist_ok=True)
with open(f"{DATA}/corpus/noise_model.json", "w") as f:
    json.dump(model, f, indent=2)
print(json.dumps(model, indent=2))

# --- Candidate pairs, by category ---


def pairs(sql, n):
    return q(f"SELECT * FROM ({sql}) ORDER BY hash(a * 7919 + b) LIMIT {n}").fetchall()


hard = "a.rec <> b.rec"
categories = {
    "pos_same_release_lb": pairs("""SELECT DISTINCT t.tid a, t.tid b FROM tk t JOIN lb l ON l.rec_gid = t.rec_gid
      AND l.rel_gid = t.rel_gid WHERE l.title IS NOT NULL""", 20000),
    "pos_same_release": pairs("SELECT tid a, tid b FROM tk", 15000),
    "pos_marker": pairs(f"SELECT tid a, tid b FROM tk WHERE regexp_matches(lower(title), '{MARKER_RE}')", 6000),
    "pos_multidisc": pairs("SELECT tid a, tid b FROM tk WHERE disc > 1", 6000),
    "pos_edition_same_position": pairs("""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.rec = b.rec AND a.rg = b.rg
      AND a.rel < b.rel AND a.disc = b.disc AND a.track = b.track""", 10000),
    "pos_edition_moved": pairs("""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.rec = b.rec AND a.rg = b.rg
      AND a.rel < b.rel AND (a.disc <> b.disc OR a.track <> b.track)""", 4000),
    "neg_clean_explicit": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.rg = b.rg AND a.rel < b.rel
      AND a.disc = b.disc AND a.track = b.track AND {hard}
      WHERE regexp_matches(lower(coalesce(a.rel_comment, '') || ' ' || coalesce(b.rel_comment, '') || ' ' ||
        coalesce(a.rec_comment, '') || ' ' || coalesce(b.rec_comment, '')), '{VERSION_COMMENT}')""", 8000),
    "neg_edition_diff_title": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.rg = b.rg AND a.rel < b.rel
      AND a.disc = b.disc AND a.track = b.track AND {hard} AND a.ntitle <> b.ntitle""", 10000),
    "neg_version_marker": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.rg = b.rg AND a.disc = b.disc
      AND a.track = b.track AND {hard} AND a.tid < b.tid AND starts_with(b.ntitle, a.ntitle) AND a.ntitle <> b.ntitle
      AND regexp_matches(lower(b.title), '{MARKER_RE}')""", 6000),
    "ambiguous_edition_same_title": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.rg = b.rg AND a.rel < b.rel
      AND a.disc = b.disc AND a.track = b.track AND {hard} AND a.ntitle = b.ntitle""", 6000),
    "neg_same_album_title": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.nalbum = b.nalbum AND a.rg < b.rg
      AND a.disc = b.disc AND a.track = b.track AND {hard} AND NOT a.compilation AND NOT b.compilation
      AND {NORM.format('a.album_artist')} <> {NORM.format('b.album_artist')}""", 15000),
    "neg_va_same_album_title": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.nalbum = b.nalbum AND a.rg < b.rg
      AND a.disc = b.disc AND a.track = b.track AND {hard} AND a.compilation AND b.compilation""", 8000),
    "neg_same_artist_album_title": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.nalbum = b.nalbum AND a.rg < b.rg
      AND a.disc = b.disc AND a.track = b.track AND {hard} AND a.ntitle <> b.ntitle
      AND {NORM.format('a.album_artist')} = {NORM.format('b.album_artist')}""", 8000),
    "ambiguous_same_artist_album_same_title": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.nalbum = b.nalbum
      AND a.rg < b.rg AND a.disc = b.disc AND a.track = b.track AND {hard} AND a.ntitle = b.ntitle
      AND {NORM.format('a.album_artist')} = {NORM.format('b.album_artist')}""", 3000),
    "neg_generic_title": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.ntitle = b.ntitle AND a.rg < b.rg
      AND a.track = b.track AND {hard} AND hash(a.tid) % 8 = 0 WHERE a.ntitle IN ({','.join(f"'{g}'" for g in GENERIC)})""", 8000),
    "neg_untagged_rip": pairs(f"""SELECT a.tid a, b.tid b FROM (SELECT * FROM tk WHERE hash(tid) % 500 = 0) a
      JOIN (SELECT * FROM tk WHERE hash(tid) % 500 = 1) b ON a.track = b.track AND a.rg <> b.rg
      AND {hard} AND a.disc = 1 AND b.disc = 1""", 3000),
    "neg_disc_collision": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.rel = b.rel AND a.disc < b.disc
      AND a.track = b.track AND {hard}""", 10000),
    "neg_same_title_artist": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.ntitle = b.ntitle AND a.artist = b.artist
      AND a.rg < b.rg AND {hard} AND NOT a.live AND NOT b.live""", 8000),
    "neg_live_vs_studio": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.ntitle = b.ntitle AND a.artist = b.artist
      AND a.rg <> b.rg AND {hard} AND NOT a.live AND b.live""", 6000),
    "neg_compilation_same_recording": pairs("""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.rec = b.rec AND a.rg <> b.rg
      AND NOT a.compilation AND b.compilation""", 6000),
    "neg_same_release_other_track": pairs("""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.rel = b.rel AND a.disc = b.disc
      AND b.track = a.track + 1""", 2000),
    # Placeholder tags ("Unknown Album", no album artist) put unrelated files in one block, so only title and duration
    # stand between them and a merge; these are over-represented on purpose
    "pos_unknown_album": pairs("SELECT tid a, tid b FROM tk WHERE hash(tid) % 4 = 0", 5000),
    "neg_unknown_album_generic_title": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.ntitle = b.ntitle
      AND a.track = b.track AND a.rg <> b.rg AND {hard} WHERE a.ntitle IN ({','.join(f"'{g}'" for g in GENERIC)})""", 8000),
    "neg_unknown_album_same_title": pairs(f"""SELECT a.tid a, b.tid b FROM (SELECT * FROM tk WHERE hash(tid) % 4 = 0) a
      JOIN tk b ON a.ntitle = b.ntitle AND a.track = b.track AND a.rg <> b.rg AND {hard}
      AND {NORM.format('a.artist')} <> {NORM.format('b.artist')}
      WHERE a.ntitle NOT IN ({','.join(f"'{g}'" for g in GENERIC)})""", 8000),
    "neg_unknown_album_same_title_artist": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.ntitle = b.ntitle
      AND a.artist = b.artist AND a.track = b.track AND a.rg <> b.rg AND {hard}""", 6000),
    "neg_generic_album_no_album_artist": pairs(f"""SELECT a.tid a, b.tid b FROM tk a JOIN tk b ON a.nalbum = b.nalbum
      AND a.rg < b.rg AND a.disc = b.disc AND a.track = b.track AND {hard}
      WHERE a.nalbum IN ({','.join(f"'{g}'" for g in GENERIC_ALBUM)})""", 6000),
}
UNKNOWN_ALBUM = {"pos_unknown_album", "neg_unknown_album_generic_title", "neg_unknown_album_same_title",
                 "neg_unknown_album_same_title_artist"}

tids = sorted({t for rows in categories.values() for pair in rows for t in pair})
q("CREATE OR REPLACE TEMP TABLE wanted (tid BIGINT)")
q("INSERT INTO wanted SELECT unnest(?)", [tids])
cols = ["tid", "rec", "rg", "title", "artist", "album", "album_artist", "disc", "discs", "track", "duration_ms", "year",
        "rg_year", "compilation"]
rows = {r[0]: dict(zip(cols, r)) for r in q(f"SELECT {', '.join(cols)} FROM tk JOIN wanted USING (tid)").fetchall()}

# --- Rendering: one source's tags for a track ---
REMASTER_TITLE = [" - Remastered {y}", " ({y} Remaster)", " (Remastered)", " - {y} Remaster", " (Remastered {y})"]
EDITION_ALBUM = [" (Remastered)", " [Deluxe Edition]", " (Deluxe)", " (Expanded Edition)", " ({y} Remaster)"]
p_case = max(model["title_case"], 0.03)
p_fold = max(model["title_fold"], 0.02)
p_feat = max(model["title_feat"] + model["artist_feat"], 0.05)
p_title_edition = max(model["title_remaster"], 0.03)
p_album_edition = max(model["album_edition"], 0.03)


def recase(s):
    return rng.choice([s.lower(), s.upper(), s.title()]) if s else s


def render(row, noisy, lb=None):
    title, artist, album = row["title"], row["artist"], row["album"]
    album_artist, disc, track = row["album_artist"], row["disc"], row["track"]
    duration, year = row["duration_ms"], row["year"]
    if lb:
        lt, la, lal, ld, ltn, ldn = lb
        title, artist, album = lt, la or artist, lal or album
        duration = ld or duration
        track = ltn if ltn is not None else track
        disc = ldn
        if rng.random() < 0.5:
            album_artist = None
    elif noisy:
        if rng.random() < p_case:
            title = recase(title)
        if rng.random() < p_fold:
            title, artist, album = fold(title), artist and fold(artist), fold(album)
        if rng.random() < p_feat and artist and FEAT.search(artist):
            main, guest = re.split(r"\s+(?:feat\.?|ft\.|featuring)\s+", artist, maxsplit=1, flags=re.I)
            title, artist = f"{title} (feat. {guest})", main
        if rng.random() < 0.1 and artist:
            artist = artist.replace(" & ", " and ") if " & " in artist else artist.replace(" and ", " & ")
        if rng.random() < 0.12:
            title = title.replace("’", "'").replace("'", "") if rng.random() < 0.5 else title.replace("'", "’")
        if rng.random() < p_title_edition:
            title += rng.choice(REMASTER_TITLE).format(y=(row["year"] or 2011) + rng.randint(0, 30))
        if rng.random() < p_album_edition:
            album += rng.choice(EDITION_ALBUM).format(y=(row["year"] or 2011) + rng.randint(0, 30))
        if rng.random() < 0.1:
            title = re.sub(r"\(([^)]*)\)$", lambda m: rng.choice([f"[{m.group(1)}]", f"- {m.group(1)}"]), title)
        if rng.random() < (0.3 if row["discs"] == 1 else 0.1):
            disc = None
        roll = rng.random()
        if roll < 0.25:
            year = row["rg_year"]
        elif roll < 0.35:
            year = None
        elif roll < 0.4 and year:
            year += rng.choice([-1, 1])
        if rng.random() < (0.3 if row["compilation"] else 0.12):
            album_artist = None
    if duration:
        if not lb:
            duration += rng.choice(jitter)
        # Subsonic servers report whole seconds
        if rng.random() < 0.3:
            duration = round(duration / 1000) * 1000
    elif rng.random() < 0.5:
        duration = None
    return [title, album, album_artist, artist, disc, track, duration, year]


def untagged(row, number):
    rec = render(row, True)
    rec[0], rec[1], rec[2], rec[3] = f"Track {number:02d}", "Unknown Album", None, None
    return rec


def clean(v):
    return "" if v is None else str(v).replace("\t", " ").replace("\n", " ").replace("\r", " ")


HEADER = ["pair_id", "category", "label"] + [f"{s}_{f}" for s in "ab" for f in
                                             ["title", "album", "album_artist", "artist", "disc", "track", "duration_ms", "year"]]
out = []
for category, cat_pairs in categories.items():
    for a, b in cat_pairs:
        ra, rb = rows[a], rows[b]
        label = "same" if ra["rec"] == rb["rec"] and ra["rg"] == rb["rg"] else "different"
        if category == "pos_same_release_lb":
            left, right = render(ra, rng.random() < 0.5), render(rb, False, rng.choice(lb_by_tid[b]))
        elif category == "neg_untagged_rip":
            left, right = untagged(ra, ra["track"]), untagged(rb, rb["track"])
        elif category in UNKNOWN_ALBUM:
            # Both sources fill the gap the same way, as one server or tagger would
            left, right = render(ra, True), render(rb, True)
            album = rng.choice(PLACEHOLDER_ALBUM)
            album_artist = rng.choice([None, None, "Unknown Artist"])
            for side in (left, right):
                side[1], side[2], side[4], side[7] = album, album_artist, None, None
        elif category == "neg_generic_album_no_album_artist":
            left, right = render(ra, True), render(rb, True)
            left[2] = right[2] = None
        else:
            left, right = render(ra, True), render(rb, True)
            if category == "neg_disc_collision":
                if rng.random() < 0.7:
                    right[4] = None
                else:
                    left[4] = right[4] = None
        out.append([category, label] + left + right)

rng.shuffle(out)
with open(f"{DATA}/corpus/pairs.tsv", "w", encoding="utf-8") as f:
    f.write("\t".join(HEADER) + "\n")
    for i, rec in enumerate(out):
        f.write("\t".join([str(i)] + [clean(v) for v in rec]) + "\n")

counts = {}
for rec in out:
    counts.setdefault((rec[0], rec[1]), 0)
    counts[(rec[0], rec[1])] += 1
print(f"pairs: {len(out)}")
for (category, label), count in sorted(counts.items()):
    print(f"  {category:42s} {label:9s} {count}")

if FIXTURE:
    # Stratified, at most 2,000 pairs; the noise is from ListenBrainz and MusicBrainz alone (CC0)
    by_category = {}
    for rec in out:
        by_category.setdefault(rec[0], []).append(rec)
    per_category = 2000 // len(by_category)
    picked = [rec for recs in by_category.values() for rec in recs[:per_category]]
    with open(FIXTURE, "w", encoding="utf-8") as f:
        f.write("\t".join(HEADER) + "\n")
        for i, rec in enumerate(picked):
            f.write("\t".join([str(i)] + [clean(v) for v in rec]) + "\n")
    print(f"fixture: {len(picked)} pairs -> {FIXTURE}")
