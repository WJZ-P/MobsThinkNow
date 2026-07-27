package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.DropChances;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** 水桶辅助兵和岩浆骚扰兵的出生装备、掉落率与持久化状态。 */
public final class ZombieSpecialEquipment {
	private static final String UTILITY_TAG = "MobsThinkNowFluidUtility";
	private static final String SOURCE_TAG = "MobsThinkNowFluidSource";
	private static final String RETRIEVE_AT_TAG = "MobsThinkNowFluidRetrieveAt";
	private static final String COOLDOWN_UNTIL_TAG = "MobsThinkNowFluidCooldownUntil";

	private ZombieSpecialEquipment() {
	}

	/**
	 * 在武装系统之前运行，确保特殊兵种获得独占主手。未命中时不会改变原版或其他 Mod 的装备。
	 */
	public static void maybeEquip(
		final Zombie zombie,
		final DifficultyInstance difficulty,
		final RandomSource random,
		final MobsThinkNowConfig config
	) {
		if (!config.enabled || !config.zombieAiEnabled || !config.specialEquipment
			|| zombie.isBaby() || !zombie.getMainHandItem().isEmpty()) {
			return;
		}

		UtilityClass selected = selectUtility(
			random.nextDouble(),
			config.waterBucketChance,
			config.lavaBucketChance,
			difficultyFactor(difficulty.getDifficulty(), difficulty.getSpecialMultiplier())
		);
		if (selected == UtilityClass.NONE) {
			return;
		}

		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(
			selected == UtilityClass.WATER ? Items.WATER_BUCKET : Items.LAVA_BUCKET
		));
		stateAccess(zombie).mobsthinknow$setFluidCarrierState(
			new ZombieFluidCarrierState(selected, null, 0L, 0L)
		);
		zombie.setDropChance(EquipmentSlot.MAINHAND, (float)config.specialEquipmentDropChance);
	}

	/** 当前仍具有辅助兵身份的类别；流体丢失且只剩空桶时返回 NONE，使普通攻击重新接管。 */
	public static UtilityClass utilityClassOf(final Zombie zombie) {
		ItemStack hand = zombie.getMainHandItem();
		if (hand.is(Items.WATER_BUCKET)) {
			return UtilityClass.WATER;
		}
		if (hand.is(Items.LAVA_BUCKET)) {
			return UtilityClass.LAVA;
		}
		ZombieFluidCarrierState state = state(zombie);
		return hand.is(Items.BUCKET) && state.isDeployed() ? state.utility() : UtilityClass.NONE;
	}

	public static boolean hasFullBucket(final Zombie zombie, final UtilityClass utility) {
		return switch (utility) {
			case WATER -> zombie.getMainHandItem().is(Items.WATER_BUCKET);
			case LAVA -> zombie.getMainHandItem().is(Items.LAVA_BUCKET);
			case NONE -> false;
		};
	}

	public static ZombieFluidCarrierState state(final Zombie zombie) {
		return stateAccess(zombie).mobsthinknow$getFluidCarrierState();
	}

	public static void markDeployed(
		final Zombie zombie,
		final UtilityClass utility,
		final BlockPos source,
		final long retrieveAt
	) {
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BUCKET));
		stateAccess(zombie).mobsthinknow$setFluidCarrierState(
			new ZombieFluidCarrierState(utility, source.immutable(), retrieveAt, 0L)
		);
	}

	public static void markRecovered(
		final Zombie zombie,
		final UtilityClass utility,
		final ItemStack recoveredBucket,
		final long cooldownUntil
	) {
		zombie.setItemSlot(EquipmentSlot.MAINHAND, recoveredBucket);
		stateAccess(zombie).mobsthinknow$setFluidCarrierState(
			new ZombieFluidCarrierState(utility, null, 0L, cooldownUntil)
		);
	}

	/** 玩家移走源方块后保留真实的空桶，但清除辅助身份，后续由普通战斗 Goal 接手。 */
	public static void markFluidLost(final Zombie zombie) {
		stateAccess(zombie).mobsthinknow$setFluidCarrierState(ZombieFluidCarrierState.NONE);
	}

	public static void save(final Zombie zombie, final ValueOutput output) {
		ZombieFluidCarrierState state = state(zombie);
		if (state.utility() == UtilityClass.NONE) {
			return;
		}
		output.putByte(UTILITY_TAG, (byte)state.utility().ordinal());
		if (state.source() != null) {
			output.putLong(SOURCE_TAG, state.source().asLong());
		}
		output.putLong(RETRIEVE_AT_TAG, state.retrieveAt());
		output.putLong(COOLDOWN_UNTIL_TAG, state.cooldownUntil());
	}

	public static void load(final Zombie zombie, final ValueInput input) {
		UtilityClass savedUtility = UtilityClass.fromId(input.getByteOr(UTILITY_TAG, (byte)0));
		BlockPos source = input.getLong(SOURCE_TAG).map(BlockPos::of).orElse(null);
		ZombieFluidCarrierState loaded = new ZombieFluidCarrierState(
			savedUtility,
			source,
			input.getLongOr(RETRIEVE_AT_TAG, 0L),
			input.getLongOr(COOLDOWN_UNTIL_TAG, 0L)
		);

		ItemStack hand = zombie.getMainHandItem();
		if (hand.is(Items.WATER_BUCKET)) {
			loaded = new ZombieFluidCarrierState(UtilityClass.WATER, null, 0L, loaded.cooldownUntil());
		} else if (hand.is(Items.LAVA_BUCKET)) {
			loaded = new ZombieFluidCarrierState(UtilityClass.LAVA, null, 0L, loaded.cooldownUntil());
		} else if (!hand.is(Items.BUCKET) || !loaded.isDeployed()) {
			loaded = ZombieFluidCarrierState.NONE;
		}
		stateAccess(zombie).mobsthinknow$setFluidCarrierState(loaded);
	}

	static UtilityClass selectUtility(
		final double roll,
		final double waterChance,
		final double lavaChance,
		final double difficultyFactor
	) {
		double water = clamp01(waterChance) * Math.max(0.0, difficultyFactor);
		double lava = clamp01(lavaChance) * Math.max(0.0, difficultyFactor);
		double total = water + lava;
		if (total > 1.0) {
			// 两项都被配置得很高时按相对权重归一化，避免“水先判断”把岩浆概率完全吞掉。
			water /= total;
			lava /= total;
		}
		double boundedRoll = clamp01(roll);
		if (boundedRoll < water) {
			return UtilityClass.WATER;
		}
		if (boundedRoll < water + lava) {
			return UtilityClass.LAVA;
		}
		return UtilityClass.NONE;
	}

	static double difficultyFactor(final Difficulty difficulty, final double regionalDifficulty) {
		double base = switch (difficulty) {
			case PEACEFUL -> 0.0;
			case EASY -> 0.70;
			case NORMAL -> 1.0;
			case HARD -> 1.35;
		};
		return base * (0.85 + 0.15 * clamp01(regionalDifficulty));
	}

	private static ZombieFluidCarrierAccess stateAccess(final Zombie zombie) {
		return (ZombieFluidCarrierAccess)zombie;
	}

	private static double clamp01(final double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}

	/** 供测试与文档明确引用原版默认值，避免魔法数字在多处漂移。 */
	static float vanillaEquipmentDropChance() {
		return DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE;
	}
}
