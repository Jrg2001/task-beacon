"""Recolours the CC0 Cat-1 sprites from ochre to the bundled Akita's orange-tan.

The main coat, the darker stripe shade and the cream chest take the Akita's colours directly; the other
coat shades keep their relative brightness at the Akita's hue. Eyes and tongue are unchanged.
Usage: python tools/pets/recolor_orange_cat.py <directory with the original Cat-1-*.png>
The files are rewritten in place; run it on the original files only.
"""
import colorsys, glob, os, sys
from PIL import Image

EXACT = {
    (155, 117, 33): (206, 146, 84),     # main coat -> Akita main
    (136, 103, 28): (186, 128, 70),     # stripes, lighter than the Akita shade so the coat stays bright
    (136, 103, 29): (186, 128, 70),
    (214, 194, 149): (230, 221, 195),   # chest / paws -> Akita cream
    (181, 152, 88): (218, 184, 140),    # chest edge
}
KEEP = {(80, 81, 33), (168, 62, 62)}    # eyes, tongue


def akita_tone(rgb):
    if rgb in EXACT:
        return EXACT[rgb]
    _, _, v = colorsys.rgb_to_hsv(*(c / 255 for c in rgb))
    v = min(0.92, 0.81 * v / 0.61)      # the main coat's brightness maps to the Akita's
    return tuple(round(c * 255) for c in colorsys.hsv_to_rgb(30 / 360, 0.6, v))


for path in sorted(glob.glob(os.path.join(sys.argv[1], 'Cat-1-*.png'))):
    image = Image.open(path).convert('RGBA')
    px = image.load()
    for y in range(image.size[1]):
        for x in range(image.size[0]):
            r, g, b, a = px[x, y]
            if a > 40 and (r, g, b) not in KEEP:
                px[x, y] = akita_tone((r, g, b)) + (a,)
    image.save(path)
