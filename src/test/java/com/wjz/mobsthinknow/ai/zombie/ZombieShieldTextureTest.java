package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ZombieShieldTextureTest {
	@Test
	void shieldHeadMaskIsAnEightByEightSolidSquare() throws IOException {
		BufferedImage image = resource("/assets/mobsthinknow/textures/entity/shield/zombie_head.png");
		assertSolidRectangle(image, 3, 6, 8, 8);
	}

	@Test
	void bannerHeadMaskUsesTheSameSquareAtThreeTimesScale() throws IOException {
		BufferedImage image = resource("/assets/mobsthinknow/textures/entity/banner/zombie_head.png");
		assertSolidRectangle(image, 9, 5, 24, 24);
	}

	private static BufferedImage resource(final String path) throws IOException {
		try (InputStream input = ZombieShieldTextureTest.class.getResourceAsStream(path)) {
			assertNotNull(input, "Missing texture resource: " + path);
			return ImageIO.read(input);
		}
	}

	private static void assertSolidRectangle(
		final BufferedImage image,
		final int expectedX,
		final int expectedY,
		final int expectedWidth,
		final int expectedHeight
	) {
		int minimumX = Integer.MAX_VALUE;
		int minimumY = Integer.MAX_VALUE;
		int maximumX = Integer.MIN_VALUE;
		int maximumY = Integer.MIN_VALUE;
		int opaquePixels = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) >>> 24) == 0) {
					continue;
				}
				opaquePixels++;
				minimumX = Math.min(minimumX, x);
				minimumY = Math.min(minimumY, y);
				maximumX = Math.max(maximumX, x);
				maximumY = Math.max(maximumY, y);
			}
		}

		assertEquals(expectedX, minimumX);
		assertEquals(expectedY, minimumY);
		assertEquals(expectedX + expectedWidth - 1, maximumX);
		assertEquals(expectedY + expectedHeight - 1, maximumY);
		assertEquals(expectedWidth * expectedHeight, opaquePixels);
		assertTrue((image.getRGB(expectedX, expectedY) >>> 24) > 0, "Top-left corner was rounded away.");
		assertTrue(
			(image.getRGB(maximumX, maximumY) >>> 24) > 0,
			"Bottom-right corner was rounded away."
		);
	}
}
