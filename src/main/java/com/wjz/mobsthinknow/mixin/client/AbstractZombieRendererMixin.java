package com.wjz.mobsthinknow.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让普通僵尸的持盾和进食手臂进入与玩家一致的基础使用姿势。 */
@Mixin(AbstractZombieRenderer.class)
public abstract class AbstractZombieRendererMixin {
	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private void mobsthinknow$selectShieldBlockingPose(
		final Zombie zombie,
		final HumanoidArm arm,
		final CallbackInfoReturnable<HumanoidModel.ArmPose> callbackInfo
	) {
		if (zombie.getType() != EntityType.ZOMBIE || !zombie.isUsingItem()) {
			return;
		}

		HumanoidArm usingArm = zombie.getUsedItemHand() == InteractionHand.MAIN_HAND
			? zombie.getMainArm()
			: zombie.getMainArm().getOpposite();
		if (arm != usingArm) {
			return;
		}
		if (zombie.getUseItem().has(DataComponents.BLOCKS_ATTACKS)) {
			callbackInfo.setReturnValue(HumanoidModel.ArmPose.BLOCK);
			return;
		}
		ItemUseAnimation animation = zombie.getUseItem().getUseAnimation();
		if (zombie.getUseItem().has(DataComponents.FOOD)
			&& (animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK)) {
			callbackInfo.setReturnValue(HumanoidModel.ArmPose.ITEM);
		}
	}
}
