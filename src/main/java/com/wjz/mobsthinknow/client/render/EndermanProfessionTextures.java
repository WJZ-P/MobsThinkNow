package com.wjz.mobsthinknow.client.render;

import com.wjz.mobsthinknow.MobsThinkNow;
import com.wjz.mobsthinknow.ai.enderman.EndermanProfession;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** 末影人职业到原生 64×32 UV 贴图的固定路由。 */
public final class EndermanProfessionTextures {
	private static final Map<EndermanProfession, Identifier> TEXTURES = createTextures();

	private EndermanProfessionTextures() {
	}

	public static @Nullable Identifier texture(final EndermanProfession profession) {
		return TEXTURES.get(profession);
	}

	private static Map<EndermanProfession, Identifier> createTextures() {
		EnumMap<EndermanProfession, Identifier> textures = new EnumMap<>(EndermanProfession.class);
		for (EndermanProfession profession : EndermanProfession.values()) {
			String textureName = profession.textureName();
			if (textureName != null) {
				textures.put(
					profession,
					Identifier.fromNamespaceAndPath(
						MobsThinkNow.MOD_ID,
						"textures/entity/enderman/" + textureName + ".png"
					)
				);
			}
		}
		return Collections.unmodifiableMap(textures);
	}
}
