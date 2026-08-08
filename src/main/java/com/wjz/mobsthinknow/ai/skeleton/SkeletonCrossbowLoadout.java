package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import com.wjz.mobsthinknow.shared.ai.CrossbowLoadoutPlanner;
import com.wjz.mobsthinknow.shared.ai.DifficultyTier;
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
		DifficultyTier difficultyTier = DifficultyTier.fromNumericId(difficulty.getId());
		if (!CrossbowLoadoutPlanner.succeeds(CrossbowLoadoutPlanner.effectiveCrossbowChance(
			config.skeletonCrossbowChance,
			difficultyTier,
			intelligence
		), random.nextDouble())) {
			return;
		}

		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		skeleton.setDropChance(EquipmentSlot.MAINHAND, VANILLA_EQUIPMENT_DROP_CHANCE);
		if (CrossbowLoadoutPlanner.succeeds(CrossbowLoadoutPlanner.effectiveFireworkChance(
				config.skeletonFireworkCrossbowChance,
				difficultyTier,
				intelligence
			), random.nextDouble())) {
			ItemStack rockets = explosiveRockets(CrossbowLoadoutPlanner.rocketCount(
				difficultyTier,
				intelligence,
				random.nextDouble()
			));
			skeleton.setItemSlot(EquipmentSlot.OFFHAND, rockets);
			skeleton.setDropChance(EquipmentSlot.OFFHAND, VANILLA_EQUIPMENT_DROP_CHANCE);
		}
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

}
