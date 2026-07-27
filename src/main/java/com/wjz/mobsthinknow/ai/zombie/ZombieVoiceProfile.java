package com.wjz.mobsthinknow.ai.zombie;

import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 每只僵尸稳定不变的声线因子。
 *
 * <p>原版 {@code LivingEntity#getVoicePitch()} 会在每次发声时加入少量随机抖动；本因子再给每个个体
 * 一个持久的高低音中心，因此同一只僵尸听起来始终相似，而一群僵尸又不会像复制同一条音轨。</p>
 */
public final class ZombieVoiceProfile {
	private static final String VOICE_FACTOR_TAG = "MobsThinkNowVoiceFactor";
	private static final float MINIMUM_FACTOR = 0.86F;
	private static final float MAXIMUM_FACTOR = 1.14F;

	private ZombieVoiceProfile() {
	}

	public static float factor(final Zombie zombie) {
		ZombieVoiceAccess access = (ZombieVoiceAccess)zombie;
		float factor = access.mobsthinknow$getVoiceFactor();
		if (factor <= 0.0F) {
			factor = factorForRoll(zombie.getRandom().nextFloat());
			access.mobsthinknow$setVoiceFactor(factor);
		}
		return clamp(factor, MINIMUM_FACTOR, MAXIMUM_FACTOR);
	}

	/** 给显式播放的小队叫声套用个体声线，并限制到声音引擎的舒适范围。 */
	public static float expressivePitch(final Zombie zombie, final float expressionPitch) {
		return clamp(expressionPitch * factor(zombie), 0.35F, 2.0F);
	}

	public static void save(final Zombie zombie, final ValueOutput output) {
		output.putFloat(VOICE_FACTOR_TAG, factor(zombie));
	}

	public static void load(final Zombie zombie, final ValueInput input) {
		float saved = input.getFloatOr(VOICE_FACTOR_TAG, 0.0F);
		((ZombieVoiceAccess)zombie).mobsthinknow$setVoiceFactor(
			saved <= 0.0F ? 0.0F : clamp(saved, MINIMUM_FACTOR, MAXIMUM_FACTOR)
		);
	}

	static float factorForRoll(final float roll) {
		return MINIMUM_FACTOR + (MAXIMUM_FACTOR - MINIMUM_FACTOR) * clamp(roll, 0.0F, 1.0F);
	}

	private static float clamp(final float value, final float minimum, final float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
