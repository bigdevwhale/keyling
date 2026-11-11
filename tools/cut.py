"""
Cuts the residents' drawn sheets (art/sheets/<id>.png: four growth stages in a row, drawn by Codex's
image tool) into small pixel sprites for the app, and finds each sprite's eyes so the app can blink them.
Taken from Songling's tools/cut.py.

    python tools/cut.py [id ...]

Each sprite comes in three sizes (SIZES, named by height): the nook and cards, the desk, the small list
portraits. Writes app/src/main/resources/pets/<id>_<stage>_<height>.bin (gzip: int w, int h, w*h ARGB ints, w*h lid
bytes, int eye count, 4 ints per eye box; big-endian), a PNG of each to art/pets/ to look at, and a debug
sheet with the eyes marked to app/build/art/sheets.png.
"""
import gzip
import os
import struct
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "art", "sheets")
OUT = os.path.join(ROOT, "app", "src", "main", "resources", "pets")
LOOK = os.path.join(ROOT, "art", "pets")
DEBUG = os.path.join(ROOT, "app", "build", "art", "sheets.png")

SPECIES = ["space", "enter", "bksp", "caps", "tab", "shift", "esc"]
STAGES = ["baby", "young", "adult", "ancient"]
# height -> (canvas width, largest figure width, largest figure height); a sprite stands on its bottom row.
SIZES = {48: (64, 62, 44), 34: (56, 54, 33), 26: (40, 38, 25)}
# How tall each stage stands against the ancient one: the sheets grow them more than a key's hollow has room for.
GROWTH = [0.66, 0.78, 0.9, 1.0]

# Eyes the finder gets wrong, by hand: "<id>_<stage>_<size>": [(x0, y0, x1, y1), ...] in sprite pixels.
EYES = {}


def lum(a):
    return (0.3 * a[..., 0] + 0.59 * a[..., 1] + 0.11 * a[..., 2]) / 255.0


def unchecker(rgb):
    """Some sheets came with the transparency checkerboard painted in. Takes it out: the part joined to
    the edge, and any closed-in patch of it (between a tail and a body)."""
    h, w, _ = rgb.shape
    mn = rgb.min(axis=2).astype(int)
    mx = rgb.max(axis=2).astype(int)
    grey = (mn >= 115) & (mx - mn <= 26)
    border = np.concatenate([rgb[0], rgb[-1], rgb[:, 0], rgb[:, -1]]).astype(int)
    q = (border // 8) * 8
    vals, counts = np.unique(q[:, 0] * 65536 + q[:, 1] * 256 + q[:, 2], return_counts=True)
    top = vals[np.argsort(-counts)][:2]
    tones = [np.array([t // 65536, (t // 256) % 256, t % 256]) + 4 for t in top]
    near = [np.abs(rgb.astype(int) - t).max(axis=2) <= 22 for t in tones]
    lab, n = ndimage.label(grey)
    gone = np.zeros((h, w), bool)
    edge = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    for i, sl in enumerate(ndimage.find_objects(lab), start=1):
        m = lab[sl] == i
        if i in edge:
            gone[sl] |= m
            continue
        area = m.sum()
        if area < 80:
            continue
        f0 = (near[0][sl] & m).sum() / area
        f1 = (near[1][sl] & m).sum() / area
        if f0 >= 0.15 and f1 >= 0.15:
            gone[sl] |= m
    return ~gone


def load(path):
    im = np.array(Image.open(path).convert("RGBA"))
    a = im[..., 3]
    if (a == 0).mean() > 0.05:
        solid = a >= 128
    else:
        solid = unchecker(im[..., :3])
        im[..., 3] = np.where(solid, 255, 0)
    solid &= ~glow(im, solid)
    # Specks left over from the background.
    lab, n = ndimage.label(solid)
    sizes = ndimage.sum(solid, lab, range(1, n + 1))
    for i, s in enumerate(sizes, start=1):
        if s < 120:
            solid[lab == i] = False
    im[..., 3] = np.where(solid, im[..., 3], 0)
    return im, solid


def glow(im, solid):
    """The halo some ancient stages were drawn with: the pale or see-through band between the outside and
    the figure's thick dark outline. Grown in from the outside, so it stops at the outline."""
    rgb = im[..., :3].astype(int)
    L = lum(rgb)
    mx, mn = rgb.max(axis=2), rgb.min(axis=2)
    sat = (mx - mn) / np.maximum(mx, 1)
    soft = solid & (L > 0.3) & ((im[..., 3] < 250) | ((L > 0.62) & (sat < 0.45)))
    halo = ndimage.binary_propagation(ndimage.binary_dilation(~solid) & soft, mask=soft)
    return halo


def figures(solid, count=4):
    """Column ranges of the figures, left to right. Touching neighbours are split at the thinnest column."""
    prof = solid.sum(axis=0)
    on = prof > 0
    runs = []
    x = 0
    while x < len(on):
        if on[x]:
            s = x
            while x < len(on) and on[x]:
                x += 1
            runs.append([s, x])
        else:
            x += 1
    while len(runs) > count:
        gaps = [runs[i + 1][0] - runs[i][1] for i in range(len(runs) - 1)]
        i = int(np.argmin(gaps))
        runs[i][1] = runs[i + 1][1]
        del runs[i + 1]
    while len(runs) < count:
        i = max(range(len(runs)), key=lambda k: runs[k][1] - runs[k][0])
        s, e = runs[i]
        lo, hi = s + (e - s) // 5, e - (e - s) // 5
        cut = lo + int(np.argmin(prof[lo:hi]))
        runs[i:i + 1] = [[s, cut], [cut, e]]
    return runs


def find_eyes(img, solid):
    """Each eye is a dark bead with a white shine in it. Returns two boxes (x0, y0, x1, y1) or None."""
    rgb = img[..., :3].astype(int)
    L = lum(rgb)
    area = solid.sum()
    white = solid & (rgb.min(axis=2) >= 225)
    dark = solid & (L < 0.32)
    lab, n = ndimage.label(white)
    cands = []
    for i, sl in enumerate(ndimage.find_objects(lab), start=1):
        m = lab == i
        a = m.sum()
        if not (area * 0.0002 <= a <= area * 0.012):
            continue
        r = max(3, int(np.sqrt(a)))
        ring = ndimage.binary_dilation(m, iterations=r) & ~m
        if dark[ring].mean() < 0.55:
            continue
        cy, cx = ndimage.center_of_mass(m)
        # The bead: dark pixels joined to the shine, kept to a window around it.
        half = int(np.sqrt(a) * 2.6) + 4
        y0, y1 = max(0, int(cy) - half), min(img.shape[0], int(cy) + half)
        x0, x1 = max(0, int(cx) - half), min(img.shape[1], int(cx) + half)
        win = (dark | m)[y0:y1, x0:x1]
        wl, _ = ndimage.label(win)
        ids = set(np.unique(wl[(m[y0:y1, x0:x1])])) - {0}
        bead = np.isin(wl, list(ids))
        # Kept to a round eye's reach from the shine, so dark wood or feathers around it don't count.
        yy, xx = np.mgrid[y0:y1, x0:x1]
        bead &= (yy - cy) ** 2 + (xx - cx) ** 2 <= (np.sqrt(a) * 2.4) ** 2
        ys, xs = np.nonzero(bead)
        cands.append((a, cx, cy, (x0 + xs.min(), y0 + ys.min(), x0 + xs.max() + 1, y0 + ys.max() + 1)))
    best = None
    for i in range(len(cands)):
        for j in range(i + 1, len(cands)):
            a, b = cands[i], cands[j]
            size = np.sqrt(max(a[0], b[0]))
            if max(a[0], b[0]) / min(a[0], b[0]) > 2.6 or abs(a[2] - b[2]) > size * 2.0 or abs(a[1] - b[1]) < size * 2.5:
                continue
            score = a[0] + b[0]
            if best is None or score > best[0]:
                best = (score, sorted([a, b], key=lambda c: c[1]))
    return None if best is None else [c[3] for c in best[1]]


def crisp(part, w, h, colors=64):
    """Shrinks a drawn figure to w x h without blending: the figure is first brought down to a small
    palette, then each sprite pixel takes the commonest palette colour in its block of the drawing.
    Averaging instead (a box filter) mixes neighbours into muddy in-between tones and soft edges."""
    ph, pw = part.shape[:2]
    opaque = part[..., 3] >= 128
    rgb = Image.fromarray(part[..., :3], "RGB")
    pal = rgb.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    idx = np.array(pal)
    palette = np.array(pal.getpalette()[:colors * 3], np.uint8).reshape(-1, 3)
    out = np.zeros((h, w, 4), np.uint8)
    xs = np.linspace(0, pw, w + 1).astype(int)
    ys = np.linspace(0, ph, h + 1).astype(int)
    for j in range(h):
        for i in range(w):
            ys0, ys1, xs0, xs1 = ys[j], max(ys[j + 1], ys[j] + 1), xs[i], max(xs[i + 1], xs[i] + 1)
            o = opaque[ys0:ys1, xs0:xs1]
            if o.mean() < 0.45:
                continue
            # The middle of the block counts double, so thin lines keep to their place.
            block = idx[ys0:ys1, xs0:xs1]
            cy0, cy1 = (ys1 - ys0) // 4, ys1 - ys0 - (ys1 - ys0) // 4
            cx0, cx1 = (xs1 - xs0) // 4, xs1 - xs0 - (xs1 - xs0) // 4
            counts = np.bincount(block[o], minlength=colors).astype(float)
            inner = block[cy0:cy1, cx0:cx1][o[cy0:cy1, cx0:cx1]]
            if inner.size:
                counts += np.bincount(inner, minlength=colors)
            out[j, i, :3] = palette[int(np.argmax(counts))]
            out[j, i, 3] = 255
    return out


def outline(sprite):
    """An even dark edge: every pixel touching the outside is darkened, so a figure the drawing
    lined thinly or in its own colour (the slime's green) still has a firm line like the rest."""
    a = sprite[..., 3] > 0
    # Small loose bits (sparks, floating flames) keep their glow.
    lab, n = ndimage.label(a)
    sizes = ndimage.sum(a, lab, range(1, n + 1))
    body = np.isin(lab, [i + 1 for i, v in enumerate(sizes) if v >= 40])
    edge = body & ndimage.binary_dilation(~a, iterations=1)
    rgb = sprite[..., :3].astype(float)
    L = lum(rgb)
    ink = np.array([22.0, 14.0, 26.0])
    for y, x in zip(*np.nonzero(edge)):
        if L[y, x] > 0.16:
            sprite[y, x, :3] = (rgb[y, x] * 0.18 + ink * 0.82).astype(np.uint8)
    return sprite




def eye_mask(sprite, box, glasses=False, core=False):
    """The exact pixels of one eye in a sprite: what differs from the skin around it, joined to
    the middle of the marked box. The app paints over just these when the eye closes."""
    H, W = sprite.shape[:2]
    rgb = sprite[..., :3].astype(int)
    a = sprite[..., 3] > 0
    L = lum(rgb)
    x0, y0, x1, y1 = box
    # The skin: the commonest colour right around the box, outline aside (the tanuki's dark mask counts as skin).
    ring = []
    for y in range(max(0, y0 - 2), min(H, y1 + 2)):
        for x in range(max(0, x0 - 2), min(W, x1 + 2)):
            if x0 <= x < x1 and y0 <= y < y1:
                continue
            if a[y, x] and L[y, x] >= 0.13:
                ring.append(tuple((rgb[y, x] // 16) * 16))
    if ring:
        vals, counts = np.unique(np.array(ring), axis=0, return_counts=True)
        skin = vals[np.argmax(counts)] + 8
    else:
        skin = np.array([200, 160, 120])
    sl = (0.3 * skin[0] + 0.59 * skin[1] + 0.11 * skin[2]) / 255
    dist = np.abs(rgb - skin).sum(axis=2)
    mx, mn = rgb.max(axis=2), rgb.min(axis=2)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1), 0)
    # Blush, not orange fur: as much blue as green.
    pink = (rgb[..., 0] - rgb[..., 1] > 50) & (rgb[..., 0] > 170) & (rgb[..., 2] >= rgb[..., 1] - 12)
    # The sprite's own outline: dark pixels touching the outside.
    outside = ~a
    edge = a & ndimage.binary_dilation(outside, iterations=1)
    m = np.zeros((H, W), bool)
    for y in range(max(0, y0 - 2), min(H, y1 + 2)):
        for x in range(max(0, x0 - 2), min(W, x1 + 2)):
            if not a[y, x] or pink[y, x] or edge[y, x]:
                continue
            # Gold spectacle rims stay on when the eyes close.
            r, g, b = rgb[y, x]
            if glasses and r > 140 and g > 90 and b < 110 and r - b > 70:
                continue
            inside = x0 <= x < x1 and y0 <= y < y1
            if core:
                # Eyes set in a dark patch (the tanuki's mask): only the black of the eye and its shine go.
                if inside and (L[y, x] < 0.2 or (L[y, x] > 0.8 and sat[y, x] < 0.15)):
                    m[y, x] = True
                continue
            darker = L[y, x] < sl - 0.15
            grey = sat[y, x] < 0.14 and dist[y, x] > 70
            if (inside and dist[y, x] > 60) or darker or grey:
                m[y, x] = True
    # In a dark patch every bit of the eye inside its box goes, joined to the middle or not.
    if core and m.any():
        return ndimage.binary_fill_holes(m)
    # Keep what joins the middle of the box.
    lab, n = ndimage.label(m, structure=np.ones((3, 3)))
    core = lab[y0 + (y1 - y0) // 4:y1 - (y1 - y0) // 4, x0 + (x1 - x0) // 4:x1 - (x1 - x0) // 4]
    ids = [i for i in np.unique(core) if i]
    if not ids:
        return None
    m = np.isin(lab, ids)
    # No further than a round eye inside its box reaches: darker fur, a mane or a wing joined to the eye stays put.
    yy, xx = np.mgrid[0:H, 0:W]
    cx, cy = (x0 + x1 - 1) / 2, (y0 + y1 - 1) / 2
    rx, ry = (x1 - x0) / 2 + 0.8, (y1 - y0) / 2 + 0.8
    m &= ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1
    # The drawn eye's own outline and lashes reach a little past that: dark or grey pixels joined to the eye
    # within a pixel or two more go as well, or they'd stay behind as stray marks beside the closed lid.
    reach = ((xx - cx) / (rx + 1.2)) ** 2 + ((yy - cy) / (ry + 1.0)) ** 2 <= 1
    stray = a & ~pink & ~edge & reach & ((L < sl - 0.15) | ((sat < 0.14) & (dist > 70)))
    if glasses:
        gold = (rgb[..., 0] > 140) & (rgb[..., 1] > 90) & (rgb[..., 2] < 110) & (rgb[..., 0] - rgb[..., 2] > 70)
        stray &= ~gold
    # One pixel only, and not onto a line that runs on past it (the moth's head, the chimera's fur and nose):
    # a pixel goes only if no dark pixel continues beyond it outside the reach.
    grow = ndimage.binary_dilation(m, structure=np.ones((3, 3))) & stray & ~m
    darkish = a & (L < sl - 0.15)
    beyond = darkish & ~reach & ~m
    grow &= ~ndimage.binary_dilation(beyond, structure=np.ones((3, 3)))
    m |= grow
    # The edge of a coloured iris (the sprout's green) can sit just past the box: bright pixels joined to the
    # eye whose hue is plainly not the skin's go too. Pale fur (the kitsune's cream muzzle) and blush stay.
    import colorsys
    sh = colorsys.rgb_to_hsv(*(skin / 255.0))[0]
    near = ndimage.binary_dilation(m, iterations=2) & ~m & a & ~pink & ~edge
    near &= ((xx - cx) / (rx + 1.5)) ** 2 + ((yy - cy) / (ry + 1.5)) ** 2 <= 1
    for y, x in zip(*np.nonzero(near)):
        if sat[y, x] < 0.4 or L[y, x] < 0.2:
            continue
        h = colorsys.rgb_to_hsv(*(rgb[y, x] / 255.0))[0]
        if min(abs(h - sh), 1 - abs(h - sh)) > 0.06:
            m[y, x] = True
    if sl < 0.3:
        # On a dark face (the magpie) the eye's own light rim and shine sit right at its edge: they go too,
        # or they'd be left behind as grey smudges when it closes. The peach cheeks stay.
        near = ndimage.binary_dilation(m, iterations=2)
        rim = near & a & (L > 0.45) & (sat < 0.3) & ~pink
        rim &= ((xx - cx) / (rx + 1.5)) ** 2 + ((yy - cy) / (ry + 1.5)) ** 2 <= 1
        m |= rim
    m = ndimage.binary_fill_holes(m)
    # A line of the face that runs on past the eye (the moth's head outline, the chimera's fur) keeps its
    # pixels near the eye too: dark pixels in the eye's outer ring joined, through dark, to dark beyond it.
    inner = ((xx - cx) / max(1.0, rx - 1.6)) ** 2 + ((yy - cy) / max(1.0, ry - 1.6)) ** 2 <= 1
    darkish = a & (L < sl - 0.15)
    ring = m & ~inner & darkish
    # Only a real line counts: near-black, and at least five pixels of it past the eye (the moth's soft purple
    # under-eye shading and its wing don't).
    outside = darkish & (L < 0.2) & ~m & ~(((xx - cx) / (rx + 1.2)) ** 2 + ((yy - cy) / (ry + 1.0)) ** 2 <= 1)
    lab, _ = ndimage.label(ring | outside, structure=np.ones((3, 3)))
    ids, counts = np.unique(lab[outside], return_counts=True)
    keep = {i for i, c in zip(ids, counts) if i and c >= 5}
    if keep:
        # Only what lies outside the eye's own box: inside it, an arc is the eye's own outline.
        box = (xx >= x0) & (xx < x1) & (yy >= y0) & (yy < y1)
        m &= ~(np.isin(lab, list(keep)) & ring & ~box)
    return m


def cut(sp, im, solid, crops, size, sheet, row):
    cw, mw, mh = SIZES[size]
    ground = size - 1
    ks = [min(mw / (b[2] - b[0]), g * mh / (b[3] - b[1])) for g, b in zip(GROWTH, crops)]
    for col, (st, box, k) in enumerate(zip(STAGES, crops, ks)):
        x0, y0, x1, y1 = box
        part = im[y0:y1, x0:x1].copy()
        part[..., 3] = np.where(solid[y0:y1, x0:x1], part[..., 3], 0)
        w, h = max(1, round((x1 - x0) * k)), max(1, round((y1 - y0) * k))
        arr = crisp(part, w, h)
        ox, oy = (cw - w) // 2, ground + 1 - h
        sprite = np.zeros((size, cw, 4), np.uint8)
        sprite[oy:oy + h, ox:ox + w] = arr
        outline(sprite)
        name = f"{sp}_{st}_{size}"
        eyes = EYES.get(name)
        if eyes is None:
            found = find_eyes(part, solid[y0:y1, x0:x1])
            if found:
                eyes = [(int(ox + a * k), int(oy + b * k), int(round(ox + c * k)), int(round(oy + d * k))) for a, b, c, d in found]
        if eyes and len(eyes) == 2:
            # An eye the finder caught only a scrap of: sized like its twin, on the twin's row.
            (a0, b0, c0, d0), (a1, b1, c1, d1) = eyes
            ar0, ar1 = (c0 - a0) * (d0 - b0), (c1 - a1) * (d1 - b1)
            if max(ar0, ar1) > 2.2 * max(1, min(ar0, ar1)):
                small, big = (0, 1) if ar0 < ar1 else (1, 0)
                sx = (eyes[small][0] + eyes[small][2]) / 2
                bw = eyes[big][2] - eyes[big][0]
                x0_ = int(round(sx - bw / 2))
                eyes = list(eyes)
                eyes[small] = (x0_, eyes[big][1], x0_ + bw, eyes[big][3])
                print("evened eyes:", name, eyes, file=sys.stderr)
        lids = np.zeros((size, cw), bool)
        tight = []
        for box in eyes or []:
            m = eye_mask(sprite, box)
            if m is None or m.sum() < 2:
                tight.append(box)
                continue
            # Never toward the other eye: the nose and the bridge between them stay.
            for ob in eyes:
                if ob == box:
                    continue
                if ob[0] > box[0]:
                    m[:, max((box[2] + ob[0]) // 2 - 1, box[2]):] = False
                else:
                    m[:, :min((ob[2] + box[0]) // 2 + 2, box[0])] = False
            lids |= m
            ys, xs = np.nonzero(m)
            tight.append((int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1))
        if not eyes:
            print("no eyes found:", name, file=sys.stderr)
        argb = (sprite[..., 3].astype(np.uint32) << 24) | (sprite[..., 0].astype(np.uint32) << 16) |                (sprite[..., 1].astype(np.uint32) << 8) | sprite[..., 2].astype(np.uint32)
        data = struct.pack(">ii", cw, size) + argb.astype(">u4").tobytes() + lids.astype(np.uint8).tobytes()
        data += struct.pack(">i", len(tight)) + b"".join(struct.pack(">iiii", *e) for e in tight)
        with open(os.path.join(OUT, name + ".bin"), "wb") as f:
            f.write(gzip.compress(data, mtime=0))
        Image.fromarray(sprite, "RGBA").save(os.path.join(LOOK, name + ".png"))
        # Debug: the sprite at 3x with its lids tinted and eyes boxed.
        big = Image.fromarray(sprite, "RGBA").resize((cw * 3, size * 3), Image.NEAREST)
        at = (col * 64 * 3 + (64 * 3 - cw * 3) // 2, row * 48 * 3 + 48 * 3 - size * 3)
        sheet.alpha_composite(big, at)
        over = np.zeros((size, cw, 4), np.uint8)
        over[lids] = (255, 0, 255, 150)
        sheet.alpha_composite(Image.fromarray(over, "RGBA").resize((cw * 3, size * 3), Image.NEAREST), at)
        px = sheet.load()
        for a, b, c, d in tight:
            for X in range(a * 3, c * 3):
                for Y in (b * 3, d * 3 - 1):
                    px[at[0] + X, at[1] + Y] = (0, 255, 120, 255)
            for Y in range(b * 3, d * 3):
                for X in (a * 3, c * 3 - 1):
                    px[at[0] + X, at[1] + Y] = (0, 255, 120, 255)
    return ks


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(LOOK, exist_ok=True)
    os.makedirs(os.path.dirname(DEBUG), exist_ok=True)
    names = sys.argv[1:] or [sp for sp in SPECIES if os.path.exists(os.path.join(SRC, sp + ".png"))]
    rows = len(names) * len(SIZES)
    sheet = Image.new("RGBA", (64 * 3 * 4, 48 * 3 * max(1, rows)), (40, 34, 56, 255))
    row = 0
    for sp in names:
        im, solid = load(os.path.join(SRC, sp + ".png"))
        runs = figures(solid)
        crops = []
        for s, e in runs:
            # A scrap of the neighbour left at the edge where touching figures were split.
            lab, n = ndimage.label(solid[:, s:e])
            if n > 1:
                sizes = ndimage.sum(solid[:, s:e], lab, range(1, n + 1))
                edge = set(np.unique(np.concatenate([lab[:, 0], lab[:, -1]]))) - {0}
                for i in edge:
                    if sizes[i - 1] < sizes.max() * 0.05:
                        solid[:, s:e][lab == i] = False
            # Sparkles and crumbs drawn loose around the figure.
            lab, n = ndimage.label(solid[:, s:e])
            if n > 1:
                sizes = ndimage.sum(solid[:, s:e], lab, range(1, n + 1))
                for i in range(1, n + 1):
                    if sizes[i - 1] < sizes.max() * 0.02:
                        solid[:, s:e][lab == i] = False
            m = solid[:, s:e]
            ys = np.nonzero(m.any(axis=1))[0]
            xs = np.nonzero(m.any(axis=0))[0]
            crops.append((s + xs[0], ys[0], s + xs[-1] + 1, ys[-1] + 1))
        for size in SIZES:
            k = cut(sp, im, solid, crops, size, sheet, row)
            row += 1
            print(sp, size, "scale", [round(k, 4) for k in k])
    sheet.save(DEBUG)


if __name__ == "__main__":
    main()
