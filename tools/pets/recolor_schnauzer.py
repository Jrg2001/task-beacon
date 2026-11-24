"""Recolours the CC0 Schnauzer sprites from the original blue-grey to ivory white.

Body shades, furnishings (beard, brows, legs) and one warm grey detail are replaced one to one, so the
drawing is unchanged. The far-side legs and shaded body stay fairly dark so the outline still reads on
light wallpapers.
Usage: python tools/pets/recolor_schnauzer.py <directory with the original Schnauzer-*.png>
The files are rewritten in place; running it on already recoloured files changes nothing.
"""
import glob, os, sys
from PIL import Image

MAPPING = {
    # body, dark -> light
    (81, 83, 98): (122, 112, 100),
    (83, 87, 106): (150, 140, 126),
    (103, 106, 124): (200, 192, 178),
    (124, 128, 148): (236, 230, 218),
    # furnishings
    (135, 140, 140): (220, 213, 200),
    (160, 162, 162): (250, 247, 240),
    # warm grey detail
    (133, 129, 121): (176, 168, 156),
}

for path in sorted(glob.glob(os.path.join(sys.argv[1], 'Schnauzer-*.png'))):
    image = Image.open(path).convert('RGBA')
    pixels = image.load()
    for y in range(image.size[1]):
        for x in range(image.size[0]):
            r, g, b, a = pixels[x, y]
            if a and (r, g, b) in MAPPING:
                pixels[x, y] = MAPPING[(r, g, b)] + (a,)
    image.save(path)
