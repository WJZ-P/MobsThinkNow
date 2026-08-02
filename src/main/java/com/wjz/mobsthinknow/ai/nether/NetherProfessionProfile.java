package com.wjz.mobsthinknow.ai.nether;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** 下界职业的出生分配、兼容检查、同步访问和存档入口。 */
public final class NetherProfessionProfile {
	private static final String PROFESSION_TAG = "MobsThinkNowNetherProfession";

	private NetherProfessionProfile() {
	}

	public static boolean supports(final Entity entity) {
		return familyOf(entity) != NetherProfessionFamily.NONE;
	}

	public static NetherProfessionFamily familyOf(final Entity entity) {
		if (entity instanceof AbstractPiglin) {
			return NetherProfessionFamily.PIGLIN;
		}
		if (entity instanceof Blaze) {
			return NetherProfessionFamily.BLAZE;
		}
		if (entity instanceof Ghast) {
			return NetherProfessionFamily.GHAST;
		}
		if (entity instanceof Hoglin || entity instanceof Zoglin) {
			return NetherProfessionFamily.HOGLIN;
		}
		if (entity instanceof MagmaCube) {
			return NetherProfessionFamily.MAGMA_CUBE;
		}
		return NetherProfessionFamily.NONE;
	}

	public static NetherProfession get(final Entity entity) {
		return entity instanceof NetherProfessionAccess access
			? access.mobsthinknow$getNetherProfession()
			: NetherProfession.NONE;
	}

	/**
	 * 强制职业仅用于出生分配和测试指令；不接受跨模型职业，避免客户端选到错误 UV 贴图。
	 */
	public static void set(final Entity entity, final NetherProfession profession) {
		if (!(entity instanceof NetherProfessionAccess access)) {
			return;
		}
		NetherProfession safe = profession == null ? NetherProfession.NONE : profession;
		NetherProfessionFamily family = familyOf(entity);
		if (safe != NetherProfession.NONE && !safe.belongsTo(family)) {
			throw new IllegalArgumentException("Profession " + safe + " is incompatible with " + entity.getType());
		}
		access.mobsthinknow$setNetherProfession(safe);
	}

	/** 在原版完成出生装备与体型选择之后调用一次。 */
	public static NetherProfession assignOnSpawn(
		final Mob mob,
		final DifficultyInstance difficulty,
		final RandomSource random
	) {
		NetherProfessionFamily family = familyOf(mob);
		if (family == NetherProfessionFamily.NONE) {
			return NetherProfession.NONE;
		}
		boolean rangedPiglin = mob instanceof Piglin && mob.isHolding(Items.CROSSBOW);
		boolean brute = mob instanceof PiglinBrute;
		NetherProfession profession = choose(
			family,
			rangedPiglin,
			brute,
			difficulty.getDifficulty().getId(),
			random.nextDouble()
		);
		set(mob, profession);
		return profession;
	}

	/** 纯分配函数：难度越高，精英职业占比越高。 */
	static NetherProfession choose(
		final NetherProfessionFamily family,
		final boolean rangedPiglin,
		final boolean brute,
		final int difficultyId,
		final double roll
	) {
		int difficulty = Mth.clamp(difficultyId, 1, 3);
		double eliteChance = switch (difficulty) {
			case 1 -> 0.08;
			case 2 -> 0.18;
			default -> 0.32;
		};
		double specialistCutoff = switch (difficulty) {
			case 1 -> 0.32;
			case 2 -> 0.46;
			default -> 0.60;
		};
		double boundedRoll = Mth.clamp(roll, 0.0, 1.0);

		return switch (family) {
			case PIGLIN -> {
				if (rangedPiglin && !brute) {
					yield NetherProfession.PIGLIN_MARKSMAN;
				}
				yield boundedRoll < eliteChance
					? NetherProfession.PIGLIN_COMMANDER
					: NetherProfession.PIGLIN_VANGUARD;
			}
			case BLAZE -> boundedRoll < eliteChance
				? NetherProfession.BLAZE_VOLLEYMASTER
				: boundedRoll < specialistCutoff
					? NetherProfession.BLAZE_CINDER_GUARD
					: NetherProfession.BLAZE_SKIRMISHER;
			case GHAST -> boundedRoll < eliteChance
				? NetherProfession.GHAST_SIEGEBREAKER
				: boundedRoll < specialistCutoff
					? NetherProfession.GHAST_SPOTTER
					: NetherProfession.GHAST_ARTILLERY;
			case HOGLIN -> boundedRoll < eliteChance
				? NetherProfession.HOGLIN_RAVAGER
				: boundedRoll < specialistCutoff
					? NetherProfession.HOGLIN_BULWARK
					: NetherProfession.HOGLIN_CHARGER;
			case MAGMA_CUBE -> boundedRoll < eliteChance
				? NetherProfession.MAGMA_TITAN
				: boundedRoll < specialistCutoff
					? NetherProfession.MAGMA_AMBUSHER
					: NetherProfession.MAGMA_HUNTER;
			case NONE -> NetherProfession.NONE;
		};
	}

	public static void save(final Entity entity, final ValueOutput output) {
		NetherProfession profession = get(entity);
		if (profession != NetherProfession.NONE) {
			output.putByte(PROFESSION_TAG, profession.id());
		}
	}

	public static void load(final Entity entity, final ValueInput input) {
		byte saved = input.getByteOr(PROFESSION_TAG, (byte)-1);
		NetherProfession profession = saved >= 0
			? NetherProfession.fromId(saved)
			: fallbackFor(entity);
		if (profession == NetherProfession.NONE || profession.belongsTo(familyOf(entity))) {
			set(entity, profession);
		} else {
			set(entity, fallbackFor(entity));
		}
	}

	/** 旧存档没有职业字段时使用稳定且不改变装备的基础身份。 */
	private static NetherProfession fallbackFor(final Entity entity) {
		return switch (familyOf(entity)) {
			case PIGLIN -> entity instanceof Piglin && ((Mob)entity).isHolding(Items.CROSSBOW)
				? NetherProfession.PIGLIN_MARKSMAN
				: NetherProfession.PIGLIN_VANGUARD;
			case BLAZE -> NetherProfession.BLAZE_SKIRMISHER;
			case GHAST -> NetherProfession.GHAST_ARTILLERY;
			case HOGLIN -> NetherProfession.HOGLIN_CHARGER;
			case MAGMA_CUBE -> NetherProfession.MAGMA_HUNTER;
			case NONE -> NetherProfession.NONE;
		};
	}
}
