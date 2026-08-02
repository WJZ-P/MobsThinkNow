package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.ai.zombie.squad.WeaponClass;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ZombieProfessionTest {
	@Test
	void classifierUsesStableTacticalPriority() {
		assertEquals(ZombieProfession.VANILLA, choose(false, true, UtilityClass.WATER, true, true, WeaponClass.AXE));
		assertEquals(ZombieProfession.AIR_ASSAULT, choose(true, true, UtilityClass.WATER, true, true, WeaponClass.AXE));
		assertEquals(ZombieProfession.WATER_SUPPORT, choose(true, false, UtilityClass.WATER, true, true, WeaponClass.AXE));
		assertEquals(ZombieProfession.LAVA_HARASSER, choose(true, false, UtilityClass.LAVA, true, true, WeaponClass.AXE));
		assertEquals(ZombieProfession.ENGINEER, choose(true, false, UtilityClass.NONE, true, true, WeaponClass.AXE));
		assertEquals(ZombieProfession.AXE_GUARD, choose(true, false, UtilityClass.NONE, false, true, WeaponClass.AXE));
		assertEquals(ZombieProfession.SWORD_GUARD, choose(true, false, UtilityClass.NONE, false, true, WeaponClass.SWORD));
		assertEquals(ZombieProfession.AXEMAN, choose(true, false, UtilityClass.NONE, false, false, WeaponClass.AXE));
		assertEquals(ZombieProfession.SWORDSMAN, choose(true, false, UtilityClass.NONE, false, false, WeaponClass.SWORD));
		assertEquals(ZombieProfession.RECRUIT, choose(true, false, UtilityClass.NONE, false, false, WeaponClass.NONE));
	}

	@Test
	void invalidSyncedIdsFallBackToVanilla() {
		assertEquals(ZombieProfession.VANILLA, ZombieProfession.fromId(-1));
		assertEquals(ZombieProfession.VANILLA, ZombieProfession.fromId(127));
		for (ZombieProfession profession : ZombieProfession.values()) {
			assertEquals(profession, ZombieProfession.fromId(profession.id()));
		}
	}

	@Test
	void everyRenderedProfessionHasAUniqueNativeResolutionTexture() throws IOException {
		Set<Integer> fingerprints = new HashSet<>();
		for (ZombieProfession profession : ZombieProfession.values()) {
			String textureName = profession.textureName();
			if (textureName == null) {
				continue;
			}
			String path = "/assets/mobsthinknow/textures/entity/zombie/profession/" + textureName + ".png";
			try (InputStream stream = ZombieProfessionTest.class.getResourceAsStream(path)) {
				assertNotNull(stream, "Missing profession texture " + path);
				BufferedImage image = ImageIO.read(stream);
				assertNotNull(image, "Unreadable profession texture " + path);
				assertEquals(64, image.getWidth(), path + " width");
				assertEquals(64, image.getHeight(), path + " height");
				int[] pixels = image.getRGB(0, 0, 64, 64, null, 0, 64);
				assertTrue(fingerprints.add(Arrays.hashCode(pixels)), "Duplicate profession texture " + path);
			}
		}
		assertEquals(9, fingerprints.size());
	}

	@Test
	void everyProfessionHasDistinctVisibleHeadLayerDetail() throws IOException {
		Set<Integer> headFingerprints = new HashSet<>();
		for (ZombieProfession profession : ZombieProfession.values()) {
			String textureName = profession.textureName();
			if (textureName == null) {
				continue;
			}
			String path = "/assets/mobsthinknow/textures/entity/zombie/profession/" + textureName + ".png";
			try (InputStream stream = ZombieProfessionTest.class.getResourceAsStream(path)) {
				assertNotNull(stream, "Missing profession texture " + path);
				BufferedImage image = ImageIO.read(stream);
				assertNotNull(image, "Unreadable profession texture " + path);
				int[] headPixels = image.getRGB(0, 0, 64, 16, null, 0, 64);
				assertTrue(headFingerprints.add(Arrays.hashCode(headPixels)), "Duplicate profession head " + path);

				int visibleOverlayPixels = 0;
				for (int y = 0; y < 16; y++) {
					for (int x = 32; x < 64; x++) {
						if ((image.getRGB(x, y) >>> 24) != 0) {
							visibleOverlayPixels++;
						}
					}
				}
				assertTrue(visibleOverlayPixels >= 16, path + " needs readable second-layer head detail");
			}
		}
		assertEquals(9, headFingerprints.size());
	}

	private static ZombieProfession choose(
		final boolean ordinary,
		final boolean air,
		final UtilityClass utility,
		final boolean engineer,
		final boolean shield,
		final WeaponClass weapon
	) {
		return ZombieProfession.choose(ordinary, air, utility, engineer, shield, weapon);
	}
}
