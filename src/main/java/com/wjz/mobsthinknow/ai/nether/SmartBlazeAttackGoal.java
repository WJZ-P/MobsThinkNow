package com.wjz.mobsthinknow.ai.nether;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/**
 * 烈焰人的空中散兵状态机。
 *
 * <p>原版攻击 Goal 保留在优先级 4；本 Goal 只在配置开启时以优先级 3 抢占 MOVE/LOOK，
 * 因此运行时关闭配置会立即无缝回到原版实现。个体用实体 ID 决定左右盘旋方向，不需要
 * 每只烈焰人扫描所有同伴，也就不会把群战变成 O(N²) 查询。</p>
 */
public final class SmartBlazeAttackGoal extends Goal {
	private static final int LOST_SIGHT_ABORT_TICKS = 12;
	private static final int MELEE_COOLDOWN_TICKS = 20;
	private static final double MINIMUM_MELEE_DISTANCE_SQUARED = 2.2 * 2.2;
	private static final double MAXIMUM_FIRE_DISTANCE_SQUARED = 32.0 * 32.0;

	private final Blaze blaze;
	private int attackStep;
	private int attackTime;
	private int lastSeen;
	private int meleeCooldown;
	private double orbitDirection;

	public SmartBlazeAttackGoal(final Blaze blaze) {
		this.blaze = blaze;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.blaze.getTarget();
		MobsThinkNowConfig config = ConfigManager.get();
		return enabled(config)
			&& target != null
			&& target.isAlive()
			&& this.blaze.canAttack(target);
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public void start() {
		this.attackStep = 0;
		this.attackTime = 10 + Math.floorMod(this.blaze.getId(), 11);
		this.lastSeen = 0;
		this.meleeCooldown = 0;
		this.orbitDirection = (this.blaze.getId() & 1) == 0 ? 1.0 : -1.0;
	}

	@Override
	public void stop() {
		this.setCharged(false);
		this.attackStep = 0;
		this.lastSeen = 0;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = this.blaze.getTarget();
		if (target == null) {
			return;
		}
		MobsThinkNowConfig config = ConfigManager.get();
		NetherProfession profession = NetherProfessionProfile.get(this.blaze);
		boolean visible = this.blaze.getSensing().hasLineOfSight(target);
		this.lastSeen = visible ? 0 : this.lastSeen + 1;
		this.attackTime--;
		this.meleeCooldown--;

		double distanceSquared = this.blaze.distanceToSqr(target);
		this.steerAround(target, config, distanceSquared, visible, profession);
		this.blaze.getLookControl().setLookAt(target, 30.0F, 30.0F);

		if (distanceSquared <= MINIMUM_MELEE_DISTANCE_SQUARED && visible) {
			if (this.meleeCooldown <= 0 && this.blaze.level() instanceof ServerLevel level) {
				this.blaze.doHurtTarget(level, target);
				this.meleeCooldown = MELEE_COOLDOWN_TICKS;
			}
			return;
		}

		if (!visible || distanceSquared > MAXIMUM_FIRE_DISTANCE_SQUARED) {
			if (this.lastSeen >= LOST_SIGHT_ABORT_TICKS) {
				this.attackStep = 0;
				this.setCharged(false);
				this.attackTime = Math.max(this.attackTime, 10);
			}
			return;
		}

		if (this.attackTime > 0) {
			return;
		}
		if (this.attackStep == 0) {
			this.attackStep = 1;
			this.attackTime = NetherProfessionTactics.blazeChargeTicks(
				chargeTicks(this.blaze.level().getDifficulty().getId()),
				profession
			);
			this.setCharged(true);
			SmartNetherMetrics.blazeVolley();
			return;
		}

		if (!this.launchFireball(target, config, profession)) {
			this.attackTime = 4;
			return;
		}
		int volleySize = NetherProfessionTactics.blazeVolleySize(
			volleySize(this.blaze.level().getDifficulty().getId()),
			profession
		);
		if (this.attackStep >= volleySize) {
			this.attackStep = 0;
			this.attackTime = 64 + this.blaze.getRandom().nextInt(25);
			this.orbitDirection = -this.orbitDirection;
			this.setCharged(false);
		} else {
			this.attackStep++;
			this.attackTime = 8;
		}
	}

	private void steerAround(
		final LivingEntity target,
		final MobsThinkNowConfig config,
		final double distanceSquared,
		final boolean visible,
		final NetherProfession profession
	) {
		Vec3 away = NetherCombatMath.horizontalUnitOrEntityFallback(
			this.blaze.position().subtract(target.position()),
			this.blaze.getId()
		);
		double preferred = config.blazePreferredRange
			* NetherProfessionTactics.blazeRangeMultiplier(profession);
		double distance = Math.sqrt(distanceSquared);
		double turn = distance < preferred * 0.65 ? 0.18 : 0.42;
		Vec3 radial = NetherCombatMath.rotateHorizontal(away, this.orbitDirection * turn);
		double radius = distance < preferred * 0.65 ? preferred + 2.0 : preferred;
		if (!visible && this.lastSeen < LOST_SIGHT_ABORT_TICKS) {
			radius = Math.max(5.0, preferred - 2.0);
		}
		double altitude = target.getEyeY() + 2.0
			+ Math.sin((this.blaze.tickCount + Math.floorMod(this.blaze.getId(), 17)) * 0.09) * 1.2;
		Vec3 destination = target.position().add(radial.scale(radius));
		this.blaze.getMoveControl().setWantedPosition(destination.x, altitude, destination.z, 1.05);
	}

	private boolean launchFireball(
		final LivingEntity target,
		final MobsThinkNowConfig config,
		final NetherProfession profession
	) {
		if (!(this.blaze.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 source = new Vec3(this.blaze.getX(), this.blaze.getY(0.5) + 0.5, this.blaze.getZ());
		double distance = source.distanceTo(target.getEyePosition());
		Vec3 predicted = NetherCombatMath.predictedPoint(
			target.getEyePosition(),
			target.getDeltaMovement(),
			distance,
			1.0,
			config.netherPredictionStrength,
			8.0
		);
		double uncertainty = (0.10 + (3 - this.blaze.level().getDifficulty().getId()) * 0.035)
			* NetherProfessionTactics.blazeUncertaintyMultiplier(profession);
		Vec3 direction = predicted.subtract(source).add(
			this.blaze.getRandom().triangle(0.0, uncertainty),
			this.blaze.getRandom().triangle(0.0, uncertainty * 0.65),
			this.blaze.getRandom().triangle(0.0, uncertainty)
		);
		if (direction.lengthSqr() < 1.0E-8) {
			return false;
		}
		SmallFireball fireball = new SmallFireball(level, this.blaze, direction.normalize());
		fireball.setPos(source);
		if (!level.addFreshEntity(fireball)) {
			return false;
		}
		if (!this.blaze.isSilent()) {
			level.levelEvent(null, 1018, this.blaze.blockPosition(), 0);
		}
		SmartNetherMetrics.blazeFireball();
		return true;
	}

	private void setCharged(final boolean charged) {
		((BlazeChargeAccess)this.blaze).mobsthinknow$setSmartCharged(charged);
	}

	static boolean enabled(final MobsThinkNowConfig config) {
		return config.enabled && config.netherAiEnabled && config.blazeCombatTactics;
	}

	static int volleySize(final int difficultyId) {
		return Mth.clamp(difficultyId + 1, 2, 4);
	}

	static int chargeTicks(final int difficultyId) {
		return switch (Mth.clamp(difficultyId, 1, 3)) {
			case 1 -> 36;
			case 2 -> 30;
			default -> 24;
		};
	}
}
