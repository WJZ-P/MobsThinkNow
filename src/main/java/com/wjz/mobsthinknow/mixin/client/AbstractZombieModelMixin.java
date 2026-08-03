package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.ai.giant.GiantArmAnimation;
import com.wjz.mobsthinknow.ai.giant.GiantBoardingPhase;
import com.wjz.mobsthinknow.ai.giant.GiantHand;
import com.wjz.mobsthinknow.ai.giant.GiantHandPhase;
import com.wjz.mobsthinknow.ai.giant.GiantMeleeAnimation;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAnimation;
import com.wjz.mobsthinknow.ai.zombie.ZombieProfession;
import com.wjz.mobsthinknow.client.render.GiantCarrierRenderStateAccess;
import com.wjz.mobsthinknow.client.render.ZombieBodyActionRenderStateAccess;
import com.wjz.mobsthinknow.client.render.ZombieProfessionRenderStateAccess;
import com.wjz.mobsthinknow.config.ConfigManager;
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
import net.minecraft.world.item.Items;
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
		mobsthinknow$applyZombieBodyLanguage(model, state);
		mobsthinknow$applyMeleePose(
			model,
			GiantMeleeAnimation.sample(
				carrier.mobsthinknow$getGiantMeleeAction(),
				carrier.mobsthinknow$getGiantMeleeProgress()
			)
		);
		GiantBoardingPhase boardingPhase = carrier.mobsthinknow$getGiantBoardingPhase();
		GiantArmAnimation.ArmPose rightPose = boardingPhase == GiantBoardingPhase.NONE
			? mobsthinknow$sampleHand(carrier, GiantHand.RIGHT)
			: GiantArmAnimation.boardingPose(boardingPhase, carrier.mobsthinknow$getGiantBoardingProgress());
		mobsthinknow$applyPose(model.rightArm, rightPose);
		mobsthinknow$applyPose(model.leftArm, mobsthinknow$sampleHand(carrier, GiantHand.LEFT));
	}

	@Unique
	private static void mobsthinknow$applyZombieBodyLanguage(
		final HumanoidModel<?> model,
		final ZombieRenderState state
	) {
		if (!ConfigManager.get().zombieBodyLanguage
			|| state.isUsingItem
			|| state.isFallFlying
			|| state.isVisuallySwimming
			|| state.swimAmount > 0.25F
			|| state.isPassenger) {
			return;
		}

		ZombieBodyActionRenderStateAccess actionState = (ZombieBodyActionRenderStateAccess)state;
		ZombieBodyAction action = actionState.mobsthinknow$getBodyAction();
		if (state.attackTime <= 0.01F) {
			int blendTicks = ConfigManager.get().zombieAnimationBlendTicks;
			ZombieBodyAction previous = actionState.mobsthinknow$getPreviousBodyAction();
			float transitionElapsed = actionState.mobsthinknow$getBodyActionTransitionElapsedTicks();
			if (blendTicks > 0 && previous != action && transitionElapsed < blendTicks) {
				ZombieBodyAnimation.BodyPose previousPose = mobsthinknow$sampleBodyAction(
					state,
					previous,
					actionState.mobsthinknow$getPreviousBodyActionElapsedTicks()
				);
				ZombieBodyAnimation.BodyPose currentPose = mobsthinknow$sampleBodyAction(
					state,
					action,
					actionState.mobsthinknow$getBodyActionElapsedTicks()
				);
				mobsthinknow$applyZombiePose(
					model,
					ZombieBodyAnimation.blend(previousPose, currentPose, transitionElapsed / blendTicks)
				);
				return;
			}
			if (action != ZombieBodyAction.NONE) {
				mobsthinknow$applyZombiePose(
					model,
					mobsthinknow$sampleBodyAction(
						state,
						action,
						actionState.mobsthinknow$getBodyActionElapsedTicks()
					)
				);
				return;
			}
		}

		if (!state.isAggressive || state.attackTime > 0.01F || state.getMainHandItemStack().isEmpty()) {
			return;
		}
		ZombieProfession profession = ((ZombieProfessionRenderStateAccess)state).mobsthinknow$getZombieProfession();
		mobsthinknow$applyZombiePose(
			model,
			ZombieBodyAnimation.combatReady(
				profession,
				state.mainArm == HumanoidArm.RIGHT,
				state.ageInTicks,
				state.walkAnimationSpeed
			)
		);
	}

	@Unique
	private static ZombieBodyAnimation.BodyPose mobsthinknow$sampleBodyAction(
		final ZombieRenderState state,
		final ZombieBodyAction action,
		final float elapsedTicks
	) {
		return ZombieBodyAnimation.sample(
			action,
			elapsedTicks,
			state.ageInTicks,
			mobsthinknow$actionArmIsRight(state, action),
			!state.rightHandItemStack.isEmpty(),
			!state.leftHandItemStack.isEmpty()
		);
	}

	@Unique
	private static boolean mobsthinknow$actionArmIsRight(
		final ZombieRenderState state,
		final ZombieBodyAction action
	) {
		if (action == ZombieBodyAction.SHIELD_BASH) {
			// 盾牌由副手持有，因此动作臂和主手相反。
			return state.mainArm != HumanoidArm.RIGHT;
		}
		if (action == ZombieBodyAction.SHIELD_TAP) {
			boolean rightShield = state.rightHandItemStack.is(Items.SHIELD);
			boolean leftShield = state.leftHandItemStack.is(Items.SHIELD);
			if (rightShield != leftShield) {
				return !rightShield;
			}
		}
		if (action == ZombieBodyAction.ENGINEER_WORK || action == ZombieBodyAction.ENGINEER_CHECK) {
			boolean rightTool = mobsthinknow$isEngineerTool(state.rightHandItemStack);
			boolean leftTool = mobsthinknow$isEngineerTool(state.leftHandItemStack);
			if (rightTool != leftTool) {
				return rightTool;
			}
		}
		if (mobsthinknow$prefersFreeGestureHand(action)) {
			boolean rightEmpty = state.rightHandItemStack.isEmpty();
			boolean leftEmpty = state.leftHandItemStack.isEmpty();
			if (rightEmpty != leftEmpty) {
				return rightEmpty;
			}
		}
		return state.mainArm == HumanoidArm.RIGHT;
	}

	@Unique
	private static boolean mobsthinknow$prefersFreeGestureHand(final ZombieBodyAction action) {
		return action == ZombieBodyAction.ACKNOWLEDGE
			|| action == ZombieBodyAction.COMMAND
			|| action == ZombieBodyAction.CALL_TO_MEETING
			|| action == ZombieBodyAction.CONFER
			|| action == ZombieBodyAction.ADVANCE_ORDER
			|| action == ZombieBodyAction.SUCCESSION_SALUTE;
	}

	@Unique
	private static boolean mobsthinknow$isEngineerTool(final ItemStack stack) {
		return stack.is(Items.TNT) || stack.is(Items.FLINT_AND_STEEL);
	}

	@Unique
	private static void mobsthinknow$applyZombiePose(
		final HumanoidModel<?> model,
		final ZombieBodyAnimation.BodyPose pose
	) {
		mobsthinknow$applyZombiePart(model.rightArm, pose.rightArm());
		mobsthinknow$applyZombiePart(model.leftArm, pose.leftArm());
		mobsthinknow$applyZombiePart(model.body, pose.body());
		mobsthinknow$applyZombiePart(model.rightLeg, pose.rightLeg());
		mobsthinknow$applyZombiePart(model.leftLeg, pose.leftLeg());
		ZombieBodyAnimation.PartPose head = pose.head();
		if (head.weight() > 0.0F) {
			// 头部采用相对偏移，保留 LookControl 已计算的目标朝向；否则点头会突然把脸扭回正前方。
			model.head.xRot += head.xRot() * head.weight();
			model.head.yRot += head.yRot() * head.weight();
			model.head.zRot += head.zRot() * head.weight();
		}
	}

	@Unique
	private static void mobsthinknow$applyZombiePart(
		final ModelPart part,
		final ZombieBodyAnimation.PartPose pose
	) {
		if (pose.weight() <= 0.0F) {
			return;
		}
		part.xRot = Mth.lerp(pose.weight(), part.xRot, pose.xRot());
		part.yRot = Mth.lerp(pose.weight(), part.yRot, pose.yRot());
		part.zRot = Mth.lerp(pose.weight(), part.zRot, pose.zRot());
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

	@Unique
	private static void mobsthinknow$applyMeleePose(
		final HumanoidModel<?> model,
		final GiantMeleeAnimation.BodyPose pose
	) {
		mobsthinknow$applyPartPose(model.rightArm, pose.rightArm());
		mobsthinknow$applyPartPose(model.leftArm, pose.leftArm());
		mobsthinknow$applyPartPose(model.body, pose.body());
		mobsthinknow$applyPartPose(model.rightLeg, pose.rightLeg());
		mobsthinknow$applyPartPose(model.leftLeg, pose.leftLeg());
		mobsthinknow$applyPartPose(model.head, pose.head());
	}

	@Unique
	private static void mobsthinknow$applyPartPose(
		final ModelPart part,
		final GiantMeleeAnimation.PartPose pose
	) {
		if (pose.weight() <= 0.0F) {
			return;
		}
		part.xRot = Mth.lerp(pose.weight(), part.xRot, pose.xRot());
		part.yRot = Mth.lerp(pose.weight(), part.yRot, pose.yRot());
		part.zRot = Mth.lerp(pose.weight(), part.zRot, pose.zRot());
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
