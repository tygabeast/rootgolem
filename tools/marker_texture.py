#!/usr/bin/env python3
"""Boundary Marker textures in the Root Golem's palette (vanilla-like shading).

block(): 16x16 sheet for the block model's explicit UVs
    post side  u0..4,  v3..13   root-wrapped wood, lit from the left
    post base  u4..8,  v4..8
    collar     side u8..14 v0..2, top u8..14 v4..10, bottom u8..14 v10..16 (moss)
    bulb       side u4..8 v0..3, top u4..8 v8..12 (shroomlight, drawn full-bright)
icon():  16x16 inventory sprite: bulb, moss collar, post, dark outline.
"""
from PIL import Image

from golem_texture import DIRT, MOSS, ROOT, hsh

WOOD = [(70, 50, 32), (92, 66, 42), (114, 84, 54)]
SHROOM = [(222, 116, 46), (246, 158, 72), (255, 200, 120), (255, 234, 178)]
OUTLINE = (36, 25, 16)


def _moss(x, y, salt, base=2):
    t = base + (1 if hsh(x // 2, y // 2, salt) > 0.75 else 0) - (1 if hsh(x // 2, y // 2, salt) < 0.25 else 0)
    return MOSS[max(0, min(3, t))]


def block():
    m = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = m.load()
    # Post side: lit column on the left, shadow on the right, a root wrapping round it.
    for y in range(3, 13):
        for x in range(4):
            c = WOOD[2] if x == 0 else WOOD[1] if x in (1, 2) else WOOD[0]
            if hsh(x, y, 3) > 0.85:
                c = WOOD[0]
            p[x, y] = c + (255,)
    for y in range(3, 13):                       # root spiralling down: one pixel per row, with a shadow below
        x = (y * 3 // 2) % 4
        p[x, y] = (ROOT[2] if x == 0 else ROOT[1]) + (255,)
    for y in (12,):                              # dark foot where it meets the ground
        for x in range(4):
            p[x, y] = DIRT[0] + (255,)
    # Post base.
    for y in range(4, 8):
        for x in range(4, 8):
            p[x, y] = DIRT[1] + (255,)
    # Moss collar: side (top row lighter, stepped drip below), top, darker underside.
    for x in range(8, 14):
        p[x, 0] = _moss(x, 0, 11, 3) + (255,)
        p[x, 1] = (MOSS[0] if x % 2 else _moss(x, 1, 11, 1)) + (255,)
    for y in range(4, 10):
        for x in range(8, 14):
            p[x, y] = _moss(x, y, 12, 2) + (255,)
    for y in range(10, 16):
        for x in range(8, 14):
            p[x, y] = MOSS[0] + (255,)
    # Bulb: warm bands, brightest at the top.
    for y, row in enumerate(["1223", "2332", "0112"]):
        for x, c in enumerate(row):
            p[4 + x, y] = SHROOM[int(c)] + (255,)
    for y, row in enumerate(["1221", "2332", "2332", "1221"]):
        for x, c in enumerate(row):
            p[4 + x, 8 + y] = SHROOM[int(c)] + (255,)
    return m


def icon():
    """16x16 item sprite. Drawn as a front view of the block, scaled to fill the icon height."""
    m = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = m.load()
    rows = {}
    # bulb rows 1-4 (cols 6-9), collar rows 5-6 (cols 4-11), post rows 7-14 (cols 6-9)
    for y in range(1, 5):
        for x in range(6, 10):
            band = 3 if y == 1 else 2 if y == 2 else 1 if y == 3 else 0
            if x in (6, 9) and band > 0:
                band -= 1
            rows[(x, y)] = SHROOM[band]
    for y in (5, 6):
        for x in range(4, 12):
            rows[(x, y)] = _moss(x, y, 21, 3 if y == 5 else 1)
    rows[(5, 6)] = rows[(10, 6)] = MOSS[0]
    for y in range(7, 15):
        for x in range(6, 10):
            rows[(x, y)] = WOOD[2] if x == 6 else WOOD[1] if x in (7, 8) else WOOD[0]
        rx = 6 + (y * 3 // 2) % 4                # the wrapping root
        rows[(rx, y)] = ROOT[2] if rx == 6 else ROOT[1]
    for x in range(6, 10):
        rows[(x, 14)] = DIRT[0]
    for (x, y), c in rows.items():
        p[x, y] = c + (255,)
    # 1px dark outline around the silhouette.
    for (x, y) in list(rows):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in rows and 0 <= q[0] < 16 and 0 <= q[1] < 16:
                p[q] = OUTLINE + (255,)
    return m
