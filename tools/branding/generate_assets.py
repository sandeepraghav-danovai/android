#!/usr/bin/env python3
"""
Regenerate every DanovAI-derived image resource from the master logo.

    python3 tools/branding/generate_assets.py [path/to/danovAI.png]

The master artwork is a landscape lockup (D mark + "DanovAI" wordmark + tagline) drawn
on a white background. That cannot be used directly as a launcher icon, so this script:

  * lifts the artwork off its white background into real alpha (un-premultiplying the
    antialiased edges, so there is no white fringing on dark surfaces);
  * crops the D mark and centres it on the 108dp adaptive-icon canvas, scaled so its
    true content radius stays inside the 66dp circle a launcher mask may crop to;
  * emits a monochrome layer for Android 13+ themed icons;
  * emits legacy 48dp raster icons for anything that asks for a non-adaptive icon;
  * emits the full lockup for in-app use in both a light and a dark variant. Only the
    near-black wordmark and tagline are lightened for dark mode - the blue/purple
    gradient is brand colour and is left exactly as drawn.

Truecolour PNGs are deliberate: palette quantisation visibly bands the gradient.

Requires Pillow and NumPy:  pip install pillow numpy
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(os.path.dirname(__file__))))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# Row bands of the master artwork, measured from the source image.
MARK_BAND = (104, 584)        # the D mark
MARK_COLS = (503, 1023)
WORDMARK_BAND = (610, 815)    # "DanovAI"
TAGLINE_BAND = (865, 915)     # "THE ENTERPRISE AI PLATFORM"

DARK_TEXT_LUMA = 70           # below this, inside the text bands, is wordmark/tagline
LIGHT_TEXT = (232, 238, 248)  # what that text becomes in the dark variant

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

ADAPTIVE_DP = 108             # adaptive icon canvas
SAFE_RADIUS_DP = 33           # a launcher may mask to a 66dp circle
LEGACY_DP = 48


def unmatte(rgb):
    """White-background artwork -> RGBA with real alpha, edges un-premultiplied."""
    c = rgb.astype(float)
    a = np.clip(1.0 - c.min(axis=2) / 255.0, 0, 1)
    out = np.zeros(c.shape[:2] + (4,), dtype=float)
    nz = a > 1e-4
    for i in range(3):
        ch = np.zeros_like(a)
        ch[nz] = (c[:, :, i][nz] - 255.0 * (1 - a[nz])) / a[nz]
        out[:, :, i] = np.clip(ch, 0, 255)
    out[:, :, 3] = a * 255
    return out.astype(np.uint8)


def crop_to_content(img):
    ys, xs = np.where(img[:, :, 3] > 8)
    return img[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def content_radius_ratio(img):
    """Max distance of any opaque pixel from centre, as a fraction of width."""
    ys, xs = np.where(img[:, :, 3] > 8)
    cy, cx = (img.shape[0] - 1) / 2, (img.shape[1] - 1) / 2
    return np.sqrt((ys - cy) ** 2 + (xs - cx) ** 2).max() / img.shape[1]


def save(img, path, width):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im = Image.fromarray(img).convert("RGBA")
    im = im.resize((width, max(1, round(im.height * width / im.width))), Image.LANCZOS)
    im.save(path, optimize=True)
    return path


def main(src_path):
    src = np.array(Image.open(src_path).convert("RGB"))
    lockup = unmatte(src)

    # ---- dark-theme variant: lighten only the near-black type
    dark = lockup.copy()
    luma = 0.299 * src[:, :, 0] + 0.587 * src[:, :, 1] + 0.114 * src[:, :, 2]
    rows = np.zeros(src.shape[0], dtype=bool)
    rows[WORDMARK_BAND[0]:WORDMARK_BAND[1]] = True
    rows[TAGLINE_BAND[0]:TAGLINE_BAND[1]] = True
    text = (luma < DARK_TEXT_LUMA) & rows[:, None]
    for i, v in enumerate(LIGHT_TEXT):
        dark[:, :, i][text] = v

    mark = crop_to_content(lockup[MARK_BAND[0]:MARK_BAND[1], MARK_COLS[0]:MARK_COLS[1]])
    mark_img = Image.fromarray(mark).convert("RGBA")

    mark_dp = min(SAFE_RADIUS_DP / content_radius_ratio(mark), 72.0)
    print(f"D mark drawn at {mark_dp:.1f}dp on the {ADAPTIVE_DP}dp canvas "
          f"(content radius {content_radius_ratio(mark):.3f} x width)")

    written = 0
    for name, scale in DENSITIES.items():
        d = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        canvas_px = round(ADAPTIVE_DP * scale)
        mark_px = round(mark_dp * scale)

        fg = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
        h = max(1, round(mark_px * mark_img.height / mark_img.width))
        m = mark_img.resize((mark_px, h), Image.LANCZOS)
        fg.paste(m, ((canvas_px - mark_px) // 2, (canvas_px - h) // 2), m)
        fg.save(os.path.join(d, "ic_launcher_foreground.png"), optimize=True)

        mono = np.array(fg)
        mono[:, :, :3] = 0
        Image.fromarray(mono).convert("RGBA").save(
            os.path.join(d, "ic_launcher_monochrome.png"), optimize=True)

        legacy_px = round(LEGACY_DP * scale)
        base = Image.new("RGBA", (legacy_px, legacy_px), (255, 255, 255, 255))
        inner = round(legacy_px * 0.74)
        h = max(1, round(inner * mark_img.height / mark_img.width))
        m = mark_img.resize((inner, h), Image.LANCZOS)
        base.paste(m, ((legacy_px - inner) // 2, (legacy_px - h) // 2), m)
        base.save(os.path.join(d, "ic_launcher.png"), optimize=True)

        rnd = base.copy()
        big = Image.new("L", (legacy_px * 4, legacy_px * 4), 0)
        ImageDraw.Draw(big).ellipse([0, 0, legacy_px * 4 - 1, legacy_px * 4 - 1], fill=255)
        rnd.putalpha(big.resize((legacy_px, legacy_px), Image.LANCZOS))
        rnd.save(os.path.join(d, "ic_launcher_round.png"), optimize=True)
        written += 4

    # ---- in-app artwork, sized for how it is actually displayed
    save(crop_to_content(lockup), os.path.join(RES, "drawable-nodpi", "danov_ai_lockup.png"), 768)
    save(crop_to_content(dark), os.path.join(RES, "drawable-night-nodpi", "danov_ai_lockup.png"), 768)
    save(mark, os.path.join(RES, "drawable-nodpi", "danov_ai_mark.png"), 128)
    written += 3
    print(f"wrote {written} image resources under {os.path.relpath(RES, ROOT)}")


if __name__ == "__main__":
    default = os.path.join(ROOT, "danovAI.png")
    src = sys.argv[1] if len(sys.argv) > 1 else default
    if not os.path.exists(src):
        sys.exit(f"master logo not found: {src}")
    main(src)
