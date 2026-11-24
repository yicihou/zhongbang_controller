# -*- coding: utf-8 -*-
"""极简绘图库：圆角框 / 菱形判断 / 折线箭头 / 图例，用于生成方案流程图。"""
import math
from PIL import Image, ImageDraw, ImageFont

S = 2  # 超采样倍数
FP = "/System/Library/Fonts/Hiragino Sans GB.ttc"

RED = (198, 18, 46)
RED_D = (146, 11, 32)
TINT = (255, 243, 245)
EDGE = (240, 190, 198)
INK = (26, 30, 36)
MUT = (108, 116, 128)
LINE = (203, 209, 217)
SOFT = (245, 246, 248)
WHITE = (255, 255, 255)
DARK = (42, 46, 54)

_fc = {}


def F(sz, bold=False):
    k = (round(sz, 1), bold)
    if k not in _fc:
        _fc[k] = ImageFont.truetype(FP, int(round(sz * S)), index=1 if bold else 0)
    return _fc[k]


def wrap(s, font, maxw):
    """按像素宽度硬折行，中文按字、英文按词。"""
    lim = maxw * S
    out = []
    for para in s.split("\n"):
        cur = ""
        for ch in para:
            if ch == "\u3000":
                ch = "  "
            if font.getlength(cur + ch) <= lim or not cur:
                cur += ch
            else:
                out.append(cur)
                cur = ch
        out.append(cur)
    return out


class C:
    def __init__(self, w, h, bg=WHITE):
        self.w, self.h = w, h
        self.im = Image.new("RGB", (int(w * S), int(h * S)), bg)
        self.d = ImageDraw.Draw(self.im)

    # ---------- 基础图元 ----------
    def rrect(self, x, y, w, h, r=12, fill=WHITE, outline=LINE, width=2):
        self.d.rounded_rectangle([x * S, y * S, (x + w) * S, (y + h) * S],
                                 radius=r * S, fill=fill, outline=outline,
                                 width=max(1, int(width * S)))

    def _lines(self, cx, cy, lines, sz, bold, color, align, lh):
        f = F(sz, bold)
        step = sz * lh
        y0 = cy - step * (len(lines) - 1) / 2
        for i, ln in enumerate(lines):
            if align == "center":
                self.d.text((cx * S, (y0 + i * step) * S), ln, font=f, fill=color, anchor="mm")
            else:
                self.d.text((cx * S, (y0 + i * step) * S), ln, font=f, fill=color, anchor="lm")

    def text(self, x, y, s, sz=18, bold=False, color=INK, align="center",
             maxw=None, lh=1.45):
        lines = wrap(s, F(sz, bold), maxw) if maxw else s.split("\n")
        self._lines(x, y, lines, sz, bold, color, align, lh)

    def node(self, x, y, w, h, s, kind="sys", sz=18, r=12, shadow=False):
        cfg = {
            "client": (TINT, RED, INK),
            "sys": (WHITE, LINE, INK),
            "staff": (SOFT, LINE, INK),
            "start": (RED, RED, WHITE),
            "end": (DARK, DARK, WHITE),
            "note": ((250, 250, 251), (214, 219, 226), MUT),
            "warn": (WHITE, RED, RED_D),
            "gold": ((255, 248, 235), (233, 196, 128), (120, 78, 20)),
        }[kind]
        fill, ol, tc = cfg
        if shadow:
            self.rrect(x + 3, y + 4, w, h, r, fill=(238, 239, 242), outline=(238, 239, 242))
        self.rrect(x, y, w, h, r, fill=fill, outline=ol, width=2)
        lines = wrap(s, F(sz), w - 26)
        need = len(lines) * sz * 1.45
        if need > h - 6:
            print("  [OVERFLOW] %.0f>%.0f :: %s" % (need, h - 6, s.replace(chr(10), " / ")), flush=True)
        if any(F(sz).getlength(l) / S > w - 20 for l in lines):
            print("  [WIDE] :: %s" % s.replace(chr(10), " / "), flush=True)
        self.text(x + w / 2, y + h / 2, s, sz=sz, color=tc, maxw=w - 26)
        return (x, y, w, h)

    def pill(self, cx, y, w, h, s, kind="start", sz=18):
        self.node(cx - w / 2, y, w, h, s, kind=kind, sz=sz, r=h / 2)

    def diamond(self, cx, cy, w, h, s, sz=17):
        pts = [(cx, cy - h / 2), (cx + w / 2, cy), (cx, cy + h / 2), (cx - w / 2, cy)]
        sp = [(p[0] * S, p[1] * S) for p in pts]
        self.d.polygon(sp, fill=WHITE, outline=RED, width=int(2 * S))
        self.text(cx, cy, s, sz=sz, color=RED_D, maxw=w * 0.62, lh=1.35)

    def _head(self, p0, p1, color, size=9):
        ang = math.atan2(p1[1] - p0[1], p1[0] - p0[0])
        L, W = size * S, size * 0.62 * S
        tip = (p1[0] * S, p1[1] * S)
        left = (tip[0] - L * math.cos(ang) + W * math.sin(ang),
                tip[1] - L * math.sin(ang) - W * math.cos(ang))
        right = (tip[0] - L * math.cos(ang) - W * math.sin(ang),
                 tip[1] - L * math.sin(ang) + W * math.cos(ang))
        self.d.polygon([tip, left, right], fill=color)

    def arrow(self, pts, color=MUT, width=2.2, dash=False, head=True,
              label=None, lsz=14, lcolor=None, lpos=None, lpad=5):
        pl = [(p[0] * S, p[1] * S) for p in pts]
        if dash:
            for i in range(len(pl) - 1):
                x0, y0 = pl[i]
                x1, y1 = pl[i + 1]
                dist = math.hypot(x1 - x0, y1 - y0)
                if dist == 0:
                    continue
                ux, uy = (x1 - x0) / dist, (y1 - y0) / dist
                t = 0.0
                while t < dist:
                    a = (x0 + ux * t, y0 + uy * t)
                    t2 = min(t + 9 * S, dist)
                    b = (x0 + ux * t2, y0 + uy * t2)
                    self.d.line([a, b], fill=color, width=int(width * S))
                    t += 15 * S
        else:
            self.d.line(pl, fill=color, width=int(width * S), joint="curve")
        if head:
            self._head(pts[-2], pts[-1], color, size=9.5 if width > 2 else 8.5)
        if label:
            lx, ly = lpos if lpos else ((pts[0][0] + pts[-1][0]) / 2, (pts[0][1] + pts[-1][1]) / 2)
            f = F(lsz)
            tw = f.getlength(label) / S
            self.d.rectangle([(lx - tw / 2 - lpad) * S, (ly - lsz * 0.78) * S,
                              (lx + tw / 2 + lpad) * S, (ly + lsz * 0.78) * S], fill=WHITE)
            self.d.text((lx * S, ly * S), label, font=f, fill=lcolor or MUT, anchor="mm")

    def legend(self, x, y, items, sz=14, gap=26):
        cx = x
        for label, kind in items:
            cfg = {"client": (TINT, RED), "sys": (WHITE, LINE),
                   "staff": (SOFT, LINE), "note": (WHITE, DARK)}[kind]
            self.rrect(cx, y - 7, 14, 14, 3, fill=cfg[0], outline=cfg[1], width=1.5)
            f = F(sz)
            self.d.text(((cx + 21) * S, y * S), label, font=f, fill=MUT, anchor="lm")
            cx += 21 + f.getlength(label) / S + gap

    def save(self, path):
        self.im.save(path, dpi=(300, 300))
