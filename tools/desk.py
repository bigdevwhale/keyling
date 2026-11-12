"""
Shrinks the desk things (art/desk/<id>.png, drawn by Codex's image tool, one thing per picture on a clear
background) to the size they stand on the desk, and finds the spots the app animates or paints on.

    python tools/desk.py [id ...]

The desk is drawn at Desk.S (3) device pixels per art pixel, so a thing FIT art pixels big comes out three
times as many pixels across. The shrink is smooth (a Lanczos filter on premultiplied colour): the pictures are
finer than the old desk, and blending keeps them from turning back into blocks.

Writes app/src/main/resources/desk/<id>.bin (gzip, big-endian: int w, int h, w*h ARGB ints, int n, n ints of
spots, see SPOTS) and a PNG of each to art/desk/out/ to look at.
"""
import gzip
import os
import struct
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "art", "desk")
OUT = os.path.join(ROOT, "app", "src", "main", "resources", "desk")
LOOK = os.path.join(SRC, "out")
S = 3  # device pixels per art pixel, Desk.S

# The largest each thing may be, in art pixels (width, height); it keeps its proportions inside that.
FIT = {
    "notebook": (100, 104), "notes": (32, 32), "eraser": (20, 11),
    "mug_heart": (38, 38), "mug_enamel": (40, 36), "mug_glass": (34, 48), "mug_thermo": (18, 48), "mug_custom": (36, 42),
    "plant_cactus": (32, 46), "plant_succulent": (32, 34), "plant_monstera": (46, 46),
    "lamp_banker": (56, 36),
    "mascot_cat": (40, 22), "mascot_candle": (30, 26), "mascot_globe": (24, 36),
}


def lum(rgb):
    return (0.3 * rgb[..., 0] + 0.59 * rgb[..., 1] + 0.11 * rgb[..., 2]) / 255.0


def load(path):
    """The picture with its background gone: what's mostly opaque, less the specks around it."""
    im = np.array(Image.open(path).convert("RGBA"))
    solid = im[..., 3] >= 128
    lab, n = ndimage.label(solid)
    if n > 1:
        sizes = ndimage.sum(solid, lab, range(1, n + 1))
        keep = [i + 1 for i, v in enumerate(sizes) if v >= sizes.max() * 0.004]
        solid = np.isin(lab, keep)
    # The soft edge just outside stays, so the shrunk outline is smooth.
    edge = ndimage.binary_dilation(solid, iterations=3)
    im[..., 3] = np.where(edge, im[..., 3], 0)
    ys, xs = np.nonzero(solid)
    return im[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def shrink(im, w, h):
    """Lanczos on premultiplied colour, so the clear background doesn't bleed dark into the edges."""
    a = im[..., 3:4].astype(np.float32) / 255
    pre = np.concatenate([im[..., :3] * a, a * 255], axis=2).astype(np.float32)
    chans = [np.array(Image.fromarray(pre[..., i]).resize((w, h), Image.LANCZOS)) for i in range(4)]
    out = np.stack(chans, axis=2)
    al = np.clip(out[..., 3], 0, 255)
    rgb = np.where(al[..., None] > 0, out[..., :3] / np.maximum(al[..., None] / 255, 1e-6), 0)
    res = np.concatenate([np.clip(rgb, 0, 255), al[..., None]], axis=2).round().astype(np.uint8)
    res[res[..., 3] < 24] = 0
    return res


def spots(name, sp):
    """Points the app needs, in device pixels of the shrunk sprite."""
    rgb = sp[..., :3].astype(int)
    a = sp[..., 3] >= 128
    L = lum(rgb)
    h, w = a.shape
    if name.startswith("mug_") and name != "mug_thermo":
        # Where the steam rises: the middle of the tea, the dark warm pool at the top.
        top = np.zeros_like(a)
        top[: int(h * 0.45)] = True
        tea = a & top & (L < 0.32) & (rgb[..., 0] > rgb[..., 2])
        if name == "mug_glass":
            # Tea in a glass is amber, not dark; the dark bits up there are the spoon's outline.
            tea = a & top & (rgb[..., 0] > 150) & (rgb[..., 1] > 50) & (rgb[..., 1] < 150) & (rgb[..., 2] < 70)
        lab, n = ndimage.label(tea)
        if n:
            big = np.argmax(ndimage.sum(tea, lab, range(1, n + 1))) + 1
            cy, cx = ndimage.center_of_mass(lab == big)
            if name == "mug_glass":
                cy = np.nonzero(lab == big)[0].min() + 3   # the surface of the tea, not the middle of the glass
            out = [int(cx), int(cy)]
        else:
            out = [int(w * 0.4), int(h * 0.12)]
        if name == "mug_custom":
            # Where the user's print goes: the white front of the mug below the rim, left of the handle.
            white = a & (L > 0.72) & ((rgb.max(axis=2) - rgb.min(axis=2)) < 40)
            white[: int(h * 0.3)] = False
            lab, n = ndimage.label(white)
            big = np.argmax(ndimage.sum(white, lab, range(1, n + 1))) + 1
            ys, xs = np.nonzero(lab == big)
            x0, x1 = np.percentile(xs, 3), np.percentile(xs, 97)
            y0, y1 = np.percentile(ys, 2), np.percentile(ys, 98)
            pad = (x1 - x0) * 0.08
            out += [int(x0 + pad), int(y0 + pad), int(x1 - pad), int(y1 - pad)]
        return out
    if name == "mascot_candle":
        # The wick: the topmost dark pixels near the middle of the candle.
        dark = a & (L < 0.2)
        dark[:, : int(w * 0.25)] = False
        dark[:, int(w * 0.75):] = False
        ys, xs = np.nonzero(dark)
        y = ys.min()
        return [int(xs[ys <= y + 2].mean()), int(y)]
    if name == "lamp_banker":
        # The light: under the middle of the green shade.
        green = a & (rgb[..., 1] > rgb[..., 0] + 25) & (rgb[..., 1] > rgb[..., 2] + 10)
        ys, xs = np.nonzero(green)
        return [int(xs.mean()), int(ys.max())]
    return []


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(LOOK, exist_ok=True)
    names = sys.argv[1:] or list(FIT)
    for name in names:
        im = load(os.path.join(SRC, name + ".png"))
        fw, fh = FIT[name]
        k = min(fw * S / im.shape[1], fh * S / im.shape[0])
        w, h = max(1, round(im.shape[1] * k)), max(1, round(im.shape[0] * k))
        # A whole number of art pixels, the sprite at the bottom middle of them.
        W, H = -(-w // S) * S, -(-h // S) * S
        sp = np.zeros((H, W, 4), np.uint8)
        ox, oy = (W - w) // 2, H - h
        sp[oy:oy + h, ox:ox + w] = shrink(im, w, h)
        pts = spots(name, sp)
        argb = (sp[..., 3].astype(np.uint32) << 24) | (sp[..., 0].astype(np.uint32) << 16) | \
               (sp[..., 1].astype(np.uint32) << 8) | sp[..., 2].astype(np.uint32)
        data = struct.pack(">ii", W, H) + argb.astype(">u4").tobytes() + struct.pack(">i", len(pts)) + \
            b"".join(struct.pack(">i", v) for v in pts)
        with open(os.path.join(OUT, name + ".bin"), "wb") as f:
            f.write(gzip.compress(data, mtime=0))
        Image.fromarray(sp, "RGBA").save(os.path.join(LOOK, name + ".png"))
        print(name, f"{W // S}x{H // S} art px", pts)


if __name__ == "__main__":
    main()
