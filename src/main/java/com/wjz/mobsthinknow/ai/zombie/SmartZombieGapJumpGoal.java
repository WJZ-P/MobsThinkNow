package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 当目标位于一格宽沟槽的另一侧时，执行一次有起点、空隙、落点三段验证的真实物理跳跃。
 * 这不是瞬移：垂直速度仍来自原版 {@link LivingEntity#jumpFromGround()}，这里只补足朝落点的水平动量。
 */
public final class SmartZombieGapJumpGoal extends Goal {
	private static final double MAXIMUM_TARGET_HEIGHT_DIFFERENCE = 1.25;
	private static final double MAXIMUM_TARGET_LATERAL_DISTANCE_SQUARED = 2.25;
	private static final double MINIMUM_TARGET_FORWARD_DISTANCE = 1.5;
	private static final double HORIZONTAL_LAUNCH_SPEED = 0.32;
	private static final int JUMP_TIMEOUT_TICKS = 20;
	private static final int JUMP_COOLDOWN_TICKS = 30;

	private final Zombie zombie;
	private @Nullable JumpPlan plan;
	private long launchedAt;
	private long cooldownUntil;

	public SmartZombieGapJumpGoal(final Zombie zombie) {
		this.zombie = zombie;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.enabled
			|| !config.zombieAiEnabled
			|| !config.smartTraversal
			|| !this.zombie.isAlive()
			|| !this.zombie.onGround()
			|| this.zombie.isInWater()
			|| this.zombie.isInLava()
			|| this.zombie.isPassenger()
			|| this.zombie.isFallFlying()
			|| this.zombie.level().getGameTime() < this.cooldownUntil) {
			return false;
		}

		LivingEntity target = this.zombie.getTarget();
		if (target == null || !target.isAlive() || ZombieAirAssault.suppressGroundCombat(this.zombie, config)) {
			return false;
		}
		this.plan = findPlan(this.zombie, target);
		return this.plan != null;
	}

	@Override
	public boolean canContinueToUse() {
		long elapsed = this.zombie.level().getGameTime() - this.launchedAt;
		return this.plan != null
			&& this.zombie.isAlive()
			&& elapsed < JUMP_TIMEOUT_TICKS
			&& (elapsed <= 2L || !this.zombie.onGround());
	}

	@Override
	public void start() {
		JumpPlan current = this.plan;
		if (current == null) {
			return;
		}
		this.zombie.getNavigation().stop();
		this.zombie.getLookControl().setLookAt(Vec3.atCenterOf(current.landing()).add(0.0, 0.5, 0.0));
		faceDirection(this.zombie, current.motion());
		this.zombie.jumpFromGround();
		Vec3 movement = this.zombie.getDeltaMovement();
		this.zombie.setDeltaMovement(
			current.motion().x * HORIZONTAL_LAUNCH_SPEED,
			movement.y,
			current.motion().z * HORIZONTAL_LAUNCH_SPEED
		);
		this.launchedAt = this.zombie.level().getGameTime();
		this.cooldownUntil = this.launchedAt + JUMP_COOLDOWN_TICKS;
	}

	@Override
	public void tick() {
		JumpPlan current = this.plan;
		if (current != null) {
			this.zombie.getLookControl().setLookAt(Vec3.atCenterOf(current.landing()).add(0.0, 0.5, 0.0));
		}
	}

	@Override
	public void stop() {
		this.zombie.getNavigation().stop();
		this.plan = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	static @Nullable JumpPlan findPlan(final Zombie zombie, final LivingEntity target) {
		if (Math.abs(target.getY() - zombie.getY()) > MAXIMUM_TARGET_HEIGHT_DIFFERENCE) {
			return null;
		}
		Vec3 targetDelta = target.position().subtract(zombie.position());
		Direction direction = dominantDirection(targetDelta);
		Vec3 motion = new Vec3(direction.getStepX(), 0.0, direction.getStepZ());
		double forward = targetDelta.dot(motion);
		Vec3 lateral = targetDelta.subtract(motion.scale(forward));
		if (forward < MINIMUM_TARGET_FORWARD_DISTANCE
			|| lateral.horizontalDistanceSqr() > MAXIMUM_TARGET_LATERAL_DISTANCE_SQUARED) {
			return null;
		}

		BlockPos feet = BlockPos.containing(
			zombie.getX(),
			zombie.getBoundingBox().minY + 0.01,
			zombie.getZ()
		);
		BlockPos gap = feet.relative(direction);
		BlockPos landing = gap.relative(direction);
		if (!ZombieTraversalRules.canStandAt(zombie.level(), feet)
			|| ZombieTraversalRules.hasStableSupport(zombie.level(), gap.below())
			|| !ZombieTraversalRules.isClearColumn(zombie.level(), gap, 3)
			|| !ZombieTraversalRules.canStandAt(zombie.level(), landing)
			|| !ZombieTraversalRules.isClearColumn(zombie.level(), landing, 3)) {
			return null;
		}
		return new JumpPlan(direction, landing.immutable(), motion);
	}

	private static Direction dominantDirection(final Vec3 delta) {
		if (Math.abs(delta.x) >= Math.abs(delta.z)) {
			return delta.x >= 0.0 ? Direction.EAST : Direction.WEST;
		}
		return delta.z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
	}

	private static void faceDirection(final Zombie zombie, final Vec3 direction) {
		float yaw = Mth.wrapDegrees((float)(Mth.atan2(direction.z, direction.x) * 180.0 / Math.PI) - 90.0F);
		zombie.setYRot(yaw);
		zombie.setYHeadRot(yaw);
		zombie.setYBodyRot(yaw);
	}

	record JumpPlan(Direction direction, BlockPos landing, Vec3 motion) {
	}
}
