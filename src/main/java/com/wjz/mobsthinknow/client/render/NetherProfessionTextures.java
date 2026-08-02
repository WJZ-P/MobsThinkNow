package com.wjz.mobsthinknow.client.render;

import com.wjz.mobsthinknow.MobsThinkNow;
import com.wjz.mobsthinknow.ai.nether.NetherProfession;
import com.wjz.mobsthinknow.ai.nether.NetherProfessionFamily;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** 下界职业贴图的集中缓存，渲染热路径不会重复解析资源标识符。 */
public final class NetherProfessionTextures {
	private static final Map<NetherProfession, Identifier> PIGLIN = textures("piglin", NetherProfessionFamily.PIGLIN, "");
	private static final Map<NetherProfession, Identifier> PIGLIN_BRUTE = textures("piglin", NetherProfessionFamily.PIGLIN, "brute_");
	private static final Map<NetherProfession, Identifier> BLAZE = textures("blaze", NetherProfessionFamily.BLAZE, "");
	private static final Map<NetherProfession, Identifier> GHAST = textures("ghast", NetherProfessionFamily.GHAST, "");
	private static final Map<NetherProfession, Identifier> GHAST_SHOOTING = textures("ghast", NetherProfessionFamily.GHAST, "", "_shooting");
	private static final Map<NetherProfession, Identifier> HOGLIN = textures("hoglin", NetherProfessionFamily.HOGLIN, "");
	private static final Map<NetherProfession, Identifier> ZOGLIN = textures("zoglin", NetherProfessionFamily.HOGLIN, "");
	private static final Map<NetherProfession, Identifier> MAGMA_CUBE = textures("magma_cube", NetherProfessionFamily.MAGMA_CUBE, "");
	private static final Map<NetherProfession, Identifier> ZOMBIFIED_PIGLIN = textures(
		"zombified_piglin",
		NetherProfessionFamily.ZOMBIFIED_PIGLIN,
		""
	);
	private static final Map<NetherProfession, Identifier> WITHER_SKELETON = textures(
		"wither_skeleton",
		NetherProfessionFamily.WITHER_SKELETON,
		""
	);

	private NetherProfessionTextures() {
	}

	public static @Nullable Identifier piglin(final NetherProfession profession, final boolean brute) {
		return (brute ? PIGLIN_BRUTE : PIGLIN).get(profession);
	}

	public static @Nullable Identifier blaze(final NetherProfession profession) {
		return BLAZE.get(profession);
	}

	public static @Nullable Identifier ghast(final NetherProfession profession, final boolean shooting) {
		return (shooting ? GHAST_SHOOTING : GHAST).get(profession);
	}

	public static @Nullable Identifier hoglin(final NetherProfession profession, final boolean zoglin) {
		return (zoglin ? ZOGLIN : HOGLIN).get(profession);
	}

	public static @Nullable Identifier magmaCube(final NetherProfession profession) {
		return MAGMA_CUBE.get(profession);
	}

	public static @Nullable Identifier zombifiedPiglin(final NetherProfession profession) {
		return ZOMBIFIED_PIGLIN.get(profession);
	}

	public static @Nullable Identifier witherSkeleton(final NetherProfession profession) {
		return WITHER_SKELETON.get(profession);
	}

	private static Map<NetherProfession, Identifier> textures(
		final String familyDirectory,
		final NetherProfessionFamily family,
		final String prefix
	) {
		return textures(familyDirectory, family, prefix, "");
	}

	private static Map<NetherProfession, Identifier> textures(
		final String familyDirectory,
		final NetherProfessionFamily family,
		final String prefix,
		final String suffix
	) {
		EnumMap<NetherProfession, Identifier> result = new EnumMap<>(NetherProfession.class);
		for (NetherProfession profession : NetherProfession.values()) {
			String textureName = profession.textureName();
			if (profession.belongsTo(family) && textureName != null) {
				result.put(profession, Identifier.fromNamespaceAndPath(
					MobsThinkNow.MOD_ID,
					"textures/entity/nether/" + familyDirectory + "/" + prefix + textureName + suffix + ".png"
				));
			}
		}
		return Map.copyOf(result);
	}
}
