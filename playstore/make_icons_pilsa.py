"""Generate 필사즉생-themed icon variants.

Concept: pun 必死則生 ↔ 筆寫則生 — calligraphy brush + red seal (낙관).
"""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from math import cos, sin, pi
import os

SIZE = 512

# Prefer serif/calligraphy-ish Korean fonts
FONT_CANDIDATES_KR = [
    "C:/Windows/Fonts/batang.ttc",
    "C:/Windows/Fonts/HMKMAMI.TTF",
    "C:/Windows/Fonts/gulim.ttc",
    "C:/Windows/Fonts/malgunbd.ttf",
    "C:/Windows/Fonts/malgun.ttf",
]

def load_font(size, bold=False):
    for fp in FONT_CANDIDATES_KR:
        if os.path.exists(fp):
            return ImageFont.truetype(fp, size)
    return ImageFont.load_default()


def rounded_bg(color_top, color_bot, radius=80):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    grad = Image.new("RGB", (SIZE, SIZE), color_top)
    d = ImageDraw.Draw(grad)
    for y in range(SIZE):
        t = y / SIZE
        r = int(color_top[0] * (1 - t) + color_bot[0] * t)
        g = int(color_top[1] * (1 - t) + color_bot[1] * t)
        b = int(color_top[2] * (1 - t) + color_bot[2] * t)
        d.line([(0, y), (SIZE, y)], fill=(r, g, b))
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, SIZE, SIZE], radius=radius, fill=255)
    img.paste(grad, (0, 0), mask)
    return img


def paper_bg(radius=80):
    # Warm cream paper texture
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    base = Image.new("RGB", (SIZE, SIZE), (0xF4, 0xE8, 0xC8))
    d = ImageDraw.Draw(base)
    # subtle aged speckles
    import random
    random.seed(7)
    for _ in range(400):
        x, y = random.randint(0, SIZE), random.randint(0, SIZE)
        a = random.randint(0, 25)
        d.point((x, y), fill=(0xC9, 0xB0, 0x7A))
    # soft radial darken at edges
    vignette = Image.new("L", (SIZE, SIZE), 0)
    vd = ImageDraw.Draw(vignette)
    vd.ellipse([-SIZE // 2, -SIZE // 2, SIZE + SIZE // 2, SIZE + SIZE // 2], fill=80)
    vignette = vignette.filter(ImageFilter.GaussianBlur(radius=80))
    base.paste((0xC8, 0xA4, 0x60), (0, 0), vignette)
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, SIZE, SIZE], radius=radius, fill=255)
    img.paste(base, (0, 0), mask)
    return img


def draw_red_seal(img, cx, cy, size, text="筆", rotated=True):
    """Draw a square red seal (낙관) with a hanja character."""
    seal = Image.new("RGBA", (size + 40, size + 40), (0, 0, 0, 0))
    sd = ImageDraw.Draw(seal)
    # Red square with slight inner border
    sd.rounded_rectangle([20, 20, size + 20, size + 20],
                         radius=size // 12,
                         fill=(0xCC, 0x22, 0x2E))
    sd.rounded_rectangle([28, 28, size + 12, size + 12],
                         radius=size // 15,
                         outline=(0xFF, 0xEC, 0xCC), width=3)
    # Character
    font = load_font(int(size * 0.62))
    bbox = sd.textbbox((0, 0), text, font=font)
    w = bbox[2] - bbox[0]
    h = bbox[3] - bbox[1]
    tx = 20 + (size - w) // 2 - bbox[0]
    ty = 20 + (size - h) // 2 - bbox[1]
    sd.text((tx, ty), text, fill=(0xFF, 0xEC, 0xCC), font=font)

    if rotated:
        seal = seal.rotate(-6, resample=Image.BICUBIC)

    img.alpha_composite(seal, (cx - seal.size[0] // 2, cy - seal.size[1] // 2))


def draw_brush_stroke(img, start, end, max_width, color=(20, 20, 28, 255),
                      taper_start=0.3, taper_end=1.0):
    """Draw a tapered brush stroke from start to end."""
    sx, sy = start
    ex, ey = end
    steps = 200
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    for i in range(steps):
        t = i / (steps - 1)
        x = sx + (ex - sx) * t
        y = sy + (ey - sy) * t
        # Tapered width: thin→fat→thin
        tw = taper_start + (taper_end - taper_start) * (1 - abs(2 * t - 1))
        w = max_width * tw
        ld.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=color)
    # slight blur for ink feel
    layer = layer.filter(ImageFilter.GaussianBlur(radius=1.5))
    img.alpha_composite(layer)


def draw_hanja_big(img, ch, color, size_ratio=0.72, offset=(0, 0), shadow=False):
    font = load_font(int(SIZE * size_ratio))
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    bbox = ld.textbbox((0, 0), ch, font=font)
    w = bbox[2] - bbox[0]
    h = bbox[3] - bbox[1]
    x = (SIZE - w) // 2 - bbox[0] + offset[0]
    y = (SIZE - h) // 2 - bbox[1] + offset[1]
    if shadow:
        sl = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(sl).text((x + 8, y + 14), ch, fill=(0, 0, 0, 130), font=font)
        sl = sl.filter(ImageFilter.GaussianBlur(radius=8))
        img.alpha_composite(sl)
    ld.text((x, y), ch, fill=color, font=font)
    img.alpha_composite(layer)


# ============================================================
# A — 한지 배경 + 붓 + 낙관 "筆"
# ============================================================
a = paper_bg()
# Diagonal bold brush stroke
draw_brush_stroke(a, (120, 380), (400, 130), max_width=110,
                  color=(18, 18, 22, 255))
# Ink drip at end
ImageDraw.Draw(a).ellipse([370, 100, 430, 160], fill=(18, 18, 22, 255))
# Red seal bottom-right
draw_red_seal(a, SIZE - 130, SIZE - 130, 150, text="筆")
a.save("icon_pilsa_A.png")

# ============================================================
# B — 진한 남색 배경 + 한자 "筆" 큼지막 + 빨간 낙관 "生"
# ============================================================
b = rounded_bg((0x1A, 0x2A, 0x5C), (0x0B, 0x17, 0x3F))
# subtle inner glow
glow = Image.new("RGBA", b.size, (0, 0, 0, 0))
gd = ImageDraw.Draw(glow)
gd.ellipse([100, 100, SIZE - 100, SIZE - 100], fill=(90, 140, 255, 40))
glow = glow.filter(ImageFilter.GaussianBlur(radius=50))
b.alpha_composite(glow)
# Big 筆 character in warm white
draw_hanja_big(b, "筆", color=(0xFD, 0xF6, 0xE3, 255), size_ratio=0.78,
               offset=(-20, -10), shadow=True)
# Red seal "生" bottom-right
draw_red_seal(b, SIZE - 115, SIZE - 115, 140, text="生")
b.save("icon_pilsa_B.png")

# ============================================================
# C — 한지배경 + 세로 4자 "필사즉생" + 빨간 점(낙관 느낌)
# ============================================================
c = paper_bg()
# Vertical 4-char calligraphy "筆寫則生"
chars = ["筆", "寫", "則", "生"]
font = load_font(int(SIZE * 0.18))
cd = ImageDraw.Draw(c)
line_h = int(SIZE * 0.20)
total_h = line_h * 4
start_y = (SIZE - total_h) // 2 - 20
col_x = SIZE // 2 + 20
for i, ch in enumerate(chars):
    y = start_y + i * line_h
    # shadow
    sl = Image.new("RGBA", c.size, (0, 0, 0, 0))
    ImageDraw.Draw(sl).text((col_x + 4, y + 6), ch, fill=(0, 0, 0, 100), font=font)
    sl = sl.filter(ImageFilter.GaussianBlur(radius=4))
    c.alpha_composite(sl)
    cd.text((col_x, y), ch, fill=(14, 14, 18, 255), font=font)
# Small red seal bottom
draw_red_seal(c, 90, SIZE - 90, 90, text="印")
c.save("icon_pilsa_C.png")

# Flatten all to RGB
for fn in ("icon_pilsa_A.png", "icon_pilsa_B.png", "icon_pilsa_C.png"):
    im = Image.open(fn).convert("RGB")
    im.save(fn)
    print(f"Saved {fn}")
