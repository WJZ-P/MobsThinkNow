package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 混编小队射手的友军射界检查。
 *
 * <p>只有真正准备开火时才进行一次有界实体查询；单兵和未成队射手通过 O(1) 小队索引直接返回，
 * 不会在每 tick 扫描附近实体。普通箭检查发射线段，烟花弩额外检查目标附近的爆炸危险区。</p>
 */
public final class SkeletonShotSafety {
	private static final double CORRIDOR_QUERY_PADDING = 0.75;
	private static final double ALLY_HITBOX_PADDING = 0.20;
	private static final double FIREWORK_DANGER_RADIUS = 4.0;

	private SkeletonShotSafety() {
	}

	/**
	 * @param explosive true 表示本次弹药会在目标附近爆炸
	 * @return 当前射界不会明显穿过或炸到同队成员
	 */
	public static boolean hasClearShot(
		final AbstractSkeleton shooter,
		final LivingEntity target,
		final boolean explosive
	) {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.enabled
			|| !config.skeletonAiEnabled
			|| !config.squadIgnoreFriendlyFire
			|| !(shooter.level() instanceof ServerLevel level)) {
			return true;
		}

		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(level);
		if (coordinator.viewFor(shooter) == null) {
			return true;
		}

		Vec3 start = shooter.getEyePosition();
		Vec3 end = target.getEyePosition();
		AABB corridor = new AABB(start, end).inflate(CORRIDOR_QUERY_PADDING);
		List<Mob> corridorAllies = level.getEntitiesOfClass(
			Mob.class,
			corridor,
			ally -> isRelevantSquadmate(shooter, ally)
				&& ally != shooter.getVehicle()
		);
		for (Mob ally : corridorAllies) {
			if (ally.getBoundingBox().inflate(ALLY_HITBOX_PADDING).clip(start, end).isPresent()) {
				return false;
			}
		}

		if (!explosive) {
			return true;
		}
		AABB dangerZone = target.getBoundingBox().inflate(FIREWORK_DANGER_RADIUS);
		return level.getEntitiesOfClass(
			Mob.class,
			dangerZone,
			ally -> isRelevantSquadmate(shooter, ally)
		).isEmpty();
	}

	private static boolean isRelevantSquadmate(final AbstractSkeleton shooter, final Mob candidate) {
		return candidate != shooter
			&& candidate.isAlive()
			&& ZombieSquadCoordinator.areSquadmates(shooter, candidate);
	}
}
