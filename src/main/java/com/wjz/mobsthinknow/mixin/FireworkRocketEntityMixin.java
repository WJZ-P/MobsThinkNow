package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.zombie.ZombieAirAssault;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 只缩放带本 Mod 同步标记的僵尸附着式烟花；普通玩家烟花的原版物理不变。 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {
	@Shadow
	private @Nullable LivingEntity attachedToEntity;

	@ModifyArg(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"
		),
		index = 0
	)
	private Vec3 mobsthinknow$scaleZombieRocketBoost(final Vec3 vanillaMovement) {
		if (!(this.attachedToEntity instanceof Zombie zombie)) {
			return vanillaMovement;
		}
		ItemStack firedRocket = ((FireworkRocketEntity)(Object)this).getItem();
		if (!ZombieAirAssault.hasMarkedRocketEfficiency(firedRocket)) {
			return vanillaMovement;
		}
		return ZombieAirAssault.rocketBoostMovement(
			zombie.getDeltaMovement(),
			zombie.getLookAngle(),
			ZombieAirAssault.markedRocketEfficiency(firedRocket)
		);
	}
}
