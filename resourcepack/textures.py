# draws every texture in this pack. run from this folder: python textures.py
#  item/fragment_<name>.png  one shard per fragment, each its own shape and colours for its name
#  block/pedestal_*.png      the altar pedestal: plain stone for base and cap, a carved column, a top
import struct
import zlib

def clamp(v):
    return max(0, min(255, int(v)))

def noise(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return (n ^ (n >> 16)) % 17 - 8

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

# ── shards ───────────────────────────────────────────────────

# each fragment is its own shape. render() outlines a mask, lights it from the top left,
# fills the rest with a per-pixel colour, then adds "marks" on the shape and "dots" anywhere
def render(mask, fill, edge, hi=None, marks=None, dots=None):
    def inm(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and mask(x, y)
    def px(x, y):
        if dots and (x, y) in dots:
            return dots[(x, y)] + (255,)
        if not inm(x, y):
            return (0, 0, 0, 0)
        if marks and (x, y) in marks:
            return marks[(x, y)] + (255,)
        if not (inm(x - 1, y) and inm(x + 1, y) and inm(x, y - 1) and inm(x, y + 1)):
            return edge + (255,)
        if hi and not (inm(x - 1, y - 1) and inm(x, y - 2) and inm(x - 2, y)):
            return hi + (255,)
        return fill(x, y) + (255,)
    return px

def flat(c):
    return lambda x, y: c

def near(x, y, x0, y0, x1, y1, w):
    dx, dy = x1 - x0, y1 - y0
    t = max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy)))
    return (x - x0 - t * dx) ** 2 + (y - y0 - t * dy) ** 2 <= w(t) ** 2

# a flame: widest low down, leaning to a point at the top, yellow core in red and orange
FLAME_W = {2: 0, 3: 1, 4: 1, 5: 2, 6: 2, 7: 3, 8: 3, 9: 4, 10: 4, 11: 4, 12: 4, 13: 3, 14: 2}
def flame_cx(y):
    return 8 + (max(0, 6 - y) + 1) // 2
def ember_mask(x, y):
    return y in FLAME_W and abs(x - flame_cx(y)) <= FLAME_W[y]
def ember_fill(x, y):
    if y >= 7 and abs(x - flame_cx(y)) <= FLAME_W[y] - 2:
        return (255, 235, 110)
    return lerp((255, 130, 30), (220, 50, 25), (14 - y) / 12)
EMBER = render(ember_mask, ember_fill, (110, 25, 10),
               dots={(2, 4): (255, 180, 60), (13, 2): (255, 180, 60), (1, 9): (255, 140, 40), (14, 7): (255, 140, 40)})

# an ice crystal: a tall spike with two smaller ones leaning off it
def frost_mask(x, y):
    return (near(x, y, 8, 1, 8, 14, lambda t: 0.5 + 1.8 * (1 - abs(2 * t - 1)))
            or near(x, y, 3, 13, 7, 7, lambda t: 0.4 + 1.0 * (1 - abs(2 * t - 1)))
            or near(x, y, 13, 12, 9, 6, lambda t: 0.4 + 1.0 * (1 - abs(2 * t - 1))))
FROST = render(frost_mask, lambda x, y: lerp((210, 242, 255), (140, 200, 245), y / 15), (70, 130, 190), hi=(255, 255, 255),
               dots={(1, 4): (255, 255, 255), (14, 3): (255, 255, 255), (2, 15): (230, 248, 255)})

# a lightning bolt: down from the top right, a jag to the right, down again
def storm_mask(x, y):
    x0 = 10.5 - y * 0.62
    x1 = 10.2 - (y - 7) * 0.62
    return ((0 <= y <= 8 and abs(x - x0) <= 1.5) or (7 <= y <= 8 and x0 - 1.5 <= x <= x0 + 5)
            or (7 <= y <= 15 and abs(x - x1) <= 1.5))
def storm_fill(x, y):
    core = abs(x - (10.5 - y * 0.62)) <= 0.6 if y <= 7 else abs(x - (10.2 - (y - 7) * 0.62)) <= 0.6
    return (255, 255, 215) if core else (255, 225, 70)
STORM = render(storm_mask, storm_fill, (60, 60, 95))

# a rock with two bumps and a crack
def stone_mask(x, y):
    ex, ey = (x - 8) / 6.2, (y - 9) / 5.2
    return ex * ex + ey * ey <= 1 or (x - 4) ** 2 + (y - 6) ** 2 <= 6 or (x - 12) ** 2 + (y - 7) ** 2 <= 5
def stone_fill(x, y):
    g = 128 + noise(x, y, 3) - (x + y - 17) * 2
    return (clamp(g), clamp(g), clamp(g))
STONE = render(stone_mask, stone_fill, (48, 48, 48), hi=(178, 178, 178),
               marks={p: (55, 55, 55) for p in [(6, 7), (7, 8), (7, 9), (8, 10), (9, 11), (9, 12), (11, 9), (12, 10)]})

# a droplet with wave crests across it
def tide_mask(x, y):
    return (x - 8) ** 2 + (y - 10.5) ** 2 <= 4.6 ** 2 or (2 <= y <= 10 and abs(x - 8) <= (y - 2) * 0.55)
TIDE = render(tide_mask, lambda x, y: lerp((70, 170, 235), (25, 80, 170), y / 15), (15, 50, 110), hi=(200, 240, 255),
              marks={p: (210, 245, 255) for p in [(5, 10), (6, 10), (9, 9), (10, 9), (6, 13), (7, 13), (10, 12)]})

# a rift: a diamond, black in the middle, violet at the rim, stars around it
def void_mask(x, y):
    return abs(x - 8) + abs(y - 8) <= 6.5
VOID = render(void_mask, lambda x, y: lerp((8, 0, 18), (125, 45, 175), (abs(x - 8) + abs(y - 8)) / 6.5), (150, 70, 220), hi=(190, 120, 255),
              dots={(1, 2): (230, 210, 255), (14, 1): (230, 210, 255), (2, 13): (230, 210, 255), (14, 14): (230, 210, 255), (12, 3): (200, 170, 255)})

# a sun coming up over a horizon, rays around it
import math
def dawn_mask(x, y):
    return y <= 12 and (x - 8) ** 2 + (y - 12.5) ** 2 <= 5.5 ** 2
def dawn_fill(x, y):
    return lerp((255, 245, 160), (255, 150, 60), math.hypot(x - 8, y - 12.5) / 5.5)
DAWN_DOTS = {(x, 13): (120, 60, 40) for x in range(1, 15)}
for a in (30, 60, 90, 120, 150):
    for r in (7, 8.5):
        DAWN_DOTS[(round(8 + r * math.cos(math.radians(a))), round(12.5 - r * math.sin(math.radians(a))))] = (255, 215, 80)
DAWN = render(dawn_mask, dawn_fill, (200, 90, 40), hi=(255, 250, 200), dots=DAWN_DOTS)

# a crescent moon, orange along its lit outer rim, stars in the hollow
def dusk_mask(x, y):
    return (x - 7.5) ** 2 + (y - 8) ** 2 <= 6.5 ** 2 and not (x - 10.5) ** 2 + (y - 6.5) ** 2 <= 5.6 ** 2
def dusk_fill(x, y):
    c = lerp((60, 30, 110), (130, 60, 140), y / 15)
    if math.hypot(x - 7.5, y - 8) >= 5 and (x < 8 or y > 9):
        c = lerp(c, (235, 130, 70), 0.6)
    return c
DUSK = render(dusk_mask, dusk_fill, (35, 15, 60), hi=(255, 200, 150),
              dots={(12, 4): (255, 230, 180), (14, 7): (255, 230, 180), (11, 9): (255, 230, 180)})

SHARDS = {"ember": EMBER, "frost": FROST, "storm": STORM, "stone": STONE, "tide": TIDE, "void": VOID, "dawn": DAWN, "dusk": DUSK}

# ── pedestal ─────────────────────────────────────────────────

# grey stone with a bevel: lit top and left, shaded bottom and right
def stone(x, y, seed, base):
    g = base + noise(x, y, seed)
    if y == 0: g += 22
    if y == 15: g -= 26
    if x == 0: g += 10
    if x == 15: g -= 14
    return clamp(g)

def pedestal_stone(x, y):
    g = stone(x, y, 7, 104)
    return (g, g, g + 3, 255)

# a keystone under the cap and two slots for legs, like a carved column
def pedestal_column(x, y):
    g = stone(x, y, 11, 124)
    if 2 <= y <= 5:
        spread = y - 2
        xl, xr = 4 - spread, 11 + spread
        if xl <= x <= xr:
            g = 58 if (y in (2, 5) or x in (xl, xr)) else 140
    if 8 <= y <= 13 and (3 <= x <= 5 or 10 <= x <= 12):
        g = 88 if (x in (4, 11) and 9 <= y <= 12) else 55
    return (g, g, g + 3, 255)

def pedestal_top(x, y):
    g = stone(x, y, 5, 118)
    if 3 <= x <= 12 and 3 <= y <= 12:
        g = g - 22 if (x in (3, 12) or y in (3, 12)) else g + 20
    return (clamp(g), clamp(g), clamp(g + 3), 255)

def write(path, px):
    rows = b"".join(b"\x00" + b"".join(bytes(px(x, y)) for x in range(16)) for y in range(16))
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(rows, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)

for name, px in SHARDS.items():
    write("assets/moneysmp/textures/item/fragment_%s.png" % name, px)
write("assets/moneysmp/textures/block/pedestal_stone.png", pedestal_stone)
write("assets/moneysmp/textures/block/pedestal_column.png", pedestal_column)
write("assets/moneysmp/textures/block/pedestal_top.png", pedestal_top)
