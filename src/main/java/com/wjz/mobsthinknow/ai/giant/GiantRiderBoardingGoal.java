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

/** 让小队射手以可见跳跃动作登上巨人头顶，登顶后立即把弓弩 Goal 的控制权还给射手。 */
public final class GiantRiderBoardingGoal extends Goal {
	private static final double LEAP_DISTANCE_SQUARED = 5.2 * 5.2;
	private static final double CATCH_DISTANCE_SQUARED = 5.8 * 5.8;
	private static final int MINIMUM_LEAP_TICKS = 4;
	private static final int MAXIMUM_LEAP_TICKS = 12;
	private final AbstractSkeleton skeleton;
	private @Nullable Giant giant;
	private int repathCooldown;
	private int leapTicks;
	private boolean leaping;

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
		if (!validMount(assigned) || (GiantPassengerLayout.headRider(assigned) != null
			&& GiantPassengerLayout.headRider(assigned) != this.skeleton)) {
			return false;
		}
		this.giant = assigned;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		if (!enabled() || this.skeleton.isPassenger() || !validMount(this.giant)
			|| !(this.skeleton.level() instanceof ServerLevel level)) {
			return false;
		}
		return ZombieSquadCoordinator.forLevel(level).assignedGiantMountFor(this.skeleton) == this.giant
			&& this.skeleton.distanceToSqr(this.giant) <= ConfigManager.get().coordinationRadius
				* ConfigManager.get().coordinationRadius * 4.0;
	}

	@Override
	public void start() {
		this.repathCooldown = 0;
		this.leapTicks = 0;
		this.leaping = false;
		this.skeleton.setAggressive(false);
	}

	@Override
	public void stop() {
		this.skeleton.getNavigation().stop();
		this.skeleton.setAggressive(this.skeleton.getTarget() != null);
		this.giant = null;
		this.leaping = false;
		this.leapTicks = 0;
	}

	@Override
	public void tick() {
		Giant current = this.giant;
		if (current == null) {
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
			this.completeBoarding(current);
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

	private void completeBoarding(final Giant current) {
		if (!GiantPassengerLayout.hasFreeHeadSeat(current)
			|| !this.skeleton.startRiding(current, true, true)) {
			this.leaping = false;
			return;
		}
		this.skeleton.getNavigation().stop();
		this.skeleton.setDeltaMovement(Vec3.ZERO);
		if (this.skeleton.getTarget() != null) {
			this.skeleton.lookAt(EntityAnchorArgument.Anchor.EYES, this.skeleton.getTarget().getEyePosition());
		}
		current.playSound(SoundEvents.IRON_GOLEM_STEP, 0.9F, 0.72F);
		if (current.level() instanceof ServerLevel level) {
			Vec3 head = GiantPassengerLayout.ridingPosition(current, this.skeleton);
			level.sendParticles(ParticleTypes.POOF, head.x, head.y, head.z, 9, 0.45, 0.25, 0.45, 0.02);
		}
		SmartGiantMetrics.riderMounted();
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
