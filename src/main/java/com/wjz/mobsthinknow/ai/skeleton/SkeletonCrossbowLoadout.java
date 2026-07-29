package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

/** 普通骷髅自然生成时的弩手与爆炸烟花弩手负载生成器。 */
public final class SkeletonCrossbowLoadout {
	private static final int FIREWORK_MINIMUM_INTELLIGENCE = 7;
	private static final float VANILLA_EQUIPMENT_DROP_CHANCE = 0.085F;

	private SkeletonCrossbowLoadout() {
	}

	public static void maybeEquip(
		final AbstractSkeleton skeleton,
		final Difficulty difficulty,
		final MobsThinkNowConfig config
	) {
		if (!config.enabled
			|| !config.skeletonAiEnabled
			|| !config.skeletonCrossbows
			|| difficulty == Difficulty.PEACEFUL
			|| !skeleton.getMainHandItem().is(Items.BOW)) {
			return;
		}

		int intelligence = SkeletonIntelligence.get(skeleton);
		RandomSource random = skeleton.getRandom();
		if (random.nextDouble() >= effectiveCrossbowChance(
			config.skeletonCrossbowChance,
			difficulty.getId(),
			intelligence
		)) {
			return;
		}

		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		skeleton.setDropChance(EquipmentSlot.MAINHAND, VANILLA_EQUIPMENT_DROP_CHANCE);
		if (intelligence >= FIREWORK_MINIMUM_INTELLIGENCE
			&& random.nextDouble() < effectiveFireworkChance(
				config.skeletonFireworkCrossbowChance,
				difficulty.getId(),
				intelligence
			)) {
			ItemStack rockets = explosiveRockets(rocketCount(random, difficulty.getId(), intelligence));
			skeleton.setItemSlot(EquipmentSlot.OFFHAND, rockets);
			skeleton.setDropChance(EquipmentSlot.OFFHAND, VANILLA_EQUIPMENT_DROP_CHANCE);
		}
	}

	static double effectiveCrossbowChance(final double baseChance, final int difficultyId, final int intelligence) {
		double difficultyFactor = switch (Math.clamp(difficultyId, 0, 3)) {
			case 0 -> 0.0;
			case 1 -> 0.65;
			case 2 -> 1.0;
			default -> 1.35;
		};
		double intelligenceFactor = 0.75 + SkeletonIntelligence.clamp(intelligence) * 0.05;
		return Math.clamp(baseChance * difficultyFactor * intelligenceFactor, 0.0, 1.0);
	}

	static double effectiveFireworkChance(final double baseChance, final int difficultyId, final int intelligence) {
		int clamped = SkeletonIntelligence.clamp(intelligence);
		if (clamped < FIREWORK_MINIMUM_INTELLIGENCE || difficultyId <= 0) {
			return 0.0;
		}
		double mastery = (clamped - FIREWORK_MINIMUM_INTELLIGENCE + 1) / 4.0;
		double difficultyFactor = switch (Math.clamp(difficultyId, 1, 3)) {
			case 1 -> 0.70;
			case 2 -> 1.0;
			default -> 1.25;
		};
		return Math.clamp(baseChance * mastery * difficultyFactor, 0.0, 1.0);
	}

	/** 命令样本和自然生成共用的真实爆炸烟花堆。 */
	public static ItemStack explosiveRockets(final int count) {
		ItemStack rockets = new ItemStack(Items.FIREWORK_ROCKET, Math.max(1, count));
		FireworkExplosion explosion = new FireworkExplosion(
			FireworkExplosion.Shape.BURST,
			IntList.of(0x80C71F, 0x474F52),
			IntList.of(0xFFFFFF),
			true,
			false
		);
		rockets.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));
		return rockets;
	}

	private static int rocketCount(final RandomSource random, final int difficultyId, final int intelligence) {
		int baseline = 2 + Math.max(0, difficultyId) + SkeletonIntelligence.clamp(intelligence) / 3;
		return Math.clamp(baseline + random.nextInt(3), 3, 9);
	}
}
