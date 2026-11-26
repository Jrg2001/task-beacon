"""Builds the Samoyed sprites from the CC0 Akita sprites (both spitz breeds: erect ears, tail over the back).

Per frame: the coat becomes warm white; the tail is redrawn as a large curl over the back (lower and
shorter when the dog sits or lies, judged per frame from how high the tail root is); cheek fur and a small bib round the face; and the ears, which would
vanish on a white head, grow a pixel at the tip and get a pale pink inside. Everything is placed relative
to the eye (or the nose when the eyes are closed) and the frame's bounds.
Usage: python tools/pets/make_samoyed.py <Akita pack directory> <output directory>
Output files are named Samoyed-*.png.
"""
import glob, os, sys
from PIL import Image, ImageDraw

FRAME = 100
DARK = (24, 15, 6)                       # eyes and nose
AKITA = [(206, 146, 84), (152, 104, 56), (230, 221, 195), (176, 168, 142)]   # coat, shade, cream, cream shade
WHITE = [(246, 242, 232), (212, 202, 186), (255, 252, 244), (222, 214, 200)]
COAT, SHADE, LIGHT, CREAM = WHITE
EAR_INNER = (212, 170, 160)
# The tail curls over the back only when its root is at least this high above the ground (standing);
# sitting, scratching and lying frames (2-7 px) get a short low tail instead.
CURL_MIN_HEIGHT = 9


def opaque(px, x, y):
    return 0 <= x < FRAME and 0 <= y < FRAME and px[x, y][3] > 40


def eye_position(px):
    dark = sorted((x, y) for y in range(FRAME) for x in range(FRAME) if opaque(px, x, y) and px[x, y][:3] == DARK)
    if not dark:
        return None
    if len(dark) == 1:                   # eyes closed: only the nose is left, three pixels in front of the eye
        return dark[0][0] - 3, dark[0][1]
    return dark[0]


def ear_pixels(px, ex, ey):
    paint, tops = {}, {}
    for x in range(ex - 3, ex + 3):
        column = [y for y in range(max(0, ey - 6), ey - 1) if opaque(px, x, y)]
        if column:
            tops[x] = min(column)
    for x, top in tops.items():
        left, right = tops.get(x - 1, 99), tops.get(x + 1, 99)
        if top < left and top <= right or top <= left and top < right:
            paint[(x, top - 1)] = COAT
        for y in range(top, ey - 1):
            if opaque(px, x, y) and px[x, y][:3] == AKITA[1]:
                paint[(x, y)] = EAR_INNER
    return paint


def samoyed_frame(src, animation):
    px = src.load()
    eye = eye_position(px)
    out = src.copy()
    opx = out.load()
    mapping = dict(zip(AKITA, WHITE))
    for y in range(FRAME):
        for x in range(FRAME):
            r, g, b, a = opx[x, y]
            if a and (r, g, b) in mapping:
                opx[x, y] = mapping[(r, g, b)] + (a,)
    box = src.getbbox()
    if box is None:
        return out
    left, top, right, bottom = box
    draw = ImageDraw.Draw(out)
    if eye:
        ex, ey = eye
        # cheek fur behind the eye and a small bib below the muzzle
        draw.polygon([(ex - 3, ey - 2), (ex - 5, ey + 1), (ex - 6, ey + 2),
                      (ex - 4, ey + 2), (ex - 5, ey + 3), (ex - 2, ey + 3)], fill=LIGHT + (255,))
        draw.line([(ex - 4, ey + 3), (ex - 2, ey + 4), (ex, ey + 4)], fill=CREAM + (255,))
        if animation not in ('lying-down', 'sleeping'):
            draw.polygon([(ex, ey + 2), (ex + 2, ey + 2), (ex + 2, ey + 4),
                          (ex + 1, ey + 4), (ex + 1, ey + 5), (ex - 1, ey + 6)], fill=LIGHT + (255,))
        for (x, y), colour in ear_pixels(px, ex, ey).items():
            opx[x, y] = colour + (255,)
        opx[ex - 2, ey - 2] = EAR_INNER + (255,)
    # tail: the Akita's starts at the frame's left edge
    tail_y = min(y for y in range(top, bottom) if any(opaque(px, x, y) for x in range(left, left + 3)))
    if bottom - tail_y < CURL_MIN_HEIGHT:
        draw.polygon([(left, tail_y + 1), (left + 1, tail_y - 1), (left + 3, tail_y),
                      (left + 4, tail_y + 2), (left + 1, tail_y + 2)], fill=COAT + (255,))
    else:
        # a curl over the back, with a notch below the hanging tip
        draw.polygon([(left, tail_y + 2), (left, tail_y), (left + 2, tail_y - 2), (left + 4, tail_y - 2),
                      (left + 6, tail_y), (left + 6, tail_y + 1), (left + 4, tail_y), (left + 2, tail_y),
                      (left + 1, tail_y + 3)], fill=LIGHT + (255,))
        draw.line([(left + 2, tail_y + 1), (left + 3, tail_y + 1)], fill=CREAM + (255,))
    return out


def animation_of(path):
    return os.path.basename(path)[len('Akita-'):-len('.png')].lower()


src_dir, dst_dir = sys.argv[1], sys.argv[2]
os.makedirs(dst_dir, exist_ok=True)
for path in sorted(glob.glob(os.path.join(src_dir, 'Akita-*.png'))):
    sheet = Image.open(path).convert('RGBA')
    animation = animation_of(path)
    out = Image.new('RGBA', sheet.size, (0, 0, 0, 0))
    for i in range(sheet.size[0] // FRAME):
        frame = sheet.crop((i * FRAME, 0, (i + 1) * FRAME, FRAME))
        out.paste(samoyed_frame(frame, animation), (i * FRAME, 0))
    name = 'Samoyed-' + os.path.basename(path)[len('Akita-'):]
    out.save(os.path.join(dst_dir, name))
