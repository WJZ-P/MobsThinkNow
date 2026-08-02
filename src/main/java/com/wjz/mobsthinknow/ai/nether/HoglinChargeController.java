package com.wjz.mobsthinknow.ai.nether;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 有明确蓄力、单次冲撞和冷却的疣猪兽直线突击状态机。 */
public final class HoglinChargeController {
	private static final double MINIMUM_START_DISTANCE_SQUARED = 6.0 * 6.0;
	private static final double MAXIMUM_START_DISTANCE_SQUARED = 13.0 * 13.0;
	private static final double IMPACT_DISTANCE_SQUARED = 2.45 * 2.45;
	private static final int MAXIMUM_CHARGE_TICKS = 24;
	private static final int RECOVERY_TICKS = 50;

	private Phase phase = Phase.IDLE;
	private int ticksRemaining;
	private int targetEntityId = -1;
	private int chargeStartedAt;
	private Vec3 chargeDirection = Vec3.ZERO;
	private boolean impactResolved;

	public void tick(final ServerLevel level, final Mob body, final boolean adult) {
		MobsThinkNowConfig config = ConfigManager.get();
		LivingEntity target = body.getTarget();
		if (!enabled(config)
			|| !adult
			|| body.isPassenger()
			|| target == null
			|| !target.isAlive()
			|| !body.canAttack(target)) {
			this.reset();
			return;
		}
		if (this.targetEntityId != -1 && this.targetEntityId != target.getId()) {
			this.reset();
		}

		switch (this.phase) {
			case IDLE -> this.tickIdle(level, body, target);
			case WINDUP -> this.tickWindup(level, body, target, config);
			case CHARGING -> this.tickCharging(level, body, target, config);
			case RECOVERING -> {
				if (--this.ticksRemaining <= 0) {
					this.reset();
				}
			}
		}
	}

	private void tickIdle(final ServerLevel level, final Mob body, final LivingEntity target) {
		double distanceSquared = body.distanceToSqr(target);
		if (!body.onGround()
			|| distanceSquared < MINIMUM_START_DISTANCE_SQUARED
			|| distanceSquared > MAXIMUM_START_DISTANCE_SQUARED
			|| Math.abs(target.getY() - body.getY()) > 2.0
			|| !body.getSensing().hasLineOfSight(target)) {
			return;
		}
		Vec3 direction = NetherCombatMath.predictiveHorizontalDirection(
			body.position(),
			target.position(),
			target.getDeltaMovement(),
			3.0,
			body.getId()
		);
		if (!this.hasSafeChargeLane(level, body, direction, Math.sqrt(distanceSquared))) {
			return;
		}

		this.phase = Phase.WINDUP;
		this.ticksRemaining = 12 + Math.floorMod(body.getId(), 5);
		this.targetEntityId = target.getId();
		this.chargeDirection = direction;
		this.impactResolved = false;
		level.playSound(
			null,
			body.getX(),
			body.getY(),
			body.getZ(),
			body instanceof Zoglin ? SoundEvents.ZOGLIN_ANGRY : SoundEvents.HOGLIN_ANGRY,
			body.getSoundSource(),
			1.0F,
			0.78F
		);
	}

	private void tickWindup(
		final ServerLevel level,
		final Mob body,
		final LivingEntity target,
		final MobsThinkNowConfig config
	) {
		if (!body.onGround()
			|| body.distanceToSqr(target) > (15.0 * 15.0)
			|| !body.getSensing().hasLineOfSight(target)) {
			this.enterRecovery();
			return;
		}
		body.getNavigation().stop();
		body.getLookControl().setLookAt(target, 45.0F, 30.0F);
		Vec3 movement = body.getDeltaMovement();
		body.setDeltaMovement(movement.x * 0.35, movement.y, movement.z * 0.35);
		if (--this.ticksRemaining > 0) {
			return;
		}

		this.chargeDirection = NetherCombatMath.predictiveHorizontalDirection(
			body.position(),
			target.position(),
			target.getDeltaMovement(),
			3.0,
			body.getId()
		);
		if (!this.hasSafeChargeLane(level, body, this.chargeDirection, Math.sqrt(body.distanceToSqr(target)))) {
			this.enterRecovery();
			return;
		}
		double impulse = chargeImpulse(config.hoglinChargeSpeed);
		Vec3 current = body.getDeltaMovement();
		body.setDeltaMovement(
			this.chargeDirection.x * impulse,
			Math.max(current.y, 0.28),
			this.chargeDirection.z * impulse
		);
		this.phase = Phase.CHARGING;
		this.ticksRemaining = MAXIMUM_CHARGE_TICKS;
		this.chargeStartedAt = body.tickCount;
		SmartNetherMetrics.hoglinCharge();
	}

	private void tickCharging(
		final ServerLevel level,
		final Mob body,
		final LivingEntity target,
		final MobsThinkNowConfig config
	) {
		Vec3 lookPoint = body.position().add(this.chargeDirection.scale(8.0)).add(0.0, body.getEyeHeight(), 0.0);
		body.getLookControl().setLookAt(lookPoint.x, lookPoint.y, lookPoint.z, 50.0F, 25.0F);
		Vec3 predicted = target.position().add(target.getDeltaMovement().scale(2.0));
		body.getNavigation().moveTo(predicted.x, predicted.y, predicted.z, config.hoglinChargeSpeed);

		if (!this.impactResolved && body.distanceToSqr(target) <= IMPACT_DISTANCE_SQUARED) {
			boolean alreadyHitDuringCharge = body.getLastHurtMob() == target
				&& body.getLastHurtMobTimestamp() >= this.chargeStartedAt;
			if (!alreadyHitDuringCharge) {
				body.doHurtTarget(level, target);
			}
			this.impactResolved = true;
			SmartNetherMetrics.hoglinImpact();
			this.enterRecovery();
			return;
		}
		if (body.horizontalCollision || --this.ticksRemaining <= 0) {
			this.enterRecovery();
		}
	}

	private boolean hasSafeChargeLane(
		final ServerLevel level,
		final Mob body,
		final Vec3 direction,
		final double distance
	) {
		double maximum = Math.min(7.0, Math.max(2.0, distance - 1.5));
		for (double step = 2.0; step <= maximum; step += 2.0) {
			Vec3 sample = body.position().add(direction.scale(step));
			if (!this.hasSupportAndClearance(level, body, sample)) {
				return false;
			}
		}
		return true;
	}

	private boolean hasSupportAndClearance(final ServerLevel level, final Mob body, final Vec3 sample) {
		BlockPos base = BlockPos.containing(sample.x, body.getY(), sample.z);
		for (int dy = 1; dy >= -1; dy--) {
			BlockPos feet = base.offset(0, dy, 0);
			BlockPos support = feet.below();
			if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
				continue;
			}
			double dx = feet.getX() + 0.5 - body.getX();
			double dyMove = feet.getY() - body.getY();
			double dz = feet.getZ() + 0.5 - body.getZ();
			AABB box = body.getBoundingBox().move(dx, dyMove, dz);
			if (level.noCollision(body, box)) {
				return true;
			}
		}
		return false;
	}

	private void enterRecovery() {
		this.phase = Phase.RECOVERING;
		this.ticksRemaining = RECOVERY_TICKS;
	}

	private void reset() {
		this.phase = Phase.IDLE;
		this.ticksRemaining = 0;
		this.targetEntityId = -1;
		this.chargeDirection = Vec3.ZERO;
		this.impactResolved = false;
	}

	public Phase phase() {
		return this.phase;
	}

	public int ticksRemaining() {
		return this.ticksRemaining;
	}

	static boolean enabled(final MobsThinkNowConfig config) {
		return config.enabled && config.netherAiEnabled && config.hoglinChargeTactics;
	}

	static double chargeImpulse(final double configuredSpeed) {
		return 0.48 + (configuredSpeed - MobsThinkNowConfig.MINIMUM_HOGLIN_CHARGE_SPEED) * 0.24;
	}

	public enum Phase {
		IDLE,
		WINDUP,
		CHARGING,
		RECOVERING
	}
}
