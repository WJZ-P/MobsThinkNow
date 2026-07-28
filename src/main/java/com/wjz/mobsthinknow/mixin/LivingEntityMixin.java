package com.wjz.mobsthinknow.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.wjz.mobsthinknow.ai.zombie.ZombieShieldMemory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 修正原版“完全盾挡仍播放实体受伤声”，同时保留盾牌自身的格挡声。 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "applyItemBlocking", at = @At("RETURN"))
	private void mobsthinknow$rememberFullyBlockedZombieHit(
		final ServerLevel level,
		final DamageSource source,
		final float damage,
		final CallbackInfoReturnable<Float> callbackInfo
	) {
		if ((Object)this instanceof Zombie zombie) {
			ZombieShieldMemory.recordItemBlockingResolution(zombie, source, damage, callbackInfo.getReturnValueF());
		}
	}

	@WrapWithCondition(
		method = "hurtServer",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;playHurtSound(Lnet/minecraft/world/damagesource/DamageSource;)V"
		)
	)
	private boolean mobsthinknow$skipZombieHurtSoundAfterFullShieldBlock(
		final LivingEntity entity,
		final DamageSource source
	) {
		return !(entity instanceof Zombie zombie)
			|| !ZombieShieldMemory.shouldSuppressHurtSound(zombie, source);
	}

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void mobsthinknow$clearShieldSoundResolution(
		final ServerLevel level,
		final DamageSource source,
		final float damage,
		final CallbackInfoReturnable<Boolean> callbackInfo
	) {
		if ((Object)this instanceof Zombie zombie) {
			ZombieShieldMemory.finishDamageSoundResolution(zombie);
		}
	}
}
