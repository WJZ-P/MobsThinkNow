package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.shared.ai.RangedSpacingPlanner;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** 为每只骷髅持久保存一次性抽取的逃跑速度因子。 */
public final class PaperSkeletonProfile {
	private final NamespacedKey escapeSpeedFactorKey;

	public PaperSkeletonProfile(final Plugin plugin) {
		this.escapeSpeedFactorKey = new NamespacedKey(plugin, "skeleton_escape_speed_factor");
	}

	public double escapePathSpeed(final AbstractSkeleton skeleton, final int intelligence) {
		return RangedSpacingPlanner.maximumEscapePathSpeed(intelligence) * this.speedFactor(skeleton);
	}

	public double speedFactor(final AbstractSkeleton skeleton) {
		PersistentDataContainer data = skeleton.getPersistentDataContainer();
		Double stored = data.get(this.escapeSpeedFactorKey, PersistentDataType.DOUBLE);
		double factor;
		if (stored == null || !Double.isFinite(stored) || stored <= 0.0) {
			factor = RangedSpacingPlanner.escapeSpeedFactor(
				PaperDifficultyAdapter.fromBukkit(skeleton.getWorld().getDifficulty()),
				ThreadLocalRandom.current().nextDouble()
			);
			data.set(this.escapeSpeedFactorKey, PersistentDataType.DOUBLE, factor);
		} else {
			factor = Math.clamp(
				stored,
				RangedSpacingPlanner.MINIMUM_ESCAPE_SPEED_FACTOR,
				RangedSpacingPlanner.MAXIMUM_ESCAPE_SPEED_FACTOR
			);
			if (factor != stored) {
				data.set(this.escapeSpeedFactorKey, PersistentDataType.DOUBLE, factor);
			}
		}
		return factor;
	}
}
