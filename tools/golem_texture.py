#!/usr/bin/env python3
"""Root Golem entity texture (128x128, RootGolemModel layout) in a vanilla-like style.

Vanilla cues: 4-tone palettes, clustered (2x2) variation instead of per-pixel static, rooted-dirt
style roots (1px pale line with a shadow pixel), grass-block-side moss overhang, lit from above.

Used by gen.py: build() returns (texture, glow layer) as PIL images. Deterministic: same output every run.
"""
import random

from PIL import Image

DIRT = [(79, 56, 38), (98, 70, 47), (117, 84, 57), (134, 97, 66)]          # dark .. light
ROOT = [(126, 97, 68), (166, 131, 94), (194, 160, 118)]                     # shadow, root, highlight
MOSS = [(64, 84, 32), (84, 108, 42), (104, 132, 52), (126, 156, 64)]
WOOD = [(72, 52, 33), (98, 72, 45), (122, 92, 60)]
LEAF = [(62, 112, 40), (88, 148, 54), (112, 172, 68)]
FLOWER = [(178, 86, 160), (214, 124, 196)]
SOCKET = (38, 26, 17)
GLOW = [(100, 214, 76), (186, 255, 150)]


def faces(u, v, w, h, d):
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }


def hsh(x, y, s):
    n = (x * 374761393 + y * 668265263 + s * 982451653) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


class Tex:
    def __init__(self, seed):
        self.rng = random.Random(seed)
        self.img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
        self.eyes = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
        self.px, self.ex = self.img.load(), self.eyes.load()

    def shade(self, pal, x, y, salt, base, face=None, ly=0, fh=1):
        """Clustered vanilla-style variation: mostly 2x2 patches, a few single pixels."""
        t = base
        if face == 'top': t += 1
        elif face == 'bottom': t -= 1
        elif face is not None and ly == fh - 1: t -= 1               # darker bottom row, lit from above
        cell = hsh(x // 2, y // 2, salt)
        if cell < 0.22: t -= 1
        elif cell > 0.82: t += 1
        if hsh(x, y, salt + 3) > 0.94: t -= 1                        # sparse dark specks, like vanilla dirt
        return pal[max(0, min(len(pal) - 1, t))]

    def fill(self, rect, pal, base, salt, face):
        x0, y0, w, h = rect
        for y in range(h):
            for x in range(w):
                self.px[x0 + x, y0 + y] = self.shade(pal, x0 + x, y0 + y, salt, base, face, y, h) + (255,)

    def root_line(self, rect, x, y, length, drift=0.25):
        """A rooted-dirt style root: pale 1px line wandering downward, shadow pixel to its lower right."""
        x0, y0, w, h = rect
        for i in range(length):
            if not (0 <= x < w and 0 <= y < h):
                break
            tip = i >= length - 2
            self.px[x0 + x, y0 + y] = (ROOT[1] if not tip else ROOT[0]) + (255,)
            if i % 4 == 0 and not tip:
                self.px[x0 + x, y0 + y] = ROOT[2] + (255,)           # occasional highlight
            if x + 1 < w and self.px[x0 + x + 1, y0 + y][:3] in DIRT:
                self.px[x0 + x + 1, y0 + y] = DIRT[0] + (255,)       # shadow beside the root
            y += 1
            r = self.rng.random()
            if r < drift / 2: x -= 1
            elif r < drift: x += 1
            if self.rng.random() < 0.08 and i > 2:                   # fine side rootlet
                sx = x + self.rng.choice([-1, 1])
                for k in range(2):
                    if 0 <= sx < w and y + k < h:
                        self.px[x0 + sx, y0 + y + k] = ROOT[0] + (255,)
                    sx += -1 if sx < x else 1

    def moss_overhang(self, rect, depth, salt):
        """Grass-block-side style moss: solid band on top, stepped drips below, darker lower edge."""
        x0, y0, w, h = rect
        for x in range(w):
            d = depth + (1 if hsh(x // 2, 0, salt) > 0.5 else 0) + (2 if hsh(x, 1, salt) > 0.82 else 0)
            d = min(d, h)
            for y in range(d):
                c = self.shade(MOSS, x0 + x, y0 + y, salt + 50, 2 if y == 0 else 1)
                if y == d - 1:
                    c = MOSS[0]
                self.px[x0 + x, y0 + y] = c + (255,)

    def box(self, uv, size, pal, base, salt, roots=0, moss=0, moss_top=True):
        u, v = uv
        w, h, d = size
        fs = faces(u, v, w, h, d)
        for name, rect in fs.items():
            if name == 'top' and moss_top and moss:
                self.fill(rect, MOSS, 2, salt + 9, name)
                continue
            self.fill(rect, pal, base, salt, name)
            if name in ('top', 'bottom'):
                continue
            rw, rh = rect[2], rect[3]
            for i in range(roots if rw >= 3 else 0):
                if rw * rh < 20 and i > 0:
                    break
                start = self.rng.randrange(max(1, rh // 2))
                self.root_line(rect, self.rng.randrange(rw), start, self.rng.randint(rh // 2, rh))
            if moss:
                self.moss_overhang(rect, moss, salt + 7)
        return fs


def build():
    T = Tex(4)
    px, ex = T.px, T.ex

    # Head: rooted dirt, moss crown covers the top; face drawn after.
    head = T.box((0, 0), (8, 10, 8), DIRT, 1, 11, roots=1)
    fx, fy, fw, fh = head['front']
    for c in range(fw):                                              # brow root
        px[fx + c, fy + 2] = (ROOT[1] if 1 <= c <= 6 else ROOT[0]) + (255,)
    px[fx, fy + 1] = px[fx + 7, fy + 1] = ROOT[0] + (255,)
    for ox, core in ((1, 2), (5, 5)):                                # eye sockets, glowing core toward the nose
        for dx in range(2):
            for dy in range(2):
                px[fx + ox + dx, fy + 3 + dy] = SOCKET + (255,)
        px[fx + core, fy + 3] = GLOW[1] + (255,)
        px[fx + core, fy + 4] = GLOW[0] + (255,)
        ex[fx + core, fy + 3] = GLOW[1] + (255,)
        ex[fx + core, fy + 4] = GLOW[0] + (190,)
    for c in (1, 6):                                                 # cheek rootlets
        for r in range(6, 10):
            px[fx + c, fy + r] = (ROOT[1] if r < 9 else ROOT[0]) + (255,)
    for c in range(fw):                                              # moss fringe at the hairline
        if hsh(c, 0, 5) > 0.35:
            px[fx + c, fy] = MOSS[1 + (c % 2)] + (255,)

    # Nose 2x4x2 (the iron golem's nose): a gnarled root knob, lighter on the front, dark tip.
    nose = faces(24, 0, 2, 4, 2)
    for name, (x0, y0, w, h) in nose.items():
        for y in range(h):
            for x in range(w):
                if name == 'front':
                    c = ROOT[2] if (x == 0 and y in (0, 1)) else ROOT[1] if y < h - 1 else ROOT[0]
                elif name in ('top',):
                    c = ROOT[1]
                elif name == 'bottom':
                    c = DIRT[0]
                else:
                    c = ROOT[0] if y == h - 1 or x == w - 1 else DIRT[2]
                px[x0 + x, y0 + y] = c + (255,)

    # Moss crown with azalea flowers on top.
    crown = T.box((84, 0), (9, 2, 9), MOSS, 2, 21)
    tx, ty, tw, th = crown['top']
    for (x, y) in ((2, 2), (6, 1), (4, 5), (1, 6), (7, 6)):
        px[tx + x, ty + y] = FLOWER[(x + y) % 2] + (255,)
    for name in ('front', 'back', 'left', 'right'):
        x0, y0, w, h = crown[name]
        for c in range(w):
            px[x0 + c, y0 + h - 1] = MOSS[0] + (255,)

    # Sapling and twig.
    T.box((84, 12), (1, 3, 1), WOOD, 1, 31)
    for uv in ((90, 12), (100, 12)):
        T.box(uv, (3, 1, 2), LEAF, 1, 32)
    T.box((110, 12), (1, 1, 4), WOOD, 1, 33)

    # Body: moss overhang from the shoulders, crawling roots, one thick root across the chest.
    body = T.box((0, 40), (18, 12, 11), DIRT, 1, 41, roots=4, moss=2)
    bx, by, bw, bh = body['front']
    for c in range(bw):
        r = 6 + (1 if 4 <= c <= 13 else 0)
        px[bx + c, by + r] = ROOT[1] + (255,)
        px[bx + c, by + r + 1] = ROOT[0] + (255,)
        if c % 5 == 2:
            px[bx + c, by + r] = ROOT[2] + (255,)
    T.box((0, 70), (9, 5, 6), DIRT, 0, 43, roots=1)

    # Shoulder moss pads with a flower each.
    for v, salt in ((21, 51), (31, 52)):
        pad = T.box((82, v), (6, 3, 7), MOSS, 2, salt)
        x0, y0, w, h = pad['top']
        px[x0 + 2, y0 + 3] = FLOWER[1] + (255,)
        px[x0 + 4, y0 + 1] = FLOWER[0] + (255,)
        for name in ('front', 'back', 'left', 'right'):
            fx0, fy0, fw0, fh0 = pad[name]
            for c in range(fw0):
                px[fx0 + c, fy0 + fh0 - 1] = MOSS[0] + (255,)

    # Arms: moss at the shoulder, root spiralling down, knuckled root hands.
    for v, salt in ((21, 61), (58, 62)):
        arm = T.box((60, v), (4, 30, 6), DIRT, 1, salt, roots=0, moss=2)
        ring = []
        for name in ('front', 'left', 'back', 'right'):
            x0, y0, w, h = arm[name]
            ring += [(x0 + c, y0) for c in range(w)]
        for row in range(3, 27):
            x, y0 = ring[(row * 2) % len(ring)]
            px[x, y0 + row] = (ROOT[2] if row % 6 == 0 else ROOT[1]) + (255,)
            x2, _ = ring[(row * 2 + 1) % len(ring)]
            px[x2, y0 + row] = ROOT[0] + (255,)
        for name in ('front', 'left', 'back', 'right'):
            x0, y0, w, h = arm[name]
            for c in range(w):
                for r in range(h - 3, h):
                    knuckle = (r == h - 3 and c % 2 == 0) or (r == h - 1)
                    px[x0 + c, y0 + r] = (ROOT[0] if knuckle else ROOT[1] if r == h - 2 else DIRT[1]) + (255,)

    # Legs: moss at the hip, roots crawling down to the feet.
    T.box((37, 0), (6, 16, 5), DIRT, 1, 71, roots=2, moss=1)
    T.box((60, 0), (6, 16, 5), DIRT, 1, 72, roots=2, moss=1)

    # Fingers, toes, hanging roots.
    for u, hh in ((82, 4), (86, 5), (90, 3)):
        T.box((u, 42), (1, hh, 1), ROOT, 1, 81)
    T.box((82, 48), (1, 1, 3), ROOT, 1, 82)
    T.box((82, 54), (1, 3, 1), ROOT, 1, 83)
    return T.img, T.eyes


