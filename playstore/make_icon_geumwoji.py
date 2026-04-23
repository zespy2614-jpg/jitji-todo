"""그뭐지 icon: thought bubble + ? on purple gradient."""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import os

SIZE = 512

def load_font(size):
    for fp in [
        "C:/Windows/Fonts/malgunbd.ttf",
        "C:/Windows/Fonts/malgun.ttf",
        "C:/Windows/Fonts/arialbd.ttf",
        "C:/Windows/Fonts/arial.ttf",
    ]:
        if os.path.exists(fp):
            return ImageFont.truetype(fp, size)
    return ImageFont.load_default()

def rounded_bg(color_top, color_bot, radius=80):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    grad = Image.new("RGB", (SIZE, SIZE), color_top)
    d = ImageDraw.Draw(grad)
    for y in range(SIZE):
        t = y / SIZE
        r = int(color_top[0]*(1-t) + color_bot[0]*t)
        g = int(color_top[1]*(1-t) + color_bot[1]*t)
        b = int(color_top[2]*(1-t) + color_bot[2]*t)
        d.line([(0,y),(SIZE,y)], fill=(r,g,b))
    mask = Image.new("L",(SIZE,SIZE),0)
    ImageDraw.Draw(mask).rounded_rectangle([0,0,SIZE,SIZE], radius=radius, fill=255)
    img.paste(grad,(0,0),mask)
    return img

# Purple thinking gradient
img = rounded_bg((0x8E, 0x6FF5 & 0xFF, 0xE8) if False else (0x8E, 0x6F, 0xE8), (0x4B, 0x2E, 0xA8))

# Large white thought bubble
d = ImageDraw.Draw(img)
# Main bubble
d.ellipse([80, 90, 432, 390], fill=(255,255,255,245))
# Small bubble trail
d.ellipse([120, 380, 180, 440], fill=(255,255,255,230))
d.ellipse([90, 440, 130, 480], fill=(255,255,255,210))

# Question mark in purple inside bubble
font = load_font(260)
qtext = "?"
bbox = d.textbbox((0,0), qtext, font=font)
w = bbox[2]-bbox[0]; h = bbox[3]-bbox[1]
tx = (SIZE - w)//2 - bbox[0]
ty = 110 - bbox[1] + 10
# shadow
sl = Image.new("RGBA", img.size, (0,0,0,0))
ImageDraw.Draw(sl).text((tx+4, ty+8), qtext, fill=(0,0,0,60), font=font)
sl = sl.filter(ImageFilter.GaussianBlur(3))
img.alpha_composite(sl)
d.text((tx, ty), qtext, fill=(0x5B, 0x3A, 0xC2), font=font)

img.convert("RGB").save("icon_geumwoji.png")
print("saved icon_geumwoji.png")
