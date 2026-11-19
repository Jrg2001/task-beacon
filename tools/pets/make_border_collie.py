"""Builds the Border Collie sprites from the CC0 Siberian Husky sprites (recolour + small pixel edits)."""
import glob, os, sys
from PIL import Image

SRC, DST, PREVIEW = sys.argv[1], sys.argv[2], sys.argv[3]
# Husky palette -> collie palette
BLACK, BLACK_DEEP, BLACK_SHADE = (28, 28, 32), (14, 14, 16), (48, 48, 54)
WHITE, WHITE_SHADE, WHITE_BRIGHT = (226, 224, 218), (176, 174, 168), (246, 245, 240)
CMAP = {(42, 43, 43): BLACK, (25, 26, 26): BLACK_DEEP, (74, 75, 75): BLACK_SHADE,
        (74, 84, 84): WHITE_SHADE,          # far-side legs: shaded white, not black
        (109, 116, 116): WHITE, (185, 197, 197): WHITE_BRIGHT, (6, 157, 157): (96, 62, 34)}
DARK = {BLACK, BLACK_DEEP, BLACK_SHADE}
LIGHT = {WHITE, WHITE_SHADE, WHITE_BRIGHT}

def opaque(px, x, y, w, h):
    return 0 <= x < w and 0 <= y < h and px[x, y][3] > 0

def edit_frame(frame):
    w, h = frame.size
    px = frame.load()
    pts = [(x, y) for y in range(h) for x in range(w) if px[x, y][3]]
    if not pts: return frame
    minx = min(p[0] for p in pts); maxx = max(p[0] for p in pts)
    miny = min(p[1] for p in pts); maxy = max(p[1] for p in pts)
    # Head faces right: the ear tip is the topmost pixel in the rightmost columns.
    head = [p for p in pts if p[0] >= maxx - 9]
    ey = min(p[1] for p in head); ex = max(p[0] for p in head if p[1] == ey)
    # 1) Tipped ear: drop the pointed tip, fold it one pixel forward and down.
    if opaque(px, ex, ey + 1, w, h) and not opaque(px, ex + 1, ey + 1, w, h):
        tip = px[ex, ey]
        px[ex, ey] = (0, 0, 0, 0)
        px[ex + 1, ey + 1] = tip[:3] + (255,)
    # 2) White collar right behind the jaw, joining the white chest so it reads as a ring rather than
    #    a patch on the shoulder (only paints over dark fur, so the outline is unchanged).
    #    Upright (sitting) poses already show a white chest, and the neck offset differs there.
    upright = (maxy - miny) >= (maxx - minx)
    for x in range(ex - 4, ex - 1) if not upright else ():
        for y in range(ey + 6, ey + 11):
            if opaque(px, x, y, w, h) and px[x, y][:3] in DARK:
                px[x, y] = WHITE + (255,)
    # 3) Tail: mostly black with a white tip. Tail = light pixels in the leftmost columns,
    #    above the legs (upper ~65% of the body height).
    tail_limit_y = miny + int((maxy - miny) * 0.65)
    for x in range(minx + 2, minx + 7):
        for y in range(miny, tail_limit_y + 1):
            if opaque(px, x, y, w, h) and px[x, y][:3] in LIGHT:
                px[x, y] = BLACK + (255,)
    return frame

os.makedirs(DST, exist_ok=True)
rows = []
for src in sorted(glob.glob(os.path.join(SRC, 'Siberian-Husky-*.png'))):
    im = Image.open(src).convert('RGBA')
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a and (r, g, b) in CMAP: px[x, y] = CMAP[(r, g, b)] + (a,)
    n = im.size[0] // 100
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    frames = []
    for i in range(n):
        f = edit_frame(im.crop((i * 100, 0, (i + 1) * 100, 100)))
        out.alpha_composite(f, (i * 100, 0))
        frames.append(f.crop((25, 25, 75, 70)))
    out.save(os.path.join(DST, os.path.basename(src).replace('Siberian-Husky', 'Border-Collie')), optimize=True)
    rows.append(frames)
W = max(len(r) for r in rows) * 50; H = len(rows) * 45
sheet = Image.new('RGBA', (W, H), (70, 100, 130, 255))
for y, r in enumerate(rows):
    for x, f in enumerate(r): sheet.alpha_composite(f, (x * 50, y * 45))
sheet.resize((W * 3, H * 3), Image.NEAREST).save(PREVIEW)
