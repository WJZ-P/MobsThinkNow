package com.wjz.mobsthinknow.ai.giant;

/**
 * 巨人全身近战关键帧采样器。
 *
 * <p>动作完全由服务端同步的枚举与进度驱动，不读取客户端本地随机数。手臂载荷姿势会在渲染层
 * 最后覆盖对应格斗手臂，因此踩踏可以在抱着队友时播放，而横扫、拍击和砸地只使用服务端已经
 * 判定为空闲的手。正蹬使用腿部，抓取则让指定空手完成伸手、抬起、后拉和抛出。</p>
 */
public final class GiantMeleeAnimation {
	private static final PartPose SWEEP_WINDUP_ARM = new PartPose(0.18F, -1.32F, 0.62F, 1.0F);
	private static final PartPose SWEEP_IMPACT_ARM = new PartPose(0.24F, 1.24F, -0.18F, 1.0F);
	private static final PartPose SLAP_WINDUP_ARM = new PartPose(-0.34F, -0.86F, 0.54F, 1.0F);
	private static final PartPose SLAP_IMPACT_ARM = new PartPose(0.08F, 0.22F, -0.08F, 1.0F);
	private static final PartPose SMASH_OVERHEAD_ARM = new PartPose(-2.72F, -0.20F, 0.10F, 1.0F);
	private static final PartPose SMASH_IMPACT_ARM = new PartPose(0.42F, -0.05F, 0.02F, 1.0F);
	private static final PartPose GRAB_WINDUP_ARM = new PartPose(0.34F, -0.62F, 0.48F, 1.0F);
	private static final PartPose GRAB_REACH_ARM = new PartPose(-1.24F, -0.18F, 0.12F, 1.0F);
	private static final PartPose GRAB_HOLD_ARM = new PartPose(-0.72F, -0.34F, 0.16F, 1.0F);
	private static final PartPose GRAB_THROW_ARM = new PartPose(-1.42F, 0.18F, -0.10F, 1.0F);

	private GiantMeleeAnimation() {
	}

	public static BodyPose sample(final GiantMeleeAction action, final float rawProgress) {
		float progress = clamp(rawProgress);
		return switch (action.family()) {
			case NONE -> BodyPose.NONE;
			case SWEEP -> singleArmSweep(action.actionHand(), progress);
			case SLAP -> singleArmSlap(action.actionHand(), progress);
			case STOMP -> stomp(action == GiantMeleeAction.STOMP_RIGHT, progress);
			case GROUND_SMASH -> groundSmash(progress);
			case KICK -> kick(action == GiantMeleeAction.KICK_RIGHT, progress);
			case GRAB -> grab(action.actionHand(), progress);
		};
	}

	private static BodyPose singleArmSweep(final GiantHand hand, final float progress) {
		boolean right = hand != GiantHand.LEFT;
		PartPose arm;
		PartPose body;
		if (progress < 0.38F) {
			float phase = smooth(progress / 0.38F);
			arm = lerp(PartPose.NONE, SWEEP_WINDUP_ARM, phase);
			body = lerp(PartPose.NONE, new PartPose(0.0F, -0.34F, 0.0F, 0.75F), phase);
		} else if (progress < 0.56F) {
			float phase = smooth((progress - 0.38F) / 0.18F);
			arm = lerp(SWEEP_WINDUP_ARM, SWEEP_IMPACT_ARM, phase);
			body = lerp(
				new PartPose(0.0F, -0.34F, 0.0F, 0.75F),
				new PartPose(0.04F, 0.38F, 0.0F, 0.85F),
				phase
			);
		} else {
			float phase = smooth((progress - 0.56F) / 0.44F);
			arm = recover(SWEEP_IMPACT_ARM, phase);
			body = recover(new PartPose(0.04F, 0.38F, 0.0F, 0.85F), phase);
		}
		if (!right) {
			arm = mirror(arm);
			body = mirror(body);
		}
		return right
			? new BodyPose(arm, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, PartPose.NONE)
			: new BodyPose(PartPose.NONE, arm, body, PartPose.NONE, PartPose.NONE, PartPose.NONE);
	}

	private static BodyPose singleArmSlap(final GiantHand hand, final float progress) {
		boolean right = hand != GiantHand.LEFT;
		PartPose arm;
		PartPose body;
		if (progress < 0.40F) {
			float phase = smooth(progress / 0.40F);
			arm = lerp(PartPose.NONE, SLAP_WINDUP_ARM, phase);
			body = lerp(PartPose.NONE, new PartPose(-0.05F, -0.18F, 0.0F, 0.55F), phase);
		} else if (progress < 0.56F) {
			float phase = smooth((progress - 0.40F) / 0.16F);
			arm = lerp(SLAP_WINDUP_ARM, SLAP_IMPACT_ARM, phase);
			body = lerp(
				new PartPose(-0.05F, -0.18F, 0.0F, 0.55F),
				new PartPose(0.10F, 0.16F, 0.0F, 0.65F),
				phase
			);
		} else {
			float phase = smooth((progress - 0.56F) / 0.44F);
			arm = recover(SLAP_IMPACT_ARM, phase);
			body = recover(new PartPose(0.10F, 0.16F, 0.0F, 0.65F), phase);
		}
		if (!right) {
			arm = mirror(arm);
			body = mirror(body);
		}
		return right
			? new BodyPose(arm, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, PartPose.NONE)
			: new BodyPose(PartPose.NONE, arm, body, PartPose.NONE, PartPose.NONE, PartPose.NONE);
	}

	private static BodyPose stomp(final boolean rightFoot, final float progress) {
		PartPose leg;
		PartPose body;
		PartPose counterArm;
		if (progress < 0.42F) {
			float phase = smooth(progress / 0.42F);
			leg = lerp(PartPose.NONE, new PartPose(-1.18F, 0.0F, 0.04F, 1.0F), phase);
			body = lerp(PartPose.NONE, new PartPose(-0.12F, 0.0F, rightFoot ? -0.08F : 0.08F, 0.70F), phase);
			counterArm = lerp(PartPose.NONE, new PartPose(-0.42F, 0.0F, rightFoot ? -0.20F : 0.20F, 0.55F), phase);
		} else if (progress < 0.58F) {
			float phase = smooth((progress - 0.42F) / 0.16F);
			leg = lerp(
				new PartPose(-1.18F, 0.0F, 0.04F, 1.0F),
				new PartPose(0.34F, 0.0F, 0.0F, 1.0F),
				phase
			);
			body = lerp(
				new PartPose(-0.12F, 0.0F, rightFoot ? -0.08F : 0.08F, 0.70F),
				new PartPose(0.16F, 0.0F, 0.0F, 0.75F),
				phase
			);
			counterArm = new PartPose(-0.42F, 0.0F, rightFoot ? -0.20F : 0.20F, 0.55F);
		} else {
			float phase = smooth((progress - 0.58F) / 0.42F);
			leg = recover(new PartPose(0.34F, 0.0F, 0.0F, 1.0F), phase);
			body = recover(new PartPose(0.16F, 0.0F, 0.0F, 0.75F), phase);
			counterArm = recover(
				new PartPose(-0.42F, 0.0F, rightFoot ? -0.20F : 0.20F, 0.55F),
				phase
			);
		}
		return rightFoot
			? new BodyPose(PartPose.NONE, counterArm, body, leg, PartPose.NONE, PartPose.NONE)
			: new BodyPose(counterArm, PartPose.NONE, body, PartPose.NONE, leg, PartPose.NONE);
	}

	private static BodyPose groundSmash(final float progress) {
		PartPose rightArm;
		PartPose leftArm;
		PartPose body;
		PartPose rightLeg;
		PartPose leftLeg;
		if (progress < 0.40F) {
			float phase = smooth(progress / 0.40F);
			rightArm = lerp(PartPose.NONE, SMASH_OVERHEAD_ARM, phase);
			leftArm = mirror(rightArm);
			body = lerp(PartPose.NONE, new PartPose(-0.20F, 0.0F, 0.0F, 0.85F), phase);
			rightLeg = lerp(PartPose.NONE, new PartPose(0.12F, 0.0F, -0.08F, 0.50F), phase);
			leftLeg = mirror(rightLeg);
		} else if (progress < 0.61F) {
			float phase = smooth((progress - 0.40F) / 0.21F);
			rightArm = lerp(SMASH_OVERHEAD_ARM, SMASH_IMPACT_ARM, phase);
			leftArm = mirror(rightArm);
			body = lerp(
				new PartPose(-0.20F, 0.0F, 0.0F, 0.85F),
				new PartPose(0.48F, 0.0F, 0.0F, 1.0F),
				phase
			);
			rightLeg = lerp(
				new PartPose(0.12F, 0.0F, -0.08F, 0.50F),
				new PartPose(-0.22F, 0.0F, -0.10F, 0.70F),
				phase
			);
			leftLeg = mirror(rightLeg);
		} else {
			float phase = smooth((progress - 0.61F) / 0.39F);
			rightArm = recover(SMASH_IMPACT_ARM, phase);
			leftArm = mirror(rightArm);
			body = recover(new PartPose(0.48F, 0.0F, 0.0F, 1.0F), phase);
			rightLeg = recover(new PartPose(-0.22F, 0.0F, -0.10F, 0.70F), phase);
			leftLeg = mirror(rightLeg);
		}
		return new BodyPose(rightArm, leftArm, body, rightLeg, leftLeg, PartPose.NONE);
	}

	private static BodyPose kick(final boolean rightFoot, final float progress) {
		PartPose leg;
		PartPose body;
		PartPose counterArm;
		if (progress < 0.38F) {
			float phase = smooth(progress / 0.38F);
			leg = lerp(PartPose.NONE, new PartPose(0.58F, 0.0F, rightFoot ? 0.08F : -0.08F, 1.0F), phase);
			body = lerp(PartPose.NONE, new PartPose(-0.18F, 0.0F, rightFoot ? -0.10F : 0.10F, 0.75F), phase);
			counterArm = lerp(PartPose.NONE, new PartPose(-0.62F, 0.0F, rightFoot ? -0.22F : 0.22F, 0.70F), phase);
		} else if (progress < 0.56F) {
			float phase = smooth((progress - 0.38F) / 0.18F);
			leg = lerp(
				new PartPose(0.58F, 0.0F, rightFoot ? 0.08F : -0.08F, 1.0F),
				new PartPose(-1.34F, 0.0F, rightFoot ? -0.03F : 0.03F, 1.0F),
				phase
			);
			body = lerp(
				new PartPose(-0.18F, 0.0F, rightFoot ? -0.10F : 0.10F, 0.75F),
				new PartPose(0.20F, 0.0F, rightFoot ? 0.08F : -0.08F, 0.85F),
				phase
			);
			counterArm = new PartPose(-0.62F, 0.0F, rightFoot ? -0.22F : 0.22F, 0.70F);
		} else {
			float phase = smooth((progress - 0.56F) / 0.44F);
			leg = recover(new PartPose(-1.34F, 0.0F, rightFoot ? -0.03F : 0.03F, 1.0F), phase);
			body = recover(new PartPose(0.20F, 0.0F, rightFoot ? 0.08F : -0.08F, 0.85F), phase);
			counterArm = recover(
				new PartPose(-0.62F, 0.0F, rightFoot ? -0.22F : 0.22F, 0.70F),
				phase
			);
		}
		return rightFoot
			? new BodyPose(PartPose.NONE, counterArm, body, leg, PartPose.NONE, PartPose.NONE)
			: new BodyPose(counterArm, PartPose.NONE, body, PartPose.NONE, leg, PartPose.NONE);
	}

	private static BodyPose grab(final GiantHand hand, final float progress) {
		boolean right = hand != GiantHand.LEFT;
		PartPose arm;
		PartPose body;
		if (progress < 0.28F) {
			float phase = smooth(progress / 0.28F);
			arm = lerp(PartPose.NONE, GRAB_WINDUP_ARM, phase);
			body = lerp(PartPose.NONE, new PartPose(-0.14F, -0.12F, 0.0F, 0.70F), phase);
		} else if (progress < 0.35F) {
			float phase = smooth((progress - 0.28F) / 0.07F);
			arm = lerp(GRAB_WINDUP_ARM, GRAB_REACH_ARM, phase);
			body = lerp(
				new PartPose(-0.14F, -0.12F, 0.0F, 0.70F),
				new PartPose(0.18F, 0.10F, 0.0F, 0.82F),
				phase
			);
		} else if (progress < 0.52F) {
			float phase = smooth((progress - 0.35F) / 0.17F);
			arm = lerp(GRAB_REACH_ARM, GRAB_HOLD_ARM, phase);
			body = lerp(
				new PartPose(0.18F, 0.10F, 0.0F, 0.82F),
				new PartPose(-0.08F, -0.08F, 0.0F, 0.72F),
				phase
			);
		} else if (progress < 0.59F) {
			arm = GRAB_HOLD_ARM;
			body = new PartPose(-0.08F, -0.08F, 0.0F, 0.72F);
		} else if (progress < 0.70F) {
			float phase = smooth((progress - 0.59F) / 0.11F);
			arm = lerp(GRAB_HOLD_ARM, GRAB_THROW_ARM, phase);
			body = lerp(
				new PartPose(-0.08F, -0.08F, 0.0F, 0.72F),
				new PartPose(0.22F, 0.14F, 0.0F, 0.85F),
				phase
			);
		} else {
			float phase = smooth((progress - 0.70F) / 0.30F);
			arm = recover(GRAB_THROW_ARM, phase);
			body = recover(new PartPose(0.22F, 0.14F, 0.0F, 0.85F), phase);
		}
		if (!right) {
			arm = mirror(arm);
			body = mirror(body);
		}
		return right
			? new BodyPose(arm, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, PartPose.NONE)
			: new BodyPose(PartPose.NONE, arm, body, PartPose.NONE, PartPose.NONE, PartPose.NONE);
	}

	private static PartPose recover(final PartPose pose, final float progress) {
		return lerp(pose, PartPose.NONE, progress);
	}

	private static PartPose mirror(final PartPose pose) {
		return new PartPose(pose.xRot(), -pose.yRot(), -pose.zRot(), pose.weight());
	}

	private static PartPose lerp(final PartPose from, final PartPose to, final float rawProgress) {
		float progress = clamp(rawProgress);
		return new PartPose(
			lerp(from.xRot(), to.xRot(), progress),
			lerp(from.yRot(), to.yRot(), progress),
			lerp(from.zRot(), to.zRot(), progress),
			lerp(from.weight(), to.weight(), progress)
		);
	}

	private static float lerp(final float from, final float to, final float progress) {
		return from + (to - from) * progress;
	}

	private static float smooth(final float value) {
		float clamped = clamp(value);
		return clamped * clamped * (3.0F - 2.0F * clamped);
	}

	private static float clamp(final float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
	}

	public record PartPose(float xRot, float yRot, float zRot, float weight) {
		public static final PartPose NONE = new PartPose(0.0F, 0.0F, 0.0F, 0.0F);
	}

	public record BodyPose(
		PartPose rightArm,
		PartPose leftArm,
		PartPose body,
		PartPose rightLeg,
		PartPose leftLeg,
		PartPose head
	) {
		public static final BodyPose NONE = new BodyPose(
			PartPose.NONE,
			PartPose.NONE,
			PartPose.NONE,
			PartPose.NONE,
			PartPose.NONE,
			PartPose.NONE
		);
	}
}
