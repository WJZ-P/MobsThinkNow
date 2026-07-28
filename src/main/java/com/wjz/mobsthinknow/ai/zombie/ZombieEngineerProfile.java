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
 * 只有被标记的少量工程兵才会周期性使用 TNT、流体和直接点燃技能。这样既保留聪明个体的
 * 通用地形能力，也避免密集僵尸群同时改造战场。</p>
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
		if (engineer) {
			// 所有工程兵都可能投放水；允许原版导航穿过自己制造的水流，避免回收事务被自身阻断。
			zombie.getNavigation().setCanFloat(true);
		}
	}

	/**
	 * 在其他出生装备全部确定后掷点。普通空手候选按概率成为工程兵；水/岩浆桶变体直接
	 * 并入工程兵并提升到地形智力门槛，使同一职业统一调度 TNT、流体和点燃技能。
	 * 武装兵与持矛空袭兵仍保持独立，避免争抢攻击姿态和双手。
	 */
	public static void maybeAssignOnSpawn(
		final Zombie zombie,
		final DifficultyInstance difficulty,
		final RandomSource random,
		final MobsThinkNowConfig config
	) {
		UtilityClass utility = ZombieSpecialEquipment.utilityClassOf(zombie);
		if (config.enabled
			&& config.zombieAiEnabled
			&& config.engineerSkills
			&& utility != UtilityClass.NONE
			&& ZombieIntelligence.get(zombie) < config.terrainMinimumIntelligence) {
			ZombieIntelligence.set(zombie, config.terrainMinimumIntelligence);
		}
		boolean eligible = config.enabled
			&& config.zombieAiEnabled
			&& config.engineerSkills
			&& zombie.getType() == EntityType.ZOMBIE
			&& !zombie.isBaby()
			&& (utility != UtilityClass.NONE || zombie.getMainHandItem().isEmpty())
			&& zombie.getOffhandItem().isEmpty()
			&& !ZombieAirAssault.isAirAssaultLoadout(zombie)
			&& ZombieIntelligence.get(zombie) >= config.terrainMinimumIntelligence;
		setEngineer(
			zombie,
			eligible && (utility != UtilityClass.NONE || shouldAssign(
				random.nextDouble(),
				config.engineerSpawnChance,
				difficulty.getDifficulty(),
				difficulty.getSpecialMultiplier()
			))
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
