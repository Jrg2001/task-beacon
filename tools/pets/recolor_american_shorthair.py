"""Recolours the CC0 Cat-6 (silver tabby) sprites into a silver American Shorthair.

Bright silver coat, near-black stripes, white chest, muzzle and paw tips (the sprite already draws those in
their own light shades), pink nose and yellow-green eyes. Colours are replaced one to one.
Usage: python tools/pets/recolor_american_shorthair.py <directory with the original Cat-6-*.png>
The files are rewritten in place; run it on the original files only.
"""
import glob, os, sys
from PIL import Image

MAPPING = {
    (214, 214, 214): (252, 252, 252),   # muzzle, paw tips -> white
    (192, 192, 192): (244, 244, 244),   # chest -> white
    (168, 169, 169): (200, 202, 205),   # coat
    (160, 160, 160): (178, 180, 183),   # far legs, belly
    (132, 132, 132): (138, 140, 144),   # darker coat
    (100, 100, 100): (58, 58, 62),      # far-leg stripes
    (79, 79, 79): (26, 26, 30),         # stripes
    (232, 216, 42): (196, 204, 72),     # eyes
    (101, 57, 27): (222, 146, 148),     # nose
}

for path in sorted(glob.glob(os.path.join(sys.argv[1], 'Cat-6-*.png'))):
    image = Image.open(path).convert('RGBA')
    px = image.load()
    for y in range(image.size[1]):
        for x in range(image.size[0]):
            r, g, b, a = px[x, y]
            if a and (r, g, b) in MAPPING:
                px[x, y] = MAPPING[(r, g, b)] + (a,)
    image.save(path)
