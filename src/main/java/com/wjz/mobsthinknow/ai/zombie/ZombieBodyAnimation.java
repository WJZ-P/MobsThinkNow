package com.wjz.mobsthinknow.ai.zombie;

/**
 * 普通僵尸全身动作的纯关键帧采样器。
 *
 * <p>不依赖客户端模型类，因此所有姿势、镜像和收势都能由单元测试覆盖。模型 Mixin 只负责
 * 把这里的结果混合到原版走路、持物和攻击姿势上。</p>
 */
public final class ZombieBodyAnimation {
	private ZombieBodyAnimation() {
	}

	public static BodyPose sample(
		final ZombieBodyAction action,
		final float elapsedTicks,
		final float ageInTicks
	) {
		return sample(action, elapsedTicks, ageInTicks, true);
	}

	/**
	 * @param actionArmRight {@code true} 表示动作使用右臂；盾击会传入副手，工程操作会传入实际工具手
	 */
	public static BodyPose sample(
		final ZombieBodyAction action,
		final float elapsedTicks,
		final float ageInTicks,
		final boolean actionArmRight
	) {
		return switch (action) {
			case NONE -> BodyPose.NONE;
			case ACKNOWLEDGE -> acknowledge(progress(action, elapsedTicks));
			case COMMAND -> command(progress(action, elapsedTicks));
			case WAR_CRY -> warCry(progress(action, elapsedTicks));
			case RETREAT -> retreat(elapsedTicks, ageInTicks);
			case SWORD_FEINT -> actionHanded(swordFeint(elapsedTicks), actionArmRight);
			case AXE_WINDUP -> actionHanded(axeWindup(progress(action, elapsedTicks)), actionArmRight);
			case AXE_LEAP -> actionHanded(axeLeap(elapsedTicks), actionArmRight);
			case SHIELD_BASH -> actionHanded(shieldBash(progress(action, elapsedTicks)), actionArmRight);
			case ENGINEER_WORK -> actionHanded(engineerWork(elapsedTicks, ageInTicks), actionArmRight);
		};
	}

	/** 武装僵尸在攻击间隙不再恢复成双臂平举；剑在胸前，斧则蓄在肩后。 */
	public static BodyPose combatReady(
		final ZombieProfession profession,
		final boolean rightHanded,
		final float ageInTicks,
		final float walkAnimationSpeed
	) {
		float movementReduction = 1.0F - 0.28F * clamp(walkAnimationSpeed);
		float breathing = (float)Math.sin(ageInTicks * 0.09F) * 0.035F;
		PartPose weaponArm = switch (profession) {
			case SWORDSMAN -> new PartPose(-1.02F + breathing, -0.24F, 0.08F, 0.88F * movementReduction);
			case SWORD_GUARD -> new PartPose(-0.92F + breathing, -0.18F, 0.06F, 0.72F * movementReduction);
			case AXEMAN -> new PartPose(-1.78F + breathing, 0.28F, 0.24F, 0.90F * movementReduction);
			case AXE_GUARD -> new PartPose(-1.64F + breathing, 0.22F, 0.20F, 0.74F * movementReduction);
			default -> PartPose.NONE;
		};
		if (weaponArm.weight() <= 0.0F) {
			return BodyPose.NONE;
		}
		PartPose body = new PartPose(0.02F, rightHanded ? -0.06F : 0.06F, 0.0F, 0.42F * movementReduction);
		return rightHanded
			? new BodyPose(weaponArm, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, PartPose.NONE)
			: new BodyPose(PartPose.NONE, mirror(weaponArm), mirror(body), PartPose.NONE, PartPose.NONE, PartPose.NONE);
	}

	private static BodyPose command(final float progress) {
		float weight = envelope(progress, 0.24F, 0.76F);
		PartPose rightArm = new PartPose(-1.34F, -0.54F, 0.14F, weight);
		PartPose leftArm = new PartPose(-0.46F, 0.18F, -0.10F, weight * 0.58F);
		PartPose body = new PartPose(-0.05F, -0.16F, 0.0F, weight * 0.72F);
		PartPose head = new PartPose(-0.05F, 0.08F, 0.0F, weight * 0.65F);
		return new BodyPose(rightArm, leftArm, body, PartPose.NONE, PartPose.NONE, head);
	}

	private static BodyPose acknowledge(final float progress) {
		float weight = envelope(progress, 0.22F, 0.72F);
		float nod = (float)Math.sin(progress * Math.PI * 2.0) * 0.18F;
		PartPose chestTap = new PartPose(-1.02F, -0.46F, 0.12F, weight * 0.82F);
		PartPose body = new PartPose(0.05F, 0.08F, 0.0F, weight * 0.45F);
		PartPose head = new PartPose(nod, -0.05F, 0.0F, weight);
		return new BodyPose(chestTap, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, head);
	}

	private static BodyPose warCry(final float progress) {
		float weight = envelope(progress, 0.20F, 0.74F);
		PartPose rightArm = new PartPose(-2.18F, -0.24F, 0.30F, weight);
		PartPose leftArm = mirror(rightArm);
		PartPose body = new PartPose(-0.16F, 0.0F, 0.0F, weight * 0.82F);
		PartPose rightLeg = new PartPose(0.10F, 0.0F, -0.08F, weight * 0.48F);
		PartPose leftLeg = mirror(rightLeg);
		PartPose head = new PartPose(-0.24F, 0.0F, 0.0F, weight * 0.90F);
		return new BodyPose(rightArm, leftArm, body, rightLeg, leftLeg, head);
	}

	private static BodyPose retreat(final float elapsedTicks, final float ageInTicks) {
		float weight = smooth(elapsedTicks / 5.0F);
		float stride = (float)Math.sin(ageInTicks * 1.20F);
		PartPose rightArm = new PartPose(-0.24F + stride * 0.78F, 0.0F, 0.08F, weight);
		PartPose leftArm = new PartPose(-0.24F - stride * 0.78F, 0.0F, -0.08F, weight);
		PartPose body = new PartPose(0.30F, 0.0F, 0.0F, weight * 0.88F);
		PartPose rightLeg = new PartPose(-stride * 0.82F, 0.0F, 0.0F, weight * 0.85F);
		PartPose leftLeg = new PartPose(stride * 0.82F, 0.0F, 0.0F, weight * 0.85F);
		// 轻微抬头抵消前倾身体造成的“盯着地面”，视线仍由 LookControl 决定。
		PartPose head = new PartPose(-0.16F, 0.0F, 0.0F, weight * 0.82F);
		return new BodyPose(rightArm, leftArm, body, rightLeg, leftLeg, head);
	}

	/** 假挥在第 6 tick 达到最像真攻击的位置，随后明显收剑后撤，全程没有真实命中帧。 */
	private static BodyPose swordFeint(final float elapsedTicks) {
		float progress = clamp(elapsedTicks / ZombieBodyAction.SWORD_FEINT.durationTicks());
		float lunge = piecewiseEnvelope(progress, 0.12F, 0.34F, 0.58F, 0.92F);
		float recoil = progress < 0.46F ? 0.0F : envelope((progress - 0.46F) / 0.54F, 0.22F, 0.78F);
		PartPose actionArm = new PartPose(-1.58F + recoil * 0.54F, -0.42F, 0.10F, lunge);
		PartPose otherArm = new PartPose(-0.52F, 0.22F, -0.10F, lunge * 0.50F);
		PartPose body = new PartPose(0.16F - recoil * 0.08F, -0.28F, 0.0F, lunge * 0.82F);
		PartPose actionLeg = new PartPose(-0.34F, 0.0F, 0.0F, lunge * 0.58F);
		PartPose otherLeg = new PartPose(0.38F, 0.0F, 0.0F, lunge * 0.58F);
		PartPose head = new PartPose(-0.06F, 0.18F, 0.0F, lunge * 0.45F);
		return new BodyPose(actionArm, otherArm, body, actionLeg, otherLeg, head);
	}

	private static BodyPose axeWindup(final float progress) {
		float weight = envelope(progress, 0.34F, 0.92F);
		PartPose actionArm = new PartPose(-2.55F, -0.22F, 0.18F, weight);
		PartPose otherArm = new PartPose(-1.96F, 0.30F, -0.18F, weight * 0.74F);
		PartPose body = new PartPose(0.34F, -0.10F, 0.0F, weight * 0.92F);
		PartPose actionLeg = new PartPose(0.48F, 0.0F, 0.0F, weight * 0.88F);
		PartPose otherLeg = new PartPose(0.16F, 0.0F, 0.0F, weight * 0.72F);
		PartPose head = new PartPose(-0.24F, 0.08F, 0.0F, weight * 0.72F);
		return new BodyPose(actionArm, otherArm, body, actionLeg, otherLeg, head);
	}

	private static BodyPose axeLeap(final float elapsedTicks) {
		float weight = smooth(elapsedTicks / 3.0F);
		PartPose actionArm = new PartPose(-2.72F, -0.18F, 0.16F, weight);
		PartPose otherArm = new PartPose(-2.28F, 0.24F, -0.18F, weight * 0.82F);
		PartPose body = new PartPose(-0.18F, -0.08F, 0.0F, weight * 0.92F);
		PartPose actionLeg = new PartPose(-0.30F, 0.0F, 0.0F, weight * 0.75F);
		PartPose otherLeg = new PartPose(0.62F, 0.0F, 0.0F, weight * 0.82F);
		PartPose head = new PartPose(0.12F, 0.06F, 0.0F, weight * 0.58F);
		return new BodyPose(actionArm, otherArm, body, actionLeg, otherLeg, head);
	}

	/** 盾臂先收至胸口，再在中段完成一次方正、短促的正面撞击。 */
	private static BodyPose shieldBash(final float progress) {
		float thrust = piecewiseEnvelope(progress, 0.16F, 0.38F, 0.62F, 0.90F);
		PartPose shieldArm = new PartPose(-1.42F, -0.18F, 0.10F, thrust);
		PartPose weaponArm = new PartPose(-0.58F, 0.32F, -0.12F, thrust * 0.58F);
		PartPose body = new PartPose(0.22F, -0.22F, 0.0F, thrust * 0.92F);
		PartPose shieldLeg = new PartPose(-0.50F, 0.0F, 0.0F, thrust * 0.82F);
		PartPose otherLeg = new PartPose(0.30F, 0.0F, 0.0F, thrust * 0.68F);
		PartPose head = new PartPose(-0.08F, 0.12F, 0.0F, thrust * 0.42F);
		return new BodyPose(shieldArm, weaponArm, body, shieldLeg, otherLeg, head);
	}

	/** 工程兵单膝降低重心，工具臂以较慢频率摆动；真实点燃 tick 仍会叠加原版挥手。 */
	private static BodyPose engineerWork(final float elapsedTicks, final float ageInTicks) {
		float weight = smooth(elapsedTicks / 4.0F);
		float work = (float)Math.sin(ageInTicks * 0.42F) * 0.16F;
		PartPose toolArm = new PartPose(-1.04F + work, -0.26F, 0.10F, weight);
		PartPose otherArm = new PartPose(-0.58F, 0.20F, -0.08F, weight * 0.62F);
		PartPose body = new PartPose(0.54F, -0.10F, 0.0F, weight * 0.94F);
		PartPose kneelingLeg = new PartPose(1.16F, 0.0F, 0.0F, weight * 0.92F);
		PartPose bracedLeg = new PartPose(0.32F, 0.0F, 0.0F, weight * 0.76F);
		PartPose head = new PartPose(0.34F, 0.08F, 0.0F, weight * 0.68F);
		return new BodyPose(toolArm, otherArm, body, kneelingLeg, bracedLeg, head);
	}

	/** 采样器统一以“动作臂在右侧”建模，左手实体只需做一次完整左右镜像。 */
	private static BodyPose actionHanded(final BodyPose pose, final boolean actionArmRight) {
		if (actionArmRight) {
			return pose;
		}
		return new BodyPose(
			mirror(pose.leftArm()),
			mirror(pose.rightArm()),
			mirror(pose.body()),
			mirror(pose.leftLeg()),
			mirror(pose.rightLeg()),
			mirror(pose.head())
		);
	}

	private static float progress(final ZombieBodyAction action, final float elapsedTicks) {
		return action.durationTicks() <= 0 ? 0.0F : clamp(elapsedTicks / action.durationTicks());
	}

	private static float envelope(
		final float progress,
		final float windupEnd,
		final float recoveryStart
	) {
		if (progress < windupEnd) {
			return smooth(progress / windupEnd);
		}
		if (progress > recoveryStart) {
			return 1.0F - smooth((progress - recoveryStart) / (1.0F - recoveryStart));
		}
		return 1.0F;
	}

	private static float piecewiseEnvelope(
		final float progress,
		final float windupStart,
		final float peakStart,
		final float peakEnd,
		final float recoveryEnd
	) {
		if (progress <= windupStart || progress >= recoveryEnd) {
			return 0.0F;
		}
		if (progress < peakStart) {
			return smooth((progress - windupStart) / (peakStart - windupStart));
		}
		if (progress <= peakEnd) {
			return 1.0F;
		}
		return 1.0F - smooth((progress - peakEnd) / (recoveryEnd - peakEnd));
	}

	private static PartPose mirror(final PartPose pose) {
		return new PartPose(pose.xRot(), -pose.yRot(), -pose.zRot(), pose.weight());
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
