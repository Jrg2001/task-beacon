"""Generates the README pet showcase (docs/images/*.gif) from the bundled sprite packs.

Usage: python tools/readme/make_readme_media.py   (run from the repository root)
"""
import os
from PIL import Image

PETS = 'ui-desktop/src/main/resources/pets'
OUT = 'docs/images'
SCALE = 4        # pet lineup
STATE_SCALE = 4  # per-state Akita animations; 4 columns must fit GitHub's ~880px README width
BG = (63, 90, 122, 255)          # readable on both light and dark GitHub themes

def frames(path, size):
    sheet = Image.open(path).convert('RGBA')
    return [sheet.crop((i * size, 0, (i + 1) * size, size)) for i in range(sheet.size[0] // size)]

def union_bbox(images):
    boxes = [im.getchannel('A').getbbox() for im in images]
    boxes = [b for b in boxes if b]
    return (min(b[0] for b in boxes), min(b[1] for b in boxes),
            max(b[2] for b in boxes), max(b[3] for b in boxes))

def render(image, box, canvas, scale=SCALE):
    """Places the box region on a background canvas (bottom-centred), scaled up with crisp pixels."""
    w, h = canvas
    part = image.crop(box)
    tile = Image.new('RGBA', (w, h), BG)
    tile.alpha_composite(part, ((w - part.size[0]) // 2, h - part.size[1] - 2))
    return tile.resize((w * scale, h * scale), Image.NEAREST)

def save_gif(tiles, durations, name):
    tiles = [t.convert('RGB') for t in tiles]
    tiles[0].save(os.path.join(OUT, name), save_all=True, append_images=tiles[1:],
                  duration=durations, loop=0, optimize=True, disposal=1)

def akita_states():
    d = f'{PETS}/dogs/Dog-2-Akita/Akita-'
    seqs = {
        # file: (frame ms, repeats)  -- timings mirror PetAnimationController.playbackFor
        'thinking':  [('stretching', 170, 1), ('Idle', 200, 1)],
        'working':   [('walk', 145, 2)],
        'running':   [('run', 95, 3)],
        'completed': [('licking1', 160, 2), ('licking2', 160, 1)],
        'attention': [('bark', 170, 4)],
        'stalled':   [('itching', 220, 5)],
        'idle':      [('Idle', 200, 1), ('lying-down', 180, 1), ('sleeping', 1200, 1)],
    }
    loaded = {k: [(frames(d + f + '.png', 100), ms, n) for f, ms, n in v] for k, v in seqs.items()}
    box = union_bbox([im for parts in loaded.values() for fs, _, _ in parts for im in fs])
    canvas = (box[2] - box[0] + 6, box[3] - box[1] + 4)
    for state, parts in loaded.items():
        tiles, durations = [], []
        for fs, ms, repeats in parts:
            for _ in range(repeats):
                for im in fs:
                    tiles.append(render(im, box, canvas, STATE_SCALE)); durations.append(ms)
        save_gif(tiles, durations, f'akita-{state}.gif')

def lineup():
    dogs = [('Dog-1-Golden-Retriever', 'Golden-Retriever-idle'), ('Dog-2-Akita', 'Akita-Idle'),
            ('Dog-3-Great-Dane', 'Great-Dane-idle'), ('Dog-4-Schnauzer', 'Schnauzer-Idle'),
            ('Dog-5-Saint-Bernard', 'Saint-Bernard-Idle'), ('Dog-6-Siberian-Husky', 'Siberian-Husky-Idle'),
            ('Dog-7-Sprite', 'Border-Collie-Idle')]
    cats = [(f'Cat-{i}', f'Cat-{i}-Idle') for i in range(1, 7)]
    rows = [[frames(f'{PETS}/dogs/{d}/{f}.png', 100) for d, f in dogs],
            [frames(f'{PETS}/cats/{c}/{f}.png', 50) for c, f in cats]]
    count = max(len(fs) for row in rows for fs in row)
    cells = []
    for row in rows:
        cells.append([(fs, union_bbox(fs)) for fs in row])
    cell_w = max(b[2] - b[0] for row in cells for _, b in row) + 6
    cell_h = max(b[3] - b[1] for row in cells for _, b in row) + 7
    cols = max(len(row) for row in cells)
    tiles = []
    for i in range(count):
        sheet = Image.new('RGBA', (cols * cell_w * SCALE, len(cells) * cell_h * SCALE), BG)
        for r, row in enumerate(cells):
            offset = (cols - len(row)) * cell_w * SCALE // 2
            for c, (fs, box) in enumerate(row):
                sheet.paste(render(fs[i % len(fs)], box, (cell_w, cell_h)),
                            (offset + c * cell_w * SCALE, r * cell_h * SCALE))
        tiles.append(sheet)
    save_gif(tiles, [200] * len(tiles), 'pets-lineup.gif')

os.makedirs(OUT, exist_ok=True)
akita_states()
lineup()
