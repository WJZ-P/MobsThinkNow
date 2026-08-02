#!/usr/bin/env python3
"""Generate the nine production zombie profession textures from a vanilla 64x64 zombie texture.

The script deliberately works at native pixel resolution.  It keeps the recognisable vanilla zombie
face as its foundation, then uses both the base head and the slightly expanded hat layer for coherent
profession-specific headwear, scars, goggles and face paint.  Clothing is recoloured with the original
shading before straps, armour and emblems are painted on top.  The Minecraft texture itself is not
stored as a source input in this repository; pass a texture extracted from the matching local client JAR
via ``--base``.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


RGBA = tuple[int, int, int, int]
RGB = tuple[int, int, int]


@dataclass(frozen=True)
class Palette:
	shirt: RGB
	pants: RGB
	dark: RGB
	accent: RGB
	metal: RGB = (178, 190, 184)
	leather: RGB = (78, 51, 35)


PALETTES: dict[str, Palette] = {
	"recruit": Palette((28, 126, 124), (70, 62, 104), (21, 68, 67), (139, 103, 55)),
	"swordsman": Palette((43, 69, 112), (45, 51, 70), (24, 37, 61), (113, 155, 188)),
	"axeman": Palette((126, 52, 35), (63, 48, 42), (65, 29, 24), (196, 112, 58)),
	"sword_guard": Palette((39, 65, 126), (41, 47, 63), (20, 31, 68), (150, 181, 207)),
	"axe_guard": Palette((110, 31, 37), (55, 43, 42), (58, 18, 23), (203, 93, 63)),
	"engineer": Palette((142, 112, 57), (75, 62, 45), (68, 52, 30), (231, 178, 46)),
	"water_support": Palette((30, 62, 93), (37, 48, 63), (18, 36, 57), (51, 188, 213)),
	"lava_harasser": Palette((54, 55, 54), (45, 40, 38), (27, 28, 27), (232, 92, 24)),
	"air_assault": Palette((62, 42, 86), (60, 48, 43), (31, 24, 47), (181, 159, 207)),
}


SHIRT_RECTS = (
	(16, 16, 40, 20),  # torso top/bottom
	(16, 20, 40, 32),  # torso sides/front/back
	(40, 16, 56, 20),  # arm top/bottom
	(40, 20, 56, 24),  # short sleeves; lower arm remains zombie skin
)
PANTS_RECTS = (
	(0, 16, 16, 20),
	(0, 20, 16, 32),
)
BODY_FACES = ((16, 20, 4, 12), (20, 20, 8, 12), (28, 20, 4, 12), (32, 20, 8, 12))
ARM_FACES = ((40, 20, 4, 12), (44, 20, 4, 12), (48, 20, 4, 12), (52, 20, 4, 12))
LEG_FACES = ((0, 20, 4, 12), (4, 20, 4, 12), (8, 20, 4, 12), (12, 20, 4, 12))

# Standard 64x64 humanoid head UVs.  Coordinates passed to the helpers below are always local 0..7
# coordinates on a cube face, which makes front/side/back designs much easier to reason about.
BASE_HEAD_FACES = {
	"top": (8, 0),
	"bottom": (16, 0),
	"right": (0, 8),
	"front": (8, 8),
	"left": (16, 8),
	"back": (24, 8),
}
HAT_HEAD_FACES = {
	"top": (40, 0),
	"bottom": (48, 0),
	"right": (32, 8),
	"front": (40, 8),
	"left": (48, 8),
	"back": (56, 8),
}


def rgba(rgb: RGB, alpha: int = 255) -> RGBA:
	return rgb[0], rgb[1], rgb[2], alpha


def shade(rgb: RGB, factor: float) -> RGB:
	return tuple(max(0, min(255, round(channel * factor))) for channel in rgb)  # type: ignore[return-value]


def recolour_rect(image: Image.Image, box: tuple[int, int, int, int], target: RGB, reference_luma: float) -> None:
	pixels = image.load()
	for y in range(box[1], box[3]):
		for x in range(box[0], box[2]):
			r, g, b, a = pixels[x, y]
			if a == 0:
				continue
			luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
			factor = max(0.58, min(1.40, luma / reference_luma))
			pixels[x, y] = rgba(shade(target, factor), a)


def band(draw: ImageDraw.ImageDraw, faces: tuple[tuple[int, int, int, int], ...], row: int, colour: RGB) -> None:
	for x, y, width, height in faces:
		if 0 <= row < height:
			draw.line((x, y + row, x + width - 1, y + row), fill=rgba(colour))


def put(draw: ImageDraw.ImageDraw, points: list[tuple[int, int]], colour: RGB) -> None:
	for point in points:
		draw.point(point, fill=rgba(colour))


def head_point(
	draw: ImageDraw.ImageDraw,
	layer: dict[str, tuple[int, int]],
	face_name: str,
	x: int,
	y: int,
	colour: RGB,
	alpha: int = 255,
) -> None:
	"""Paint one native head pixel without leaking raw atlas coordinates into the designs."""
	origin_x, origin_y = layer[face_name]
	draw.point((origin_x + x, origin_y + y), fill=rgba(colour, alpha))


def head_rect(
	draw: ImageDraw.ImageDraw,
	layer: dict[str, tuple[int, int]],
	face_name: str,
	box: tuple[int, int, int, int],
	colour: RGB,
	alpha: int = 255,
) -> None:
	origin_x, origin_y = layer[face_name]
	draw.rectangle(
		(origin_x + box[0], origin_y + box[1], origin_x + box[2], origin_y + box[3]),
		fill=rgba(colour, alpha),
	)


def head_band(
	draw: ImageDraw.ImageDraw,
	layer: dict[str, tuple[int, int]],
	faces: tuple[str, ...],
	start_row: int,
	end_row: int,
	colour: RGB,
) -> None:
	for face_name in faces:
		head_rect(draw, layer, face_name, (0, start_row, 7, end_row), colour)


def clear_head_overlay(image: Image.Image) -> None:
	"""Vanilla zombie has an empty overlay, but clearing it makes regeneration deterministic."""
	ImageDraw.Draw(image).rectangle((32, 0, 63, 15), fill=(0, 0, 0, 0))


def add_common_finish(draw: ImageDraw.ImageDraw, palette: Palette) -> None:
	# Waist belt, sleeve hems and dark boots make every profession readable from the side and rear too.
	band(draw, BODY_FACES, 9, palette.leather)
	band(draw, BODY_FACES, 10, shade(palette.leather, 0.72))
	band(draw, ARM_FACES, 3, shade(palette.shirt, 0.72))
	band(draw, ARM_FACES, 7, shade(palette.leather, 1.15))
	band(draw, ARM_FACES, 8, palette.leather)
	band(draw, LEG_FACES, 9, palette.dark)
	band(draw, LEG_FACES, 10, shade(palette.dark, 0.78))
	band(draw, LEG_FACES, 11, shade(palette.dark, 0.64))


def add_recruit_head(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	# A cloth headband is deliberately light equipment: it identifies the recruit without making it elite.
	head_band(draw, HAT_HEAD_FACES, ("front", "right", "left", "back"), 0, 0, shade(p.dark, 0.82))
	head_band(draw, HAT_HEAD_FACES, ("front", "right", "left", "back"), 1, 1, shade(p.shirt, 1.08))
	head_point(draw, HAT_HEAD_FACES, "front", 5, 0, p.metal)
	head_point(draw, HAT_HEAD_FACES, "front", 6, 0, shade(p.metal, 0.78))
	# A tiny old scratch breaks the cloned-face look while keeping the vanilla eyes and mouth intact.
	head_point(draw, BASE_HEAD_FACES, "front", 0, 5, (67, 77, 38))
	head_point(draw, BASE_HEAD_FACES, "front", 1, 6, (80, 88, 46))


def add_swordsman_head(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	wrap = shade(p.shirt, 0.72)
	wrap_dark = shade(p.dark, 0.82)
	head_rect(draw, HAT_HEAD_FACES, "top", (0, 0, 7, 7), wrap)
	head_band(draw, HAT_HEAD_FACES, ("front", "right", "left", "back"), 0, 2, wrap)
	head_band(draw, HAT_HEAD_FACES, ("right", "left", "back"), 3, 3, wrap_dark)
	head_rect(draw, HAT_HEAD_FACES, "front", (3, 0, 3, 2), p.metal)
	head_rect(draw, HAT_HEAD_FACES, "front", (4, 0, 4, 2), shade(p.accent, 0.72))
	head_rect(draw, HAT_HEAD_FACES, "top", (3, 0, 3, 7), shade(p.metal, 0.88))
	head_rect(draw, HAT_HEAD_FACES, "top", (4, 0, 4, 7), shade(p.accent, 0.62))
	# Knotted cloth tails on the rear face.
	head_point(draw, HAT_HEAD_FACES, "back", 5, 3, wrap)
	head_point(draw, HAT_HEAD_FACES, "back", 5, 4, wrap)
	head_point(draw, HAT_HEAD_FACES, "back", 6, 4, shade(p.accent, 0.72))
	head_point(draw, HAT_HEAD_FACES, "back", 6, 5, wrap_dark)
	# Subtle cheek scar below the right eye.
	head_point(draw, BASE_HEAD_FACES, "front", 6, 5, (101, 67, 48))
	head_point(draw, BASE_HEAD_FACES, "front", 5, 6, (83, 57, 42))


def add_axeman_head(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	leather_dark = shade(p.leather, 0.72)
	head_band(draw, HAT_HEAD_FACES, ("front", "right", "left", "back"), 0, 0, leather_dark)
	head_band(draw, HAT_HEAD_FACES, ("front", "right", "left", "back"), 1, 1, p.leather)
	# Crossed skull straps read from above as well as from ground level.
	for index in range(8):
		head_point(draw, HAT_HEAD_FACES, "top", index, index, p.leather)
		head_point(draw, HAT_HEAD_FACES, "top", 7 - index, index, leather_dark)
	# Rust-red war paint and a nicked brow make this face intentionally rougher than the swordsman.
	head_point(draw, BASE_HEAD_FACES, "front", 0, 3, shade(p.accent, 0.82))
	head_point(draw, BASE_HEAD_FACES, "front", 0, 4, p.accent)
	head_point(draw, BASE_HEAD_FACES, "front", 1, 5, shade(p.accent, 0.70))
	head_point(draw, BASE_HEAD_FACES, "front", 6, 3, (44, 55, 34))
	head_point(draw, HAT_HEAD_FACES, "right", 2, 2, p.accent)
	head_point(draw, HAT_HEAD_FACES, "left", 5, 2, p.accent)


def add_guard_head(draw: ImageDraw.ImageDraw, p: Palette, axe: bool) -> None:
	# Open-face helmets use the outer layer instead of erasing the zombie face.  The straight edges are
	# intentionally square so the silhouette stays faithful to vanilla Minecraft rather than looking round.
	steel = shade(p.metal, 0.82) if not axe else (70, 70, 67)
	steel_dark = shade(steel, 0.58)
	ridge = p.accent if axe else shade(p.metal, 1.08)
	head_rect(draw, HAT_HEAD_FACES, "top", (0, 0, 7, 7), steel)
	head_rect(draw, HAT_HEAD_FACES, "back", (0, 0, 7, 7), steel_dark)
	for side in ("right", "left"):
		head_rect(draw, HAT_HEAD_FACES, side, (0, 0, 7, 5), steel)
		head_rect(draw, HAT_HEAD_FACES, side, (0, 5, 7, 7), steel_dark)
	head_rect(draw, HAT_HEAD_FACES, "front", (0, 0, 7, 1), steel)
	head_rect(draw, HAT_HEAD_FACES, "front", (0, 2, 0, 6), steel_dark)
	head_rect(draw, HAT_HEAD_FACES, "front", (7, 2, 7, 6), steel_dark)
	head_rect(draw, HAT_HEAD_FACES, "front", (1, 5, 1, 7), steel)
	head_rect(draw, HAT_HEAD_FACES, "front", (6, 5, 6, 7), steel)
	head_rect(draw, HAT_HEAD_FACES, "top", (3, 0, 4, 7), ridge)
	head_rect(draw, HAT_HEAD_FACES, "front", (3, 0, 4, 1), ridge)
	head_rect(draw, HAT_HEAD_FACES, "back", (3, 0, 4, 5), shade(ridge, 0.82))
	head_rect(draw, HAT_HEAD_FACES, "back", (0, 6, 7, 7), shade(steel_dark, 0.78))
	head_point(draw, HAT_HEAD_FACES, "back", 1, 2, shade(steel, 1.18))
	head_point(draw, HAT_HEAD_FACES, "back", 6, 2, shade(steel, 1.18))
	if axe:
		# The axe guard has a heavier jaw and a red command ridge.
		head_rect(draw, HAT_HEAD_FACES, "front", (0, 7, 2, 7), steel_dark)
		head_rect(draw, HAT_HEAD_FACES, "front", (5, 7, 7, 7), steel_dark)
		head_point(draw, HAT_HEAD_FACES, "back", 3, 2, ridge)
		head_point(draw, HAT_HEAD_FACES, "back", 4, 2, ridge)
	else:
		head_point(draw, HAT_HEAD_FACES, "front", 0, 2, shade(p.accent, 1.10))
		head_point(draw, HAT_HEAD_FACES, "front", 7, 2, shade(p.accent, 1.10))


def add_engineer_head(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	yellow = p.accent
	yellow_light = shade(yellow, 1.18)
	yellow_dark = shade(yellow, 0.64)
	head_rect(draw, HAT_HEAD_FACES, "top", (0, 0, 7, 7), yellow)
	head_rect(draw, HAT_HEAD_FACES, "top", (2, 1, 5, 5), yellow_light)
	head_band(draw, HAT_HEAD_FACES, ("front", "right", "left", "back"), 0, 2, yellow)
	head_band(draw, HAT_HEAD_FACES, ("front", "right", "left", "back"), 3, 3, yellow_dark)
	# A one-pixel protruding brim and soot goggles are chunky enough to survive native resolution.
	head_rect(draw, HAT_HEAD_FACES, "front", (0, 2, 7, 2), yellow_light)
	head_rect(draw, HAT_HEAD_FACES, "front", (0, 4, 7, 4), shade(p.leather, 0.64))
	for x in (1, 2, 5, 6):
		head_point(draw, HAT_HEAD_FACES, "front", x, 4, (57, 91, 91))
	head_point(draw, HAT_HEAD_FACES, "front", 1, 4, (147, 187, 171))
	head_point(draw, HAT_HEAD_FACES, "front", 5, 4, (147, 187, 171))
	head_band(draw, HAT_HEAD_FACES, ("right", "left", "back"), 4, 4, shade(p.leather, 0.64))
	head_point(draw, BASE_HEAD_FACES, "front", 6, 6, (49, 58, 38))
	head_point(draw, BASE_HEAD_FACES, "front", 7, 7, (62, 66, 39))


def add_water_head(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	hood = shade(p.shirt, 0.76)
	hood_dark = shade(p.dark, 0.76)
	cyan = shade(p.accent, 1.12)
	head_rect(draw, HAT_HEAD_FACES, "top", (0, 0, 7, 7), hood)
	head_rect(draw, HAT_HEAD_FACES, "back", (0, 0, 7, 7), hood)
	for side in ("right", "left"):
		head_rect(draw, HAT_HEAD_FACES, side, (0, 0, 7, 7), hood)
	head_rect(draw, HAT_HEAD_FACES, "front", (0, 0, 7, 2), hood)
	head_rect(draw, HAT_HEAD_FACES, "front", (0, 2, 0, 7), hood_dark)
	head_rect(draw, HAT_HEAD_FACES, "front", (7, 2, 7, 7), hood_dark)
	# Cyan rescue goggles: dark strap, bright square frames, tinted lenses and a central bridge.
	head_rect(draw, HAT_HEAD_FACES, "front", (0, 3, 7, 5), hood_dark)
	for x in (1, 2, 5, 6):
		head_point(draw, HAT_HEAD_FACES, "front", x, 3, cyan)
		head_point(draw, HAT_HEAD_FACES, "front", x, 5, cyan)
	head_point(draw, HAT_HEAD_FACES, "front", 1, 4, (105, 190, 190), 225)
	head_point(draw, HAT_HEAD_FACES, "front", 2, 4, (73, 143, 151), 225)
	head_point(draw, HAT_HEAD_FACES, "front", 5, 4, (105, 190, 190), 225)
	head_point(draw, HAT_HEAD_FACES, "front", 6, 4, (73, 143, 151), 225)
	head_point(draw, HAT_HEAD_FACES, "front", 3, 4, cyan)
	head_point(draw, HAT_HEAD_FACES, "front", 4, 4, cyan)
	head_band(draw, HAT_HEAD_FACES, ("right", "left", "back"), 4, 4, hood_dark)
	# Bubble and wave marks make the rear readable when the zombie is assisting its squad.
	head_point(draw, HAT_HEAD_FACES, "back", 5, 1, cyan)
	head_point(draw, HAT_HEAD_FACES, "back", 6, 0, shade(cyan, 0.82))
	for x, y in ((1, 5), (2, 6), (3, 6), (4, 5), (5, 5), (6, 4)):
		head_point(draw, HAT_HEAD_FACES, "back", x, y, shade(cyan, 0.86))


def add_lava_head(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	hood = shade(p.dark, 0.86)
	hood_shadow = shade(p.dark, 0.58)
	ember = shade(p.accent, 1.08)
	head_rect(draw, HAT_HEAD_FACES, "top", (0, 0, 7, 7), hood)
	head_rect(draw, HAT_HEAD_FACES, "back", (0, 0, 7, 7), hood)
	for side in ("right", "left"):
		head_rect(draw, HAT_HEAD_FACES, side, (0, 0, 7, 7), hood_shadow)
	head_rect(draw, HAT_HEAD_FACES, "front", (0, 0, 7, 1), hood)
	head_rect(draw, HAT_HEAD_FACES, "front", (0, 2, 0, 7), hood_shadow)
	head_rect(draw, HAT_HEAD_FACES, "front", (7, 2, 7, 7), hood_shadow)
	head_rect(draw, HAT_HEAD_FACES, "front", (0, 1, 7, 1), ember)
	head_point(draw, HAT_HEAD_FACES, "front", 0, 3, p.accent)
	head_point(draw, HAT_HEAD_FACES, "front", 7, 4, p.accent)
	# Raised visor and rear ember crack keep the face readable instead of replacing it with a black square.
	head_rect(draw, HAT_HEAD_FACES, "front", (1, 2, 6, 2), shade(hood_shadow, 0.70))
	for x, y in ((3, 1), (3, 2), (4, 3), (4, 4), (5, 5), (4, 6)):
		head_point(draw, HAT_HEAD_FACES, "back", x, y, ember)
	head_point(draw, BASE_HEAD_FACES, "front", 0, 6, (58, 65, 37))


def add_air_assault_head(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	cap = shade(p.leather, 0.86)
	cap_dark = shade(p.leather, 0.58)
	lens_light = (151, 224, 218)
	lens_dark = (73, 154, 158)
	head_rect(draw, HAT_HEAD_FACES, "top", (0, 0, 7, 7), cap)
	head_rect(draw, HAT_HEAD_FACES, "back", (0, 0, 7, 7), cap_dark)
	for side in ("right", "left"):
		head_rect(draw, HAT_HEAD_FACES, side, (0, 0, 7, 5), cap)
	head_rect(draw, HAT_HEAD_FACES, "front", (0, 0, 7, 2), cap)
	head_rect(draw, HAT_HEAD_FACES, "front", (0, 3, 7, 3), cap_dark)
	# Large square aviator lenses deliberately cover the vanilla eyes; their highlights keep the face alive.
	for x in (1, 2, 5, 6):
		head_point(draw, HAT_HEAD_FACES, "front", x, 3, p.metal)
		head_point(draw, HAT_HEAD_FACES, "front", x, 5, p.metal)
	head_point(draw, HAT_HEAD_FACES, "front", 1, 4, lens_light)
	head_point(draw, HAT_HEAD_FACES, "front", 2, 4, lens_dark)
	head_point(draw, HAT_HEAD_FACES, "front", 5, 4, lens_light)
	head_point(draw, HAT_HEAD_FACES, "front", 6, 4, lens_dark)
	head_point(draw, HAT_HEAD_FACES, "front", 3, 4, p.metal)
	head_point(draw, HAT_HEAD_FACES, "front", 4, 4, p.metal)
	# Chin straps run down both cheeks but leave the mouth exposed.
	head_rect(draw, HAT_HEAD_FACES, "front", (0, 4, 0, 6), cap_dark)
	head_rect(draw, HAT_HEAD_FACES, "front", (7, 4, 7, 6), cap_dark)
	head_point(draw, HAT_HEAD_FACES, "front", 1, 7, cap_dark)
	head_point(draw, HAT_HEAD_FACES, "front", 6, 7, cap_dark)
	head_band(draw, HAT_HEAD_FACES, ("right", "left", "back"), 4, 4, p.accent)
	for x in (1, 3, 5):
		head_point(draw, HAT_HEAD_FACES, "back", x, 4, lens_dark)


def add_recruit(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	# A worn two-pixel sling and patched trousers keep the baseline recruit distinct without looking elite.
	for offset in (0, 1):
		put(draw, [(20 + index, 20 + index + offset) for index in range(8) if 20 + index + offset <= 31], p.accent)
		put(draw, [(39 - index, 20 + index + offset) for index in range(8) if 20 + index + offset <= 31], p.accent)
	put(draw, [(6, 25), (7, 25), (6, 26)], (91, 120, 66))
	put(draw, [(5, 27), (6, 27), (5, 28)], shade(p.accent, 0.72))
	add_recruit_head(draw, p)


def add_swordsman(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	band(draw, BODY_FACES, 1, p.accent)
	band(draw, ARM_FACES, 1, p.accent)
	# Slim vertical sword rune.
	put(draw, [(23, 21), (24, 21), (23, 22), (24, 22), (23, 23), (23, 24), (23, 25), (22, 26), (23, 25), (24, 26)], p.metal)
	put(draw, [(36, 21), (35, 22), (35, 23), (35, 24), (34, 25), (35, 24), (36, 25)], p.metal)
	band(draw, ARM_FACES, 5, p.accent)
	# Leather scabbard straps repeat on the rear and legs.
	put(draw, [(38, 22), (37, 23), (36, 24), (35, 25), (34, 26), (33, 27)], p.leather)
	band(draw, LEG_FACES, 5, shade(p.accent, 0.62))
	add_swordsman_head(draw, p)


def add_axeman(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	band(draw, BODY_FACES, 1, shade(p.accent, 0.85))
	band(draw, ARM_FACES, 1, p.accent)
	# Broad X/axe-head mark.
	put(draw, [(21, 22), (26, 22), (22, 23), (25, 23), (23, 24), (24, 24), (22, 25), (25, 25), (21, 26), (26, 26)], p.metal)
	put(draw, [(33, 22), (38, 22), (34, 23), (37, 23), (35, 24), (36, 24), (34, 25), (37, 25)], p.metal)
	band(draw, ARM_FACES, 6, p.leather)
	# Broad leather shoulder strap and reinforced knees sell the heavier weapon class.
	put(draw, [(39, 20), (38, 21), (37, 22), (36, 23), (35, 24), (34, 25), (33, 26)], p.leather)
	band(draw, LEG_FACES, 6, shade(p.leather, 0.72))
	add_axeman_head(draw, p)


def add_guard(draw: ImageDraw.ImageDraw, p: Palette, axe: bool) -> None:
	# Squared shoulder armour and a compact shield badge; axe guard adds an internal X.
	band(draw, BODY_FACES, 0, p.metal)
	band(draw, BODY_FACES, 1, shade(p.metal, 0.72))
	band(draw, ARM_FACES, 0, p.metal)
	band(draw, ARM_FACES, 1, shade(p.metal, 0.72))
	draw.rectangle((22, 22, 25, 26), outline=rgba(p.metal), fill=rgba(shade(p.shirt, 0.72)))
	draw.point((23, 27), fill=rgba(p.metal))
	draw.point((24, 27), fill=rgba(p.metal))
	if axe:
		put(draw, [(22, 22), (25, 22), (23, 23), (24, 23), (23, 25), (24, 25), (22, 26), (25, 26)], p.accent)
	else:
		put(draw, [(23, 23), (24, 23), (23, 24), (24, 24), (23, 25), (24, 25)], p.accent)
	# Back plate echoes the front without tiny detail.
	draw.rectangle((34, 22, 37, 26), outline=rgba(p.metal), fill=rgba(shade(p.shirt, 0.70)))
	band(draw, ARM_FACES, 4, p.metal)
	band(draw, LEG_FACES, 5, shade(p.metal, 0.62))
	add_guard_head(draw, p, axe)


def add_engineer(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	# Crossed harness, tool belt and hazard stripes.
	put(draw, [(20, 20), (21, 21), (22, 22), (23, 23), (24, 24), (25, 25), (26, 26), (27, 27)], p.leather)
	put(draw, [(27, 20), (26, 21), (25, 22), (24, 23), (23, 24), (22, 25), (21, 26), (20, 27)], p.leather)
	draw.rectangle((22, 25, 25, 27), fill=rgba(shade(p.shirt, 0.78)), outline=rgba(p.accent))
	put(draw, [(22, 25), (24, 25), (23, 26), (25, 26), (22, 27), (24, 27)], p.accent)
	for x in (16, 18, 28, 30, 32, 34, 36, 38):
		draw.point((x, 30), fill=rgba(p.accent if x % 4 == 0 else p.dark))
	band(draw, ARM_FACES, 3, p.accent)
	# Pouch, wrench glint and hard hat/goggles make the engineer readable from every angle.
	draw.rectangle((25, 27, 27, 29), fill=rgba(p.leather), outline=rgba(shade(p.accent, 0.76)))
	put(draw, [(36, 26), (35, 27), (34, 28), (35, 28)], p.metal)
	add_engineer_head(draw, p)


def add_water(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	# Cyan flotation straps and a square wave/ring badge.
	put(draw, [(20, 20), (21, 21), (22, 22), (22, 23), (22, 24), (22, 25), (22, 26)], p.accent)
	put(draw, [(27, 20), (26, 21), (25, 22), (25, 23), (25, 24), (25, 25), (25, 26)], p.accent)
	put(draw, [(22, 22), (23, 21), (24, 21), (25, 22), (22, 23), (25, 23), (22, 24), (25, 24), (23, 25), (24, 25)], shade(p.accent, 1.18))
	draw.rectangle((34, 21, 37, 27), outline=rgba(p.accent), fill=rgba(shade(p.shirt, 0.72)))
	band(draw, ARM_FACES, 3, p.accent)
	band(draw, LEG_FACES, 8, shade(p.accent, 0.70))
	put(draw, [(38, 22), (39, 23), (38, 24), (37, 25)], shade(p.accent, 0.88))
	add_water_head(draw, p)


def add_lava(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	# Heat-resistant orange seams and a compact pixel flame.
	band(draw, BODY_FACES, 0, shade(p.accent, 0.72))
	band(draw, ARM_FACES, 0, p.accent)
	band(draw, ARM_FACES, 3, shade(p.accent, 0.80))
	put(draw, [(24, 21), (23, 22), (24, 22), (23, 23), (24, 23), (25, 23), (22, 24), (23, 24), (24, 24), (25, 24), (23, 25), (24, 25)], p.accent)
	put(draw, [(24, 23), (23, 24), (24, 24)], (255, 185, 45))
	put(draw, [(35, 21), (35, 22), (34, 23), (35, 23), (36, 23), (34, 24), (35, 24), (36, 24), (35, 25)], p.accent)
	band(draw, LEG_FACES, 7, p.accent)
	# Heat-resistant apron seam and rear ember line.
	put(draw, [(20, 28), (21, 28), (26, 28), (27, 28), (33, 27), (34, 28), (35, 28)], shade(p.accent, 0.78))
	add_lava_head(draw, p)


def add_air_assault(draw: ImageDraw.ImageDraw, p: Palette) -> None:
	# Leather flight harness, pale scarf and goggles remain visible around an equipped elytra.
	band(draw, BODY_FACES, 0, p.accent)
	put(draw, [(20, 20), (21, 21), (22, 22), (23, 23), (24, 24), (25, 25), (26, 26), (27, 27)], p.leather)
	put(draw, [(27, 20), (26, 21), (25, 22), (24, 23), (23, 24), (22, 25), (21, 26), (20, 27)], p.leather)
	draw.rectangle((22, 24, 25, 26), outline=rgba(p.metal), fill=rgba(shade(p.shirt, 0.78)))
	band(draw, ARM_FACES, 4, p.leather)
	band(draw, LEG_FACES, 3, p.leather)
	put(draw, [(35, 27), (36, 27), (34, 28), (37, 28)], p.metal)
	add_air_assault_head(draw, p)


DECORATORS = {
	"recruit": add_recruit,
	"swordsman": add_swordsman,
	"axeman": add_axeman,
	"sword_guard": lambda draw, palette: add_guard(draw, palette, False),
	"axe_guard": lambda draw, palette: add_guard(draw, palette, True),
	"engineer": add_engineer,
	"water_support": add_water,
	"lava_harasser": add_lava,
	"air_assault": add_air_assault,
}


def generate_texture(base: Image.Image, name: str, palette: Palette) -> Image.Image:
	image = base.copy().convert("RGBA")
	clear_head_overlay(image)
	for box in SHIRT_RECTS:
		recolour_rect(image, box, palette.shirt, 122.0)
	for box in PANTS_RECTS:
		recolour_rect(image, box, palette.pants, 69.0)
	draw = ImageDraw.Draw(image)
	add_common_finish(draw, palette)
	DECORATORS[name](draw, palette)
	return image


def face(image: Image.Image, box: tuple[int, int, int, int]) -> Image.Image:
	return image.crop(box)


def model_preview(image: Image.Image, back: bool = False, scale: int = 5) -> Image.Image:
	canvas = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
	if back:
		head = face(image, (24, 8, 32, 16))
		hat = face(image, (56, 8, 64, 16))
		body = face(image, (32, 20, 40, 32))
		arm = face(image, (52, 20, 56, 32))
		leg = face(image, (12, 20, 16, 32))
	else:
		head = face(image, (8, 8, 16, 16))
		hat = face(image, (40, 8, 48, 16))
		body = face(image, (20, 20, 28, 32))
		arm = face(image, (44, 20, 48, 32))
		leg = face(image, (4, 20, 8, 32))
	canvas.alpha_composite(head, (4, 0))
	canvas.alpha_composite(hat, (4, 0))
	canvas.alpha_composite(body, (4, 8))
	canvas.alpha_composite(arm, (0, 8))
	canvas.alpha_composite(arm.transpose(Image.Transpose.FLIP_LEFT_RIGHT), (12, 8))
	canvas.alpha_composite(leg, (4, 20))
	canvas.alpha_composite(leg.transpose(Image.Transpose.FLIP_LEFT_RIGHT), (8, 20))
	return canvas.resize((16 * scale, 32 * scale), Image.Resampling.NEAREST)


def head_preview(image: Image.Image, scale: int = 9) -> Image.Image:
	"""Render the base face and expanded head layer together for native-pixel design review."""
	canvas = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
	canvas.alpha_composite(face(image, (8, 8, 16, 16)))
	canvas.alpha_composite(face(image, (40, 8, 48, 16)))
	return canvas.resize((8 * scale, 8 * scale), Image.Resampling.NEAREST)


def write_preview(textures: dict[str, Image.Image], output: Path) -> None:
	cell_width, cell_height = 380, 230
	preview = Image.new("RGB", (cell_width * 3, cell_height * 3), (35, 38, 42))
	draw = ImageDraw.Draw(preview)
	font = ImageFont.load_default(size=16)
	for index, (name, texture) in enumerate(textures.items()):
		column, row = index % 3, index // 3
		x, y = column * cell_width, row * cell_height
		draw.rounded_rectangle((x + 8, y + 8, x + cell_width - 8, y + cell_height - 8), 10, fill=(61, 66, 72))
		front = model_preview(texture, False, 5)
		back = model_preview(texture, True, 5)
		head = head_preview(texture)
		preview.paste(front, (x + 54, y + 42), front)
		preview.paste(back, (x + 166, y + 42), back)
		preview.paste(head, (x + 278, y + 58), head)
		draw.rectangle((x + 274, y + 54, x + 353, y + 133), outline=(93, 100, 108), width=2)
		draw.text((x + 18, y + 16), name.replace("_", " ").upper(), font=font, fill=(235, 238, 241))
		draw.text((x + 294, y + 140), "HEAD", font=font, fill=(178, 184, 191))
	output.parent.mkdir(parents=True, exist_ok=True)
	preview.save(output, optimize=True)


def main() -> None:
	parser = argparse.ArgumentParser()
	parser.add_argument("--base", type=Path, required=True, help="64x64 vanilla zombie.png extracted locally")
	parser.add_argument("--out", type=Path, required=True, help="texture output directory")
	parser.add_argument("--preview", type=Path, help="optional front/back contact sheet")
	args = parser.parse_args()

	base = Image.open(args.base).convert("RGBA")
	if base.size != (64, 64):
		raise SystemExit(f"Expected a 64x64 zombie texture, got {base.size}")
	args.out.mkdir(parents=True, exist_ok=True)
	textures: dict[str, Image.Image] = {}
	for name, palette in PALETTES.items():
		texture = generate_texture(base, name, palette)
		texture.save(args.out / f"{name}.png", optimize=True)
		textures[name] = texture
	if args.preview:
		write_preview(textures, args.preview)


if __name__ == "__main__":
	main()
