package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.MobsThinkNow;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** 在出生阶段给普通僵尸写入会随实体一同存档的永久属性差异。 */
public final class ZombieIndividualTraits {
	private static final Identifier SPEED_ID = id("individual_speed");
	private static final Identifier HEALTH_ID = id("individual_health");
	private static final Identifier DAMAGE_ID = id("individual_damage");
	private static final Identifier FOLLOW_RANGE_ID = id("individual_follow_range");

	private ZombieIndividualTraits() {
	}

	public static void applyOnSpawn(
		final Zombie zombie,
		final DifficultyInstance difficulty,
		final RandomSource random,
		final MobsThinkNowConfig config
	) {
		if (!config.enabled || !config.zombieAiEnabled || !config.individualTraits) {
			return;
		}

		float oldMaximumHealth = zombie.getMaxHealth();
		float healthFraction = oldMaximumHealth <= 0.0F ? 1.0F : zombie.getHealth() / oldMaximumHealth;
		Difficulty worldDifficulty = difficulty.getDifficulty();
		double regional = difficulty.getSpecialMultiplier();

		apply(zombie.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID,
			traitAmount(worldDifficulty, regional, random.nextDouble(), Trait.SPEED));
		apply(zombie.getAttribute(Attributes.MAX_HEALTH), HEALTH_ID,
			traitAmount(worldDifficulty, regional, random.nextDouble(), Trait.HEALTH));
		apply(zombie.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID,
			traitAmount(worldDifficulty, regional, random.nextDouble(), Trait.DAMAGE));
		apply(zombie.getAttribute(Attributes.FOLLOW_RANGE), FOLLOW_RANGE_ID,
			traitAmount(worldDifficulty, regional, random.nextDouble(), Trait.FOLLOW_RANGE));

		// finalizeSpawn 发生在出生完成前；按原生命比例同步新上限，避免高生命个体出生时反而缺血。
		zombie.setHealth(Math.min(zombie.getMaxHealth(), zombie.getMaxHealth() * healthFraction));
	}

	static double traitAmount(
		final Difficulty difficulty,
		final double regionalDifficulty,
		final double roll,
		final Trait trait
	) {
		double difficultyBias = switch (difficulty) {
			case PEACEFUL -> -0.08;
			case EASY -> -0.04;
			case NORMAL -> 0.02;
			case HARD -> 0.09;
		};
		double regionalBonus = Math.max(0.0, Math.min(1.0, regionalDifficulty)) * trait.regionalBonus;
		double centeredRoll = (Math.max(0.0, Math.min(1.0, roll)) * 2.0 - 1.0) * trait.spread;
		return clamp(difficultyBias * trait.difficultyScale + regionalBonus + centeredRoll, trait.minimum, trait.maximum);
	}

	private static void apply(
		final AttributeInstance attribute,
		final Identifier id,
		final double amount
	) {
		if (attribute != null) {
			attribute.addOrReplacePermanentModifier(new AttributeModifier(
				id,
				amount,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE
			));
		}
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MobsThinkNow.MOD_ID, path);
	}

	private static double clamp(final double value, final double minimum, final double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	enum Trait {
		SPEED(0.075, 0.035, 0.80, -0.16, 0.20),
		HEALTH(0.11, 0.045, 1.00, -0.22, 0.28),
		DAMAGE(0.10, 0.050, 1.15, -0.22, 0.30),
		FOLLOW_RANGE(0.09, 0.040, 0.85, -0.18, 0.24);

		private final double spread;
		private final double regionalBonus;
		private final double difficultyScale;
		private final double minimum;
		private final double maximum;

		Trait(
			final double spread,
			final double regionalBonus,
			final double difficultyScale,
			final double minimum,
			final double maximum
		) {
			this.spread = spread;
			this.regionalBonus = regionalBonus;
			this.difficultyScale = difficultyScale;
			this.minimum = minimum;
			this.maximum = maximum;
		}
	}
}
