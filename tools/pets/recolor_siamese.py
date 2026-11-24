"""Recolours the CC0 Cat-3 sprites into a dark seal-point Siamese.

The original has a cool light-grey body with mid-grey points and light ears, which reads more like a sheepskin
than a Siamese. The body becomes light brown, the points (face, legs, tail) near black, and the ears and top of
the head - drawn in the body colour - are found per frame and given the point colour too.
Usage: python tools/pets/recolor_siamese.py <directory with the original Cat-3-*.png>
The files are rewritten in place; running it on already recoloured files changes nothing.
"""
import glob, os, sys
from PIL import Image

FRAME = 50
BODY = [(194, 194, 194), (213, 213, 213)]
POINTS = [(98, 89, 86), (85, 76, 72), (93, 91, 91), (102, 96, 93), (113, 111, 111)]
PADS = (131, 109, 103)
EYE, NOSE = (38, 119, 146), (23, 25, 25)
NEW = {
    (194, 194, 194): (178, 152, 120), (213, 213, 213): (200, 178, 146),           # body
    (98, 89, 86): (40, 30, 26), (85, 76, 72): (30, 22, 20), (93, 91, 91): (38, 28, 24),
    (102, 96, 93): (46, 34, 28), (113, 111, 111): (56, 42, 34),                   # points
    PADS: (80, 58, 50),
}
EAR = NEW[(98, 89, 86)]
FACE = set(POINTS) | {EYE, NOSE}


def ear_pixels(px, x0, w, h):
    """Body-coloured pixels above the face: found from the eyes (or nose when they are closed), then grown
    upwards from the face so ear tips above a light forehead are reached too."""
    def find(colour):
        return [(x, y) for y in range(h) for x in range(x0, x0 + w) if px[x, y][3] and px[x, y][:3] == colour]
    anchors = find(EYE) or find(NOSE)
    if not anchors:
        return set()
    ax = int(sum(x for x, _ in anchors) / len(anchors))
    ay = min(y for _, y in anchors)
    converted, changed = set(), True
    while changed:
        changed = False
        for y in range(max(0, ay - 8), ay):
            for x in range(max(x0, ax - 6), min(x0 + w, ax + 5)):
                if (x, y) in converted or not px[x, y][3] or px[x, y][:3] not in BODY:
                    continue
                if any((x + dx, y + 1) in converted
                       or px[x + dx, y + 1][3] and px[x + dx, y + 1][:3] in FACE
                       for dx in (-1, 0, 1) if x0 <= x + dx < x0 + w):
                    converted.add((x, y))
                    changed = True
    return converted


for path in sorted(glob.glob(os.path.join(sys.argv[1], 'Cat-3-*.png'))):
    image = Image.open(path).convert('RGBA')
    px = image.load()
    width, height = image.size
    ears = set()
    for x0 in range(0, width, FRAME):
        ears |= ear_pixels(px, x0, FRAME, height)
    for y in range(height):
        for x in range(width):
            r, g, b, a = px[x, y]
            if a and (x, y) in ears:
                px[x, y] = EAR + (a,)
            elif a and (r, g, b) in NEW:
                px[x, y] = NEW[(r, g, b)] + (a,)
    image.save(path)
