"""아맞다 icon: lightbulb/aha moment on warm gradient."""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import os
from math import pi, cos, sin

SIZE = 512

def load_font(size):
    for fp in ["C:/Windows/Fonts/malgunbd.ttf", "C:/Windows/Fonts/malgun.ttf",
               "C:/Windows/Fonts/arialbd.ttf"]:
        if os.path.exists(fp):
            return ImageFont.truetype(fp, size)
    return ImageFont.load_default()

def rounded_bg(c_top, c_bot, radius=80):
    img = Image.new("RGBA",(SIZE,SIZE),(0,0,0,0))
    grad = Image.new("RGB",(SIZE,SIZE),c_top)
    d = ImageDraw.Draw(grad)
    for y in range(SIZE):
        t = y/SIZE
        d.line([(0,y),(SIZE,y)], fill=tuple(int(c_top[i]*(1-t)+c_bot[i]*t) for i in range(3)))
    mask = Image.new("L",(SIZE,SIZE),0)
    ImageDraw.Draw(mask).rounded_rectangle([0,0,SIZE,SIZE], radius=radius, fill=255)
    img.paste(grad,(0,0),mask)
    return img

# Warm sunshine gradient — yellow to orange (aha moment!)
img = rounded_bg((0xFF, 0xD1, 0x4A), (0xFF, 0x8A, 0x3C))

# Sun rays radiating from center (subtle)
rays = Image.new("RGBA", img.size, (0,0,0,0))
rd = ImageDraw.Draw(rays)
cx, cy = SIZE//2, SIZE//2 - 20
for i in range(12):
    ang = i * pi / 6
    x1 = cx + 180 * cos(ang)
    y1 = cy + 180 * sin(ang)
    x2 = cx + 260 * cos(ang)
    y2 = cy + 260 * sin(ang)
    rd.line([(x1,y1),(x2,y2)], fill=(255,255,255,90), width=14)
rays = rays.filter(ImageFilter.GaussianBlur(3))
img.alpha_composite(rays)

# Lightbulb body (white rounded shape) with big exclamation mark
# Draw lightbulb
d = ImageDraw.Draw(img)

# Bulb top (circle) + neck (rectangle) + base (trapezoid)
bulb_cx, bulb_cy = SIZE//2, SIZE//2 - 30
bulb_r = 140

# Shadow
sh = Image.new("RGBA", img.size, (0,0,0,0))
shd = ImageDraw.Draw(sh)
shd.ellipse([bulb_cx-bulb_r+10, bulb_cy-bulb_r+18, bulb_cx+bulb_r+10, bulb_cy+bulb_r+18],
            fill=(0,0,0,80))
sh = sh.filter(ImageFilter.GaussianBlur(12))
img.alpha_composite(sh)

# Main bulb circle
d.ellipse([bulb_cx-bulb_r, bulb_cy-bulb_r, bulb_cx+bulb_r, bulb_cy+bulb_r],
          fill=(255, 250, 240))

# Bulb neck (below circle)
neck_w = 90
neck_top = bulb_cy + bulb_r - 20
neck_bot = neck_top + 60
d.rectangle([bulb_cx-neck_w//2, neck_top, bulb_cx+neck_w//2, neck_bot],
            fill=(255, 250, 240))

# Bulb base (screw threads) - rounded gray rectangle
base_w = 80
d.rounded_rectangle([bulb_cx-base_w//2, neck_bot, bulb_cx+base_w//2, neck_bot+40],
                    radius=10, fill=(140, 140, 150))
# Thread lines
for i in range(3):
    y = neck_bot + 8 + i*10
    d.line([(bulb_cx-base_w//2+4, y), (bulb_cx+base_w//2-4, y)],
           fill=(90,90,100), width=4)

# Big "!" inside bulb in warm orange
excl_font = load_font(200)
text = "!"
bbox = d.textbbox((0,0), text, font=excl_font)
w = bbox[2]-bbox[0]; h = bbox[3]-bbox[1]
tx = bulb_cx - w//2 - bbox[0]
ty = bulb_cy - h//2 - bbox[1] - 15
d.text((tx, ty), text, fill=(0xFF, 0x6B, 0x1F), font=excl_font)

img.convert("RGB").save("icon_amajda.png")
print("saved icon_amajda.png")
