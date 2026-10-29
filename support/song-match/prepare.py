"""Loads the subset of MusicBrainz, ListenBrainz and AcoustID data the corpus needs into work/song-match.duckdb.

The MusicBrainz subset is seeded from the releases ListenBrainz listeners actually play (listens that carry a release
MBID, mostly from Navidrome and other file-based players), then widened with what makes hard negatives: every other
release of their release groups, releases of other albums with the same title, compilations holding the same
recordings, and other recordings by the same artist with the same title.

Usage: venv/bin/python -I prepare.py [data dir]
"""

import os
import sys

import duckdb

DATA = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/data/song-match")
MB = f"{DATA}/raw/mb/x/mbdump"

# Column layouts of the MusicBrainz server schema (admin/sql/CreateTables.sql); the dumps carry no header.
COLUMNS = {
    "artist_credit": ["id", "name", "artist_count", "ref_count", "created", "edits_pending", "gid"],
    "recording": ["id", "gid", "name", "artist_credit", "length", "comment", "edits_pending", "last_updated", "video"],
    "track": ["id", "gid", "recording", "medium", "position", "number", "name", "artist_credit", "length",
              "edits_pending", "last_updated", "is_data_track"],
    "medium": ["id", "release", "position", "format", "name", "edits_pending", "last_updated", "track_count", "gid"],
    "release": ["id", "gid", "name", "artist_credit", "release_group", "status", "packaging", "language", "script",
                "barcode", "comment", "edits_pending", "quality", "last_updated"],
    "release_group": ["id", "gid", "name", "artist_credit", "type", "comment", "edits_pending", "last_updated"],
    "release_country": ["release", "country", "date_year", "date_month", "date_day"],
    "release_unknown_country": ["release", "date_year", "date_month", "date_day"],
    "release_group_secondary_type": ["id", "name", "parent", "child_order", "description", "gid"],
    "release_group_secondary_type_join": ["release_group", "secondary_type", "created"],
}


def mb(table):
    names = COLUMNS[table]
    with open(f"{MB}/{table}", encoding="utf-8") as f:
        found = len(f.readline().rstrip("\n").split("\t"))
    # Checked against schema sequence 31; a schema change must be read before the columns are trusted
    if found != len(names):
        sys.exit(f"{table}: {found} columns in the dump, {len(names)} expected; update COLUMNS from CreateTables.sql")
    cols = ", ".join(f"'{n}': 'VARCHAR'" for n in names)
    return (f"read_csv('{MB}/{table}', delim='\t', header=false, quote='', escape='', nullstr='\\N', "
            f"columns={{{cols}}}, parallel=true)")


db = duckdb.connect(f"{DATA}/work/song-match.duckdb")
db.execute("SET preserve_insertion_order=false")
q = db.execute

# ListenBrainz: listens whose client sent the recording MBID, i.e. the file's own MusicBrainz tags
q(f"""CREATE OR REPLACE TABLE lb AS
SELECT track_metadata.track_name AS title, track_metadata.artist_name AS artist, track_metadata.release_name AS album,
  json_extract_string(track_metadata.additional_info, '$.recording_mbid') AS rec_gid,
  json_extract_string(track_metadata.additional_info, '$.release_mbid') AS rel_gid,
  TRY_CAST(json_extract_string(track_metadata.additional_info, '$.duration_ms') AS BIGINT) AS duration_ms,
  TRY_CAST(json_extract_string(track_metadata.additional_info, '$.tracknumber') AS INTEGER) AS track,
  TRY_CAST(json_extract_string(track_metadata.additional_info, '$.discnumber') AS INTEGER) AS disc,
  json_extract_string(track_metadata.additional_info, '$.submission_client') AS client
FROM read_json('{DATA}/raw/lb/x/*/listens/*/*.listens', format='newline_delimited',
  columns={{'track_metadata': 'STRUCT(track_name VARCHAR, artist_name VARCHAR, release_name VARCHAR, additional_info JSON)'}})
WHERE json_extract_string(track_metadata.additional_info, '$.recording_mbid') IS NOT NULL""")

# AcoustID: file lengths (whole seconds) of fingerprints submitted against a recording
q(f"""CREATE OR REPLACE TABLE acoustid_length AS
WITH fp AS (SELECT id, length FROM read_json('{DATA}/raw/acoustid/*-fingerprint.jsonl.gz', columns={{'id': 'BIGINT', 'length': 'INTEGER'}})),
tf AS (SELECT track_id, fingerprint_id FROM read_json('{DATA}/raw/acoustid/*-track_fingerprint.jsonl.gz', columns={{'track_id': 'BIGINT', 'fingerprint_id': 'BIGINT'}})),
tm AS (SELECT DISTINCT track_id, mbid FROM read_json('{DATA}/raw/acoustid/*-track_mbid.jsonl.gz', columns={{'track_id': 'BIGINT', 'mbid': 'VARCHAR', 'disabled': 'BOOLEAN'}}) WHERE NOT coalesce(disabled, false))
SELECT tm.mbid AS rec_gid, fp.length FROM fp JOIN tf ON tf.fingerprint_id = fp.id JOIN tm ON tm.track_id = tf.track_id""")

q(f"CREATE OR REPLACE TABLE release AS SELECT id::BIGINT id, gid, name, artist_credit::BIGINT ac, release_group::BIGINT rg, status::INT status, comment FROM {mb('release')}")
q(f"CREATE OR REPLACE TABLE release_group AS SELECT id::BIGINT id, gid, name, artist_credit::BIGINT ac, type::INT type, comment FROM {mb('release_group')}")
q(f"CREATE OR REPLACE TABLE artist_credit AS SELECT id::BIGINT id, name FROM {mb('artist_credit')}")
q(f"CREATE OR REPLACE TABLE medium AS SELECT id::BIGINT id, release::BIGINT release, position::INT position, track_count::INT track_count FROM {mb('medium')}")
q(f"""CREATE OR REPLACE TABLE rg_secondary AS SELECT j.release_group::BIGINT rg, t.name
FROM {mb('release_group_secondary_type_join')} j JOIN {mb('release_group_secondary_type')} t ON t.id = j.secondary_type""")
q(f"""CREATE OR REPLACE TABLE release_year AS SELECT release::BIGINT release, min(date_year::INT) AS year FROM (
  SELECT release, date_year FROM {mb('release_country')} UNION ALL SELECT release, date_year FROM {mb('release_unknown_country')})
WHERE date_year IS NOT NULL GROUP BY 1""")

# The seed: release groups of releases listeners play, and every release in them
q("""CREATE OR REPLACE TABLE seed_release AS SELECT DISTINCT r.id, r.rg, lower(r.name) AS lname
FROM release r JOIN (SELECT DISTINCT rel_gid FROM lb) l ON l.rel_gid = r.gid""")
q("CREATE OR REPLACE TABLE seed_rg AS SELECT DISTINCT rg FROM seed_release")
# Other albums with a seed album's title, at most six release groups a title, so "Greatest Hits" doesn't take over
q("""CREATE OR REPLACE TABLE title_rg AS SELECT rg FROM (
  SELECT r.rg, row_number() OVER (PARTITION BY lower(r.name) ORDER BY hash(r.rg)) AS n
  FROM release r JOIN (SELECT DISTINCT lname FROM seed_release) s ON s.lname = lower(r.name)
  WHERE r.rg NOT IN (SELECT rg FROM seed_rg) GROUP BY r.rg, lower(r.name)) WHERE n <= 6""")
q("""CREATE OR REPLACE TABLE sel_release AS SELECT r.* FROM release r
WHERE r.rg IN (SELECT rg FROM seed_rg UNION SELECT rg FROM title_rg)""")
q("CREATE OR REPLACE TABLE sel_medium AS SELECT m.* FROM medium m WHERE m.release IN (SELECT id FROM sel_release)")

q(f"CREATE OR REPLACE TABLE recording AS SELECT id::BIGINT id, gid, name, artist_credit::BIGINT ac, length::BIGINT length, comment FROM {mb('recording')}")
q(f"""CREATE OR REPLACE TABLE track_all AS SELECT recording::BIGINT recording, medium::BIGINT medium, position::INT position,
  number, name, artist_credit::BIGINT ac, length::BIGINT length FROM {mb('track')}""")

q("""CREATE OR REPLACE TABLE track AS SELECT t.* FROM track_all t WHERE t.medium IN (SELECT id FROM sel_medium)""")
q("""CREATE OR REPLACE TABLE seed_title AS SELECT DISTINCT t.ac, lower(t.name) lname FROM track t
JOIN medium m ON m.id = t.medium JOIN seed_release s ON s.id = m.release""")
# Elsewhere: the seed recordings on other release groups (compilations) and same-titled recordings by the same artist
q("""CREATE OR REPLACE TABLE track_elsewhere AS SELECT t.* FROM track_all t
WHERE t.medium NOT IN (SELECT id FROM sel_medium)
  AND (t.recording IN (SELECT DISTINCT recording FROM track) OR (t.ac, lower(t.name)) IN (SELECT ac, lname FROM seed_title))""")
q("""CREATE OR REPLACE TABLE track_elsewhere AS SELECT * FROM (
  SELECT t.*, row_number() OVER (PARTITION BY t.recording ORDER BY hash(t.medium)) AS n FROM track_elsewhere t) WHERE n <= 4""")
q("""INSERT INTO sel_medium SELECT m.* FROM medium m WHERE m.id IN (SELECT medium FROM track_elsewhere) AND m.id NOT IN (SELECT id FROM sel_medium)""")
q("""INSERT INTO sel_release SELECT r.* FROM release r WHERE r.id IN (SELECT release FROM sel_medium) AND r.id NOT IN (SELECT id FROM sel_release)""")
q("INSERT INTO track SELECT recording, medium, position, number, name, ac, length FROM track_elsewhere")
q("CREATE OR REPLACE TABLE recording AS SELECT * FROM recording WHERE id IN (SELECT recording FROM track)")
q("DROP TABLE track_all")
q("DROP TABLE track_elsewhere")
q("DROP TABLE medium")

for table in ["lb", "acoustid_length", "seed_release", "seed_rg", "sel_release", "sel_medium", "track", "recording"]:
    print(table, db.execute(f"SELECT count(*) FROM {table}").fetchone()[0])
db.execute("CHECKPOINT")
