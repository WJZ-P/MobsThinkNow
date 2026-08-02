package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.nether.NetherCombatMath;
import com.wjz.mobsthinknow.ai.nether.NetherProfessionProfile;
import com.wjz.mobsthinknow.ai.nether.NetherProfessionTactics;
import com.wjz.mobsthinknow.ai.nether.SmartNetherMetrics;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 把岩浆怪原本只朝当前朝向跳跃，增强为每次离地时锁定一次短期预测落点。 */
@Mixin(MagmaCube.class)
public abstract class MagmaCubeMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void mobsthinknow$countController(final CallbackInfo callbackInfo) {
		SmartNetherMetrics.controllerInstalled();
	}

	@Inject(method = "jumpFromGround", at = @At("TAIL"))
	private void mobsthinknow$applyPredictivePounce(final CallbackInfo callbackInfo) {
		MagmaCube cube = (MagmaCube)(Object)this;
		var config = ConfigManager.get();
		if (cube.level().isClientSide()
			|| !config.enabled
			|| !config.netherAiEnabled
			|| !config.magmaCubePredictivePounce) {
			return;
		}
		LivingEntity target = cube.getTarget();
		if (target == null
			|| !target.isAlive()
			|| !cube.canAttack(target)
			|| !cube.getSensing().hasLineOfSight(target)) {
			return;
		}
		double distanceSquared = cube.distanceToSqr(target);
		if (distanceSquared < 2.5 * 2.5 || distanceSquared > 16.0 * 16.0) {
			return;
		}

		double leadTicks = Math.min(6.0, Math.sqrt(distanceSquared) * 0.45);
		Vec3 direction = NetherCombatMath.predictiveHorizontalDirection(
			cube.position(),
			target.position(),
			target.getDeltaMovement(),
			leadTicks,
			cube.getId()
		);
		double sizeFactor = 0.82 + Math.min(cube.getSize(), 4) * 0.045;
		double difficultyFactor = 0.85 + cube.level().getDifficulty().getId() * 0.05;
		double speed = config.magmaCubePounceSpeed
			* sizeFactor
			* difficultyFactor
			* NetherProfessionTactics.magmaPounceMultiplier(NetherProfessionProfile.get(cube));
		Vec3 current = cube.getDeltaMovement();
		cube.setDeltaMovement(
			current.x * 0.18 + direction.x * speed,
			current.y,
			current.z * 0.18 + direction.z * speed
		);
		SmartNetherMetrics.magmaPounce();
	}
}
