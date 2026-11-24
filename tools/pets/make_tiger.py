"""Recolours the CC0 Cat-4 sprites (grey back, pinkish-beige chest and legs) into a golden tiger cub.

Golden-yellow coat with black stripes, white chest, legs and muzzle. The sprite's darker grey streaks become
black patches and extra vertical stripes are drawn on the coat; they are placed relative to the head (found
from the eyes, or the closed-eye line) so they move with the body. The ears are drawn in the chest colours,
so they are recoloured per frame: rim like the head; inside pale pink from the front, mostly coat-coloured
from the side (a large pink patch there looked like a horn).
Usage: python tools/pets/make_tiger.py <directory with the original Cat-4-*.png>
The files are rewritten in place; run it on the original files only.
"""
import glob, os, sys
from PIL import Image

FRAME = 50
GREY, STREAK = (108, 108, 108), (92, 91, 91)
EYE, NOSE = (50, 138, 149), (47, 46, 46)
COAT, BLACK = (232, 176, 64), (40, 36, 36)
LIGHT = {
    (172, 132, 117): (240, 240, 238), (199, 167, 155): (252, 252, 250), (145, 110, 97): (200, 200, 198),
    (182, 140, 125): (246, 246, 244), (217, 187, 176): (255, 255, 255), (211, 178, 165): (250, 250, 248),
    (119, 99, 96): (190, 190, 188), (164, 123, 112): (236, 236, 234),
    EYE: (196, 196, 70), NOSE: (16, 14, 14),
}
EAR_RIM = {(199, 167, 155), (217, 187, 176), (211, 178, 165)}
EAR_INSIDE = {(172, 132, 117), (182, 140, 125), (145, 110, 97)}
INSIDE_PINK, SIDE_INSIDE = (214, 150, 150), (150, 96, 96)


def find_head(px, x0, height):
    def find(colour):
        return [(x, y) for y in range(height) for x in range(x0, x0 + FRAME)
                if px[x, y][3] > 40 and px[x, y][:3] == colour]
    eyes = find(EYE)
    anchors = eyes or find(NOSE)
    if not anchors:
        return None
    return sum(x for x, _ in anchors) // len(anchors), min(y for _, y in anchors), len(eyes) < 2


def recolour_frame(px, x0, height, new):
    head = find_head(px, x0, height)
    head_box = set()
    if head:
        ax, ay, side = head
        head_box = {(x, y) for y in range(max(0, ay - 7), min(height, ay + 4))
                    for x in range(max(x0, ax - 5), min(x0 + FRAME, ax + 5))}
        inside = []
        for y in range(max(0, ay - 6), ay):
            for x in range(max(x0, ax - 6), min(x0 + FRAME, ax + 6)):
                if px[x, y][3] <= 40:
                    continue
                c = px[x, y][:3]
                if c in EAR_RIM:
                    new[(x, y)] = COAT
                elif c in EAR_INSIDE:
                    inside.append((x, y))
        if side and inside:
            lowest = max(y for _, y in inside)
            for x, y in inside:
                new[(x, y)] = SIDE_INSIDE if y == lowest else COAT
        else:
            for xy in inside:
                new[xy] = INSIDE_PINK
    for y in range(height):
        for x in range(x0, x0 + FRAME):
            if (x, y) in new or px[x, y][3] <= 40:
                continue
            c = px[x, y][:3]
            if c in (GREY, STREAK) and (x, y) in head_box:
                new[(x, y)] = COAT
            elif c == GREY:
                striped = head and abs(x - head[0]) > 3 and (abs(x - head[0]) + y // 4) % 3 == 0
                new[(x, y)] = BLACK if striped else COAT
            elif c == STREAK:
                new[(x, y)] = BLACK
            elif c in LIGHT:
                new[(x, y)] = LIGHT[c]


for path in sorted(glob.glob(os.path.join(sys.argv[1], 'Cat-4-*.png'))):
    image = Image.open(path).convert('RGBA')
    px = image.load()
    width, height = image.size
    new = {}
    for x0 in range(0, width, FRAME):
        recolour_frame(px, x0, height, new)
    for (x, y), colour in new.items():
        px[x, y] = colour + (px[x, y][3],)
    image.save(path)
