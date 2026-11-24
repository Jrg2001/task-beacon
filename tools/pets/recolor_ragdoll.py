"""Recolours the CC0 Cat-5 (silver longhair) sprites into a seal bicolor Ragdoll.

Cream body with a light fawn back, seal-brown ears, eye mask and tail, white face (inverted V), chest and
legs, and two blue eyes (the original is odd-eyed). The tail tip is drawn in the same light shade as the face,
and the body, so in every frame small light patches that touch only the tail (and transparency) are given
the tail colour.
Usage: python tools/pets/recolor_ragdoll.py <directory with the original Cat-5-*.png>
The files are rewritten in place; run it on the original files only.
"""
import glob, os, sys
from PIL import Image

FRAME = 50
HEAD = (230, 230, 230)
TAIL = (125, 116, 108)
MAPPING = {
    HEAD: (250, 248, 244),               # face, chest -> white
    (209, 209, 209): (240, 232, 218),    # body -> cream
    (195, 186, 178): (206, 186, 160),    # back -> light fawn
    (173, 173, 173): (236, 232, 226),    # near legs -> white
    (180, 180, 180): (228, 222, 212),
    (126, 126, 126): (192, 184, 172),    # far legs
    (141, 141, 141): (92, 66, 52),       # ears, eye mask -> seal
    TAIL: (84, 60, 48),                  # tail -> seal
    (89, 89, 89): (170, 112, 108),       # nose
    (226, 214, 86): (19, 133, 192),      # yellow eye -> blue
    (75, 75, 75): (60, 44, 36),
}
TAIL_TIP_MAX = 16
LIGHT = {HEAD, (209, 209, 209)}


def tail_tip_pixels(px, x0, height):
    """Small light patches whose only neighbours are tail pixels and transparency."""
    def colour(x, y):
        p = px[x, y]
        return p[:3] if p[3] > 40 else None

    seen, result = set(), set()
    for y in range(height):
        for x in range(x0, x0 + FRAME):
            own = colour(x, y)
            if (x, y) in seen or own not in LIGHT:
                continue
            patch, stack, touches_tail, touches_other = [], [(x, y)], False, False
            seen.add((x, y))
            while stack:
                cx, cy = stack.pop()
                patch.append((cx, cy))
                for nx in (cx - 1, cx, cx + 1):
                    for ny in (cy - 1, cy, cy + 1):
                        if not (x0 <= nx < x0 + FRAME and 0 <= ny < height) or (nx, ny) in seen:
                            continue
                        c = colour(nx, ny)
                        if c is None:
                            continue
                        if c == TAIL:
                            touches_tail = True
                        elif c == own and abs(nx - cx) + abs(ny - cy) == 1:
                            seen.add((nx, ny))
                            stack.append((nx, ny))
                        elif c != own:
                            touches_other = True
            if touches_tail and not touches_other and len(patch) <= TAIL_TIP_MAX:
                result.update(patch)
    return result


for path in sorted(glob.glob(os.path.join(sys.argv[1], 'Cat-5-*.png'))):
    image = Image.open(path).convert('RGBA')
    px = image.load()
    width, height = image.size
    tips = set()
    for x0 in range(0, width, FRAME):
        tips |= tail_tip_pixels(px, x0, height)
    for y in range(height):
        for x in range(width):
            r, g, b, a = px[x, y]
            if not a:
                continue
            if (x, y) in tips:
                px[x, y] = MAPPING[TAIL] + (a,)
            elif (r, g, b) in MAPPING:
                px[x, y] = MAPPING[(r, g, b)] + (a,)
    image.save(path)
