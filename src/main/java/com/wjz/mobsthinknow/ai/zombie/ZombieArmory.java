package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.WeaponClass;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.Vec3;

/**
 * 武装小队（armedSquads，默认关闭）的装备与"攻击方式"层。
 *
 * <p>原版普通僵尸只有 1%（困难 5%）概率持械。开启后按世界难度放大这一概率，
 * 并保持原版 8.5% 的装备掉落率，不给玩家刷装备的额外途径。</p>
 */
public final class ZombieArmory {
	private ZombieArmory() {
	}

	/**
	 * 在 {@code finalizeSpawn} 尾部调用。只补空手僵尸，不覆盖原版或其他 Mod 已发放的武器。
	 * 区域难度（{@link DifficultyInstance#getSpecialMultiplier()}）越成熟，实际概率越接近配置值。
	 */
	public static void maybeEquipForSquad(
		final Zombie zombie,
		final DifficultyInstance difficulty,
		final RandomSource random,
		final MobsThinkNowConfig config
	) {
		// 服从总开关：enabled/zombieAiEnabled 关闭时，武装系统即使单独开着也不生效。
		if (!config.enabled || !config.zombieAiEnabled || !config.armedSquads) {
			return;
		}
		if (zombie.isBaby() || !zombie.getMainHandItem().isEmpty()) {
			return;
		}

		double chance = switch (difficulty.getDifficulty()) {
			case PEACEFUL -> 0.0;
			case EASY -> config.armedChanceEasy;
			case NORMAL -> config.armedChanceNormal;
			case HARD -> config.armedChanceHard;
		};
		chance *= 0.6 + 0.4 * difficulty.getSpecialMultiplier();
		if (random.nextDouble() >= chance) {
			return;
		}

		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(rollWeapon(random, difficulty.getDifficulty())));

		// 持械者才有资格配盾：简单难度不发，普通减半，困难全额概率。
		// 持有 KINETIC_WEAPON（矛类）的僵尸不发盾：原版 SpearUseGoal 独占 useItem，盾会变成死物。
		double shieldChance = switch (difficulty.getDifficulty()) {
			case PEACEFUL, EASY -> 0.0;
			case NORMAL -> config.armedShieldChance * 0.5;
			case HARD -> config.armedShieldChance;
		};
		if (zombie.getOffhandItem().isEmpty()
			&& !zombie.getMainHandItem().has(DataComponents.KINETIC_WEAPON)
			&& random.nextDouble() < shieldChance) {
			zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		}
	}

	/** 副手是否持有可格挡的盾类物品（按 BLOCKS_ATTACKS 组件识别，兼容模组盾）。 */
	public static boolean hasShield(final Zombie zombie) {
		return zombie.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	/** 被斧击破盾的僵尸 → 恢复时间。原版盾牌禁用冷却只对玩家生效，这里给怪物补上对称机制。 */
	private static final Map<Integer, Long> SHIELD_DISABLED_UNTIL = new HashMap<>();

	/**
	 * ALLOW_DAMAGE 入口：任何生物用斧头攻击举盾僵尸时，先打掉它的盾并进入禁用窗口，
	 * 本次斧击伤害因此正常结算。没有这条对称路径，怪物之盾在正面将无法被任何手段破除。
	 */
	public static void onZombieAttacked(final Zombie zombie, final DamageSource source, final MobsThinkNowConfig config) {
		if (!config.armedSquads || config.armedShieldBreakSeconds <= 0.0) {
			return;
		}
		if (zombie.getItemBlockingWith() == null) {
			return;
		}
		if (!(source.getEntity() instanceof LivingEntity attacker)
			|| attacker == zombie
			|| !attacker.getMainHandItem().is(ItemTags.AXES)) {
			return;
		}

		zombie.stopUsingItem();
		long now = zombie.level().getGameTime();
		SHIELD_DISABLED_UNTIL.put(zombie.getId(), now + (long)(config.armedShieldBreakSeconds * 20.0));
		if (SHIELD_DISABLED_UNTIL.size() > 256) {
			SHIELD_DISABLED_UNTIL.values().removeIf(until -> until <= now);
		}
	}

	/** 盾卫 AI 在禁用窗口内不允许重新举盾。 */
	public static boolean isShieldDisabled(final Zombie zombie) {
		Long until = SHIELD_DISABLED_UNTIL.get(zombie.getId());
		return until != null && zombie.level().getGameTime() < until;
	}

	/** 服务器停止时清空禁用表，避免同一 JVM 内切换存档后实体 ID 撞车。 */
	public static void clearShieldState() {
		SHIELD_DISABLED_UNTIL.clear();
	}

	/** 通过物品标签识别兵种，兼容其他 Mod 加入 swords/axes/spears 标签的自定义武器。 */
	public static WeaponClass weaponClassOf(final ItemStack stack) {
		if (stack.isEmpty()) {
			return WeaponClass.NONE;
		}
		if (stack.is(ItemTags.AXES)) {
			return WeaponClass.AXE;
		}
		if (stack.is(ItemTags.SPEARS)) {
			return WeaponClass.SPEAR;
		}
		if (stack.is(ItemTags.SWORDS)) {
			return WeaponClass.SWORD;
		}
		return WeaponClass.NONE;
	}

	/**
	 * 斧手命中格挡目标后触发原版盾牌禁用。26.1.2 的
	 * {@code LivingEntity.getSecondsToDisableBlocking} 要求武器是攻击者的 activeItem，
	 * 怪物普通挥击不满足，所以这里显式走 {@link BlocksAttacks#disable} 补上这条链路。
	 *
	 * <p>与原版语义对齐：只有盾牌真正挡下的攻击才禁用——攻击必须来自目标视线的正面半球
	 * （对应 {@code horizontal_blocking_angle} 默认 90°），创造/旁观玩家不受影响。</p>
	 */
	public static void tryBreakShield(final Zombie zombie, final LivingEntity target, final MobsThinkNowConfig config) {
		if (!config.enabled || !config.zombieAiEnabled || !config.armedSquads || config.armedShieldBreakSeconds <= 0.0) {
			return;
		}
		if (weaponClassOf(zombie.getMainHandItem()) != WeaponClass.AXE) {
			return;
		}
		if (!(zombie.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return;
		}
		if (!isWithinShieldArc(zombie, target)) {
			return;
		}

		ItemStack blockingWith = target.getItemBlockingWith();
		if (blockingWith == null) {
			return;
		}
		BlocksAttacks blocksAttacks = blockingWith.get(DataComponents.BLOCKS_ATTACKS);
		if (blocksAttacks != null) {
			blocksAttacks.disable(serverLevel, target, (float)config.armedShieldBreakSeconds, blockingWith);
		}
	}

	/** 背后偷袭挡不住也就谈不上"破盾"；与原版正面半球格挡判定保持一致。 */
	private static boolean isWithinShieldArc(final Zombie zombie, final LivingEntity target) {
		Vec3 toAttacker = new Vec3(zombie.getX() - target.getX(), 0.0, zombie.getZ() - target.getZ());
		if (toAttacker.horizontalDistanceSqr() < 1.0E-6) {
			return true;
		}
		Vec3 view = target.getViewVector(1.0F);
		Vec3 horizontalView = new Vec3(view.x, 0.0, view.z);
		if (horizontalView.horizontalDistanceSqr() < 1.0E-6) {
			return true;
		}
		return horizontalView.normalize().dot(toAttacker.normalize()) > 0.0;
	}

	/**
	 * 只发剑和斧。26.1.2 的矛由原版 {@code SpearUseGoal}（优先级 2）接管战斗，
	 * 会永久抢占本 Mod 的小队 Goal，持矛僵尸无法参与协同——因此武装系统不再发矛；
	 * 原版自然生成的持矛僵尸保持原版突刺行为。
	 */
	private static Item rollWeapon(final RandomSource random, final Difficulty difficulty) {
		int tier = rollTier(random, difficulty);
		if (random.nextFloat() < 0.55F) {
			return switch (tier) {
				case 0 -> Items.WOODEN_SWORD;
				case 1 -> Items.STONE_SWORD;
				case 2 -> Items.COPPER_SWORD;
				default -> Items.IRON_SWORD;
			};
		}
		return switch (tier) {
			case 0 -> Items.WOODEN_AXE;
			case 1 -> Items.STONE_AXE;
			case 2 -> Items.COPPER_AXE;
			default -> Items.IRON_AXE;
		};
	}

	/** 0=木，1=石，2=铜，3=铁。材质随难度整体上移，困难下也不会全员铁器。 */
	private static int rollTier(final RandomSource random, final Difficulty difficulty) {
		return switch (difficulty) {
			case PEACEFUL, EASY -> random.nextFloat() < 0.6F ? 0 : 1;
			case NORMAL -> random.nextFloat() < 0.55F ? 1 : 2;
			case HARD -> random.nextFloat() < 0.5F ? 2 : 3;
		};
	}
}
