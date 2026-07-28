package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 工程兵身份的生成、持久化与查询入口。
 *
 * <p>身份和“智力达到地形战术门槛”是两个不同概念：所有高智力僵尸仍能按需采集、垫高，
 * 只有被标记的少量工程兵才会周期性使用爆破、维修和加固技能。这样既保留聪明个体的通用
 * 地形能力，也避免密集僵尸群同时投放 TNT。</p>
 */
public final class ZombieEngineerProfile {
	private static final String ENGINEER_TAG = "MobsThinkNowEngineer";

	private ZombieEngineerProfile() {
	}

	public static boolean isEngineer(final Zombie zombie) {
		return ((ZombieEngineerAccess)zombie).mobsthinknow$isEngineer();
	}

	public static void setEngineer(final Zombie zombie, final boolean engineer) {
		((ZombieEngineerAccess)zombie).mobsthinknow$setEngineer(engineer);
	}

	/**
	 * 在其他出生装备全部确定后掷点。工程兵必须是普通成年、高智力且双手为空的僵尸；
	 * 水/岩浆辅助兵、武装兵和持矛空袭兵因此不会再叠加一个互相争抢双手与 Goal 的职业。
	 */
	public static void maybeAssignOnSpawn(
		final Zombie zombie,
		final DifficultyInstance difficulty,
		final RandomSource random,
		final MobsThinkNowConfig config
	) {
		boolean eligible = config.enabled
			&& config.zombieAiEnabled
			&& config.engineerSkills
			&& zombie.getType() == EntityType.ZOMBIE
			&& !zombie.isBaby()
			&& zombie.getMainHandItem().isEmpty()
			&& zombie.getOffhandItem().isEmpty()
			&& ZombieSpecialEquipment.utilityClassOf(zombie) == UtilityClass.NONE
			&& !ZombieAirAssault.isAirAssaultLoadout(zombie)
			&& ZombieIntelligence.get(zombie) >= config.terrainMinimumIntelligence;
		setEngineer(
			zombie,
			eligible && shouldAssign(
				random.nextDouble(),
				config.engineerSpawnChance,
				difficulty.getDifficulty(),
				difficulty.getSpecialMultiplier()
			)
		);
	}

	/** 纯函数供概率边界测试；配置值表示“符合资格的高智力空手僵尸”中的基础占比。 */
	static boolean shouldAssign(
		final double roll,
		final double configuredChance,
		final Difficulty difficulty,
		final float regionalDifficulty
	) {
		return boundedRoll(roll) < effectiveChance(configuredChance, difficulty, regionalDifficulty);
	}

	static double effectiveChance(
		final double configuredChance,
		final Difficulty difficulty,
		final float regionalDifficulty
	) {
		double difficultyFactor = switch (difficulty) {
			case PEACEFUL -> 0.0;
			case EASY -> 0.75;
			case NORMAL -> 1.0;
			case HARD -> 1.25;
		};
		double regionalFactor = 0.90 + 0.10 * clamp01(regionalDifficulty);
		return clamp01(clamp01(configuredChance) * difficultyFactor * regionalFactor);
	}

	public static void save(final Zombie zombie, final ValueOutput output) {
		output.putBoolean(ENGINEER_TAG, isEngineer(zombie));
	}

	public static void load(final Zombie zombie, final ValueInput input) {
		setEngineer(zombie, input.getBooleanOr(ENGINEER_TAG, false));
	}

	private static double boundedRoll(final double value) {
		if (!Double.isFinite(value)) {
			return 1.0;
		}
		return Math.max(0.0, Math.min(Math.nextDown(1.0), value));
	}

	private static double clamp01(final double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}
}
