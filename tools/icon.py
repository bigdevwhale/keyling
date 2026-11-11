"""
Draws the app icon from Spacelet's drawn sheet (art/sheets/space.png): the dormouse with its letter tile
on a pool of lighter blue, cut into chunky pixels the way tools/cut.py cuts the residents.

    python tools/icon.py [stage]      # stage: 0 baby (the default, its eyes read best that small) .. 3 ancient

Writes the launcher foreground (app/src/main/res/drawable-nodpi/ic_launcher_fg.png, 432 px: 72 art
pixels at 6x, the figure inside the adaptive icon's safe circle) and its monochrome layer (ic_launcher_mono.png), docs/logo.png (512) and, when the
site sits next to this repo, ../stillcraft/assets/keyling-logo.png (192), both on the icon's blue.
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cut import crisp, find_eyes, figures, load, outline  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = 72            # the foreground is 72 x 72 art pixels, shown at 6x
BOX = 38            # the figure fits a 38 x 38 square in the middle: the launcher shows the middle 48, the safe circle is 44
BG = (0x2B, 0x3A, 0x6B, 255)   # @color/icon_bg


def figure(stage):
    im, solid = load(os.path.join(ROOT, "art", "sheets", "space.png"))
    s, e = figures(solid)[stage]
    m = solid[:, s:e]
    ys, xs = np.nonzero(m)
    x0, y0, x1, y1 = s + xs.min(), ys.min(), s + xs.max() + 1, ys.max() + 1
    part = im[y0:y1, x0:x1].copy()
    part[..., 3] = np.where(solid[y0:y1, x0:x1], part[..., 3], 0)
    k = min(BOX / (x1 - x0), BOX / (y1 - y0))
    w, h = max(1, round((x1 - x0) * k)), max(1, round((y1 - y0) * k))
    sprite = outline(crisp(part, w, h))
    # Big round eyes blur into slits when they shrink this far: each is painted again as a dark oval the size
    # the drawing has it, with its shine at the top left.
    for a, b, c, d in find_eyes(part, solid[y0:y1, x0:x1]) or []:
        cx, cy = (a + c) / 2 * k, (b + d) / 2 * k
        rx, ry = max(1.6, (c - a) / 2 * k), max(1.8, (d - b) / 2 * k)
        for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
            for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
                if 0 <= y < h and 0 <= x < w and ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2 <= 1:
                    sprite[y, x] = (0x2A, 0x14, 0x1C, 255)
        gx, gy = int(cx - rx * .35), int(cy - ry * .35)
        sprite[gy, gx, :3] = 255
    return sprite


def place(fig):
    """Where the figure stands in the art square: (x, y)."""
    h, w = fig.shape[:2]
    return (ART - w) // 2, (ART - h) // 2 + 1


def foreground(fig):
    h, w = fig.shape[:2]
    art = np.zeros((ART, ART, 4), np.uint8)
    # A pool of lighter blue behind it, in hard pixel steps.
    yy, xx = np.mgrid[0:ART, 0:ART]
    d = np.hypot(xx + .5 - ART / 2, yy + .5 - ART / 2)
    for r, col in ((23, (0x33, 0x45, 0x7C, 255)), (19, (0x3B, 0x50, 0x8C, 255))):
        art[d < r] = col
    ox, oy = place(fig)
    a = fig[..., 3] > 0
    art[oy:oy + h, ox:ox + w][a] = fig[a]
    return Image.fromarray(art, "RGBA")


def logo(fg, size):
    """The foreground on the icon's blue, in a rounded square like a launcher shows it."""
    big = 512
    base = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    ImageDraw.Draw(base).rounded_rectangle((0, 0, big - 1, big - 1), radius=big * 22 // 100, fill=BG)
    # The launcher shows the middle 72/108 of the foreground.
    crop = round(ART * 18 / 108)
    face = fg.crop((crop, crop, ART - crop, ART - crop)).resize((big, big), Image.NEAREST)
    base.alpha_composite(face)
    return base if size == big else base.resize((size, size), Image.LANCZOS)


def main():
    stage = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    fig = figure(stage)
    fg = foreground(fig)
    res = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
    fg.resize((ART * 6, ART * 6), Image.NEAREST).save(os.path.join(res, "ic_launcher_fg.png"))
    # Themed icons tint the monochrome layer by its alpha: the figure alone, without the blue pool.
    mono = np.zeros((ART, ART, 4), np.uint8)
    ox, oy = place(fig)
    h, w = fig.shape[:2]
    mono[oy:oy + h, ox:ox + w][fig[..., 3] > 0] = (255, 255, 255, 255)
    Image.fromarray(mono, "RGBA").resize((ART * 6, ART * 6), Image.NEAREST).save(os.path.join(res, "ic_launcher_mono.png"))
    logo(fg, 512).save(os.path.join(ROOT, "docs", "logo.png"))
    site = os.path.join(ROOT, "..", "stillcraft", "assets")
    if os.path.isdir(site):
        logo(fg, 192).save(os.path.join(site, "keyling-logo.png"))


if __name__ == "__main__":
    main()
