package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.DropChances;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 持矛空袭僵尸的生成装备、身份识别和地面战斗边界。 */
public final class ZombieAirAssault {
	public static final int MINIMUM_ROCKETS = 16;
	public static final int MAXIMUM_ROCKETS = 64;

	private ZombieAirAssault() {
	}

	/**
	 * 原版自然装备已经在 {@code finalizeSpawn} 的本 Mod 尾部注入前生成完毕。
	 * 因此只要此时检测到矛，就把胸甲和副手明确改造成完整空袭套装。
	 */
	public static boolean equipForSpawn(
		final Zombie zombie,
		final Difficulty difficulty,
		final RandomSource random,
		final MobsThinkNowConfig config
	) {
		if (!isEnabled(config)
			|| zombie.getType() != EntityType.ZOMBIE
			|| !isSpear(zombie.getMainHandItem())) {
			return false;
		}

		zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
		zombie.setItemSlot(
			EquipmentSlot.OFFHAND,
			new ItemStack(Items.FIREWORK_ROCKET, rocketCount(difficulty, random.nextDouble()))
		);
		// 与原版 Mob 自带装备一致：装备和剩余火箭会自动持久化，死亡时各槽保持 8.5% 默认掉落率。
		zombie.setDropChance(EquipmentSlot.CHEST, DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE);
		zombie.setDropChance(EquipmentSlot.OFFHAND, DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE);
		return true;
	}

	/**
	 * 所有难度都覆盖 16～64 的同一合法区间，但高难度通过更小的幂指数把分布向上推。
	 * 这样不是简单抬高下限：简单难度仍可能出现满载精英，困难难度也仍可能出现弹药不足的个体。
	 */
	public static int rocketCount(final Difficulty difficulty, final double roll) {
		double boundedRoll = Double.isFinite(roll) ? Math.clamp(roll, 0.0, 1.0) : 0.0;
		double exponent = switch (difficulty) {
			case PEACEFUL -> 2.20;
			case EASY -> 1.80;
			case NORMAL -> 1.00;
			case HARD -> 0.60;
		};
		int spread = MAXIMUM_ROCKETS - MINIMUM_ROCKETS;
		return MINIMUM_ROCKETS + (int)Math.round(spread * Math.pow(boundedRoll, exponent));
	}

	public static boolean isSpear(final ItemStack stack) {
		return !stack.isEmpty()
			&& (stack.is(ItemTags.SPEARS) || stack.has(DataComponents.KINETIC_WEAPON));
	}

	public static boolean hasRockets(final Zombie zombie) {
		return zombie.getOffhandItem().is(Items.FIREWORK_ROCKET)
			&& !zombie.getOffhandItem().isEmpty();
	}

	public static boolean hasUsableGlider(final Zombie zombie) {
		return LivingEntity.canGlideUsing(zombie.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST);
	}

	/** 即使弹药刚好耗尽，也保留这只僵尸的空袭兵身份，确保它先安全落地再切回地面突刺。 */
	public static boolean isAirAssaultLoadout(final Zombie zombie) {
		return zombie.getType() == EntityType.ZOMBIE
			&& isSpear(zombie.getMainHandItem())
			&& zombie.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER);
	}

	public static boolean isFlightReady(final Zombie zombie, final MobsThinkNowConfig config) {
		return isEnabled(config)
			&& isAirAssaultLoadout(zombie)
			&& hasUsableGlider(zombie)
			&& hasRockets(zombie);
	}

	/**
	 * 有弹药时绝不在地面近战；最后一枚火箭用完后，飞行 Goal 仍独占 MOVE/LOOK 直至着地。
	 * 鞘翅损坏视为飞行能力耗尽，避免带着火箭在地面永久发呆。
	 */
	public static boolean suppressGroundCombat(final Zombie zombie, final MobsThinkNowConfig config) {
		return isFlightReady(zombie, config)
			|| isEnabled(config) && isAirAssaultLoadout(zombie) && (!zombie.onGround() || zombie.isFallFlying());
	}

	public static boolean isEnabled(final MobsThinkNowConfig config) {
		return config.enabled && config.zombieAiEnabled && config.spearAirAssault;
	}
}
