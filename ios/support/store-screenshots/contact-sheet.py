#!/usr/bin/env python3
"""Side-by-side contact sheet (<= 2400 px wide) of a directory of PNGs: contact-sheet.py <dir> <out.png>."""
import sys
from pathlib import Path
from PIL import Image

src, out = Path(sys.argv[1]), Path(sys.argv[2])
files = sorted(src.glob("*.png"), key=lambda p: int(p.stem) if p.stem.isdigit() else 0)
files = [f for f in files if f.stem.isdigit()]
ims = [Image.open(f).convert("RGB") for f in files]
h = 1000
ims = [i.resize((round(i.width * h / i.height), h)) for i in ims]
sheet = Image.new("RGB", (sum(i.width for i in ims) + 8 * (len(ims) - 1), h), "white")
x = 0
for i in ims:
    sheet.paste(i, (x, 0))
    x += i.width + 8
sheet.thumbnail((2400, 2400))
sheet.save(out)
print(out, sheet.size)
