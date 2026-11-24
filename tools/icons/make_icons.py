"""Builds TaskBeacon's icons from the bundled Akita sprite (the default pet).

Large sizes show the whole dog, small ones (tray, 16/24/32) a close-up of the head so it stays readable.
Pixels are only ever scaled by whole numbers, on a rounded square in the README showcase blue.
Outputs: app/src/main/packaging/TaskBeacon.ico (exe, installer, shortcuts) and the tray PNGs in ui-desktop.
Usage: python tools/icons/make_icons.py   (run from the repository root)
"""
import os
from PIL import Image, ImageDraw

SPRITE = 'ui-desktop/src/main/resources/pets/dogs/Dog-2-Akita/Akita-Idle.png'
ICO = 'app/src/main/packaging/TaskBeacon.ico'
TRAY_DIR = 'ui-desktop/src/main/resources/icons'
BG = (63, 90, 122, 255)
PREVIEW = 'build/icons-preview.png'

frame = Image.open(SPRITE).convert('RGBA').crop((0, 0, 100, 100))
body = frame.crop(frame.getchannel('A').getbbox())          # 19 x 15
head = body.crop((10, 0, 19, 9))                            # 9 x 9 close-up


def tile(size, art, radius_ratio=0.22):
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(canvas).rounded_rectangle((0, 0, size - 1, size - 1), round(size * radius_ratio), fill=BG)
    scale = max(1, min((size * 0.84) // art.size[0], (size * 0.84) // art.size[1]))
    big = art.resize((int(art.size[0] * scale), int(art.size[1] * scale)), Image.NEAREST)
    canvas.alpha_composite(big, ((size - big.size[0]) // 2, (size - big.size[1]) // 2 + (size // 32)))
    return canvas


sizes = [256, 128, 64, 48, 32, 24, 16]
images = {s: tile(s, body if s >= 48 else head) for s in sizes}

os.makedirs(os.path.dirname(ICO), exist_ok=True)
images[256].save(ICO, format='ICO', sizes=[(s, s) for s in sizes],
                 append_images=[images[s] for s in sizes[1:]])
os.makedirs(TRAY_DIR, exist_ok=True)
for s in (16, 32):
    images[s].save(os.path.join(TRAY_DIR, f'tray-{s}.png'))

# side-by-side preview at true size and enlarged
preview = Image.new('RGBA', (sum(sizes) + 20 * len(sizes) + 300, 300), (236, 238, 242, 255))
x = 10
for s in sizes:
    preview.alpha_composite(images[s], (x, 10))
    x += s + 20
preview.alpha_composite(images[16].resize((128, 128), Image.NEAREST), (x, 10))
os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
preview.save(PREVIEW)
print('ok', ICO)
