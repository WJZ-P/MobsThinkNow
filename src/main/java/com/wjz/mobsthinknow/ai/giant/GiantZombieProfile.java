package com.wjz.mobsthinknow.ai.giant;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Giant;

/** 巨人生成概率、重型属性和出生原因边界。 */
public final class GiantZombieProfile {
	private GiantZombieProfile() {
	}

	public static double chanceFor(final Difficulty difficulty, final double normalChance) {
		double base = Double.isFinite(normalChance) ? Math.max(0.0, Math.min(1.0, normalChance)) : 0.0;
		return switch (difficulty) {
			case PEACEFUL -> 0.0;
			case EASY -> base * 0.4;
			case NORMAL -> base;
			case HARD -> Math.min(1.0, base * 2.0);
		};
	}

	public static boolean shouldReplace(
		final Difficulty difficulty,
		final EntitySpawnReason reason,
		final double roll,
		final MobsThinkNowConfig config
	) {
		return config.enabled
			&& config.giantZombieAiEnabled
			&& eligibleSpawnReason(reason)
			&& Double.isFinite(roll)
			&& roll >= 0.0
			&& roll < chanceFor(difficulty, config.giantZombieSpawnChance);
	}

	static boolean eligibleSpawnReason(final EntitySpawnReason reason) {
		return reason != EntitySpawnReason.CONVERSION
			&& reason != EntitySpawnReason.COMMAND
			&& reason != EntitySpawnReason.JOCKEY
			&& reason != EntitySpawnReason.LOAD
			&& reason != EntitySpawnReason.DIMENSION_TRAVEL;
	}

	public static void applyAttributes(final Giant giant, final MobsThinkNowConfig config) {
		setBaseValue(giant.getAttribute(Attributes.MAX_HEALTH), config.giantZombieMaximumHealth);
		setBaseValue(giant.getAttribute(Attributes.MOVEMENT_SPEED), config.giantZombieMovementSpeed);
		setBaseValue(giant.getAttribute(Attributes.ATTACK_DAMAGE), config.giantZombieAttackDamage);
		setBaseValue(giant.getAttribute(Attributes.FOLLOW_RANGE), 40.0);
		setBaseValue(giant.getAttribute(Attributes.ARMOR), 8.0);
		setBaseValue(giant.getAttribute(Attributes.KNOCKBACK_RESISTANCE), 0.70);
		setBaseValue(giant.getAttribute(Attributes.ATTACK_KNOCKBACK), 2.0);
	}

	private static void setBaseValue(final AttributeInstance attribute, final double value) {
		if (attribute != null) {
			attribute.setBaseValue(value);
		}
	}
}
