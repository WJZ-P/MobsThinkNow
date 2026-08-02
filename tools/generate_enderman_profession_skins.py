#!/usr/bin/env python3
"""Generate deterministic 64x32 Enderman profession textures from the vanilla client JAR."""

from __future__ import annotations

import argparse
import zipfile
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


RGB = tuple[int, int, int]


@dataclass(frozen=True)
class Palette:
	shadow: RGB
	mid: RGB
	highlight: RGB
	accent: RGB
	secondary: RGB


PALETTES = {
	"riftblade": Palette((7, 3, 15), (35, 13, 61), (90, 31, 126), (224, 53, 205), (72, 211, 224)),
	"void_guard": Palette((4, 10, 17), (17, 46, 61), (47, 104, 117), (65, 220, 229), (156, 57, 191)),
	"void_lancer": Palette((9, 7, 21), (35, 28, 75), (82, 68, 146), (236, 184, 54), (72, 200, 218)),
	"creeper_herald": Palette((4, 16, 10), (17, 57, 32), (47, 119, 65), (85, 222, 91), (205, 55, 186)),
}


def mix(first: RGB, second: RGB, amount: float) -> RGB:
	return tuple(round(a + (b - a) * amount) for a, b in zip(first, second))  # type: ignore[return-value]


def colourise(base: Image.Image, palette: Palette) -> Image.Image:
	image = base.copy().convert("RGBA")
	pixels = image.load()
	for y in range(image.height):
		for x in range(image.width):
			r, g, b, alpha = pixels[x, y]
			if alpha == 0 or min(r, g, b) >= 245:
				continue
			luma = (r + g + b) / (3.0 * 22.0)
			if luma < 0.52:
				colour = mix(palette.shadow, palette.mid, luma / 0.52)
			else:
				colour = mix(palette.mid, palette.highlight, min(1.0, (luma - 0.52) / 0.48))
			pixels[x, y] = (*colour, alpha)
	return image


def paint(image: Image.Image, points: list[tuple[int, int]], colour: RGB) -> None:
	pixels = image.load()
	for x, y in points:
		if 0 <= x < image.width and 0 <= y < image.height and pixels[x, y][3] > 0:
			pixels[x, y] = (*colour, 255)


def add_role_marks(image: Image.Image, role: str, palette: Palette) -> None:
	# Torso front occupies x=20..27, y=20..31 on the classic humanoid atlas.
	if role == "riftblade":
		paint(image, [(20 + i, 21 + i) for i in range(7)], palette.accent)
		paint(image, [(27 - i, 21 + i) for i in range(7)], palette.secondary)
		paint(image, [(10, 9), (13, 9), (11, 10), (12, 10)], palette.accent)
	elif role == "void_guard":
		border = [(x, y) for x in range(20, 28) for y in range(21, 31) if x in (20, 27) or y in (21, 30)]
		paint(image, border, palette.accent)
		paint(image, [(23, 24), (24, 24), (22, 25), (25, 25), (23, 26), (24, 26)], palette.secondary)
		paint(image, [(9, 9), (14, 9), (10, 10), (13, 10)], palette.accent)
	elif role == "void_lancer":
		paint(image, [(23, y) for y in range(20, 31)] + [(24, y) for y in range(20, 31)], palette.accent)
		paint(image, [(21, 23), (22, 22), (25, 22), (26, 23), (22, 29), (25, 29)], palette.secondary)
		paint(image, [(11, 8), (12, 8), (10, 9), (13, 9)], palette.accent)
	else:
		# Compact creeper face glyph: readable on the chest without replacing the Enderman head.
		paint(image, [(21, 22), (22, 22), (25, 22), (26, 22), (23, 24), (24, 24)], palette.accent)
		paint(image, [(22, 25), (23, 25), (24, 25), (25, 25), (21, 26), (22, 26), (25, 26), (26, 26)], palette.accent)
		paint(image, [(10, 9), (13, 9), (11, 11), (12, 11)], palette.secondary)

	# The long limbs share x=56..63; alternating bands remain visible while walking and attacking.
	paint(
		image,
		[(x, y) for y in range(4, 32) if y % 7 == 0 for x in range(56, 64)],
		palette.secondary,
	)


def generate(jar_path: Path, output_root: Path) -> list[tuple[str, Image.Image]]:
	with zipfile.ZipFile(jar_path) as archive:
		base = Image.open(BytesIO(archive.read("assets/minecraft/textures/entity/enderman/enderman.png"))).convert("RGBA")
	assets: list[tuple[str, Image.Image]] = []
	for role, palette in PALETTES.items():
		image = colourise(base, palette)
		add_role_marks(image, role, palette)
		destination = output_root / f"{role}.png"
		destination.parent.mkdir(parents=True, exist_ok=True)
		image.save(destination, optimize=True)
		assets.append((role, image))
	return assets


def preview(assets: list[tuple[str, Image.Image]], output: Path) -> None:
	cell_width, cell_height = 310, 190
	canvas = Image.new("RGBA", (cell_width * len(assets), cell_height), (14, 16, 23, 255))
	draw = ImageDraw.Draw(canvas)
	font = ImageFont.load_default()
	for index, (name, image) in enumerate(assets):
		x = index * cell_width
		draw.text((x + 10, 8), name, font=font, fill=(235, 238, 245, 255))
		scaled = image.resize((256, 128), Image.Resampling.NEAREST)
		canvas.alpha_composite(scaled, (x + 27, 40))
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
		default=Path("src/main/resources/assets/mobsthinknow/textures/entity/enderman"),
	)
	parser.add_argument(
		"--preview",
		type=Path,
		default=Path("docs/concepts/enderman-profession-skin-preview.png"),
	)
	args = parser.parse_args()
	jar_path = args.minecraft_jar or locate_default_jar(Path.cwd())
	assets = generate(jar_path, args.output)
	preview(assets, args.preview)
	print(f"Generated {len(assets)} Enderman textures in {args.output}")
	print(f"Preview: {args.preview}")


if __name__ == "__main__":
	main()
