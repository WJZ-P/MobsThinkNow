package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.ai.giant.GiantArmAnimation;
import com.wjz.mobsthinknow.ai.giant.GiantBoardingPhase;
import com.wjz.mobsthinknow.ai.giant.GiantHand;
import com.wjz.mobsthinknow.ai.giant.GiantHandPhase;
import com.wjz.mobsthinknow.client.render.GiantCarrierRenderStateAccess;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.zombie.AbstractZombieModel;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 保留 {@link HumanoidModel} 已计算好的胸前格挡动作。
 *
 * <p>原版调用顺序是：先由 {@code HumanoidModel.setupAnim} 套用 {@code BLOCK}，再由
 * {@code AbstractZombieModel.setupAnim} 强制把双臂改回僵尸前伸姿势。注入点位于第二步之前；
 * 只有确实存在格挡臂时才结束本方法，因此另一只手仍保留人形模型计算出的自然持械/走路动作。</p>
 */
@Mixin(AbstractZombieModel.class)
public abstract class AbstractZombieModelMixin {
	/** 每只手按自己的阶段采样关键帧；登乘流程会临时覆盖右手载荷动作。 */
	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void mobsthinknow$poseGiantTacticalHands(
		final ZombieRenderState state,
		final CallbackInfo callbackInfo
	) {
		GiantCarrierRenderStateAccess carrier = (GiantCarrierRenderStateAccess)state;
		HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
		GiantBoardingPhase boardingPhase = carrier.mobsthinknow$getGiantBoardingPhase();
		GiantArmAnimation.ArmPose rightPose = boardingPhase == GiantBoardingPhase.NONE
			? mobsthinknow$sampleHand(carrier, GiantHand.RIGHT)
			: GiantArmAnimation.boardingPose(boardingPhase, carrier.mobsthinknow$getGiantBoardingProgress());
		mobsthinknow$applyPose(model.rightArm, rightPose);
		mobsthinknow$applyPose(model.leftArm, mobsthinknow$sampleHand(carrier, GiantHand.LEFT));
	}

	@Unique
	private static GiantArmAnimation.ArmPose mobsthinknow$sampleHand(
		final GiantCarrierRenderStateAccess carrier,
		final GiantHand hand
	) {
		GiantHandPhase phase = carrier.mobsthinknow$getGiantHandPhase(hand);
		if (phase == GiantHandPhase.EMPTY && carrier.mobsthinknow$isGiantHandLoaded(hand)) {
			phase = GiantHandPhase.HOLDING;
		}
		return GiantArmAnimation.handPose(hand, phase, carrier.mobsthinknow$getGiantHandProgress(hand));
	}

	@Unique
	private static void mobsthinknow$applyPose(
		final ModelPart arm,
		final GiantArmAnimation.ArmPose pose
	) {
		if (pose.weight() <= 0.0F) {
			return;
		}
		arm.xRot = Mth.lerp(pose.weight(), arm.xRot, pose.xRot());
		arm.yRot = Mth.lerp(pose.weight(), arm.yRot, pose.yRot());
		arm.zRot = Mth.lerp(pose.weight(), arm.zRot, pose.zRot());
	}

	@Inject(
		method = "setupAnim",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/model/AnimationUtils;animateZombieArms(Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;ZLnet/minecraft/client/renderer/entity/state/UndeadRenderState;)V"
		),
		cancellable = true
	)
	private void mobsthinknow$preserveUsingPose(
		final ZombieRenderState state,
		final CallbackInfo callbackInfo
	) {
		if (state.isUsingItem
			&& (state.leftArmPose == HumanoidModel.ArmPose.BLOCK
				|| state.rightArmPose == HumanoidModel.ArmPose.BLOCK)) {
			callbackInfo.cancel();
			return;
		}
		if (!state.isUsingItem) {
			return;
		}

		HumanoidArm usingArm = state.useItemHand == InteractionHand.MAIN_HAND
			? state.mainArm
			: state.mainArm.getOpposite();
		ItemStack usedItem = state.getUseItemStackForArm(usingArm);
		if (state.isFallFlying && usedItem.has(DataComponents.KINETIC_WEAPON)) {
			// HumanoidModel 已经算出双手持矛俯冲姿势；跳过僵尸双臂前伸覆盖，视觉上才是真正“举矛冲锋”。
			callbackInfo.cancel();
			return;
		}
		ItemUseAnimation animation = usedItem.getUseAnimation();
		if (!usedItem.has(DataComponents.FOOD)
			|| (animation != ItemUseAnimation.EAT && animation != ItemUseAnimation.DRINK)) {
			return;
		}

		// HumanoidModel 已先完成走路和 ITEM 基础姿态；这里只把使用手抬到嘴边并加入轻微咀嚼摆动。
		ModelPart arm = ((HumanoidModel<?>)(Object)this).getArm(usingArm);
		float chewing = Mth.sin(state.ticksUsingItem * 0.9F) * 0.08F;
		arm.xRot = (animation == ItemUseAnimation.DRINK ? -1.45F : -1.25F) + chewing;
		arm.yRot = usingArm == HumanoidArm.RIGHT ? -0.18F : 0.18F;
		arm.zRot = usingArm == HumanoidArm.RIGHT ? 0.04F : -0.04F;
		// 取消后续的僵尸双臂前伸覆盖；另一只手保留 HumanoidModel 已算好的自然持械姿势。
		callbackInfo.cancel();
	}
}
