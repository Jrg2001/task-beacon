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

# Mirrors BundledPetPacks.Pack#sizeFactor.
PACK_SIZE_FACTORS = {'Dog-4-Schnauzer': 0.75, 'Dog-8-Samoyed': 1.2}

def app_size_factor(pack_dir):
    """Mirrors PetAnimationController.packPixelScale: sprites taller than the Akita's crop are shown smaller."""
    return _crop_factor(pack_dir) * PACK_SIZE_FACTORS.get(os.path.basename(pack_dir), 1.0)

def _crop_factor(pack_dir):
    boxes = []
    for f in os.listdir(pack_dir):
        if f.endswith('.png'):
            sheet = Image.open(os.path.join(pack_dir, f))
            if sheet.size[1] == 100 and sheet.size[0] % 100 == 0:
                boxes.append(union_bbox(frames(os.path.join(pack_dir, f), 100)))
    w = max(b[2] for b in boxes) - min(b[0] for b in boxes)
    h = max(b[3] for b in boxes) - min(b[1] for b in boxes)
    h = max(h, max(w, h * 4 / 3) * 3 / 4)
    h = -(-h // 2) * 2
    return 1.0 if h <= 22 else (22 / h) ** 0.5

def lineup():
    """Idle animations of every pet, dogs at their relative in-app size (see app_size_factor)."""
    dogs = [('Dog-1-Golden-Retriever', 'Golden-Retriever-idle'), ('Dog-2-Akita', 'Akita-Idle'),
            ('Dog-3-Great-Dane', 'Weimaraner-idle'), ('Dog-4-Schnauzer', 'Schnauzer-Idle'),
            ('Dog-5-Saint-Bernard', 'Saint-Bernard-Idle'), ('Dog-6-Siberian-Husky', 'Siberian-Husky-Idle'),
            ('Dog-7-Sprite', 'Border-Collie-Idle'), ('Dog-8-Samoyed', 'Samoyed-Idle')]
    cats = [(f'Cat-{i}', f'Cat-{i}-Idle') for i in range(1, 7)]

    def scaled(fs, factor):
        """Crops every frame to the animation's bounds and enlarges it with crisp pixels."""
        box = union_bbox(fs)
        w, h = box[2] - box[0], box[3] - box[1]
        size = (round(w * SCALE * factor), round(h * SCALE * factor))
        return [im.crop(box).resize(size, Image.NEAREST) for im in fs]

    rows = [[scaled(frames(f'{PETS}/dogs/{d}/{f}.png', 100), app_size_factor(f'{PETS}/dogs/{d}'))
             for d, f in dogs],
            [scaled(frames(f'{PETS}/cats/{c}/{f}.png', 50), 1.0) for c, f in cats]]
    count = max(len(fs) for row in rows for fs in row)
    cell_w = max(fs[0].size[0] for row in rows for fs in row) + 6 * SCALE
    row_h = [max(fs[0].size[1] for fs in row) + 7 * SCALE for row in rows]
    cols = max(len(row) for row in rows)
    tiles = []
    for i in range(count):
        sheet = Image.new('RGBA', (cols * cell_w, sum(row_h)), BG)
        y = 0
        for row, h in zip(rows, row_h):
            offset = (cols - len(row)) * cell_w // 2
            for c, fs in enumerate(row):
                im = fs[i % len(fs)]
                sheet.alpha_composite(im, (offset + c * cell_w + (cell_w - im.size[0]) // 2,
                                           y + h - im.size[1] - 2 * SCALE))
            y += h
        tiles.append(sheet)
    save_gif(tiles, [200] * len(tiles), 'pets-lineup.gif')

os.makedirs(OUT, exist_ok=True)
akita_states()
lineup()
