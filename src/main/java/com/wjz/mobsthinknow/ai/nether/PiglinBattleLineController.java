package com.wjz.mobsthinknow.ai.nether;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 猪灵战线的无全局扫描控制器。
 *
 * <p>弩手和近战单位都用实体 ID 稳定映射到一条战术通道，而不是逐个搜寻同伴；
 * 因此同屏 N 只猪灵每轮仍是 O(N) 次本地决策。控制器只覆写 Brain 的 WALK_TARGET，
 * 仇恨、交易、畏惧僵尸化生物和疣猪兽数量判断全部继续由原版 Brain 决定。</p>
 */
public final class PiglinBattleLineController {
	private static final int[] VERTICAL_OFFSETS = {0, 1, -1, 2, -2, 3, -3};
	private long nextDecisionAt;

	public void tick(final ServerLevel level, final AbstractPiglin piglin) {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!enabled(config) || piglin.isBaby() || piglin.isPassenger()) {
			return;
		}
		LivingEntity target = piglin.getTarget();
		if (target == null || !target.isAlive() || !piglin.canAttack(target)) {
			return;
		}
		long now = level.getGameTime();
		if (now < this.nextDecisionAt) {
			return;
		}
		this.nextDecisionAt = now + 16L + Math.floorMod(piglin.getId() * 5, 12);

		boolean crossbow = piglin instanceof Piglin && piglin.isHolding(Items.CROSSBOW);
		Vec3 desired = crossbow
			? this.crossbowLane(piglin, target)
			: this.meleeLane(piglin, target, piglin instanceof PiglinBrute);
		if (desired == null) {
			return;
		}
		BlockPos stand = this.findReachableStand(level, piglin, desired);
		if (stand == null) {
			return;
		}
		float speed = crossbow ? 0.92F : piglin instanceof PiglinBrute ? 1.12F : 1.02F;
		piglin.getBrain().setMemory(
			MemoryModuleType.WALK_TARGET,
			new WalkTarget(new BlockPosTracker(Vec3.atBottomCenterOf(stand)), speed, 1)
		);
		piglin.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
		SmartNetherMetrics.piglinFormationMove();
	}

	private Vec3 crossbowLane(final AbstractPiglin piglin, final LivingEntity target) {
		Vec3 away = NetherCombatMath.horizontalUnitOrEntityFallback(
			piglin.position().subtract(target.position()),
			piglin.getId()
		);
		double distance = Math.sqrt(piglin.distanceToSqr(target));
		double side = (piglin.getId() & 1) == 0 ? 1.0 : -1.0;
		int lane = Math.floorMod(piglin.getId(), 3);
		if (distance < 6.0) {
			Vec3 lateral = NetherCombatMath.rotateHorizontal(away, side * Math.PI * 0.5);
			return piglin.position().add(away.scale(5.5)).add(lateral.scale(1.5 + lane * 0.45));
		}
		if (distance > 15.0) {
			return null;
		}
		Vec3 laneDirection = NetherCombatMath.rotateHorizontal(away, side * (0.42 + lane * 0.10));
		return target.position()
			.add(target.getDeltaMovement().scale(2.0))
			.add(laneDirection.scale(9.0 + lane * 0.75));
	}

	private Vec3 meleeLane(
		final AbstractPiglin piglin,
		final LivingEntity target,
		final boolean brute
	) {
		double distanceSquared = piglin.distanceToSqr(target);
		if (distanceSquared < 3.0 * 3.0 || distanceSquared > 12.0 * 12.0) {
			return null;
		}
		Vec3 targetForward = NetherCombatMath.horizontalUnitOrEntityFallback(target.getLookAngle(), target.getId());
		Vec3 targetRight = NetherCombatMath.rotateHorizontal(targetForward, Math.PI * 0.5);
		int lane = Math.floorMod(piglin.getId(), 3) - 1;
		double behind = brute ? 0.8 : 2.2;
		double side = brute ? lane * 1.2 : lane * 2.6;
		return target.position()
			.add(target.getDeltaMovement().scale(1.5))
			.subtract(targetForward.scale(behind))
			.add(targetRight.scale(side));
	}

	private @Nullable BlockPos findReachableStand(
		final ServerLevel level,
		final AbstractPiglin piglin,
		final Vec3 desired
	) {
		BlockPos base = BlockPos.containing(desired);
		for (int dy : VERTICAL_OFFSETS) {
			BlockPos feet = base.offset(0, dy, 0);
			BlockPos support = feet.below();
			if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
				continue;
			}
			double dx = feet.getX() + 0.5 - piglin.getX();
			double dyMove = feet.getY() - piglin.getY();
			double dz = feet.getZ() + 0.5 - piglin.getZ();
			AABB destination = piglin.getBoundingBox().move(dx, dyMove, dz);
			if (!level.noCollision(piglin, destination)) {
				continue;
			}
			Path path = piglin.getNavigation().createPath(feet, 1);
			if (path != null && path.canReach()) {
				return feet.immutable();
			}
		}
		return null;
	}

	static boolean enabled(final MobsThinkNowConfig config) {
		return config.enabled && config.netherAiEnabled && config.piglinFormationTactics;
	}
}
