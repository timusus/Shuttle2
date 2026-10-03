#!/usr/bin/env python3
"""Builds the invented library the App Store screenshots show, as tagged audio files for the app's Documents folder.

    ./build-library.py [OUT]        OUT defaults to library/ next to this script (git-ignored)

Reads the sample library manifest (android/fixtures/src/main/resources/sample-library/library.json: 16 albums by
invented artists, every name made up) and writes <artist>/<album>/<nn> <title>.flac for each track: silent 16-bit
44.1 kHz FLAC at the manifest's duration, tagged (title, artist, album artist, album, track, disc, year, genre) and
with the album's generated cover (covers/<id>.jpg) embedded as its front cover. Silence compresses to almost nothing,
so the whole library is a few MB, yet every duration, the progress bar and the "FLAC 16/44.1" badge read as real.

A file that already exists is kept, so a second run costs nothing; delete OUT to rebuild it after the manifest changes.
"""
import json
import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[2]
LIBRARY = REPO / "android/fixtures/src/main/resources/sample-library"


def safe(name: str) -> str:
    """A path component: no slashes, no leading dot."""
    return name.replace("/", "-").replace(":", "-").lstrip(".")


def main() -> int:
    out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "library"
    manifest = json.loads((LIBRARY / "library.json").read_text())
    written = 0
    for album in manifest["albums"]:
        folder = out / safe(album["artist"]) / safe(album["title"])
        folder.mkdir(parents=True, exist_ok=True)
        cover = LIBRARY / "covers" / f"{album['id']}.jpg"
        tracks = album["tracks"]
        for number, track in enumerate(tracks, 1):
            file = folder / f"{number:02d} {safe(track['title'])}.flac"
            if file.exists():
                continue
            tags = {
                "title": track["title"],
                "artist": track.get("artist", album["artist"]),
                "album_artist": album["artist"],
                "album": album["title"],
                "track": f"{number}/{len(tracks)}",
                "disc": "1/1",
                "date": str(album["year"]),
                "genre": album["genre"],
            }
            command = [
                "ffmpeg", "-nostdin", "-loglevel", "error",
                "-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo", "-i", str(cover),
                "-t", str(track["duration"]), "-map", "0:a", "-map", "1:v",
                "-c:a", "flac", "-sample_fmt", "s16", "-c:v", "copy", "-disposition:v", "attached_pic",
                "-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)",
            ]
            for key, value in tags.items():
                command += ["-metadata", f"{key}={value}"]
            subprocess.run(command + ["-y", str(file)], check=True)
            written += 1
    total = sum(len(a["tracks"]) for a in manifest["albums"])
    print(f"build-library: {total} tracks in {out} ({written} written)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
