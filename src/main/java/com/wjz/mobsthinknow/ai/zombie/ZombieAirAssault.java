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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/** 持矛空袭僵尸的生成装备、身份识别和地面战斗边界。 */
public final class ZombieAirAssault {
	public static final int MINIMUM_ROCKETS = 16;
	public static final int MAXIMUM_ROCKETS = 64;
	private static final String ROCKET_EFFICIENCY_TAG = "MobsThinkNowRocketEfficiency";

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

	/**
	 * 把本次推进效率写入发射出去的单枚烟花，而不是只读服务端全局配置。
	 * DATA_ID_FIREWORKS_ITEM 会把该组件同步给客户端，因此专用服务器两端使用完全相同的推进值，
	 * 不会因客户端本地配置不同产生飞行预测抖动；僵尸副手里的剩余整组烟花保持原样。
	 */
	public static void markRocketEfficiency(final ItemStack firedRocket, final double efficiency) {
		double bounded = sanitizeRocketEfficiency(efficiency);
		CustomData.update(
			DataComponents.CUSTOM_DATA,
			firedRocket,
			tag -> tag.putDouble(ROCKET_EFFICIENCY_TAG, bounded)
		);
	}

	/** 只有本 Mod 发射并带同步标记的烟花才改写推进；玩家与其他实体的原版烟花保持 1.0。 */
	public static boolean hasMarkedRocketEfficiency(final ItemStack firedRocket) {
		CustomData data = firedRocket.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(ROCKET_EFFICIENCY_TAG);
	}

	public static double markedRocketEfficiency(final ItemStack firedRocket) {
		CustomData data = firedRocket.get(DataComponents.CUSTOM_DATA);
		return data == null
			? 1.0
			: sanitizeRocketEfficiency(data.copyTag().getDoubleOr(ROCKET_EFFICIENCY_TAG, 1.0));
	}

	/**
	 * 原版单 tick 推进为 {@code v += look*0.1 + (look*1.5-v)*0.5}，稳定速度约为 1.7。
	 * 这里同时按效率缩放吸引强度、目标速度和附加推力，使稳定速度严格线性缩放：
	 * 效率 0.5 时约为 0.85，而效率 1.0 与原版公式逐项相同。
	 */
	public static Vec3 rocketBoostMovement(
		final Vec3 currentMovement,
		final Vec3 lookDirection,
		final double efficiency
	) {
		double bounded = sanitizeRocketEfficiency(efficiency);
		double attraction = 0.5 * bounded;
		double targetSpeed = 1.5 * bounded;
		double additiveThrust = 0.1 * bounded * bounded;
		return currentMovement.add(
			lookDirection.scale(additiveThrust)
				.add(lookDirection.scale(targetSpeed).subtract(currentMovement).scale(attraction))
		);
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

	private static double sanitizeRocketEfficiency(final double efficiency) {
		if (!Double.isFinite(efficiency)) {
			return MobsThinkNowConfig.DEFAULT_SPEAR_ROCKET_EFFICIENCY;
		}
		return Math.clamp(
			efficiency,
			MobsThinkNowConfig.MINIMUM_SPEAR_ROCKET_EFFICIENCY,
			MobsThinkNowConfig.MAXIMUM_SPEAR_ROCKET_EFFICIENCY
		);
	}
}
