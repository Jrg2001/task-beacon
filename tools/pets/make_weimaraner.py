"""Builds the Weimaraner sprites from the CC0 Great Dane sprites (recolour only).

The Great Dane's tall, lean, short-coated and floppy-eared drawing suits a Weimaraner; its grey coat and
brown patches both become the breed's solid silver-taupe, and the black nose becomes taupe grey.
Usage: python tools/pets/make_weimaraner.py <directory with the original Great-Dane-*.png> <output directory>
Output files are named Weimaraner-*.png.
"""
import glob, os, sys
from PIL import Image

MAPPING = {
    # coat: highlight, main, mid, shade, far legs
    (183, 189, 192): (190, 184, 172),
    (169, 173, 174): (168, 160, 148),
    (98, 107, 107): (130, 122, 112),
    (107, 116, 118): (136, 128, 118),
    (73, 79, 79): (96, 90, 82),
    # former brown patches blend into the coat
    (112, 80, 9): (160, 152, 140),
    (85, 61, 8): (140, 132, 122),
    # nose
    (29, 35, 41): (84, 74, 70),
}

src, dst = sys.argv[1], sys.argv[2]
os.makedirs(dst, exist_ok=True)
for path in sorted(glob.glob(os.path.join(src, 'Great-Dane-*.png'))):
    image = Image.open(path).convert('RGBA')
    pixels = image.load()
    for y in range(image.size[1]):
        for x in range(image.size[0]):
            r, g, b, a = pixels[x, y]
            if a and (r, g, b) in MAPPING:
                pixels[x, y] = MAPPING[(r, g, b)] + (a,)
    image.save(os.path.join(dst, os.path.basename(path).replace('Great-Dane-', 'Weimaraner-')))
