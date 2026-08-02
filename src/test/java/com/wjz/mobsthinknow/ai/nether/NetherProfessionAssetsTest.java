package com.wjz.mobsthinknow.ai.nether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class NetherProfessionAssetsTest {
	@Test
	void allTwentyFourProfessionTexturesExistWithTheVanillaAtlasDimensions() throws IOException {
		List<ExpectedTexture> expected = expectedTextures();
		assertEquals(24, expected.size());
		for (ExpectedTexture texture : expected) {
			String resource = "/assets/mobsthinknow/textures/entity/nether/" + texture.path();
			try (InputStream stream = NetherProfessionAssetsTest.class.getResourceAsStream(resource)) {
				assertNotNull(stream, "Missing profession texture " + resource);
				BufferedImage image = ImageIO.read(stream);
				assertNotNull(image, "Unreadable profession texture " + resource);
				assertEquals(texture.width(), image.getWidth(), "Wrong atlas width for " + resource);
				assertEquals(texture.height(), image.getHeight(), "Wrong atlas height for " + resource);
			}
		}
	}

	private static List<ExpectedTexture> expectedTextures() {
		List<ExpectedTexture> result = new ArrayList<>();
		for (NetherProfession profession : NetherProfession.values()) {
			String name = profession.textureName();
			if (name == null) {
				continue;
			}
			switch (profession.family()) {
				case PIGLIN -> {
					result.add(new ExpectedTexture("piglin/" + name + ".png", 64, 64));
					result.add(new ExpectedTexture("piglin/brute_" + name + ".png", 64, 64));
				}
				case BLAZE -> result.add(new ExpectedTexture("blaze/" + name + ".png", 64, 32));
				case GHAST -> {
					result.add(new ExpectedTexture("ghast/" + name + ".png", 128, 64));
					result.add(new ExpectedTexture("ghast/" + name + "_shooting.png", 128, 64));
				}
				case HOGLIN -> {
					result.add(new ExpectedTexture("hoglin/" + name + ".png", 128, 64));
					result.add(new ExpectedTexture("zoglin/" + name + ".png", 128, 64));
				}
				case MAGMA_CUBE -> result.add(new ExpectedTexture("magma_cube/" + name + ".png", 64, 64));
				case NONE -> {
				}
			}
		}
		return List.copyOf(result);
	}

	private record ExpectedTexture(String path, int width, int height) {
	}
}
