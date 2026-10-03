#!/usr/bin/env python3
"""Gives the screenshot simulator a listening history, so Home opens on its real sections rather than its first-run hint.

    ./seed-history.py <song.db>     the app's database, with the app stopped

A fresh library has no history, so Home opens on its cold-start hint ("Home learns from what you play…") and
recently-added rows. This writes completed plays of a handful of the invented albums, near the current hour, into
play_events, and marks those songs played (lastCompleted, playCount), so Home opens on Jump Back In with Recently
Added below it. The plays are contextless ('none'), as a song played from a list is, and fall 29 to 42 days back:
outside Heavy Rotation's 28-day window, whose artist circles have no cover for an invented artist (artist images come
from the S2 artwork API by name), and inside Rediscover's 90 days. Runs again cleanly: it clears play_events first.
"""
import datetime
import random
import sqlite3
import sys

# Albums the history favours, most-played first; the screenshots' own album (Undertow) is played live by the capture.
ALBUMS = ["Cassette Summer", "Night Bus Frequencies", "Phase Garden", "Harbour Weather", "Slow Bloom", "Signal Room"]
# Days back the plays fall on: past Heavy Rotation's 28-day window, short of Rediscover's 90.
DAYS = range(29, 43)


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    random.seed(7)
    db = sqlite3.connect(sys.argv[1])
    now = datetime.datetime.now().astimezone()
    db.execute("DELETE FROM play_events")
    events = 0
    for rank, album in enumerate(ALBUMS):
        songs = db.execute("SELECT mediaProvider, path, duration FROM songs WHERE album = ?", (album,)).fetchall()
        if not songs:
            print(f"seed-history: no songs on {album!r}; was the library imported?", file=sys.stderr)
            return 1
        # The favourites are played on most days, the rest on a few.
        days = sorted(random.sample(DAYS, max(3, len(DAYS) - 2 * rank)), reverse=True)
        for day in days:
            started = now - datetime.timedelta(days=day, minutes=random.randint(-50, 50))
            for provider, path, duration in random.sample(songs, min(len(songs), 4)):
                at = int(started.timestamp() * 1000)
                db.execute(
                    "INSERT INTO play_events (mediaProvider, songPath, startedAt, listenedMs, completed, localHour,"
                    " weekday, contextType, contextId) VALUES (?, ?, ?, ?, 1, ?, ?, 'none', NULL)",
                    (provider, path, at, duration, started.hour, started.isoweekday()),
                )
                db.execute(
                    "UPDATE songs SET lastCompleted = MAX(COALESCE(lastCompleted, 0), ?), playCount = playCount + 1"
                    " WHERE path = ?",
                    (at, path),
                )
                started += datetime.timedelta(milliseconds=duration)
                events += 1
    db.commit()
    db.close()
    print(f"seed-history: {events} plays across {len(ALBUMS)} albums", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
