#!/usr/bin/env python3
"""Render design previews of the INDRA AI home screen.

These are *design previews* drawn from the same palette, artwork and layout metrics as
``HomeScreen.java`` / ``AuraOrbView.java`` - they are not device screenshots. Run from the
repository root::

    python3 scripts/preview_home.py

Writes ``.branding/home_preview.png`` (light) and ``.branding/home_preview_dark.png``.
"""

import glob
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "modules/indra-android/src/main/res")
OUT = os.path.join(ROOT, ".branding")

W, H = 900, 1800
S = W / 1080.0

LIGHT = dict(bg=(246, 247, 252), surface=(255, 255, 255), glow=(255, 232, 240),
             accent=(255, 43, 78), secondary=(124, 77, 255), text=(20, 20, 43),
             dim=(110, 113, 145), stroke=(240, 220, 227), soft=(255, 242, 246),
             nav_line=(247, 238, 241))
DARK = dict(bg=(11, 11, 22), surface=(20, 20, 31), glow=(46, 12, 40),
            accent=(255, 59, 92), secondary=(155, 107, 255), text=(244, 244, 250),
            dim=(156, 156, 180), stroke=(42, 42, 60), soft=(29, 29, 43),
            nav_line=(30, 30, 44))

FONTS = glob.glob("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")


def font(size, bold=False):
    path = glob.glob("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf") if bold else FONTS
    return ImageFont.truetype((path or FONTS)[0], int(size * S))


def circle(im, size):
    im = im.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size, size), fill=255)
    im.putalpha(mask)
    return im


def glyph(d, kind, cx, cy, size, color, width=2):
    """Tiny line-art icons so the preview does not depend on emoji fonts."""
    u = size / 2.0
    if kind == "home":
        d.line((cx - u, cy + u * 0.15, cx, cy - u * 0.7, cx + u, cy + u * 0.15),
               fill=color, width=width, joint="curve")
        d.line((cx - u * 0.7, cy, cx - u * 0.7, cy + u * 0.85, cx + u * 0.7, cy + u * 0.85),
               fill=color, width=width)
    elif kind == "chat":
        d.rounded_rectangle((cx - u, cy - u * 0.8, cx + u, cy + u * 0.45), radius=int(u * 0.45),
                            outline=color, width=width)
        d.line((cx - u * 0.35, cy + u * 0.45, cx - u * 0.1, cy + u * 0.9), fill=color, width=width)
    elif kind == "mic":
        d.rounded_rectangle((cx - u * 0.35, cy - u, cx + u * 0.35, cy + u * 0.25),
                            radius=int(u * 0.35), outline=color, width=width)
        d.arc((cx - u * 0.75, cy - u * 0.35, cx + u * 0.75, cy + u * 0.8), 0, 180,
              fill=color, width=width)
        d.line((cx, cy + u * 0.8, cx, cy + u), fill=color, width=width)
    elif kind == "bolt":
        d.line((cx + u * 0.25, cy - u, cx - u * 0.45, cy + u * 0.1, cx + u * 0.1, cy + u * 0.1,
                cx - u * 0.25, cy + u), fill=color, width=width, joint="curve")
    elif kind == "gear":
        d.ellipse((cx - u * 0.42, cy - u * 0.42, cx + u * 0.42, cy + u * 0.42),
                  outline=color, width=width)
        for i in range(8):
            import math
            a = i * math.pi / 4
            d.line((cx + math.cos(a) * u * 0.55, cy + math.sin(a) * u * 0.55,
                    cx + math.cos(a) * u, cy + math.sin(a) * u), fill=color, width=width)
    elif kind == "bell":
        d.arc((cx - u * 0.8, cy - u, cx + u * 0.8, cy + u * 0.55), 180, 360, fill=color,
              width=width)
        d.line((cx - u * 0.8, cy + u * 0.1, cx + u * 0.8, cy + u * 0.1), fill=color, width=width)
        d.ellipse((cx - u * 0.16, cy + u * 0.15, cx + u * 0.16, cy + u * 0.5), fill=color)
    elif kind == "spark":
        d.line((cx, cy - u, cx, cy + u), fill=color, width=width)
        d.line((cx - u, cy, cx + u, cy), fill=color, width=width)
        d.line((cx - u * 0.6, cy - u * 0.6, cx + u * 0.6, cy + u * 0.6), fill=color, width=width)
        d.line((cx - u * 0.6, cy + u * 0.6, cx + u * 0.6, cy - u * 0.6), fill=color, width=width)


def render(palette, path, dark):
    """Bottom-up layout: the orb absorbs whatever vertical space is left, exactly like the
    weighted ``orbBlock`` in ``HomeScreen``."""
    img = Image.new("RGB", (W, H), palette["bg"])

    # --- vertical plan -------------------------------------------------------------
    nav_top = H - 74 * S
    tools_b = nav_top - 18 * S
    tools_h = 172 * S
    tools_t = tools_b - tools_h
    cards_b = tools_t - 22 * S
    cards_h = 100 * S
    cards_t = cards_b - cards_h
    composer_b = cards_t - 24 * S
    composer_h = 88 * S
    composer_t = composer_b - composer_h
    chips_b = composer_t - 30 * S
    chips_h = 40 * S
    chips_t = chips_b - chips_h
    sub_t = chips_t - 42 * S
    title_t = sub_t - 54 * S
    aurachip_b = title_t - 26 * S
    aurachip_h = 44 * S
    aurachip_t = aurachip_b - aurachip_h
    orb_top = 206 * S
    orb_bottom = aurachip_t - 16 * S
    cy = (orb_top + orb_bottom) / 2
    R = min((orb_bottom - orb_top) / 2 - 30 * S, 330 * S)

    def rrect(box, r, fill, outline=None, width=1):
        d.rounded_rectangle(box, radius=int(r * S), fill=fill, outline=outline, width=width)

    face = Image.open(os.path.join(RES, "drawable-nodpi/indra_orb_face.webp")).convert("RGBA")

    glow = Image.new("RGB", (W, H), palette["bg"])
    ImageDraw.Draw(glow).ellipse((W / 2 - R * 1.9, cy - R * 1.9, W / 2 + R * 1.9, cy + R * 1.9),
                                 fill=palette["glow"])
    img = Image.blend(img, glow.filter(ImageFilter.GaussianBlur(90)), 0.9)
    d = ImageDraw.Draw(img)

    # --- top bar ------------------------------------------------------------------
    d.text((28 * S, 22 * S), "9:41", font=font(16), fill=palette["text"])
    d.text((W - 88 * S, 22 * S), "5G", font=font(14), fill=palette["dim"])

    avatar = circle(face, int(100 * S))
    img.paste(avatar, (int(44 * S), int(90 * S)), avatar)
    d.ellipse((44 * S, 90 * S, 144 * S, 190 * S), outline=palette["accent"], width=int(2 * S))
    d.text((162 * S, 106 * S), "INDRA AI", font=font(23, True), fill=palette["text"])
    d.text((163 * S, 138 * S), "YOUR PERSONAL AI ASSISTANT", font=font(9), fill=palette["dim"])
    for i, kind in enumerate(("bell", "gear")):
        gx = W - 62 * S - i * 58 * S
        d.ellipse((gx - 23 * S, 92 * S, gx + 23 * S, 146 * S), fill=palette["soft"],
                  outline=palette["stroke"])
        glyph(d, kind, gx, 119 * S, 20 * S,
              palette["accent"] if i == 0 else palette["text"], width=int(2 * S))

    # --- aura orb -----------------------------------------------------------------
    cx = W / 2
    for k in range(30, 0, -1):
        a = k / 30.0
        r = R * (1 + 0.12 * a)
        d.ellipse((cx - r, cy - r, cx + r, cy + r),
                  outline=(255 - int(50 * a), 70 + int(120 * a), 120 + int(110 * a)),
                  width=max(1, int(3 * S)))
    core = int(R * 0.86)
    for k in range(36, 0, -1):
        a = k / 36.0
        r = core * (1 + 0.14 * a)
        d.ellipse((cx - r, cy - r, cx + r, cy + r),
                  fill=(int(26 + 229 * (1 - a)), int(8 + 110 * (1 - a)), int(20 + 195 * (1 - a))))
    head = circle(face, core * 2)
    img.paste(head, (int(cx - core), int(cy - core)), head)

    # aura chip
    aura_label = "\u25c9  AI Aura \u00b7 Energy"
    cw = d.textlength(aura_label, font=font(12, True)) + 44 * S
    rrect((cx - cw / 2, aurachip_t, cx + cw / 2, aurachip_b), 22, palette["surface"],
          palette["stroke"])
    d.text((cx - cw / 2 + 22 * S, aurachip_t + 13 * S), aura_label, font=font(12, True),
           fill=palette["accent"])

    # greeting
    hello = "Hello, Arjun"
    d.text((cx - d.textlength(hello, font=font(30, True)) / 2, title_t), hello,
           font=font(30, True), fill=palette["text"])
    sub = "I'm here. Ask me anything, or speak."
    d.text((cx - d.textlength(sub, font=font(13)) / 2, sub_t + 6 * S), sub, font=font(13),
           fill=palette["dim"])

    # starter chips
    starters = ("Summarise my day", "Help me plan something", "What do you remember?")
    widths = [d.textlength(c, font=font(11, True)) + 34 * S for c in starters]
    total = sum(widths) + 8 * S * (len(starters) - 1)
    x = cx - total / 2
    for c, wpx in zip(starters, widths):
        rrect((x, chips_t, x + wpx, chips_b), 20, palette["surface"], palette["stroke"])
        d.text((x + 17 * S, chips_t + 12 * S), c, font=font(11, True), fill=palette["secondary"])
        x += wpx + 8 * S

    # composer
    rrect((28 * S, composer_t, W - 28 * S, composer_b), 26, palette["surface"], palette["stroke"])
    d.ellipse((42 * S, composer_t + 16 * S, 94 * S, composer_t + 68 * S), fill=palette["soft"],
              outline=palette["stroke"])
    d.text((60 * S, composer_t + 25 * S), "+", font=font(19, True), fill=palette["accent"])
    d.text((112 * S, composer_t + 36 * S), "Message INDRA\u2026", font=font(14),
           fill=(150, 153, 182))
    mx = W - 124 * S
    d.ellipse((mx, composer_t + 14 * S, mx + 52 * S, composer_t + 66 * S), fill=palette["accent"])
    d.polygon([(mx + 17 * S, composer_t + 27 * S), (mx + 40 * S, composer_t + 40 * S),
               (mx + 17 * S, composer_t + 53 * S)], fill=(255, 255, 255))

    # quick action cards
    card_w = (W - 56 * S - 36 * S) / 4
    for i, (label, kind) in enumerate((("Voice", "mic"), ("Memory", "chat"),
                                       ("Triggers", "bolt"), ("Aura", "spark"))):
        x0 = 28 * S + i * (card_w + 12 * S)
        rrect((x0, cards_t, x0 + card_w, cards_b), 20, palette["surface"], palette["stroke"])
        d.ellipse((x0 + 14 * S, cards_t + 14 * S, x0 + 46 * S, cards_t + 46 * S), fill=palette["soft"])
        glyph(d, kind, x0 + 30 * S, cards_t + 30 * S, 15 * S, palette["secondary"], width=int(2 * S))
        d.text((x0 + 14 * S, cards_t + 60 * S), label, font=font(11, True), fill=palette["text"])

    # quick tools
    rrect((28 * S, tools_t, W - 28 * S, tools_b), 24, palette["surface"], palette["stroke"])
    d.text((48 * S, tools_t + 18 * S), "QUICK TOOLS", font=font(10, True), fill=palette["accent"])
    for i, (title, sub2) in enumerate((("Whiteboard", "Sketch ideas"),
                                       ("Weather", "Local forecast"),
                                       ("API keys", "Connect a model"))):
        yy = tools_t + 52 * S + i * 36 * S
        d.text((48 * S, yy), title, font=font(13, True), fill=palette["text"])
        d.text((205 * S, yy + 1 * S), sub2, font=font(10), fill=palette["dim"])
        d.text((W - 62 * S, yy - 1 * S), "\u203a", font=font(16, True), fill=palette["dim"])

    # navigation
    d.rectangle((0, nav_top, W, H), fill=palette["surface"])
    d.line((0, nav_top, W, nav_top), fill=palette["nav_line"], width=2)
    for i, (label, kind) in enumerate((("Home", "home"), ("Chat", "chat"), ("Voice", "mic"),
                                       ("Triggers", "bolt"), ("Settings", "gear"))):
        x0 = W / 10 * (2 * i + 1)
        col = palette["accent"] if i == 0 else palette["dim"]
        glyph(d, kind, x0, H - 46 * S, 19 * S, col, width=int(2 * S))
        d.text((x0 - d.textlength(label, font=font(9, True)) / 2, H - 27 * S), label,
               font=font(9, True), fill=col)
        if i == 0:
            rrect((x0 - 10 * S, nav_top + 3 * S, x0 + 10 * S, nav_top + 7 * S), 2, palette["accent"])

    os.makedirs(OUT, exist_ok=True)
    img.save(path)
    print("wrote", os.path.relpath(path, ROOT), img.size, "dark" if dark else "light")


if __name__ == "__main__":
    render(LIGHT, os.path.join(OUT, "home_preview.png"), False)
    render(DARK, os.path.join(OUT, "home_preview_dark.png"), True)
