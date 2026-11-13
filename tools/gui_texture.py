#!/usr/bin/env python3
"""Planting Boundary screen texture (512x256), in the Root Golem's materials.

Layout (GUI pixels; must match BoundaryScreen.java):
  background  (0, 0) 264x184: rooted-dirt frame with moss top edge, dark oak board, golem head,
              root-wrapped map frame around the map area (14, 30) 116x116
  presets     u = 272, 308, 328, 348, 368 (widths 34, 18, 18, 18, 18), height 18,
              v = state * 20 (0 normal, 1 hover, 2 selected, 3 disabled); sapling icons baked in
  done button u = 272, v = 90 (normal) / 110 (hover), 62x18
"""
from PIL import Image

from golem_texture import DIRT, MOSS, ROOT, hsh

W, H = 264, 184
MAP = (14, 30, 116, 116)
PRESET_U = (272, 308, 328, 348, 368)
PRESET_W = (34, 18, 18, 18, 18)
PRESET_SAPLINGS = (0, 4, 3, 2, 1)          # Default, 2, 4, 8, 12: more saplings = denser planting
DONE_U, DONE_V, DONE_W, BTN_H = 272, 90, 62, 18

DARKOAK = [(52, 36, 22), (64, 45, 28), (78, 56, 35), (92, 66, 41)]
OAK = [(98, 72, 45), (120, 90, 58), (140, 106, 70), (158, 122, 82)]
OAK_HOVER = [(116, 88, 56), (138, 106, 70), (158, 122, 82), (176, 138, 94)]
GREY = [(84, 72, 60), (96, 84, 70), (106, 94, 80), (116, 104, 88)]
FLOWER = [(178, 86, 160), (214, 124, 196)]
OUTLINE = (24, 16, 10)
MAP_BG = (58, 74, 40)


class Canvas:
    def __init__(self):
        self.im = Image.new('RGBA', (512, 256), (0, 0, 0, 0))
        self.p = self.im.load()

    def px(self, x, y, c):
        if 0 <= x < 512 and 0 <= y < 256:
            self.p[x, y] = c + (255,)

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.px(xx, yy, c)

    def tex(self, x0, y0, w, h, pal, salt, base=1, grain=False):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                t = base
                cell = hsh(x // 2, y // 2, salt)
                if cell < 0.22: t -= 1
                elif cell > 0.82: t += 1
                if grain and hsh(x // 6, y, salt + 1) > 0.8: t -= 1      # wood grain
                if hsh(x, y, salt + 3) > 0.95: t -= 1
                self.px(x, y, pal[max(0, min(len(pal) - 1, t))])

    def root_line(self, x, y, dx, dy, n, salt):
        """Root along a frame side, wandering at most 1px from its start line, with a shadow."""
        x0, y0 = x, y
        for i in range(n):
            self.px(x, y, ROOT[1] if i % 4 else ROOT[2])
            self.px(x + (1 if dy else 0), y + (1 if dx else 0), ROOT[0])
            r = hsh(i, salt, 7)
            step = -1 if r < 0.15 else 1 if r > 0.85 else 0
            if dx:
                x += dx; y = max(y0 - 1, min(y0 + 1, y + step))
            else:
                y += dy; x = max(x0 - 1, min(x0 + 1, x + step))

    def moss_top(self, x0, y0, w, depth, salt, flowers=True):
        for x in range(x0, x0 + w):
            d = depth + (1 if hsh(x // 2, 0, salt) > 0.5 else 0) + (2 if hsh(x, 1, salt) > 0.84 else 0)
            for y in range(d):
                c = MOSS[3] if y == 0 else MOSS[0] if y == d - 1 else MOSS[1 + int(hsh(x // 2, y, salt) > 0.5)]
                self.px(x, y0 + y, c)
        if flowers:
            for i, fx in enumerate(range(x0 + 9, x0 + w - 4, 23)):
                self.px(fx, y0, FLOWER[i % 2]); self.px(fx + 1, y0, FLOWER[(i + 1) % 2])


def background(c, head):
    # Rooted-dirt frame with roots along each side.
    c.tex(0, 4, W, H - 4, DIRT, 1)
    c.rect(0, 4, W, 1, OUTLINE); c.rect(0, H - 1, W, 1, OUTLINE)
    c.rect(0, 4, 1, H - 4, OUTLINE); c.rect(W - 1, 4, 1, H - 4, OUTLINE)
    c.root_line(2, 9, 1, 0, W - 6, 2)
    c.root_line(3, H - 4, 1, 0, W - 7, 3)
    c.root_line(2, 10, 0, 1, H - 16, 4)
    c.root_line(W - 4, 10, 0, 1, H - 16, 5)
    # Moss over the top edge, trailing down both corners.
    c.moss_top(0, 4, W, 4, 6)
    for cx, n in ((0, 14), (W - 2, 18)):
        for yy in range(n):
            if hsh(yy, cx, 5) > 0.3:
                c.px(cx, 6 + yy, MOSS[1]); c.px(cx + 1, 6 + yy, MOSS[0])
    # Dark oak board.
    c.tex(7, 12, W - 14, H - 19, DARKOAK, 3, 1, grain=True)
    c.rect(7, 12, W - 14, 1, OUTLINE); c.rect(7, 12, 1, H - 19, OUTLINE)
    # Map area with a root-wrapped dark frame (the map itself is drawn in game).
    x, y, w, h = MAP
    c.rect(x, y, w, h, MAP_BG)
    for t in range(2):
        col = DARKOAK[t]
        c.rect(x - 2 + t, y - 2 + t, w + 4 - 2 * t, 1, col); c.rect(x - 2 + t, y + h + 1 - t, w + 4 - 2 * t, 1, col)
        c.rect(x - 2 + t, y - 2 + t, 1, h + 4 - 2 * t, col); c.rect(x + w + 1 - t, y - 2 + t, 1, h + 4 - 2 * t, col)
    for i in range(0, w, 7):
        c.px(x + i, y - 2, ROOT[1]); c.px(x + i + 1, y - 1, ROOT[0]); c.px(x + i + 3, y + h + 1, ROOT[1])
    for i in range(0, h, 9):
        c.px(x - 2, y + i, ROOT[1]); c.px(x + w + 1, y + i + 4, ROOT[1])
    # The golem's head beside the title.
    c.im.alpha_composite(head.resize((26, 26), Image.LANCZOS), (8, 0))


def button(c, u, v, w, pal, salt, moss=False, saplings=0):
    c.rect(u, v, w, BTN_H, OUTLINE)
    c.tex(u + 1, v + 1, w - 2, BTN_H - 2, pal, salt, 1, grain=True)
    c.rect(u + 1, v + 1, w - 2, 1, pal[3]); c.rect(u + 1, v + BTN_H - 2, w - 2, 1, pal[0])
    if moss:
        c.moss_top(u + 1, v + 1, w - 2, 2, salt + 50, flowers=False)
    span = w - 6
    for k in range(saplings):
        sx = u + 3 + (span * (2 * k + 1)) // (2 * saplings) - 1
        c.px(sx, v + 3, (110, 180, 66)); c.px(sx + 1, v + 3, (88, 156, 54))
        c.px(sx, v + 4, (64, 124, 42)); c.px(sx + 1, v + 4, (110, 180, 66))
        c.px(sx, v + 5, (84, 60, 36))


def build(head):
    c = Canvas()
    background(c, head)
    for i, (u, w) in enumerate(zip(PRESET_U, PRESET_W)):
        for state, pal in enumerate((OAK, OAK_HOVER, OAK, GREY)):
            button(c, u, state * 20, w, pal, 900 + u, moss=(state == 2), saplings=PRESET_SAPLINGS[i])
    button(c, DONE_U, DONE_V, DONE_W, OAK, 950)
    button(c, DONE_U, DONE_V + 20, DONE_W, OAK_HOVER, 950)
    return c.im
