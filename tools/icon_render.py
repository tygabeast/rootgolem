#!/usr/bin/env python3
"""Renders the mod icon: the Root Golem's head (with nose, moss crown, sapling and twig) from the
mod's own texture, three-quarter view, on a transparent square. Glow-layer pixels are drawn unshaded.

Usage: python3 tools/icon_render.py <entity texture> <glow texture> <out.png> [size]
"""
import math
import sys

from PIL import Image, ImageDraw

# Head parts of RootGolemModel, in the head's own space: (texture origin, cube origin, size, pivot, rotation, parent)
PARTS = {
    'head': ((0, 0), (-4, -12, -5.5), (8, 10, 8), (0, 0, 0), (0, 0, 0), None),
    'nose': ((24, 0), (-1, -5, -7.5), (2, 4, 2), (0, 0, 0), (0, 0, 0), None),
    'crown': ((84, 0), (-4.5, -12.5, -6), (9, 2, 9), (0, 0, 0), (0, 0, 0), None),
    'sprout': ((84, 12), (-0.5, -3, -0.5), (1, 3, 1), (1.5, -12.5, -1.5), (0, 0, 0), None),
    'leaf_a': ((90, 12), (0, -1, -1), (3, 1, 2), (0.5, -2.5, 0), (0, 0, -0.5), 'sprout'),
    'leaf_b': ((100, 12), (-3, -1, -1), (3, 1, 2), (-0.5, -2, 0), (0, 0, 0.5), 'sprout'),
    'twig': ((110, 12), (-0.5, -0.5, -4), (1, 1, 4), (-3, -11, 0), (-0.9, 0.5, 0), None),
}
SHADE = {'front': 0.92, 'back': 0.7, 'right': 0.8, 'left': 0.8, 'top': 1.0, 'bottom': 0.55}


def rot(p, r):
    x, y, z = p
    rx, ry, rz = r
    c, s = math.cos(rz), math.sin(rz); x, y = x * c - y * s, x * s + y * c
    c, s = math.cos(ry), math.sin(ry); x, z = x * c + z * s, -x * s + z * c
    c, s = math.cos(rx), math.sin(rx); y, z = y * c - z * s, y * s + z * c
    return x, y, z


def to_head(name, p):
    _, _, _, piv, r, parent = PARTS[name]
    p = rot(p, r)
    p = (p[0] + piv[0], p[1] + piv[1], p[2] + piv[2])
    return to_head(parent, p) if parent else p


def quads(tex, glow):
    for name, (uv, o, size, _, _, _) in PARTS.items():
        u, v = uv
        w, h, d = size
        x0, y0, z0 = o
        x1, y1, z1 = x0 + w, y0 + h, z0 + d
        faces = [
            ('front', (u + d, v + d, w, h), lambda i, j: [(x0 + i, y0 + j, z0), (x0 + i + 1, y0 + j, z0), (x0 + i + 1, y0 + j + 1, z0), (x0 + i, y0 + j + 1, z0)]),
            ('back', (u + 2 * d + w, v + d, w, h), lambda i, j: [(x1 - i, y0 + j, z1), (x1 - i - 1, y0 + j, z1), (x1 - i - 1, y0 + j + 1, z1), (x1 - i, y0 + j + 1, z1)]),
            ('right', (u, v + d, d, h), lambda i, j: [(x0, y0 + j, z1 - i), (x0, y0 + j, z1 - i - 1), (x0, y0 + j + 1, z1 - i - 1), (x0, y0 + j + 1, z1 - i)]),
            ('left', (u + d + w, v + d, d, h), lambda i, j: [(x1, y0 + j, z0 + i), (x1, y0 + j, z0 + i + 1), (x1, y0 + j + 1, z0 + i + 1), (x1, y0 + j + 1, z0 + i)]),
            ('top', (u + d, v, w, d), lambda i, j: [(x0 + i, y0, z1 - j), (x0 + i + 1, y0, z1 - j), (x0 + i + 1, y0, z1 - j - 1), (x0 + i, y0, z1 - j - 1)]),
            ('bottom', (u + d + w, v, w, d), lambda i, j: [(x0 + i, y1, z0 + j), (x0 + i + 1, y1, z0 + j), (x0 + i + 1, y1, z0 + j + 1), (x0 + i, y1, z0 + j + 1)]),
        ]
        for face, (tu, tv, tw, th), corners in faces:
            for j in range(th):
                for i in range(tw):
                    c = tex[tu + i, tv + j]
                    if c[3] == 0:
                        continue
                    g = glow[tu + i, tv + j]
                    col = g[:3] if g[3] else tuple(int(ch * SHADE[face]) for ch in c[:3])
                    yield [to_head(name, p) for p in corners(i, j)], col


def render(tex_path, glow_path, out, size=512, yaw=-35.0, pitch=8.0, ss=3):
    tex = Image.open(tex_path).convert('RGBA').load()
    glow = Image.open(glow_path).convert('RGBA').load()
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    projected = []
    for pts, col in quads(tex, glow):
        pr, depth = [], 0.0
        for x, y, z in pts:
            x, z = x * cy - z * sy, x * sy + z * cy
            y, z = y * cp - z * sp, y * sp + z * cp
            pr.append((x, y))
            depth += z
        projected.append((depth / 4, pr, col))
    xs = [p[0] for _, pr, _ in projected for p in pr]
    ys = [p[1] for _, pr, _ in projected for p in pr]
    span = max(max(xs) - min(xs), max(ys) - min(ys))
    big = size * ss
    scale = big * 0.86 / span                      # 7% margin each side
    ox = big / 2 - (min(xs) + max(xs)) / 2 * scale
    oy = big / 2 - (min(ys) + max(ys)) / 2 * scale
    img = Image.new('RGBA', (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    projected.sort(key=lambda t: -t[0])            # far to near
    for _, pr, col in projected:
        draw.polygon([(ox + x * scale, oy + y * scale) for x, y in pr], fill=col + (255,))
    img.resize((size, size), Image.LANCZOS).save(out)


if __name__ == '__main__':
    render(sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 512)
    print('wrote', sys.argv[3])
