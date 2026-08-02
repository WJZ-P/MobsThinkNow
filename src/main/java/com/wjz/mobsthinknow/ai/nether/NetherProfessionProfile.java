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
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
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
		// 僵尸猪灵与凋灵骷髅必须先于宽泛的人形父类逻辑判断；它们拥有独立 UV 与战术。
		if (entity instanceof ZombifiedPiglin) {
			return NetherProfessionFamily.ZOMBIFIED_PIGLIN;
		}
		if (entity instanceof WitherSkeleton) {
			return NetherProfessionFamily.WITHER_SKELETON;
		}
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
		boolean specialistWeapon = mob instanceof Piglin && mob.isHolding(Items.CROSSBOW)
			|| mob instanceof ZombifiedPiglin && mob.getMainHandItem().has(DataComponents.KINETIC_WEAPON)
			|| mob instanceof WitherSkeleton && mob.isHolding(Items.BOW);
		boolean brute = mob instanceof PiglinBrute;
		NetherProfession profession = choose(
			family,
			specialistWeapon,
			brute,
			difficulty.getDifficulty().getId(),
			random.nextDouble()
		);
		set(mob, profession);
		applySpawnLoadout(mob, profession);
		return profession;
	}

	/**
	 * 这两类实体在 {@link Mob#finalizeSpawn} 返回后才由子类生成武器，因此公共生命周期注入必须
	 * 等到 Zombie/AbstractSkeleton 的真实出生尾部再分配，避免把尚未出现的矛或弓误判成近战职业。
	 */
	public static boolean requiresLateSpawnAssignment(final Mob mob) {
		return mob instanceof ZombifiedPiglin || mob instanceof WitherSkeleton;
	}

	/** 纯分配函数：难度越高，精英职业占比越高。 */
	static NetherProfession choose(
		final NetherProfessionFamily family,
		final boolean specialistWeapon,
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
				if (specialistWeapon && !brute) {
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
			case ZOMBIFIED_PIGLIN -> {
				if (specialistWeapon) {
					yield NetherProfession.ZOMBIFIED_PIGLIN_LANCER;
				}
				yield boundedRoll < eliteChance
					? NetherProfession.ZOMBIFIED_PIGLIN_WARCALLER
					: NetherProfession.ZOMBIFIED_PIGLIN_BERSERKER;
			}
			case WITHER_SKELETON -> {
				if (specialistWeapon) {
					yield NetherProfession.WITHER_SKELETON_HEXER;
				}
				// 远程凋灵箭手是稀有精英：困难约 21%，不会让堡垒走廊被弓手淹没。
				yield boundedRoll < eliteChance * 0.65
					? NetherProfession.WITHER_SKELETON_HEXER
					: boundedRoll < specialistCutoff
						? NetherProfession.WITHER_SKELETON_DUELIST
						: NetherProfession.WITHER_SKELETON_REAPER;
			}
			case NONE -> NetherProfession.NONE;
		};
	}

	/** 只在新出生时按职业冻结武器；读旧存档绝不覆盖玩家或其他 Mod 已经修改过的装备。 */
	private static void applySpawnLoadout(final Mob mob, final NetherProfession profession) {
		// 僵尸猪灵直接按原版已生成的金剑/金矛分类，不需要覆写装备；这样也兼容其他 Mod 的动能武器。
		if (mob instanceof WitherSkeleton
			&& profession == NetherProfession.WITHER_SKELETON_HEXER
			&& !mob.isHolding(Items.BOW)) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}
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
			case ZOMBIFIED_PIGLIN -> entity instanceof Mob mob
				&& mob.getMainHandItem().has(DataComponents.KINETIC_WEAPON)
					? NetherProfession.ZOMBIFIED_PIGLIN_LANCER
					: NetherProfession.ZOMBIFIED_PIGLIN_BERSERKER;
			case WITHER_SKELETON -> entity instanceof Mob mob && mob.isHolding(Items.BOW)
				? NetherProfession.WITHER_SKELETON_HEXER
				: NetherProfession.WITHER_SKELETON_REAPER;
			case NONE -> NetherProfession.NONE;
		};
	}
}
