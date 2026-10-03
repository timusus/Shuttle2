#!/usr/bin/env python3
"""Frames the raw simulator captures for the App Store.

Reads slots.json, wraps each raw/<device>/<n>.png in template.html and screenshots the page with
Chrome headless, one PNG per canvas:

    iphone-6.9  1320 x 2868  from raw/iphone   (iPhone 16 Pro Max)
    iphone-6.5  1284 x 2778  from raw/iphone   (the 6.5" listing accepts the same aspect)
    ipad-13     2064 x 2752  from raw/ipad     (iPad Pro 13-inch)

Output: ios/store/screenshots/<locale>/<canvas>/<n>.png

The paywall capture (raw/iphone/paywall.png, slots.json's "paywall") is the in-app purchase's review screenshot:
no frame or headline, just the screen scaled to the 6.9" canvas, at
ios/store/screenshots/<locale>/iap-review/paywall.png.

    ./render.py                 all canvases
    ./render.py --canvas ipad-13
    ./render.py --slot 1 --slot 2

Fonts: the template asks Google Fonts for DM Sans and falls back to the system sans. Chrome is
given a virtual-time budget so the font and the image are in before the capture; the last line
of output says which font actually rendered.
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
IOS_DIR = os.path.abspath(os.path.join(HERE, "..", ".."))
TEMPLATE = os.path.join(HERE, "template.html")
SLOTS = os.path.join(HERE, "slots.json")
RAW = os.path.join(HERE, "raw")
OUT_ROOT = os.path.join(IOS_DIR, "store", "screenshots")

CHROME_PATHS = [
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
]

# Per-canvas geometry. Sizes in CSS px; the frame is rendered 1:1 so they are output pixels.
# The iPhone values are the App Store 6.9" canvas; 6.5" scales the same layout by 1284/1320.
CANVASES = {
    "iphone-6.9": {
        "device": "iphone", "width": 1320, "height": 2868, "scale": 1.0,
        "deviceWidth": "74%", "deviceRadius": 66, "screenRadius": 55, "deviceBezel": 11,
        "island": True, "islandWidth": 268, "islandHeight": 80, "islandTop": 24,
        "headlineSize": 96, "subheadlineSize": 40, "headlineHPad": 90,
        "heroHeadlineHeight": 560, "topPad": 130, "bottomPad": 60,
    },
    "iphone-6.5": {
        "device": "iphone", "width": 1284, "height": 2778, "scale": 1284 / 1320,
        "deviceWidth": "74%", "deviceRadius": 66, "screenRadius": 55, "deviceBezel": 11,
        "island": True, "islandWidth": 268, "islandHeight": 80, "islandTop": 24,
        "headlineSize": 96, "subheadlineSize": 40, "headlineHPad": 90,
        "heroHeadlineHeight": 560, "topPad": 130, "bottomPad": 60,
    },
    "ipad-13": {
        "device": "ipad", "width": 2064, "height": 2752, "scale": 1.0,
        "deviceWidth": "76%", "deviceRadius": 44, "screenRadius": 30, "deviceBezel": 22,
        "island": False, "islandWidth": 0, "islandHeight": 0, "islandTop": 0,
        "headlineSize": 118, "subheadlineSize": 50, "headlineHPad": 140,
        "heroHeadlineHeight": 600, "topPad": 140, "bottomPad": 80,
    },
}

SCALED_KEYS = [
    "deviceRadius", "screenRadius", "deviceBezel", "islandWidth", "islandHeight", "islandTop",
    "headlineSize", "subheadlineSize", "headlineHPad", "heroHeadlineHeight", "topPad", "bottomPad",
]


def find_chrome():
    for p in CHROME_PATHS:
        if os.path.exists(p):
            return p
    sys.exit("Chrome not found; install Google Chrome or Chromium")


def config_for(canvas_name, slot):
    c = CANVASES[canvas_name]
    s = c["scale"]
    config = {
        "layout": slot.get("layout", "headline-bottom"),
        "frameWidth": f"{c['width']}px",
        "frameHeight": f"{c['height']}px",
        "deviceWidth": c["deviceWidth"],
        "island": c["island"],
        "headline": slot["headline"],
        "subheadline": slot.get(f"subheadline_{c['device']}", slot.get("subheadline", "")),
        "screenshotUrl": "file://" + os.path.join(RAW, c["device"], f"{slot['n']}.png"),
    }
    for key in SCALED_KEYS:
        config[key] = f"{round(c[key] * s)}px"
    return config


def render(chrome, config, output_path):
    with open(TEMPLATE) as f:
        html = f.read()
    html = html.replace(
        "const config = window.__CONFIG__ || {};",
        f"const config = window.__CONFIG__ || {json.dumps(config)};",
    )
    with tempfile.NamedTemporaryFile(mode="w", suffix=".html", delete=False) as tmp:
        tmp.write(html)
        tmp_path = tmp.name

    width = config["frameWidth"].replace("px", "")
    height = config["frameHeight"].replace("px", "")
    base = [
        chrome, "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
        "--force-device-scale-factor=1",
        "--virtual-time-budget=15000", f"--window-size={width},{height}",
    ]
    try:
        subprocess.run(base + [f"--screenshot={output_path}", f"file://{tmp_path}"],
                       capture_output=True, text=True, timeout=60)
        dom = subprocess.run(base + ["--dump-dom", f"file://{tmp_path}"],
                             capture_output=True, text=True, timeout=60).stdout
    finally:
        os.unlink(tmp_path)

    if not os.path.exists(output_path):
        sys.exit(f"Chrome produced no file at {output_path}")
    font = "DM Sans" if 'id="font-probe" hidden="">DM Sans' in dom else "fallback (system sans)"
    return font


def render_paywall(locale):
    """The raw paywall, scaled to cover the 6.9" canvas and centre-cropped: App Review wants the screen as is."""
    raw = os.path.join(RAW, "iphone", "paywall.png")
    if not os.path.exists(raw):
        sys.exit(f"missing raw capture {raw}; run capture.sh first")
    width, height = CANVASES["iphone-6.9"]["width"], CANVASES["iphone-6.9"]["height"]
    with Image.open(raw) as image:
        image = image.convert("RGB")
        scale = max(width / image.width, height / image.height)
        image = image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
        left, top = (image.width - width) // 2, (image.height - height) // 2
        image = image.crop((left, top, left + width, top + height))
        out_dir = os.path.join(OUT_ROOT, locale, "iap-review")
        os.makedirs(out_dir, exist_ok=True)
        out = os.path.join(out_dir, "paywall.png")
        image.save(out)
    print(f"iap-review paywall: {os.path.relpath(out, IOS_DIR)} ({width} x {height})")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--canvas", action="append", choices=sorted(CANVASES), help="one or more canvases; default all")
    parser.add_argument("--slot", action="append", type=int, help="one or more slot numbers; default all")
    args = parser.parse_args()

    with open(SLOTS) as f:
        spec = json.load(f)
    locale = spec["locale"]
    slots = [s for s in spec["slots"] if not args.slot or s["n"] in args.slot]
    canvases = args.canvas or sorted(CANVASES)
    chrome = find_chrome()

    fonts = set()
    for canvas_name in canvases:
        device = CANVASES[canvas_name]["device"]
        out_dir = os.path.join(OUT_ROOT, locale, canvas_name)
        os.makedirs(out_dir, exist_ok=True)
        for slot in slots:
            raw = os.path.join(RAW, device, f"{slot['n']}.png")
            if not os.path.exists(raw):
                sys.exit(f"missing raw capture {raw}; run capture.sh first")
            out = os.path.join(out_dir, f"{slot['n']}.png")
            fonts.add(render(chrome, config_for(canvas_name, slot), out))
            print(f"{canvas_name} {slot['n']}: {os.path.relpath(out, IOS_DIR)} ({os.path.getsize(out) // 1024} KB)")
    if not args.slot and "paywall" in spec:
        render_paywall(locale)
    print(f"headline font: {', '.join(sorted(fonts))}")


if __name__ == "__main__":
    main()
