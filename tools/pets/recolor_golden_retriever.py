"""Recolours the CC0 Golden Retriever sprites from the original reddish brown to a light show gold.

Only the five coat shades are replaced (dark -> light), so shading, nose, eyes and tongue are unchanged.
Usage: python tools/pets/recolor_golden_retriever.py <directory with the original Golden-Retriever-*.png>
The files are rewritten in place; running it on already recoloured files changes nothing.
"""
import glob, os, sys
from PIL import Image

ORIGINAL = [(112, 69, 24), (132, 83, 33), (148, 93, 37), (164, 102, 39), (178, 114, 49)]
SHOW_GOLD = [(186, 136, 62), (208, 160, 84), (226, 182, 106), (236, 198, 126), (244, 214, 150)]
MAPPING = dict(zip(ORIGINAL, SHOW_GOLD))

for path in sorted(glob.glob(os.path.join(sys.argv[1], 'Golden-Retriever-*.png'))):
    image = Image.open(path).convert('RGBA')
    pixels = image.load()
    for y in range(image.size[1]):
        for x in range(image.size[0]):
            r, g, b, a = pixels[x, y]
            if a and (r, g, b) in MAPPING:
                pixels[x, y] = MAPPING[(r, g, b)] + (a,)
    image.save(path)
