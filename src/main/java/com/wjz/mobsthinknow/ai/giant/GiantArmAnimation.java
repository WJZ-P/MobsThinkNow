package com.wjz.mobsthinknow.ai.giant;

/**
 * 与渲染器无关的巨人手臂关键帧采样器，方便用普通 JUnit 锁定动作连续性。
 *
 * <p>x/y/z 对应 HumanoidModel 手臂欧拉角；weight 控制本 Mod 姿势覆盖原版走路摆臂
 * 的强度。左右手由同一组关键帧镜像生成。</p>
 */
public final class GiantArmAnimation {
	private static final ArmPose HOLD = new ArmPose(-1.03F, -0.12F, 0.10F, 1.0F);
	private static final ArmPose AIM = new ArmPose(0.28F, -0.38F, 0.18F, 1.0F);
	private static final ArmPose THROW_FORWARD = new ArmPose(-1.78F, -0.08F, 0.05F, 1.0F);
	private static final ArmPose REACH_LOW = new ArmPose(0.20F, -0.46F, 0.20F, 1.0F);
	private static final ArmPose BOARDING_SHOULDER = new ArmPose(-1.58F, -0.34F, 0.12F, 1.0F);
	private static final ArmPose BOARDING_OVERHEAD = new ArmPose(-2.78F, -0.08F, 0.04F, 1.0F);

	private GiantArmAnimation() {
	}

	public static ArmPose handPose(
		final GiantHand hand,
		final GiantHandPhase phase,
		final float rawProgress
	) {
		float progress = clamp(rawProgress);
		ArmPose right = switch (phase) {
			case EMPTY -> ArmPose.NONE;
			case RENDEZVOUS -> new ArmPose(-0.34F, -0.14F, 0.08F, 0.42F);
			case PICKUP -> progress < 0.52F
				? lerp(
					new ArmPose(-0.34F, -0.14F, 0.08F, 0.55F),
					REACH_LOW,
					smooth(progress / 0.52F)
				)
				: lerp(REACH_LOW, HOLD, smooth((progress - 0.52F) / 0.48F));
			case HOLDING -> HOLD;
			case AIMING -> lerp(HOLD, AIM, smooth(progress));
			case THROWING -> progress < 0.55F
				? lerp(AIM, THROW_FORWARD, smooth(progress / 0.55F))
				: lerp(THROW_FORWARD, HOLD, smooth((progress - 0.55F) / 0.45F));
			case COOLDOWN -> new ArmPose(HOLD.xRot, HOLD.yRot, HOLD.zRot, 1.0F - smooth(progress));
		};
		return hand == GiantHand.RIGHT ? right : mirror(right);
	}

	/** 登乘流程独占右臂；最后 35% 在射手落到头顶后自然把手收回。 */
	public static ArmPose boardingPose(final GiantBoardingPhase phase, final float rawProgress) {
		float progress = clamp(rawProgress);
		return switch (phase) {
			case NONE -> ArmPose.NONE;
			case CATCHING -> lerp(
				new ArmPose(0.0F, 0.0F, 0.0F, 0.0F),
				REACH_LOW,
				smooth(progress)
			);
			case LIFTING -> lerp(REACH_LOW, BOARDING_SHOULDER, smooth(progress));
			case SHOULDER -> BOARDING_SHOULDER;
			case TO_HEAD -> {
				if (progress < 0.65F) {
					yield lerp(BOARDING_SHOULDER, BOARDING_OVERHEAD, smooth(progress / 0.65F));
				}
				float recovery = smooth((progress - 0.65F) / 0.35F);
				yield new ArmPose(
					lerp(BOARDING_OVERHEAD.xRot, 0.0F, recovery),
					lerp(BOARDING_OVERHEAD.yRot, 0.0F, recovery),
					lerp(BOARDING_OVERHEAD.zRot, 0.0F, recovery),
					1.0F - recovery
				);
			}
		};
	}

	private static ArmPose mirror(final ArmPose pose) {
		return new ArmPose(pose.xRot, -pose.yRot, -pose.zRot, pose.weight);
	}

	private static ArmPose lerp(final ArmPose from, final ArmPose to, final float rawProgress) {
		float progress = clamp(rawProgress);
		return new ArmPose(
			lerp(from.xRot, to.xRot, progress),
			lerp(from.yRot, to.yRot, progress),
			lerp(from.zRot, to.zRot, progress),
			lerp(from.weight, to.weight, progress)
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

	public record ArmPose(float xRot, float yRot, float zRot, float weight) {
		public static final ArmPose NONE = new ArmPose(0.0F, 0.0F, 0.0F, 0.0F);
	}
}
