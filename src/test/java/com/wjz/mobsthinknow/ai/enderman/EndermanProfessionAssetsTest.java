package com.wjz.mobsthinknow.ai.enderman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class EndermanProfessionAssetsTest {
	@Test
	void everyProfessionHasOneNativeEndermanTexture() throws Exception {
		long expected = java.util.Arrays.stream(EndermanProfession.values())
			.filter(profession -> profession != EndermanProfession.NONE)
			.count();
		long found = 0;
		for (EndermanProfession profession : EndermanProfession.values()) {
			if (profession == EndermanProfession.NONE) {
				continue;
			}
			String resource = "/assets/mobsthinknow/textures/entity/enderman/" + profession.textureName() + ".png";
			try (InputStream stream = EndermanProfessionAssetsTest.class.getResourceAsStream(resource)) {
				assertNotNull(stream, "Missing profession texture: " + resource);
				BufferedImage image = ImageIO.read(stream);
				assertNotNull(image, "Unreadable profession texture: " + resource);
				assertEquals(64, image.getWidth());
				assertEquals(32, image.getHeight());
				boolean hasVisiblePixel = false;
				for (int y = 0; y < image.getHeight() && !hasVisiblePixel; y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						if ((image.getRGB(x, y) >>> 24) != 0) {
							hasVisiblePixel = true;
							break;
						}
					}
				}
				assertTrue(hasVisiblePixel, "Profession texture is fully transparent: " + resource);
				found++;
			}
		}
		assertEquals(expected, found);
	}
}
