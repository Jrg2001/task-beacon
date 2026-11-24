"""Adds a tail to the bundled cats' lying-down poses.

In the CC0 cat packs the last two frames of Laying and the Sleeping1 frame have no tail, so a lying cat reads
as a loaf. This draws one in each cat's own tail colours (sampled from the standing first frame of Laying):
out of the rump along the ground, the tip curling up.
Usage: python tools/pets/add_lying_tails.py <pets/cats directory>
Run it once, on sprites without the tail (it would extend an existing one).
"""
import collections, os, sys
from PIL import Image

FRAME = 50
CATS = ['Cat-1', 'Cat-2', 'Cat-3', 'Cat-4', 'Cat-5', 'Cat-6']
TARGETS = [('Laying', 6), ('Laying', 7), ('Sleeping1', 0)]


def opaque(px, size, x, y):
    return 0 <= x < size[0] and 0 <= y < size[1] and px[x, y][3] > 40


def tail_colours(laying):
    frame = laying.crop((0, 0, FRAME, FRAME))
    px = frame.load()
    xs = [x for x in range(FRAME) for y in range(FRAME) if opaque(px, frame.size, x, y)]
    left = min(xs)
    count = collections.Counter(px[x, y][:3] for x in range(left, left + 3) for y in range(FRAME)
                                if opaque(px, frame.size, x, y))
    ranked = [c for c, _ in count.most_common()]
    return ranked[0], (ranked[1] if len(ranked) > 1 else ranked[0])


def add_tail(image, x0, main, tip):
    px = image.load()
    cells = [(x, y) for y in range(FRAME) for x in range(x0, x0 + FRAME) if opaque(px, image.size, x, y)]
    bottom = max(y for _, y in cells)
    rear = min(x for x, y in cells if y == bottom)
    path = [(rear - d, bottom) for d in range(1, 6)] + [(rear - 6, bottom - 1), (rear - 6, bottom - 2)]
    for i, (x, y) in enumerate(path):
        px[x, y] = (tip if i >= len(path) - 2 else main) + (255,)


root = sys.argv[1]
for cat in CATS:
    folder = os.path.join(root, cat)
    main, tip = tail_colours(Image.open(os.path.join(folder, f'{cat}-Laying.png')).convert('RGBA'))
    for name, index in TARGETS:
        path = os.path.join(folder, f'{cat}-{name}.png')
        image = Image.open(path).convert('RGBA')
        add_tail(image, index * FRAME, main, tip)
        image.save(path)
