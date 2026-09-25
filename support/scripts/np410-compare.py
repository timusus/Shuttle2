#!/usr/bin/env python3
"""Stitches the #410 Now Playing variant renders into side-by-side comparison boards.

Writes docs/design/np-sheet-410/compare-rest.png, (A) to (D) at rest on the phone, and
compare-expanded.png, each variant at its queue, left to right with a label over each. Record the
renders first:

    ./gradlew :android:app:recordRoborazziDebug --tests '*NowPlayingSheetVariants*'

Usage: support/scripts/np410-compare.py   (needs Python 3 and Pillow)
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parents[2]
SHOTS = REPO / "docs/design/np-sheet-410"
FONT = REPO / "android/app/src/main/res/font/opensans_semibold.ttf"

VARIANTS = [
    ("a-full-level", "A  Full level"),
    ("b-content-height", "B  Content height"),
    ("c-artwork-fill", "C  Artwork fills"),
    ("d-podcasts", "D  Podcasts panels"),
]
BOARDS = [("compare-rest.png", "rest"), ("compare-expanded.png", "queue")]

GAP = 32
LABEL_HEIGHT = 96


def board(out: str, state: str) -> Path:
    paths = [SHOTS / f"phone-{slug}-{state}.png" for slug, _ in VARIANTS]
    missing = [str(p) for p in paths if not p.exists()]
    if missing:
        sys.exit("np410-compare: missing " + ", ".join(missing))
    shots = [Image.open(p).convert("RGB") for p in paths]
    width, height = shots[0].size
    canvas = Image.new("RGB", (GAP + len(shots) * (width + GAP), LABEL_HEIGHT + height + GAP), "white")
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype(str(FONT), 52)
    for i, (shot, (_, label)) in enumerate(zip(shots, VARIANTS)):
        x = GAP + i * (width + GAP)
        draw.text((x + width / 2, LABEL_HEIGHT / 2), label, fill="black", font=font, anchor="mm")
        canvas.paste(shot, (x, LABEL_HEIGHT))
        draw.rectangle((x - 1, LABEL_HEIGHT - 1, x + width, LABEL_HEIGHT + height), outline="#b0b0b0", width=2)
    path = SHOTS / out
    canvas.save(path, optimize=True)
    return path


if __name__ == "__main__":
    for out, state in BOARDS:
        print(board(out, state))
