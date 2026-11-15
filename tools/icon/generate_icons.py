#!/usr/bin/env python3
"""Generates CrystalN icons: purple faceted crystal gem.

Outputs:
  desktop/src/main/resources/crystaln/icon.png       512x512, rounded bg (window/rpm icon)
  desktop/src/main/resources/crystaln/icon-mark.png  512x512, crystal only (header badge)

Also prints the Android vector paths (same numbers as ic_launcher_foreground.xml).
Geometry lives in a 108x108 viewBox, supersampled x10 and downsampled for clean
antialiasing.
"""

from PIL import Image, ImageDraw
import os

S = 10  # supersample: 108 -> 1080
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT_RES = os.path.join(ROOT, "desktop/src/main/resources/crystaln")
OUT_SVG = os.path.dirname(os.path.abspath(__file__))

# --- geometry (108x108) ------------------------------------------------------
G = (54, 56)  # gem center
A = (54, 14)  # top apex
B = (84, 38)  # right-top
C = (76, 76)  # right-bottom
D = (54, 92)  # bottom apex
E = (32, 76)  # left-bottom
F = (24, 38)  # left-top

FACES = [  # (triangle, fill)
    (A, B, G, "#C9B4FF"),
    (B, C, G, "#8B5CF6"),
    (C, D, G, "#6D28D9"),
    (D, E, G, "#5B21B6"),
    (E, F, G, "#A78BFA"),
    (F, A, G, "#B49BF9"),
]

SPARKLES = [  # (cx, cy, r, alpha)
    (63, 33, 8, 235),
    (46, 66, 4.5, 190),
]

# Тонкие светлые рёбра между гранями: (p1, p2, alpha)
EDGES = [
    ((A, G), 70),
    ((B, G), 45),
    ((C, G), 55),
    ((D, G), 70),
    ((E, G), 45),
    ((F, G), 55),
]

BG_TOP = (42, 21, 87)      # #2A1557
BG_BOTTOM = (21, 10, 46)   # #150A2E
GLOW = (124, 58, 237)      # #7C3AED
RADIUS = 24


def ss(v):
    return v * S


def diamond(cx, cy, r):
    return [(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)]


def draw_crystal(d, scale=1.0):
    cx, cy = G
    for a, b, c, _ in FACES:
        pass
    for (p1, p2, p3, color) in FACES:
        d.polygon(
            [(ss(cx + (p1[0] - cx) * scale), ss(cy + (p1[1] - cy) * scale)),
             (ss(cx + (p2[0] - cx) * scale), ss(cy + (p2[1] - cy) * scale)),
             (ss(cx + (p3[0] - cx) * scale), ss(cy + (p3[1] - cy) * scale))],
            fill=color,
        )
    for ((e1, e2), alpha) in EDGES:
        d.line(
            [(ss(cx + (e1[0] - cx) * scale), ss(cy + (e1[1] - cy) * scale)),
             (ss(cx + (e2[0] - cx) * scale), ss(cy + (e2[1] - cy) * scale))],
            fill=(237, 228, 255, int(alpha)), width=int(ss(0.55)),
        )
    for (sx, sy, r, alpha) in SPARKLES:
        x, y = cx + (sx - cx) * scale, cy + (sy - cy) * scale
        d.polygon([(ss(px), ss(py)) for px, py in diamond(x, y, r * scale)],
                  fill=(245, 239, 255, alpha))


def gradient(w, h, top, bottom):
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        t = y / (h - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
        for x in range(w):
            px[x, y] = c
    return img


def with_rounded_bg(size):
    """Full icon: rounded-square gradient bg + glow + crystal."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ss_size = size
    # gradient layer
    grad = gradient(ss_size, ss_size, BG_TOP, BG_BOTTOM)
    mask = Image.new("L", (ss_size, ss_size), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, ss_size - 1, ss_size - 1], radius=int(ss_size / 108 * RADIUS), fill=255)
    img.paste(grad, (0, 0), mask)
    # radial glow
    glow = Image.new("RGBA", (ss_size, ss_size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([ss(54) - ss(40), ss(52) - ss(40), ss(54) + ss(40), ss(52) + ss(40)],
               fill=GLOW + (80,))
    glow = glow.filter(__import__("PIL.ImageFilter", fromlist=["GaussianBlur"]).GaussianBlur(ss(10)))
    glow.putalpha(glow.split()[3].point(lambda a: a))  # keep
    r, g_, b, a = glow.split()
    bg_alpha = img.split()[3]
    img.paste(glow, (0, 0), Image.composite(a, Image.new("L", img.size, 0), bg_alpha))
    # crystal
    d = ImageDraw.Draw(img)
    draw_crystal(d, scale=0.92)
    return img


def crystal_only(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_crystal(d, scale=0.92)
    return img


def save_512(img, path):
    img.resize((512, 512), Image.LANCZOS).save(path)


def main():
    os.makedirs(OUT_RES, exist_ok=True)
    save_512(with_rounded_bg(108 * S), os.path.join(OUT_RES, "icon.png"))
    save_512(crystal_only(108 * S), os.path.join(OUT_RES, "icon-mark.png"))

    # SVG source (viewBox 108, same numbers as Android vector)
    face_paths = []
    for (p1, p2, p3, color) in FACES:
        face_paths.append(
            f'    <path android:fillColor="{color}" '
            f'android:pathData="M{p1[0]},{p1[1]} L{p2[0]},{p2[1]} L{p3[0]},{p3[1]} Z" />'
        )
    print("\n".join(face_paths))


if __name__ == "__main__":
    main()
