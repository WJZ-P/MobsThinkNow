package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.nether.NetherCombatMath;
import com.wjz.mobsthinknow.ai.nether.SmartNetherMetrics;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在原版恶魂射击 Goal 内替换启用时的 tick，关闭配置时原方法原样继续。
 * 这样不会复制或删除原版 Goal，也不会破坏其他模组对 GoalSelector 的排序。
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Ghast$GhastShootFireballGoal")
public abstract class GhastShootFireballGoalMixin {
	@Shadow
	@Final
	private Ghast ghast;
	@Shadow
	public int chargeTime;
	@Unique
	private long mobsthinknow$nextRelocationAt;
	@Unique
	private int mobsthinknow$shotIndex;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void mobsthinknow$tickSmartArtillery(final CallbackInfo callbackInfo) {
		var config = ConfigManager.get();
		if (!config.enabled || !config.netherAiEnabled || !config.ghastArtilleryTactics) {
			return;
		}
		callbackInfo.cancel();

		LivingEntity target = this.ghast.getTarget();
		if (target == null || !target.isAlive()) {
			this.ghast.setCharging(false);
			return;
		}
		long now = this.ghast.level().getGameTime();
		if (now >= this.mobsthinknow$nextRelocationAt || this.ghast.distanceToSqr(target) < 12.0 * 12.0) {
			this.mobsthinknow$relocateAround(target, now);
		}

		double distanceSquared = target.distanceToSqr(this.ghast);
		boolean firingSolution = distanceSquared < 64.0 * 64.0 && this.ghast.hasLineOfSight(target);
		if (firingSolution) {
			this.chargeTime++;
			if (this.chargeTime == 10 && !this.ghast.isSilent()) {
				this.ghast.level().levelEvent(null, 1015, this.ghast.blockPosition(), 0);
			}
			if (this.chargeTime == 20 && this.mobsthinknow$fireAtPredictedPoint(target, config.netherPredictionStrength)) {
				this.chargeTime = -40;
				this.mobsthinknow$shotIndex++;
				this.mobsthinknow$nextRelocationAt = now;
				this.mobsthinknow$relocateAround(target, now);
			}
		} else if (this.chargeTime > 0) {
			this.chargeTime--;
		}
		this.ghast.setCharging(this.chargeTime > 10);
	}

	@Unique
	private boolean mobsthinknow$fireAtPredictedPoint(final LivingEntity target, final double predictionStrength) {
		if (!(this.ghast.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 view = this.ghast.getViewVector(1.0F);
		Vec3 source = new Vec3(
			this.ghast.getX() + view.x * 4.0,
			this.ghast.getY(0.5) + 0.5,
			this.ghast.getZ() + view.z * 4.0
		);
		double distance = source.distanceTo(target.getEyePosition());
		Vec3 predicted = NetherCombatMath.predictedPoint(
			target.getEyePosition(),
			target.getDeltaMovement(),
			distance,
			1.0,
			predictionStrength,
			12.0
		);
		Vec3 direction = predicted.subtract(source);
		if (direction.lengthSqr() < 1.0E-8) {
			return false;
		}
		LargeFireball fireball = new LargeFireball(
			level,
			this.ghast,
			direction.normalize(),
			this.ghast.getExplosionPower()
		);
		fireball.setPos(source);
		if (!level.addFreshEntity(fireball)) {
			return false;
		}
		if (!this.ghast.isSilent()) {
			level.levelEvent(null, 1016, this.ghast.blockPosition(), 0);
		}
		SmartNetherMetrics.ghastShot();
		return true;
	}

	@Unique
	private void mobsthinknow$relocateAround(final LivingEntity target, final long now) {
		Vec3 away = NetherCombatMath.horizontalUnitOrEntityFallback(
			this.ghast.position().subtract(target.position()),
			this.ghast.getId()
		);
		double side = ((this.ghast.getId() + this.mobsthinknow$shotIndex) & 1) == 0 ? 1.0 : -1.0;
		double turn = this.ghast.distanceToSqr(target) < 12.0 * 12.0 ? 0.15 : 0.72;
		Vec3 direction = NetherCombatMath.rotateHorizontal(away, side * turn);
		double radius = 22.0 + Math.floorMod(this.ghast.getId() * 3 + this.mobsthinknow$shotIndex * 5, 7);
		Vec3 destination = target.position().add(direction.scale(radius)).add(
			0.0,
			7.0 + Math.floorMod(this.ghast.getId() + this.mobsthinknow$shotIndex, 5),
			0.0
		);
		this.ghast.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 1.0);
		this.mobsthinknow$nextRelocationAt = now + 45L + Math.floorMod(this.ghast.getId(), 20);
		SmartNetherMetrics.ghastRelocation();
	}
}
