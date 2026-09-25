#!/usr/bin/env python3
"""Generates the invented sample library's album covers and the review contact sheet.

Reads android/fixtures/src/main/resources/sample-library/library.json (the one source of truth for
the sample artists, albums, songs and playlists) and draws one 600x600 JPEG per album into
covers/<id>.jpg next to it, from the album's `cover` block (style, palette, seed). Then writes
docs/design/fake-artwork/contact-sheet.png, a 4x4 grid of every cover with its title.

Deterministic: every random choice comes from the album's seed, the grain comes from Python's
seeded RNG rather than Pillow's C noise, and the type is the Open Sans shipped in the repo, so a
re-run produces byte-identical files. Every cover is original, generated artwork.

Usage: support/scripts/generate-fake-artwork.py   (needs Python 3 and Pillow)
"""

import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

REPO = Path(__file__).resolve().parents[2]
LIBRARY_DIR = REPO / "android/fixtures/src/main/resources/sample-library"
MANIFEST = LIBRARY_DIR / "library.json"
COVERS_DIR = LIBRARY_DIR / "covers"
CONTACT_SHEET = REPO / "docs/design/fake-artwork/contact-sheet.png"
FONT_DIR = REPO / "android/app/src/main/res/font"

SIZE = 600  # the stored cover
SS = 2  # drawn at twice the size and downsampled, for anti-aliased edges
S = SIZE * SS
MAX_BYTES = 60 * 1024


def rgb(hex_colour):
    h = hex_colour.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def font(weight, size):
    """Open Sans from the repo, or None when it's missing (covers then fall back to shapes only)."""
    path = FONT_DIR / f"opensans_{weight}.ttf"
    return ImageFont.truetype(str(path), size) if path.exists() else None


def fitted_font(weight, text, width, max_size):
    f = font(weight, max_size)
    if f is None:
        return None
    size = max_size
    while size > 8 and f.getlength(text) > width:
        size -= 2
        f = font(weight, size)
    return f


def text(draw, xy, value, fill, weight="semibold", size=40, anchor="la", spacing=0):
    f = font(weight, size)
    if f is not None:
        draw.text(xy, value, fill=fill, font=f, anchor=anchor, spacing=spacing)


def noise_layer(rng, size, cells):
    """Smooth value noise: a cells x cells grid of random greys upscaled bicubically."""
    small = Image.frombytes("L", (cells, cells), rng.randbytes(cells * cells))
    return small.resize((size, size), Image.BICUBIC)


def fractal_noise(rng, size, octaves=((4, 0.55), (9, 0.3), (20, 0.15))):
    out = None
    total = 0.0
    for cells, weight in octaves:
        layer = noise_layer(rng, size, cells)
        if out is None:
            out, total = layer, weight
        else:
            total += weight
            out = Image.blend(out, layer, weight / total)
    return autocontrast(out)


def autocontrast(image):
    lo, hi = image.getextrema()
    if hi <= lo:
        return image
    return image.point(lambda v: round((v - lo) * 255 / (hi - lo)))


def grain(image, rng, amount):
    """Film grain: seeded per-pixel noise overlaid around mid-grey, so it keeps the image's tone."""
    w, h = image.size
    noise = Image.frombytes("L", (w, h), rng.randbytes(w * h))
    noise = noise.point(lambda v: round(128 + (v - 128) * amount))
    return ImageChops.overlay(image.convert("RGB"), Image.merge("RGB", (noise, noise, noise)))


def vertical_gradient(size, top, bottom):
    mask = Image.linear_gradient("L").resize((size, size))
    return Image.composite(Image.new("RGB", (size, size), bottom), Image.new("RGB", (size, size), top), mask)


def glow(size, centre, radius, colour, strength=1.0):
    """An RGBA soft radial glow."""
    g = Image.radial_gradient("L").resize((radius * 2, radius * 2))
    g = g.point(lambda v: round(max(0.0, 1 - v / 181) ** 2 * 255 * strength))
    layer = Image.new("RGBA", (size, size), colour + (0,))
    alpha = Image.new("L", (size, size), 0)
    alpha.paste(g, (centre[0] - radius, centre[1] - radius))
    layer.putalpha(alpha)
    return layer


def ink_mask(rng, mask, dropout):
    """Riso ink: the shape mask with speckled dropout where the drum missed."""
    w, h = mask.size
    speck = Image.frombytes("L", (w, h), rng.randbytes(w * h)).filter(ImageFilter.GaussianBlur(0.6 * SS))
    speck = speck.point(lambda v: 0 if v < 128 - dropout else 255)
    return ImageChops.multiply(mask, speck)


def multiply_ink(paper, mask, ink, offset=(0, 0)):
    shifted = ImageChops.offset(mask, *offset)
    layer = Image.composite(Image.new("RGB", paper.size, ink), Image.new("RGB", paper.size, (255, 255, 255)), shifted)
    return ImageChops.multiply(paper, layer)


# --- styles -----------------------------------------------------------------------------------


def halftone(album, rng, p):
    bg, dot, light, deep = (rgb(c) for c in p)
    img = Image.new("RGB", (S, S), bg)
    # A tone field of overlapping petals around a centre, softened by noise.
    tone = Image.new("L", (S, S), 0)
    td = ImageDraw.Draw(tone)
    cx, cy = S * 0.52, S * 0.46
    for i in range(6):
        a = i * math.pi / 3 + 0.3
        px, py = cx + math.cos(a) * S * 0.2, cy + math.sin(a) * S * 0.2
        r = S * 0.2
        td.ellipse((px - r, py - r, px + r, py + r), fill=190)
    td.ellipse((cx - S * 0.12, cy - S * 0.12, cx + S * 0.12, cy + S * 0.12), fill=255)
    tone = tone.filter(ImageFilter.GaussianBlur(S * 0.05))
    tone = Image.blend(tone, fractal_noise(rng, S), 0.25)
    # Glow in the deep colour behind the dots.
    img.paste(Image.new("RGB", (S, S), deep), mask=tone.point(lambda v: v // 2))
    draw = ImageDraw.Draw(img)
    step = 26 * SS
    angle = math.radians(30)
    ca, sa = math.cos(angle), math.sin(angle)
    for layer, colour, shift, scale in ((0, dot, 0, 0.62), (1, light, step * 0.35, 0.32)):
        n = int(S / step) + 8
        for i in range(-n, n):
            for j in range(-n, n):
                u, v = i * step + shift, j * step + shift
                x = S / 2 + u * ca - v * sa
                y = S / 2 + u * sa + v * ca
                if not (0 <= x < S and 0 <= y < S):
                    continue
                t = tone.getpixel((int(x), int(y))) / 255
                if layer == 1:
                    t = max(0.0, t - 0.45) * 1.8
                r = step * scale * t
                if r > 1.5:
                    draw.ellipse((x - r, y - r, x + r, y + r), fill=colour)
    text(draw, (60 * SS, S - 60 * SS), album["artist"].upper(), light, "semibold", 26 * SS, "ls")
    text(draw, (S - 60 * SS, S - 60 * SS), album["title"].lower(), light, "light", 26 * SS, "rs")
    return img


def contour(album, rng, p):
    bg, line, mid, pale = (rgb(c) for c in p)
    field = fractal_noise(rng, S, ((3, 0.6), (7, 0.3), (15, 0.1))).filter(ImageFilter.GaussianBlur(4 * SS))
    bands = 18
    quant = field.point(lambda v: min(bands - 1, v * bands // 256) * (255 // bands))
    edges = quant.filter(ImageFilter.FIND_EDGES).point(lambda v: 255 if v > 0 else 0).filter(ImageFilter.MaxFilter(3))
    # FIND_EDGES also traces the image border; clear it.
    ImageDraw.Draw(edges).rectangle((0, 0, S - 1, S - 1), outline=0, width=3)
    # Brighter lines towards the peaks.
    bright = Image.composite(Image.new("RGB", (S, S), line), Image.new("RGB", (S, S), mid), field)
    img = Image.new("RGB", (S, S), bg)
    img.paste(bright, mask=edges)
    img = img.filter(ImageFilter.GaussianBlur(0.4 * SS))
    img = Image.alpha_composite(img.convert("RGBA"), glow(S, (int(S * 0.7), int(S * 0.3)), int(S * 0.08), pale, 0.9)).convert("RGB")
    draw = ImageDraw.Draw(img)
    r = 7 * SS
    draw.ellipse((S * 0.7 - r, S * 0.3 - r, S * 0.7 + r, S * 0.3 + r), fill=pale)
    text(draw, (56 * SS, 64 * SS), album["artist"].lower(), pale, "light", 24 * SS)
    text(draw, (56 * SS, 100 * SS), album["title"].lower(), line, "semibold", 24 * SS)
    return img


def riso_landscape(album, rng, p):
    paper, ochre, teal, brown = (rgb(c) for c in p)
    img = Image.new("RGB", (S, S), paper)
    a = Image.new("L", (S, S), 0)
    ad = ImageDraw.Draw(a)
    r = S * 0.2
    ad.ellipse((S * 0.52 - r, S * 0.3 - r, S * 0.52 + r, S * 0.3 + r), fill=255)
    hills = [(0, S * 0.58)] + [(x, S * 0.58 - math.sin(x / S * 5 + 1) * S * 0.04 - rng.random() * 6 * SS) for x in range(0, S + 1, 40)] + [(S, S * 0.66), (0, S * 0.66)]
    ad.polygon(hills, fill=255)
    b = Image.new("L", (S, S), 0)
    bd = ImageDraw.Draw(b)
    bd.rectangle((0, S * 0.64, S, S * 0.86), fill=200)
    for i in range(6):
        y = S * 0.68 + i * S * 0.03
        bd.line((S * 0.08 + rng.random() * 40 * SS, y, S * 0.92 - rng.random() * 40 * SS, y), fill=0, width=int(3 * SS))
    # A small boat and a harbour arm.
    bd.polygon([(S * 0.3, S * 0.61), (S * 0.44, S * 0.61), (S * 0.41, S * 0.645), (S * 0.33, S * 0.645)], fill=255)
    bd.line((S * 0.37, S * 0.61, S * 0.37, S * 0.5), fill=255, width=int(3 * SS))
    bd.polygon([(S * 0.37, S * 0.5), (S * 0.43, S * 0.59), (S * 0.37, S * 0.59)], fill=255)
    bd.rectangle((S * 0.72, S * 0.55, S, S * 0.61), fill=255)
    bd.rectangle((S * 0.84, S * 0.42, S * 0.88, S * 0.55), fill=255)
    img = multiply_ink(img, ink_mask(rng, a, 40), ochre)
    img = multiply_ink(img, ink_mask(rng, b, 40), teal, (5 * SS, -4 * SS))
    draw = ImageDraw.Draw(img)
    text(draw, (S / 2, 34 * SS), album["artist"].upper(), brown, "semibold", 22 * SS, "ma")
    text(draw, (S / 2, S - 30 * SS), album["title"], brown, "italic", 34 * SS, "ms")
    return grain(img, rng, 0.18)


def typographic_serif(album, rng, p):
    paper, red, ink, blush = (rgb(c) for c in p)
    img = Image.new("RGB", (S, S), paper)
    draw = ImageDraw.Draw(img)
    words = album["title"].split()
    margin = 48 * SS
    width = S - margin * 2
    if font("light", 10) is None:
        draw.ellipse((S * 0.2, S * 0.2, S * 0.8, S * 0.8), fill=red)
        return img
    # Greedy lines of up to ~16 characters, each set to fill the width: a justified stack.
    lines, current = [], ""
    for word in words:
        candidate = f"{current} {word}".strip()
        if len(candidate) > 16 and current:
            lines.append(current)
            current = word
        else:
            current = candidate
    lines.append(current)
    fonts = [fitted_font("semibold" if i == 0 else "light", line, width, 200 * SS) for i, line in enumerate(lines)]
    gap = 6 * SS

    def extents():
        return [draw.textbbox((0, 0), line, font=f, anchor="ls") for f, line in zip(fonts, lines)]

    boxes = extents()
    total = sum(b[3] - b[1] for b in boxes) + gap * (len(lines) - 1)
    available = S - margin * 2 - 70 * SS
    if total > available:
        fonts = [font("semibold" if i == 0 else "light", int(f.size * available / total)) for i, f in enumerate(fonts)]
        boxes = extents()
    y = margin
    for f, line, box in zip(fonts, lines, boxes):
        draw.text((margin, y - box[1]), line, fill=red, font=f, anchor="ls")
        y += box[3] - box[1] + gap
    draw.rectangle((margin, S - margin - 40 * SS, margin + 60 * SS, S - margin - 36 * SS), fill=ink)
    text(draw, (margin, S - margin), album["artist"].upper(), ink, "semibold", 22 * SS, "ls")
    text(draw, (S - margin, S - margin), str(album["year"]), blush, "semibold", 22 * SS, "rs")
    return grain(img, rng, 0.08)


def stripes(album, rng, p):
    paper, orange, rust, brown, mustard = (rgb(c) for c in p)
    img = Image.new("RGB", (S, S), paper)
    draw = ImageDraw.Draw(img)
    cx, cy = -S * 0.1, S * 1.1
    band = S * 0.085
    colours = [mustard, orange, rust, brown]
    outer = S * 0.95
    for i, colour in enumerate(colours):
        r = outer - i * band
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=colour)
    r = outer - len(colours) * band
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=paper)
    # A second, smaller rainbow echoing the first.
    cx2, cy2 = S * 0.86, S * 0.3
    for i, colour in enumerate(colours):
        r = S * 0.13 - i * band * 0.28
        draw.ellipse((cx2 - r, cy2 - r, cx2 + r, cy2 + r), fill=colour)
    draw.ellipse((cx2 - S * 0.018, cy2 - S * 0.018, cx2 + S * 0.018, cy2 + S * 0.018), fill=paper)
    text(draw, (56 * SS, 80 * SS), album["artist"].lower(), brown, "semibold", 34 * SS)
    text(draw, (56 * SS, 122 * SS), album["title"].lower(), rust, "light", 34 * SS)
    return grain(img, rng, 0.14)


def riso(album, rng, p):
    paper, blue, pink, navy = (rgb(c) for c in p)
    img = Image.new("RGB", (S, S), paper)
    blue_mask = Image.new("L", (S, S), 0)
    bm = ImageDraw.Draw(blue_mask)
    pink_mask = Image.new("L", (S, S), 0)
    pm = ImageDraw.Draw(pink_mask)
    # Coins: a loose stack of circles in two inks, overlapping so the multiply shows.
    coins = [(0.3, 0.38, 0.2), (0.62, 0.3, 0.14), (0.66, 0.62, 0.22), (0.3, 0.72, 0.12), (0.46, 0.52, 0.09)]
    for i, (x, y, r) in enumerate(coins):
        box = (S * (x - r), S * (y - r), S * (x + r), S * (y + r))
        if i % 2 == 0:
            bm.ellipse(box, fill=255)
            inner = S * r * 0.72
            bm.ellipse((S * x - inner, S * y - inner, S * x + inner, S * y + inner), outline=0, width=int(5 * SS))
        else:
            pm.ellipse(box, fill=255)
    pm.rectangle((S * 0.08, S * 0.84, S * 0.92, S * 0.9), fill=255)
    img = multiply_ink(img, ink_mask(rng, blue_mask, 50), blue)
    img = multiply_ink(img, ink_mask(rng, pink_mask, 50), pink, (-6 * SS, 5 * SS))
    draw = ImageDraw.Draw(img)
    text(draw, (S * 0.08, S * 0.1), album["title"].upper(), navy, "semibold", 44 * SS, "ls")
    text(draw, (S * 0.08, S * 0.965), album["artist"].upper(), navy, "semibold", 18 * SS, "ls")
    return grain(img, rng, 0.16)


def bauhaus(album, rng, p):
    blue, cream, ochre, black, red = (rgb(c) for c in p)
    img = Image.new("RGB", (S, S), blue)
    draw = ImageDraw.Draw(img)
    # Big cream semicircle rising from the bottom edge.
    r = S * 0.36
    draw.pieslice((S * 0.08, S * 0.66 - r, S * 0.08 + 2 * r, S * 0.66 + r), 180, 360, fill=cream)
    draw.rectangle((S * 0.08, S * 0.66, S * 0.08 + 2 * r, S * 0.68), fill=cream)
    # Black circle overlapping it.
    r2 = S * 0.15
    draw.ellipse((S * 0.62 - r2, S * 0.3 - r2, S * 0.62 + r2, S * 0.3 + r2), fill=black)
    # Ochre quarter circle in the corner.
    r3 = S * 0.26
    draw.pieslice((S - r3, S - r3, S + r3, S + r3), 180, 270, fill=ochre)
    # A red bar and a column of small circles.
    draw.rectangle((S * 0.08, S * 0.74, S * 0.6, S * 0.76), fill=red)
    for i in range(5):
        y = S * 0.16 + i * S * 0.055
        rr = S * 0.014
        draw.ellipse((S * 0.14 - rr, y - rr, S * 0.14 + rr, y + rr), fill=cream if i != 2 else ochre)
    text(draw, (S * 0.08, S * 0.84), album["artist"], cream, "semibold", 26 * SS)
    text(draw, (S * 0.08, S * 0.89), album["title"], ochre, "light", 26 * SS)
    return grain(img, rng, 0.1)


def flowfield(album, rng, p):
    charcoal, tan, cream, brown = (rgb(c) for c in p)
    img = Image.new("RGB", (S, S), charcoal)
    field = fractal_noise(rng, S // 4, ((3, 0.7), (6, 0.3))).filter(ImageFilter.GaussianBlur(3))
    px = field.load()
    for colour, count, width, blur in ((brown, 500, 2, 3), (tan, 420, 2, 0), (cream, 90, 2, 0)):
        mask = Image.new("L", (S, S), 0)
        md = ImageDraw.Draw(mask)
        for _ in range(count):
            # Smoke rises from the lower middle.
            x = S * (0.3 + rng.random() * 0.4)
            y = S * (0.55 + rng.random() * 0.45)
            points = [(x, y)]
            for _ in range(160):
                v = px[min(S // 4 - 1, max(0, int(x / 4))), min(S // 4 - 1, max(0, int(y / 4)))]
                a = v / 255 * math.pi * 2.4 - math.pi * 0.7
                x += math.cos(a) * 5 * SS * 0.8
                y += (math.sin(a) - 0.9) * 5 * SS * 0.8
                if not (0 <= x < S and 0 <= y < S):
                    break
                points.append((x, y))
            if len(points) > 2:
                md.line(points, fill=160 if colour != cream else 220, width=width)
        if blur:
            mask = mask.filter(ImageFilter.GaussianBlur(blur * SS))
        img.paste(Image.new("RGB", (S, S), colour), mask=mask)
    img = img.filter(ImageFilter.GaussianBlur(0.5 * SS))
    draw = ImageDraw.Draw(img)
    text(draw, (S / 2, 64 * SS), album["artist"].upper(), cream, "light", 22 * SS, "ma")
    text(draw, (S / 2, 100 * SS), album["title"].lower(), tan, "italic", 40 * SS, "ma")
    return grain(img, rng, 0.14)


def gradient_mesh(album, rng, p):
    base, lilac, peach, cream, violet = (rgb(c) for c in p)
    img = Image.new("RGB", (S, S), base)
    draw = ImageDraw.Draw(img)
    blobs = [(lilac, 0.2, 0.25, 0.38), (peach, 0.78, 0.7, 0.42), (cream, 0.7, 0.18, 0.3), (violet, 0.25, 0.85, 0.32), (peach, 0.4, 0.5, 0.18)]
    for colour, x, y, r in blobs:
        x += (rng.random() - 0.5) * 0.08
        y += (rng.random() - 0.5) * 0.08
        draw.ellipse((S * (x - r), S * (y - r), S * (x + r), S * (y + r)), fill=colour)
    img = img.filter(ImageFilter.GaussianBlur(S * 0.11))
    draw = ImageDraw.Draw(img)
    text(draw, (S / 2, S / 2), album["title"].lower(), (255, 255, 255), "light", 54 * SS, "mm")
    text(draw, (S / 2, S - 54 * SS), album["artist"].lower(), (255, 255, 255), "semibold", 20 * SS, "ms")
    return grain(img, rng, 0.12)


def fog(album, rng, p):
    sage, deep, pale, dark = (rgb(c) for c in p)
    img = vertical_gradient(S, pale, sage)
    for i, (colour, top) in enumerate(((mix(pale, sage, 0.5), 0.52), (sage, 0.6), (mix(sage, deep, 0.5), 0.68), (deep, 0.78), (dark, 0.9))):
        mask = Image.new("L", (S, S), 0)
        md = ImageDraw.Draw(mask)
        phase = rng.random() * 6
        pts = [(x, S * top + math.sin(x / S * (3 + i) + phase) * S * 0.02) for x in range(0, S + 1, 20)]
        md.polygon(pts + [(S, S), (0, S)], fill=255)
        mask = mask.filter(ImageFilter.GaussianBlur((10 - i * 2) * SS))
        img.paste(Image.new("RGB", (S, S), colour), mask=mask)
    draw = ImageDraw.Draw(img)
    # Reeds along the bank.
    for _ in range(70):
        x = rng.random() * S
        h = S * (0.05 + rng.random() * 0.1)
        lean = (rng.random() - 0.5) * 20 * SS
        draw.line((x, S, x + lean, S - h), fill=dark, width=int(2 * SS))
    img = img.filter(ImageFilter.GaussianBlur(0.6 * SS))
    draw = ImageDraw.Draw(img)
    text(draw, (60 * SS, 76 * SS), album["title"].upper(), dark, "light", 30 * SS)
    text(draw, (60 * SS, 112 * SS), album["artist"].lower(), deep, "semibold", 20 * SS)
    return grain(img, rng, 0.1)


def horizon_moon(album, rng, p):
    night, dusk, gold, violet = (rgb(c) for c in p)
    img = vertical_gradient(S, night, violet)
    horizon = int(S * 0.62)
    sea = vertical_gradient(S, dusk, night).crop((0, 0, S, S - horizon)).resize((S, S - horizon))
    img.paste(sea, (0, horizon))
    draw = ImageDraw.Draw(img)
    for _ in range(90):
        x, y = rng.random() * S, rng.random() * horizon * 0.9
        r = rng.choice((1, 1, 1.5, 2)) * SS
        draw.ellipse((x - r, y - r, x + r, y + r), fill=mix(gold, (255, 255, 255), 0.5))
    mx, my, mr = int(S * 0.5), int(S * 0.4), int(S * 0.12)
    img = Image.alpha_composite(img.convert("RGBA"), glow(S, (mx, my), int(mr * 3), gold, 0.55)).convert("RGB")
    draw = ImageDraw.Draw(img)
    draw.ellipse((mx - mr, my - mr, mx + mr, my + mr), fill=mix(gold, (255, 255, 255), 0.35))
    # Moonlight broken on the water.
    for i in range(26):
        y = horizon + 10 * SS + i * 9 * SS
        w = (S * 0.12) * (1 - i / 40) * (0.6 + rng.random() * 0.8)
        x = mx + (rng.random() - 0.5) * 30 * SS
        draw.line((x - w, y, x + w, y), fill=mix(gold, dusk, 0.3 + i / 50), width=int(2 * SS))
    draw.line((0, horizon, S, horizon), fill=mix(violet, gold, 0.2), width=SS)
    text(draw, (S / 2, S - 60 * SS), album["title"].lower(), gold, "light", 34 * SS, "ms")
    return grain(img, rng, 0.12)


def op_art(album, rng, p):
    paper, black, red = (rgb(c) for c in p)
    a = Image.new("1", (S, S), 0)
    ad = ImageDraw.Draw(a)
    b = Image.new("1", (S, S), 0)
    bd = ImageDraw.Draw(b)
    step = 14 * SS
    for i in range(int(S * 0.75 / step), 0, -1):
        r = i * step
        ad.ellipse((S * 0.45 - r, S * 0.5 - r, S * 0.45 + r, S * 0.5 + r), fill=i % 2)
        bd.ellipse((S * 0.58 - r, S * 0.44 - r, S * 0.58 + r, S * 0.44 + r), fill=i % 2)
    pattern = ImageChops.logical_xor(a, b)
    ring = Image.new("L", (S, S), 0)
    rd = ImageDraw.Draw(ring)
    rd.ellipse((S * 0.12, S * 0.12, S * 0.88, S * 0.88), fill=255)
    img = Image.new("RGB", (S, S), paper)
    img.paste(Image.new("RGB", (S, S), black), mask=ImageChops.multiply(pattern.convert("L"), ring))
    draw = ImageDraw.Draw(img)
    r = S * 0.03
    draw.ellipse((S * 0.8 - r, S * 0.2 - r, S * 0.8 + r, S * 0.2 + r), fill=red)
    text(draw, (S * 0.06, S * 0.95), album["artist"].upper(), black, "semibold", 18 * SS, "ls")
    text(draw, (S * 0.94, S * 0.95), album["title"].upper(), red, "semibold", 18 * SS, "rs")
    return grain(img, rng, 0.06)


def halftone_sun(album, rng, p):
    cream, green, light, dark = (rgb(c) for c in p)
    img = Image.new("RGB", (S, S), cream)
    draw = ImageDraw.Draw(img)
    cx, cy, R = S * 0.5, S * 0.48, S * 0.34
    step = 18 * SS
    n = int(R * 2 / step) + 2
    for i in range(-n, n + 1):
        for j in range(-n, n + 1):
            x = cx + i * step + (step / 2 if j % 2 else 0)
            y = cy + j * step * 0.87
            d = math.hypot(x - cx, y - cy)
            if d > R:
                continue
            t = 0.25 + 0.75 * ((y - (cy - R)) / (2 * R))
            r = step * 0.48 * t
            draw.ellipse((x - r, y - r, x + r, y + r), fill=green)
    # Leaves unfurling from the base.
    for side in (-1, 1):
        pts = []
        for k in range(21):
            u = k / 20
            pts.append((cx + side * (S * 0.05 + u * S * 0.3), S * 0.86 - math.sin(u * math.pi) * S * 0.09 - u * S * 0.08))
        for k in range(20, -1, -1):
            u = k / 20
            pts.append((cx + side * (S * 0.05 + u * S * 0.3), S * 0.86 - math.sin(u * math.pi) * S * 0.02 - u * S * 0.08))
        draw.polygon(pts, fill=light)
    draw.line((cx, S * 0.82, cx, S * 0.89), fill=dark, width=int(4 * SS))
    text(draw, (S / 2, 52 * SS), album["artist"].upper(), dark, "semibold", 22 * SS, "ma")
    text(draw, (S / 2, S - 26 * SS), album["title"].lower(), dark, "italic", 30 * SS, "ms")
    return grain(img, rng, 0.1)


def swiss_grid(album, rng, p):
    black, lime, white, olive = (rgb(c) for c in p)
    img = Image.new("RGB", (S, S), black)
    draw = ImageDraw.Draw(img)
    cells = 6
    margin = S * 0.1
    cell = (S - 2 * margin) / cells
    for i in range(cells):
        for j in range(cells):
            x0, y0 = margin + i * cell, margin + j * cell
            x1, y1 = x0 + cell, y0 + cell
            pad = cell * 0.08
            kind = rng.random()
            colour = lime if rng.random() < 0.75 else olive
            if kind < 0.3:
                draw.ellipse((x0 + pad, y0 + pad, x1 - pad, y1 - pad), fill=colour)
            elif kind < 0.55:
                start = rng.choice((0, 90, 180, 270))
                cx = x0 if start in (0, 270) else x1
                cy = y0 if start in (0, 90) else y1
                rr = cell - pad
                draw.pieslice((cx - rr, cy - rr, cx + rr, cy + rr), start, start + 90, fill=colour)
            elif kind < 0.7:
                draw.rectangle((x0 + pad, y0 + cell * 0.42, x1 - pad, y0 + cell * 0.58), fill=colour)
            elif kind < 0.8:
                draw.ellipse((x0 + pad, y0 + pad, x1 - pad, y1 - pad), outline=colour, width=int(3 * SS))
    text(draw, (margin, S - margin * 0.35), album["artist"].upper(), white, "semibold", 18 * SS, "ls")
    text(draw, (S - margin, S - margin * 0.35), album["title"].upper(), lime, "semibold", 18 * SS, "rs")
    text(draw, (margin, margin * 0.62), "GR-04", olive, "semibold", 16 * SS, "ls")
    return img


def horizon_sun(album, rng, p):
    wine, rose, blush, crimson = (rgb(c) for c in p)
    img = vertical_gradient(S, wine, rose)
    horizon = int(S * 0.64)
    draw = ImageDraw.Draw(img)
    cx, cy, r = S * 0.5, S * 0.5, S * 0.24
    sun = Image.new("L", (S, S), 0)
    sd = ImageDraw.Draw(sun)
    sd.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    # Retro slices cut from the lower half of the sun, widening towards the horizon.
    for i in range(7):
        y = cy + r * 0.1 + i * r * 0.14
        sd.rectangle((0, y, S, y + (2 + i * 1.6) * SS), fill=0)
    img.paste(vertical_gradient(S, blush, mix(rose, blush, 0.3)), mask=sun)
    sea = vertical_gradient(S, crimson, wine).crop((0, 0, S, S - horizon))
    img.paste(sea, (0, horizon))
    draw = ImageDraw.Draw(img)
    for i in range(22):
        y = horizon + 8 * SS + i * 10 * SS
        w = r * (1 - i / 26) * (0.5 + rng.random() * 0.7)
        draw.line((cx - w, y, cx + w, y), fill=mix(blush, crimson, 0.2 + i / 30), width=int(3 * SS))
    img = img.filter(ImageFilter.GaussianBlur(2.5 * SS))
    draw = ImageDraw.Draw(img)
    text(draw, (S / 2, 70 * SS), album["artist"].lower(), blush, "light", 28 * SS, "ma")
    text(draw, (S / 2, S - 44 * SS), album["title"].upper(), blush, "semibold", 44 * SS, "ms")
    return grain(img, rng, 0.22)


def typographic_swiss(album, rng, p):
    yellow, black, cream, orange = (rgb(c) for c in p)
    img = Image.new("RGB", (S, S), yellow)
    draw = ImageDraw.Draw(img)
    # Wave lines across the lower half.
    for k in range(9):
        base = S * 0.68 + k * 13 * SS
        pts = [(x, base + math.sin(x / S * math.pi * 3 + k * 0.4) * 14 * SS) for x in range(0, S + 1, 8)]
        draw.line(pts, fill=black, width=int(3 * SS))
    number = font("semibold", 330 * SS)
    if number is not None:
        draw.text((S * 1.04, S * 1.08), "02", fill=black, font=number, anchor="rs")
    else:
        draw.ellipse((S * 0.55, S * 0.55, S * 1.1, S * 1.1), fill=black)
    title, _, volume = album["title"].partition(", ")
    margin = 44 * SS
    text(draw, (margin, margin), title, black, "semibold", 56 * SS, "la")
    text(draw, (margin, margin + 66 * SS), volume, orange, "semibold", 56 * SS, "la")
    artists = sorted({t.get("artist", album["artist"]) for t in album["tracks"]})
    text(draw, (margin, margin + 150 * SS), "\n".join(artists), black, "regular", 16 * SS, "la", spacing=4 * SS)
    return grain(img, rng, 0.08)


STYLES = {
    "halftone": halftone,
    "contour": contour,
    "riso-landscape": riso_landscape,
    "typographic-serif": typographic_serif,
    "stripes": stripes,
    "riso": riso,
    "bauhaus": bauhaus,
    "flowfield": flowfield,
    "gradient-mesh": gradient_mesh,
    "fog": fog,
    "horizon-moon": horizon_moon,
    "op-art": op_art,
    "halftone-sun": halftone_sun,
    "swiss-grid": swiss_grid,
    "horizon-sun": horizon_sun,
    "typographic-swiss": typographic_swiss,
}


def render(album):
    cover = album["cover"]
    rng = random.Random(cover["seed"])
    image = STYLES[cover["style"]](album, rng, cover["palette"])
    return image.convert("RGB").resize((SIZE, SIZE), Image.LANCZOS)


def save_jpeg(image, path):
    # Lower the quality until the cover fits the size budget.
    for quality in (86, 82, 78, 74, 70, 66, 62):
        image.save(path, "JPEG", quality=quality, optimize=True, subsampling=2)
        if path.stat().st_size <= MAX_BYTES:
            return quality
    raise SystemExit(f"{path.name} is still over {MAX_BYTES} bytes at quality 62")


def contact_sheet(albums):
    cell, pad, label = 260, 20, 58
    cols = 4
    rows = math.ceil(len(albums) / cols)
    sheet = Image.new("RGB", (cols * (cell + pad) + pad, rows * (cell + label + pad) + pad), (250, 249, 246))
    draw = ImageDraw.Draw(sheet)
    title_font, artist_font = font("semibold", 15), font("regular", 13)
    for index, album in enumerate(albums):
        x = pad + (index % cols) * (cell + pad)
        y = pad + (index // cols) * (cell + label + pad)
        cover = Image.open(COVERS_DIR / f"{album['id']}.jpg").resize((cell, cell), Image.LANCZOS)
        sheet.paste(cover, (x, y))
        if title_font is not None:
            title = album["title"]
            while title_font.getlength(title) > cell and len(title) > 4:
                title = title[:-2].rstrip() + "…"
            draw.text((x, y + cell + 8), title, fill=(28, 27, 25), font=title_font)
            draw.text((x, y + cell + 30), f"{album['artist']} · {album['cover']['style']}", fill=(110, 106, 100), font=artist_font)
    CONTACT_SHEET.parent.mkdir(parents=True, exist_ok=True)
    # A 256-colour palette keeps the grainy covers from bloating the committed PNG.
    # Pillow only dithers against a given palette, so build it first; dithering hides the banding.
    palette = sheet.quantize(256, method=Image.Quantize.MEDIANCUT)
    sheet.quantize(palette=palette, dither=Image.Dither.FLOYDSTEINBERG).save(CONTACT_SHEET, "PNG", optimize=True)


def main():
    library = json.loads(MANIFEST.read_text())
    albums = library["albums"]
    COVERS_DIR.mkdir(parents=True, exist_ok=True)
    expected = {f"{a['id']}.jpg" for a in albums}
    for stale in COVERS_DIR.glob("*.jpg"):
        if stale.name not in expected:
            stale.unlink()
    for album in albums:
        path = COVERS_DIR / f"{album['id']}.jpg"
        quality = save_jpeg(render(album), path)
        print(f"{path.relative_to(REPO)}  {path.stat().st_size // 1024} KB  q{quality}")
    contact_sheet(albums)
    print(f"{CONTACT_SHEET.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
