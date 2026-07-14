#!/usr/bin/env python3
"""Generate the Solace logo: a pixel-art pink heart glowing softly in a dusk sky with a few
stars, on a rounded-corner tile. The heart is the mod's identity (safety and comfort on a
survival world); the calm night sky is the world it protects you in. Pixel art keeps it
on-theme for a Minecraft mod and simple to edit: change the LOGICAL grid below and re-run.
Output is nearest-neighbour upscaled so the blocks stay crisp.

    python3 icon_art.py

Writes icon.png (repo root, Modrinth/GitHub page) and src/main/resources/assets/solace/icon.png
(referenced by fabric.mod.json, shown in the mods list).
"""
import struct
import zlib

N = 32            # logical grid (NxN), then scaled up
SCALE = 8         # -> 256x256 output
CORNER = 4        # rounded-corner radius in logical pixels

# palette ------------------------------------------------------------------------------------------
SKY_TOP = (34, 30, 62)
SKY_BOT = (72, 54, 100)
GLOW = (214, 116, 142)        # halo colour the sky blends toward near the heart
HEART = (236, 142, 162)
HEART_DK = (198, 104, 128)    # lower-third shading
HEART_HI = (252, 196, 208)    # top-left highlight
OUTLINE = (58, 34, 60)
STAR = (232, 228, 244)
STAR_DIM = (150, 142, 190)

# heart geometry: implicit heart curve, centred and scaled to the grid
HX, HY = 15.5, 16.0           # heart centre (grid coords)
HSX, HSY = 8.6, 8.2           # half-extents mapped to the curve's unit box

# hand-placed stars: (x, y, bright); a couple get a twinkle cross
STARS = [(4, 4, True), (26, 3, False), (9, 8, False), (23, 8, True),
         (28, 12, False), (3, 14, False), (6, 25, False), (27, 24, True)]
TWINKLES = [(4, 4), (23, 8)]


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def in_heart(x, y, inset=0.0):
    # classic implicit heart: (u^2 + v^2 - 1)^3 - u^2 v^3 <= 0, v pointing up
    u = (x - HX) / (HSX - inset)
    v = (HY - y) / (HSY - inset) + 0.28
    return (u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0


def in_tile(x, y):
    # rounded-rect alpha mask for the whole tile
    dx = max(CORNER - 1 - x, x - (N - CORNER)) if (x < CORNER or x >= N - CORNER) else -1
    dy = max(CORNER - 1 - y, y - (N - CORNER)) if (y < CORNER or y >= N - CORNER) else -1
    if dx < 0 or dy < 0:
        return True
    return dx * dx + dy * dy <= (CORNER - 1) ** 2 + 1


def render():
    grid = [[None] * N for _ in range(N)]

    # dusk sky with a soft radial glow bleeding out from the heart
    for y in range(N):
        sky = lerp(SKY_TOP, SKY_BOT, y / (N - 1))
        for x in range(N):
            d = ((x - HX) ** 2 + (y - HY) ** 2) ** 0.5
            halo = max(0.0, 1.0 - d / 13.5) ** 2 * 0.45
            grid[y][x] = lerp(sky, GLOW, halo) + (255,)

    # stars (skip any the glow has washed out)
    for x, y, bright in STARS:
        grid[y][x] = (STAR if bright else STAR_DIM) + (255,)
    for x, y in TWINKLES:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if 0 <= x + dx < N and 0 <= y + dy < N:
                grid[y + dy][x + dx] = STAR_DIM + (255,)

    # heart: fill, lower-third shade, top-left highlight patch
    for y in range(N):
        for x in range(N):
            if in_heart(x, y):
                c = HEART_DK if y > HY + HSY * 0.28 else HEART
                if in_heart(x + 1.6, y + 1.8, inset=1.4):
                    c = HEART_HI
                grid[y][x] = c + (255,)

    # 1px dark outline around the heart silhouette
    out = [row[:] for row in grid]
    for y in range(N):
        for x in range(N):
            if in_heart(x, y):
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if in_heart(x + dx, y + dy):
                    out[y][x] = OUTLINE + (255,)
                    break

    # punch out the rounded corners
    for y in range(N):
        for x in range(N):
            if not in_tile(x, y):
                out[y][x] = (0, 0, 0, 0)
    return out


def scale(grid):
    px = []
    for y in range(N):
        row = [grid[y][x] for x in range(N)]
        for _ in range(SCALE):
            for c in row:
                px.extend([c] * SCALE)
    return px


def write_png(path, px, w, h):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw += bytes(px[y * w + x])

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff))

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
        f.write(chunk(b"IEND", b""))


def preview():
    lines = []
    for y in range(N):
        line = ""
        for x in range(N):
            if not in_tile(x, y):
                line += " "
            elif in_heart(x, y):
                line += "#"
            elif (x, y, True) in STARS or (x, y, False) in STARS:
                line += "*"
            else:
                line += "."
        lines.append(line)
    return "\n".join(lines)


def main():
    grid = render()
    print(preview())
    out = N * SCALE
    px = scale(grid)
    for path in ("icon.png", "src/main/resources/assets/solace/icon.png"):
        write_png(path, px, out, out)
    print(f"\nwrote {out}x{out} icon.png + assets copy")


if __name__ == "__main__":
    main()
