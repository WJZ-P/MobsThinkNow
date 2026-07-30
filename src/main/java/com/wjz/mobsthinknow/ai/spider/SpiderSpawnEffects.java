package com.wjz.mobsthinknow.ai.spider;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.spider.Spider;

/** 普通蜘蛛出生时的低概率永久药水特质：4% 速度 I、1% 速度 II。 */
public final class SpiderSpawnEffects {
	static final double SPEED_II_CHANCE = 0.01;
	static final double TOTAL_SPEED_CHANCE = 0.05;

	private SpiderSpawnEffects() {
	}

	public static void maybeApplySpeed(final Spider spider, final double randomSample) {
		int amplifier = speedAmplifier(randomSample);
		if (amplifier < 0) {
			return;
		}
		MobEffectInstance existing = spider.getEffect(MobEffects.SPEED);
		if (existing != null && existing.getAmplifier() >= amplifier) {
			return;
		}
		spider.addEffect(new MobEffectInstance(
			MobEffects.SPEED,
			MobEffectInstance.INFINITE_DURATION,
			amplifier,
			false,
			true
		));
	}

	/** 返回原版 amplifier：0=速度 I，1=速度 II，-1=本次不附加。 */
	static int speedAmplifier(final double randomSample) {
		if (randomSample >= 0.0 && randomSample < SPEED_II_CHANCE) {
			return 1;
		}
		return randomSample >= SPEED_II_CHANCE && randomSample < TOTAL_SPEED_CHANCE ? 0 : -1;
	}
}
