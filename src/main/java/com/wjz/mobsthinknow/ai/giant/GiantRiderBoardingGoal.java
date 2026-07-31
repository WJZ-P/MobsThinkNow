package com.wjz.mobsthinknow.ai.giant;

import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 让小队射手以“跳向低掌—巨人接住—举到肩部—送上头顶”的连续动作登乘。
 *
 * <p>射手被接住后 Goal 仍继续运行，直至完整举升结束；不再发生低空短跳后直接瞬移
 * 12 格的现象。登乘期间右手被专门预留，左手载荷状态机仍可独立工作。</p>
 */
public final class GiantRiderBoardingGoal extends Goal {
	private static final double LEAP_DISTANCE_SQUARED = 5.2 * 5.2;
	private static final double CATCH_DISTANCE_SQUARED = 5.8 * 5.8;
	private static final int MINIMUM_LEAP_TICKS = 4;
	private static final int MAXIMUM_LEAP_TICKS = 12;
	private final AbstractSkeleton skeleton;
	private @Nullable Giant giant;
	private int repathCooldown;
	private int leapTicks;
	private int mountedPhaseTicks;
	private boolean leaping;
	private boolean completed;

	public GiantRiderBoardingGoal(final AbstractSkeleton skeleton) {
		this.skeleton = skeleton;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (!enabled() || !this.skeleton.isAlive() || this.skeleton.getType() != EntityType.SKELETON
			|| this.skeleton.isPassenger() || !(this.skeleton.level() instanceof ServerLevel level)) {
			return false;
		}
		Giant assigned = ZombieSquadCoordinator.forLevel(level).activeGiantMountFor(this.skeleton);
		if (!validMount(assigned)
			|| !GiantPassengerLayout.canAcceptHeadRider(assigned, this.skeleton)
			|| GiantTacticsState.hasPayloadReservation(assigned, GiantHand.RIGHT)) {
			return false;
		}
		this.giant = assigned;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		if (this.completed || !enabled() || !this.skeleton.isAlive() || !validMount(this.giant)
			|| !(this.skeleton.level() instanceof ServerLevel level)) {
			return false;
		}
		Giant current = this.giant;
		if (ZombieSquadCoordinator.forLevel(level).assignedGiantMountFor(this.skeleton) != current
			|| !GiantTacticsState.isBoardingRider(current, this.skeleton)) {
			return false;
		}
		if (this.skeleton.isPassenger()) {
			return this.skeleton.getVehicle() == current
				&& GiantTacticsState.boardingPhase(current) != GiantBoardingPhase.NONE;
		}
		double radius = ConfigManager.get().coordinationRadius;
		return this.skeleton.distanceToSqr(current) <= radius * radius * 4.0;
	}

	@Override
	public void start() {
		this.repathCooldown = 0;
		this.leapTicks = 0;
		this.mountedPhaseTicks = 0;
		this.leaping = false;
		this.completed = false;
		this.skeleton.setAggressive(false);
		Giant current = this.giant;
		if (current != null) {
			GiantTacticsState.beginBoarding(current, this.skeleton);
		}
	}

	@Override
	public void stop() {
		this.skeleton.getNavigation().stop();
		Giant current = this.giant;
		if (!this.completed && current != null
			&& GiantTacticsState.isBoardingRider(current, this.skeleton)) {
			if (this.skeleton.getVehicle() == current) {
				this.skeleton.stopRiding();
				this.skeleton.snapTo(
					current.getX(),
					current.getY() + 0.25,
					current.getZ(),
					this.skeleton.getYRot(),
					this.skeleton.getXRot()
				);
			}
			GiantTacticsState.clearBoarding(current);
		}
		this.skeleton.setAggressive(this.skeleton.getTarget() != null);
		this.giant = null;
		this.leaping = false;
		this.leapTicks = 0;
		this.mountedPhaseTicks = 0;
	}

	@Override
	public void tick() {
		Giant current = this.giant;
		if (current == null) {
			return;
		}
		if (this.skeleton.getVehicle() == current) {
			this.tickMounted(current);
			return;
		}

		this.skeleton.getLookControl().setLookAt(current, 70.0F, 55.0F);
		if (!this.leaping) {
			if (this.skeleton.distanceToSqr(current) <= LEAP_DISTANCE_SQUARED) {
				this.beginLeap(current);
				return;
			}
			if (--this.repathCooldown <= 0 || this.skeleton.getNavigation().isDone()) {
				this.repathCooldown = 6;
				this.skeleton.getNavigation().moveTo(current, 1.18);
			}
			return;
		}

		this.leapTicks++;
		this.steerLeap(current);
		if (this.leapTicks >= MINIMUM_LEAP_TICKS && this.skeleton.distanceToSqr(current) <= CATCH_DISTANCE_SQUARED) {
			this.catchInPalm(current);
		} else if (this.leapTicks >= MAXIMUM_LEAP_TICKS) {
			this.leaping = false;
			this.leapTicks = 0;
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void beginLeap(final Giant current) {
		this.leaping = true;
		this.leapTicks = 0;
		this.skeleton.getNavigation().stop();
		this.skeleton.lookAt(EntityAnchorArgument.Anchor.EYES, current.getEyePosition());
		Vec3 horizontal = current.position().subtract(this.skeleton.position()).multiply(1.0, 0.0, 1.0);
		Vec3 direction = horizontal.lengthSqr() < 1.0E-6 ? Vec3.ZERO : horizontal.normalize();
		this.skeleton.setDeltaMovement(direction.scale(0.48).add(0.0, 0.72, 0.0));
		this.skeleton.setOnGround(false);
		this.skeleton.playSound(SoundEvents.SKELETON_STEP, 0.8F, 0.72F);
	}

	private void steerLeap(final Giant current) {
		Vec3 offset = current.position().subtract(this.skeleton.position()).multiply(1.0, 0.0, 1.0);
		if (offset.lengthSqr() < 1.0E-6) {
			return;
		}
		Vec3 desired = offset.normalize().scale(Mth.clamp(offset.length() * 0.12, 0.24, 0.48));
		Vec3 movement = this.skeleton.getDeltaMovement();
		this.skeleton.setDeltaMovement(
			Mth.lerp(0.28, movement.x, desired.x),
			movement.y,
			Mth.lerp(0.28, movement.z, desired.z)
		);
	}

	private void catchInPalm(final Giant current) {
		if (!GiantPassengerLayout.canAcceptHeadRider(current, this.skeleton)
			|| GiantTacticsState.hasPayloadReservation(current, GiantHand.RIGHT)
			|| !this.skeleton.startRiding(current, true, true)) {
			this.leaping = false;
			return;
		}
		this.skeleton.getNavigation().stop();
		this.skeleton.setDeltaMovement(Vec3.ZERO);
		this.mountedPhaseTicks = 0;
		GiantTacticsState.transitionBoarding(current, GiantBoardingPhase.LIFTING);
		current.positionRider(this.skeleton);
		current.playSound(SoundEvents.IRON_GOLEM_STEP, 0.9F, 0.72F);
		if (current.level() instanceof ServerLevel level) {
			Vec3 palm = this.skeleton.position();
			level.sendParticles(ParticleTypes.POOF, palm.x, palm.y + 0.5, palm.z, 9, 0.45, 0.25, 0.45, 0.02);
		}
	}

	private void tickMounted(final Giant current) {
		this.mountedPhaseTicks++;
		if (this.skeleton.getTarget() != null) {
			this.skeleton.lookAt(EntityAnchorArgument.Anchor.EYES, this.skeleton.getTarget().getEyePosition());
		} else {
			this.skeleton.getLookControl().setLookAt(current.getLookAngle().add(current.getEyePosition()));
		}
		current.positionRider(this.skeleton);

		switch (GiantTacticsState.boardingPhase(current)) {
			case LIFTING -> {
				if (this.mountedPhaseTicks >= GiantBoardingPhase.LIFTING.durationTicks()) {
					this.transitionMounted(current, GiantBoardingPhase.SHOULDER);
					current.playSound(SoundEvents.IRON_GOLEM_STEP, 0.75F, 0.86F);
				}
			}
			case SHOULDER -> {
				if (this.mountedPhaseTicks >= GiantBoardingPhase.SHOULDER.durationTicks()) {
					this.transitionMounted(current, GiantBoardingPhase.TO_HEAD);
				}
			}
			case TO_HEAD -> {
				if (this.mountedPhaseTicks >= GiantBoardingPhase.TO_HEAD.durationTicks()) {
					this.completeBoarding(current);
				}
			}
			case CATCHING, NONE -> {
				// CATCHING 只存在于射手尚未成为乘客时；NONE 表示流程已由外部结束。
			}
		}
	}

	private void transitionMounted(final Giant current, final GiantBoardingPhase next) {
		this.mountedPhaseTicks = 0;
		GiantTacticsState.transitionBoarding(current, next);
		current.positionRider(this.skeleton);
	}

	private void completeBoarding(final Giant current) {
		GiantTacticsState.clearBoarding(current);
		current.positionRider(this.skeleton);
		this.skeleton.setAggressive(this.skeleton.getTarget() != null);
		current.playSound(SoundEvents.IRON_GOLEM_STEP, 0.85F, 1.02F);
		if (current.level() instanceof ServerLevel level) {
			Vec3 head = this.skeleton.position();
			level.sendParticles(ParticleTypes.POOF, head.x, head.y, head.z, 7, 0.35, 0.20, 0.35, 0.015);
		}
		SmartGiantMetrics.riderMounted();
		this.completed = true;
	}

	private static boolean validMount(final @Nullable Giant giant) {
		return giant != null && giant.isAlive() && giant.getType() == EntityType.GIANT;
	}

	private static boolean enabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled
			&& config.packSurrounding
			&& config.skeletonAiEnabled
			&& config.giantZombieAiEnabled
			&& config.giantZombiePayloadThrowing;
	}
}
