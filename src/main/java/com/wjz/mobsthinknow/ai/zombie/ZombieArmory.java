package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.WeaponClass;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.core.component.DataComponents;
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

	private static Item rollWeapon(final RandomSource random, final Difficulty difficulty) {
		int tier = rollTier(random, difficulty);
		float classRoll = random.nextFloat();
		if (classRoll < 0.45F) {
			return switch (tier) {
				case 0 -> Items.WOODEN_SWORD;
				case 1 -> Items.STONE_SWORD;
				case 2 -> Items.COPPER_SWORD;
				default -> Items.IRON_SWORD;
			};
		}
		if (classRoll < 0.75F) {
			return switch (tier) {
				case 0 -> Items.WOODEN_AXE;
				case 1 -> Items.STONE_AXE;
				case 2 -> Items.COPPER_AXE;
				default -> Items.IRON_AXE;
			};
		}
		return switch (tier) {
			case 0 -> Items.WOODEN_SPEAR;
			case 1 -> Items.STONE_SPEAR;
			case 2 -> Items.COPPER_SPEAR;
			default -> Items.IRON_SPEAR;
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
