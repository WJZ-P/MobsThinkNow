#!/usr/bin/env python3
"""Generate production-ready Nether profession textures from the matching vanilla client JAR.

The generator preserves every vanilla UV and alpha boundary, recolours at native pixel resolution,
and adds role markings only on already-mapped pixels.  This keeps the assets deterministic and makes
them safe to regenerate when the project updates to another Minecraft patch version.
"""

from __future__ import annotations

import argparse
import colorsys
import zipfile
from collections.abc import Callable
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


RGB = tuple[int, int, int]
RGBA = tuple[int, int, int, int]


@dataclass(frozen=True)
class RolePalette:
	shadow: RGB
	mid: RGB
	highlight: RGB
	accent: RGB
	secondary: RGB


PALETTES: dict[str, RolePalette] = {
	"marksman": RolePalette((18, 30, 39), (42, 73, 88), (91, 139, 145), (82, 205, 211), (201, 155, 52)),
	"vanguard": RolePalette((48, 14, 18), (112, 37, 36), (181, 74, 47), (236, 123, 47), (83, 55, 42)),
	"commander": RolePalette((31, 20, 42), (78, 42, 90), (143, 77, 126), (242, 184, 46), (112, 24, 38)),
	"skirmisher": RolePalette((83, 27, 8), (213, 85, 15), (255, 199, 54), (69, 194, 186), (255, 238, 139)),
	"volleymaster": RolePalette((35, 17, 61), (100, 42, 126), (213, 86, 179), (104, 216, 255), (255, 186, 70)),
	"cinder_guard": RolePalette((31, 29, 31), (77, 55, 46), (156, 83, 43), (255, 92, 24), (243, 190, 63)),
	"artillery": RolePalette((177, 169, 163), (220, 213, 207), (249, 245, 236), (150, 42, 38), (88, 73, 68)),
	"spotter": RolePalette((157, 184, 186), (210, 231, 227), (248, 250, 242), (38, 169, 186), (65, 91, 112)),
	"siegebreaker": RolePalette((121, 112, 111), (181, 166, 160), (226, 215, 202), (164, 37, 28), (43, 40, 42)),
	"charger": RolePalette((71, 26, 22), (151, 62, 45), (212, 116, 74), (239, 154, 52), (70, 81, 83)),
	"bulwark": RolePalette((27, 31, 34), (66, 70, 70), (132, 117, 101), (210, 168, 62), (108, 35, 32)),
	"ravager": RolePalette((47, 10, 15), (111, 24, 31), (192, 51, 42), (247, 88, 29), (42, 39, 43)),
	"hunter": RolePalette((30, 0, 0), (100, 16, 6), (217, 62, 8), (255, 177, 35), (80, 31, 19)),
	"ambusher": RolePalette((15, 17, 25), (47, 42, 72), (128, 65, 123), (242, 78, 170), (64, 193, 203)),
	"titan": RolePalette((22, 21, 19), (68, 51, 34), (171, 83, 24), (255, 214, 71), (116, 25, 20)),
}


BASE_PATHS = {
	"piglin": "assets/minecraft/textures/entity/piglin/piglin.png",
	"piglin_brute": "assets/minecraft/textures/entity/piglin/piglin_brute.png",
	"blaze": "assets/minecraft/textures/entity/blaze/blaze.png",
	"ghast": "assets/minecraft/textures/entity/ghast/ghast.png",
	"ghast_shooting": "assets/minecraft/textures/entity/ghast/ghast_shooting.png",
	"hoglin": "assets/minecraft/textures/entity/hoglin/hoglin.png",
	"zoglin": "assets/minecraft/textures/entity/hoglin/zoglin.png",
	"magma_cube": "assets/minecraft/textures/entity/slime/magmacube.png",
}


def clamp_byte(value: float) -> int:
	return max(0, min(255, round(value)))


def mix(first: RGB, second: RGB, amount: float) -> RGB:
	return tuple(clamp_byte(a + (b - a) * amount) for a, b in zip(first, second))  # type: ignore[return-value]


def gradient_colour(palette: RolePalette, luma: float) -> RGB:
	position = max(0.0, min(1.0, luma / 255.0))
	if position < 0.52:
		return mix(palette.shadow, palette.mid, position / 0.52)
	return mix(palette.mid, palette.highlight, (position - 0.52) / 0.48)


def colourise(
	image: Image.Image,
	palette: RolePalette,
	strength: float,
	predicate: Callable[[int, int, RGBA], bool] | None = None,
) -> None:
	pixels = image.load()
	for y in range(image.height):
		for x in range(image.width):
			r, g, b, alpha = pixels[x, y]
			if alpha == 0 or (predicate is not None and not predicate(x, y, (r, g, b, alpha))):
				continue
			luma = r * 0.2126 + g * 0.7152 + b * 0.0722
			target = gradient_colour(palette, luma)
			pixels[x, y] = (*mix((r, g, b), target, strength), alpha)


def paint_if_mapped(image: Image.Image, points: list[tuple[int, int]], colour: RGB, alpha: int = 255) -> None:
	pixels = image.load()
	for x, y in points:
		if 0 <= x < image.width and 0 <= y < image.height and pixels[x, y][3] > 0:
			pixels[x, y] = (*colour, alpha)


def paint_opaque_pattern(
	image: Image.Image,
	colour: RGB,
	selector: Callable[[int, int], bool],
	blend_amount: float = 1.0,
) -> None:
	pixels = image.load()
	for y in range(image.height):
		for x in range(image.width):
			r, g, b, alpha = pixels[x, y]
			if alpha > 0 and selector(x, y):
				pixels[x, y] = (*mix((r, g, b), colour, blend_amount), alpha)


def piglin_texture(base: Image.Image, role: str, brute: bool) -> Image.Image:
	image = base.copy().convert("RGBA")
	palette = PALETTES[role]

	# Preserve the recognisable piglin face and ears; recolour clothing, limbs and the brute overlay.
	def gear(x: int, y: int, pixel: RGBA) -> bool:
		return y >= 16 or (brute and x >= 32 and y >= 8)

	colourise(image, palette, 0.60 if brute else 0.52, gear)

	# Standard humanoid torso front: sash/emblem remains legible from normal play distance.
	if role == "marksman":
		points = [(20 + i, 20 + i) for i in range(8)] + [(27 - i, 20 + i) for i in range(8)]
		paint_if_mapped(image, points, palette.accent)
		paint_if_mapped(image, [(23, 24), (24, 24), (23, 25), (24, 25)], palette.secondary)
	elif role == "vanguard":
		points = [(20 + i, 23 + abs(3 - i) // 2) for i in range(8)]
		paint_if_mapped(image, points, palette.accent)
		paint_if_mapped(image, [(22, 29), (23, 30), (24, 30), (25, 29)], palette.secondary)
	else:
		paint_if_mapped(image, [(22, y) for y in range(20, 32)] + [(25, y) for y in range(20, 32)], palette.accent)
		paint_if_mapped(image, [(23, 22), (24, 22), (23, 23), (24, 23)], palette.secondary)

	# Small forehead/cheek marks vary silhouettes without replacing the vanilla snout.
	face_points = {
		"marksman": [(9, 10), (10, 10), (13, 10), (14, 10)],
		"vanguard": [(8, 9), (9, 10), (10, 11), (15, 9), (14, 10), (13, 11)],
		"commander": [(10, 8), (11, 9), (12, 9), (13, 8), (11, 10), (12, 10)],
	}[role]
	paint_if_mapped(image, face_points, palette.accent)
	return image


def blaze_texture(base: Image.Image, role: str) -> Image.Image:
	image = base.copy().convert("RGBA")
	palette = PALETTES[role]
	colourise(image, palette, 0.72)

	# Rods occupy repeated UV strips. Alternating metal/rune bands remain visible during rotation.
	paint_opaque_pattern(image, palette.accent, lambda x, y: y >= 16 and (x + y) % 9 == 0, 0.84)
	paint_opaque_pattern(image, palette.secondary, lambda x, y: y < 16 and x % 7 in (0, 1), 0.70)
	if role == "volleymaster":
		paint_opaque_pattern(image, palette.accent, lambda x, y: (x - y) % 11 == 0, 0.72)
	elif role == "cinder_guard":
		paint_opaque_pattern(image, palette.shadow, lambda x, y: (x + 2 * y) % 13 in (0, 1), 0.75)
	return image


def ghast_texture(base: Image.Image, role: str) -> Image.Image:
	image = base.copy().convert("RGBA")
	palette = PALETTES[role]
	# Very light tint keeps the iconic pale cube while making silhouettes readable at Nether distances.
	colourise(image, palette, 0.22)

	# Face UV is x=32..63, y=32..63 in the 128x64 atlas.
	if role == "artillery":
		points = [(34 + i, 36 + i) for i in range(8)] + [(61 - i, 36 + i) for i in range(8)]
		paint_if_mapped(image, points, palette.accent)
		paint_if_mapped(image, [(46, 35), (47, 34), (48, 34), (49, 35)], palette.secondary)
	elif role == "spotter":
		points = [(x, 41) for x in range(36, 46)] + [(41, y) for y in range(36, 47)]
		paint_if_mapped(image, points, palette.accent)
		paint_if_mapped(image, [(40, 40), (42, 40), (40, 42), (42, 42)], palette.secondary)
	else:
		paint_if_mapped(image, [(35, y) for y in range(34, 58)] + [(60, y) for y in range(34, 58)], palette.secondary)
		paint_if_mapped(image, [(36 + i, 37 + i // 2) for i in range(24)], palette.accent)
		paint_if_mapped(image, [(59 - i, 37 + i // 2) for i in range(24)], palette.accent)
	return image


def hoglin_texture(base: Image.Image, role: str, zoglin: bool) -> Image.Image:
	image = base.copy().convert("RGBA")
	palette = PALETTES[role]

	# Tusks/bones are nearly neutral and bright; keep them readable while colourising hide and bristles.
	def hide(_x: int, _y: int, pixel: RGBA) -> bool:
		r, g, b, _a = pixel
		saturation = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)[1]
		return not (max(r, g, b) > 175 and saturation < 0.20)

	colourise(image, palette, 0.56 if zoglin else 0.50, hide)
	if role == "charger":
		paint_opaque_pattern(image, palette.accent, lambda x, y: (x + y) % 19 == 0, 0.76)
	elif role == "bulwark":
		paint_opaque_pattern(image, palette.shadow, lambda x, y: y % 9 in (0, 1), 0.62)
		paint_opaque_pattern(image, palette.accent, lambda x, y: y % 18 == 2, 0.78)
	else:
		paint_opaque_pattern(image, palette.accent, lambda x, y: (x - 2 * y) % 17 in (0, 1), 0.78)
		paint_opaque_pattern(image, palette.secondary, lambda x, y: (x + y) % 23 == 0, 0.70)
	return image


def magma_texture(base: Image.Image, role: str) -> Image.Image:
	image = base.copy().convert("RGBA")
	palette = PALETTES[role]
	colourise(image, palette, 0.68)
	if role == "hunter":
		paint_opaque_pattern(image, palette.accent, lambda x, y: (x + y) % 13 == 0, 0.60)
	elif role == "ambusher":
		paint_opaque_pattern(image, palette.secondary, lambda x, y: (x - y) % 11 in (0, 1), 0.72)
		paint_opaque_pattern(image, palette.accent, lambda x, y: (x + y) % 17 == 0, 0.84)
	else:
		paint_opaque_pattern(image, palette.shadow, lambda x, y: y % 8 in (0, 1), 0.70)
		paint_opaque_pattern(image, palette.accent, lambda x, y: y % 16 == 2, 0.82)
	return image


def load_bases(jar_path: Path) -> dict[str, Image.Image]:
	with zipfile.ZipFile(jar_path) as archive:
		return {
			name: Image.open(BytesIO(archive.read(path))).convert("RGBA")
			for name, path in BASE_PATHS.items()
		}


def generate(jar_path: Path, output_root: Path) -> list[tuple[str, Image.Image]]:
	bases = load_bases(jar_path)
	assets: list[tuple[str, Image.Image]] = []

	for role in ("marksman", "vanguard", "commander"):
		assets.append((f"piglin/{role}.png", piglin_texture(bases["piglin"], role, False)))
		assets.append((f"piglin/brute_{role}.png", piglin_texture(bases["piglin_brute"], role, True)))
	for role in ("skirmisher", "volleymaster", "cinder_guard"):
		assets.append((f"blaze/{role}.png", blaze_texture(bases["blaze"], role)))
	for role in ("artillery", "spotter", "siegebreaker"):
		assets.append((f"ghast/{role}.png", ghast_texture(bases["ghast"], role)))
		assets.append((f"ghast/{role}_shooting.png", ghast_texture(bases["ghast_shooting"], role)))
	for role in ("charger", "bulwark", "ravager"):
		assets.append((f"hoglin/{role}.png", hoglin_texture(bases["hoglin"], role, False)))
		assets.append((f"zoglin/{role}.png", hoglin_texture(bases["zoglin"], role, True)))
	for role in ("hunter", "ambusher", "titan"):
		assets.append((f"magma_cube/{role}.png", magma_texture(bases["magma_cube"], role)))

	for relative_path, image in assets:
		destination = output_root / relative_path
		destination.parent.mkdir(parents=True, exist_ok=True)
		image.save(destination, optimize=True)
	return assets


def preview(assets: list[tuple[str, Image.Image]], output: Path) -> None:
	cell_width, cell_height = 294, 176
	columns = 4
	rows = (len(assets) + columns - 1) // columns
	canvas = Image.new("RGBA", (cell_width * columns, cell_height * rows), (15, 17, 22, 255))
	draw = ImageDraw.Draw(canvas)
	font = ImageFont.load_default()
	for index, (name, image) in enumerate(assets):
		column, row = index % columns, index // columns
		x, y = column * cell_width, row * cell_height
		checker = Image.new("RGBA", (cell_width - 12, cell_height - 32), (26, 28, 34, 255))
		zoom = 4 if image.width == 64 else 2
		scaled = image.resize((image.width * zoom, image.height * zoom), Image.Resampling.NEAREST)
		checker.alpha_composite(scaled, ((checker.width - scaled.width) // 2, (checker.height - scaled.height) // 2))
		canvas.alpha_composite(checker, (x + 6, y + 25))
		draw.text((x + 8, y + 7), name, font=font, fill=(235, 238, 242, 255))
	output.parent.mkdir(parents=True, exist_ok=True)
	canvas.save(output, optimize=True)


def locate_default_jar(project_root: Path) -> Path:
	candidates = sorted(project_root.glob(".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/26.1.2/*.jar"))
	candidates = [path for path in candidates if "sources" not in path.name and ".backup" not in path.name]
	if not candidates:
		raise FileNotFoundError("Pass --minecraft-jar; no Loom Minecraft 26.1.2 JAR was found.")
	return candidates[0]


def main() -> None:
	parser = argparse.ArgumentParser()
	parser.add_argument("--minecraft-jar", type=Path)
	parser.add_argument(
		"--output",
		type=Path,
		default=Path("src/main/resources/assets/mobsthinknow/textures/entity/nether"),
	)
	parser.add_argument(
		"--preview",
		type=Path,
		default=Path("docs/concepts/nether-profession-skin-preview.png"),
	)
	args = parser.parse_args()
	project_root = Path.cwd()
	jar_path = args.minecraft_jar or locate_default_jar(project_root)
	assets = generate(jar_path, args.output)
	preview(assets, args.preview)
	print(f"Generated {len(assets)} textures in {args.output}")
	print(f"Preview: {args.preview}")


if __name__ == "__main__":
	main()
