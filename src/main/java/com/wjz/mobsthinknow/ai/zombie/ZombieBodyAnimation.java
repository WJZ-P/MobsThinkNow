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
		return sample(action, elapsedTicks, ageInTicks, true, false, false);
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
		return sample(action, elapsedTicks, ageInTicks, actionArmRight, false, false);
	}

	/**
	 * @param actionArmRight 动作臂；客户端优先选择空闲手，双手占用时选择主手
	 * @param rightHandOccupied 右手是否持有物品
	 * @param leftHandOccupied 左手是否持有物品
	 */
	public static BodyPose sample(
		final ZombieBodyAction action,
		final float elapsedTicks,
		final float ageInTicks,
		final boolean actionArmRight,
		final boolean rightHandOccupied,
		final boolean leftHandOccupied
	) {
		boolean bothHandsOccupied = rightHandOccupied && leftHandOccupied;
		return switch (action) {
			case NONE -> BodyPose.NONE;
			case ACKNOWLEDGE -> preserveOccupiedHands(
				actionHanded(acknowledge(progress(action, elapsedTicks)), actionArmRight),
				rightHandOccupied,
				leftHandOccupied
			);
			case COMMAND -> actionHanded(
				bothHandsOccupied ? armedCommand(progress(action, elapsedTicks)) : command(progress(action, elapsedTicks)),
				actionArmRight
			);
			case CALL_TO_MEETING -> actionHanded(
				bothHandsOccupied ? armedCallToMeeting(progress(action, elapsedTicks)) : callToMeeting(progress(action, elapsedTicks)),
				actionArmRight
			);
			case SURVEY_MEMBERS -> preserveOccupiedHands(
				surveyMembers(progress(action, elapsedTicks)),
				rightHandOccupied,
				leftHandOccupied
			);
			case COMMAND_LEFT -> preserveNonPointingHand(
				commandSide(progress(action, elapsedTicks), false),
				false,
				rightHandOccupied,
				leftHandOccupied
			);
			case COMMAND_RIGHT -> preserveNonPointingHand(
				commandSide(progress(action, elapsedTicks), true),
				true,
				rightHandOccupied,
				leftHandOccupied
			);
			case NOD -> preserveOccupiedHands(nod(progress(action, elapsedTicks)), rightHandOccupied, leftHandOccupied);
			case SHAKE_HEAD -> preserveOccupiedHands(
				shakeHead(progress(action, elapsedTicks)),
				rightHandOccupied,
				leftHandOccupied
			);
			case CONFER -> actionHanded(
				bothHandsOccupied ? armedConfer(progress(action, elapsedTicks)) : confer(progress(action, elapsedTicks)),
				actionArmRight
			);
			case ADVANCE_ORDER -> actionHanded(
				bothHandsOccupied ? armedAdvanceOrder(progress(action, elapsedTicks)) : advanceOrder(progress(action, elapsedTicks)),
				actionArmRight
			);
			case WAR_CRY -> warCry(progress(action, elapsedTicks));
			case RETREAT -> retreat(elapsedTicks, ageInTicks);
			case SWORD_FEINT -> actionHanded(swordFeint(elapsedTicks), actionArmRight);
			case AXE_WINDUP -> actionHanded(axeWindup(progress(action, elapsedTicks)), actionArmRight);
			case AXE_LEAP -> actionHanded(axeLeap(elapsedTicks), actionArmRight);
			case SHIELD_BASH -> actionHanded(shieldBash(progress(action, elapsedTicks)), actionArmRight);
			case ENGINEER_WORK -> actionHanded(engineerWork(elapsedTicks, ageInTicks), actionArmRight);
			case SHIELD_TAP -> actionHanded(shieldTap(progress(action, elapsedTicks)), actionArmRight);
			case SWORD_INSPECT -> actionHanded(swordInspect(progress(action, elapsedTicks)), actionArmRight);
			case AXE_SHOULDER -> actionHanded(axeShoulder(progress(action, elapsedTicks)), actionArmRight);
			case ENGINEER_CHECK -> actionHanded(engineerCheck(progress(action, elapsedTicks)), actionArmRight);
			case CONFUSED_TILT -> preserveOccupiedHands(
				confusedTilt(progress(action, elapsedTicks)),
				rightHandOccupied,
				leftHandOccupied
			);
			case SUCCESSION_LOOK_AROUND -> preserveOccupiedHands(
				successionLookAround(progress(action, elapsedTicks)),
				rightHandOccupied,
				leftHandOccupied
			);
			case SUCCESSION_SALUTE -> actionHanded(
				bothHandsOccupied ? armedSuccessionSalute(progress(action, elapsedTicks)) : successionSalute(progress(action, elapsedTicks)),
				actionArmRight
			);
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

	/** 双手都有装备时不强压副手姿势，只用主手武器做短促指令。 */
	private static BodyPose armedCommand(final float progress) {
		float weight = envelope(progress, 0.22F, 0.78F);
		PartPose weaponArm = new PartPose(-1.10F, -0.34F, 0.10F, weight * 0.84F);
		PartPose body = new PartPose(-0.04F, -0.14F, 0.0F, weight * 0.70F);
		PartPose head = new PartPose(-0.04F, 0.10F, 0.0F, weight * 0.62F);
		return new BodyPose(weaponArm, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, head);
	}

	private static BodyPose acknowledge(final float progress) {
		float weight = envelope(progress, 0.22F, 0.72F);
		float nod = (float)Math.sin(progress * Math.PI * 2.0) * 0.18F;
		PartPose chestTap = new PartPose(-1.02F, -0.46F, 0.12F, weight * 0.82F);
		PartPose body = new PartPose(0.05F, 0.08F, 0.0F, weight * 0.45F);
		PartPose head = new PartPose(nod, -0.05F, 0.0F, weight);
		return new BodyPose(chestTap, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, head);
	}

	/** 单臂高举并左右招手；另一只手压低，避免与原版双臂前伸轮廓混淆。 */
	private static BodyPose callToMeeting(final float progress) {
		float weight = envelope(progress, 0.16F, 0.86F);
		float wave = (float)Math.sin(progress * Math.PI * 4.0);
		PartPose rightArm = new PartPose(-2.22F, -0.52F, 0.24F + wave * 0.30F, weight);
		PartPose leftArm = new PartPose(-0.62F, 0.22F, -0.10F, weight * 0.62F);
		PartPose body = new PartPose(-0.08F, -0.14F, 0.0F, weight * 0.72F);
		PartPose head = new PartPose(-0.10F, 0.12F, -wave * 0.04F, weight * 0.74F);
		return new BodyPose(rightArm, leftArm, body, PartPose.NONE, PartPose.NONE, head);
	}

	/** 武器与盾牌同时占手时改成高举武器两次示意，避免盾臂穿过胸口。 */
	private static BodyPose armedCallToMeeting(final float progress) {
		float weight = envelope(progress, 0.16F, 0.86F);
		float beat = (1.0F - (float)Math.cos(progress * Math.PI * 4.0)) * 0.12F;
		PartPose weaponArm = new PartPose(-1.84F - beat, -0.20F, 0.18F, weight);
		PartPose body = new PartPose(-0.06F, -0.12F, 0.0F, weight * 0.68F);
		PartPose head = new PartPose(-0.12F, 0.10F, 0.0F, weight * 0.72F);
		return new BodyPose(weaponArm, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, head);
	}

	/** 头和肩膀先扫向一侧再扫向另一侧，LookControl 的基础朝向仍会保留。 */
	private static BodyPose surveyMembers(final float progress) {
		float weight = envelope(progress, 0.12F, 0.90F);
		float scan = (float)Math.sin(progress * Math.PI * 2.0);
		PartPose rightArm = new PartPose(-0.48F, -0.18F, 0.08F, weight * 0.52F);
		PartPose leftArm = mirror(rightArm);
		PartPose body = new PartPose(-0.02F, scan * 0.20F, 0.0F, weight * 0.68F);
		PartPose head = new PartPose(-0.04F, scan * 0.62F, 0.0F, weight);
		return new BodyPose(rightArm, leftArm, body, PartPose.NONE, PartPose.NONE, head);
	}

	/** 以右侧指令为母版，左侧指令做全身镜像，确保两个方向的轮廓严格对称。 */
	private static BodyPose commandSide(final float progress, final boolean right) {
		float weight = envelope(progress, 0.18F, 0.82F);
		PartPose pointingArm = new PartPose(-1.26F, -0.94F, 0.16F, weight);
		PartPose otherArm = new PartPose(-0.58F, 0.24F, -0.10F, weight * 0.56F);
		PartPose body = new PartPose(-0.04F, -0.34F, 0.0F, weight * 0.82F);
		PartPose head = new PartPose(-0.06F, -0.42F, 0.0F, weight * 0.88F);
		BodyPose rightOrder = new BodyPose(
			pointingArm,
			otherArm,
			body,
			PartPose.NONE,
			PartPose.NONE,
			head
		);
		return right ? rightOrder : mirrorBody(rightOrder);
	}

	/** 两次明确的低头—复位，而不是只做一次很难察觉的细小摆动。 */
	private static BodyPose nod(final float progress) {
		float weight = envelope(progress, 0.10F, 0.90F);
		float nod = (1.0F - (float)Math.cos(progress * Math.PI * 4.0)) * 0.18F;
		PartPose rightArm = new PartPose(-0.36F, -0.08F, 0.04F, weight * 0.34F);
		PartPose leftArm = mirror(rightArm);
		PartPose body = new PartPose(nod * 0.16F, 0.0F, 0.0F, weight * 0.38F);
		PartPose head = new PartPose(nod, 0.0F, 0.0F, weight);
		return new BodyPose(rightArm, leftArm, body, PartPose.NONE, PartPose.NONE, head);
	}

	/** 头部左右摆动两次，胸口做小幅反向补偿，保持重心稳定。 */
	private static BodyPose shakeHead(final float progress) {
		float weight = envelope(progress, 0.10F, 0.90F);
		float shake = (float)Math.sin(progress * Math.PI * 4.0);
		PartPose rightArm = new PartPose(-0.42F, -0.12F, 0.06F, weight * 0.38F);
		PartPose leftArm = mirror(rightArm);
		PartPose body = new PartPose(0.0F, -shake * 0.10F, 0.0F, weight * 0.52F);
		PartPose head = new PartPose(0.0F, shake * 0.42F, shake * 0.025F, weight);
		return new BodyPose(rightArm, leftArm, body, PartPose.NONE, PartPose.NONE, head);
	}

	/** 向惯用手一侧侧身、抬手做短促讨论动作；左撇子由调用层镜像。 */
	private static BodyPose confer(final float progress) {
		float weight = envelope(progress, 0.16F, 0.84F);
		float gesture = (float)Math.sin(progress * Math.PI * 3.0) * 0.14F;
		PartPose actionArm = new PartPose(-0.92F + gesture, -0.46F, 0.16F, weight * 0.82F);
		PartPose otherArm = new PartPose(-0.48F, 0.14F, -0.06F, weight * 0.42F);
		PartPose body = new PartPose(0.04F, -0.18F, 0.0F, weight * 0.66F);
		PartPose head = new PartPose(0.02F, -0.36F, 0.04F, weight * 0.86F);
		return new BodyPose(actionArm, otherArm, body, PartPose.NONE, PartPose.NONE, head);
	}

	/** 双持成员只用武器手做很小的侧摆，另一只装备手完全保留原版姿势。 */
	private static BodyPose armedConfer(final float progress) {
		float weight = envelope(progress, 0.16F, 0.84F);
		float gesture = (float)Math.sin(progress * Math.PI * 3.0) * 0.10F;
		PartPose weaponArm = new PartPose(-0.76F + gesture, -0.24F, 0.10F, weight * 0.62F);
		PartPose body = new PartPose(0.03F, -0.16F, 0.0F, weight * 0.62F);
		PartPose head = new PartPose(0.02F, -0.34F, 0.04F, weight * 0.86F);
		return new BodyPose(weaponArm, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, head);
	}

	/** 首领先把手举过肩，再沿目标方向挥下；中段形成清晰的“全队出发”定格。 */
	private static BodyPose advanceOrder(final float progress) {
		float weight = envelope(progress, 0.14F, 0.88F);
		float strike = smooth((progress - 0.24F) / 0.28F);
		PartPose actionArm = new PartPose(-2.42F + strike * 1.18F, -0.28F, 0.20F, weight);
		PartPose otherArm = new PartPose(-0.72F, 0.22F, -0.10F, weight * 0.62F);
		PartPose body = new PartPose(0.10F, -0.18F, 0.0F, weight * 0.78F);
		PartPose actionLeg = new PartPose(-0.24F, 0.0F, 0.0F, weight * 0.46F);
		PartPose otherLeg = new PartPose(0.18F, 0.0F, 0.0F, weight * 0.40F);
		PartPose head = new PartPose(-0.08F, 0.12F, 0.0F, weight * 0.58F);
		return new BodyPose(actionArm, otherArm, body, actionLeg, otherLeg, head);
	}

	/** 双持首领以主手武器完成举起—前压，盾牌或副手装备继续保持自然位置。 */
	private static BodyPose armedAdvanceOrder(final float progress) {
		float weight = envelope(progress, 0.14F, 0.88F);
		float strike = smooth((progress - 0.24F) / 0.28F);
		PartPose weaponArm = new PartPose(-2.10F + strike * 0.92F, -0.18F, 0.18F, weight);
		PartPose body = new PartPose(0.09F, -0.16F, 0.0F, weight * 0.76F);
		PartPose actionLeg = new PartPose(-0.22F, 0.0F, 0.0F, weight * 0.44F);
		PartPose otherLeg = new PartPose(0.16F, 0.0F, 0.0F, weight * 0.38F);
		PartPose head = new PartPose(-0.08F, 0.10F, 0.0F, weight * 0.58F);
		return new BodyPose(weaponArm, PartPose.NONE, body, actionLeg, otherLeg, head);
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

	/** 动作臂轻敲另一侧盾面两次；调用层把动作臂选为非盾牌手。 */
	private static BodyPose shieldTap(final float progress) {
		float weight = envelope(progress, 0.14F, 0.86F);
		float tap = (1.0F - (float)Math.cos(progress * Math.PI * 4.0)) * 0.12F;
		PartPose tappingArm = new PartPose(-0.88F - tap, -0.56F, 0.12F, weight * 0.86F);
		PartPose shieldArm = new PartPose(-1.12F, 0.18F, -0.08F, weight * 0.74F);
		PartPose body = new PartPose(0.02F, -0.12F, 0.0F, weight * 0.52F);
		PartPose head = new PartPose(0.10F, -0.16F, 0.0F, weight * 0.60F);
		return new BodyPose(tappingArm, shieldArm, body, PartPose.NONE, PartPose.NONE, head);
	}

	private static BodyPose swordInspect(final float progress) {
		float weight = envelope(progress, 0.18F, 0.84F);
		float inspect = smooth(progress / 0.34F);
		PartPose swordArm = new PartPose(-1.02F - inspect * 0.24F, -0.42F, 0.16F, weight * 0.92F);
		PartPose body = new PartPose(0.03F, -0.12F, 0.0F, weight * 0.56F);
		PartPose head = new PartPose(0.18F, -0.24F, 0.0F, weight * 0.84F);
		return new BodyPose(swordArm, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, head);
	}

	private static BodyPose axeShoulder(final float progress) {
		float weight = envelope(progress, 0.18F, 0.84F);
		PartPose axeArm = new PartPose(-1.84F, 0.30F, 0.24F, weight * 0.94F);
		PartPose body = new PartPose(0.02F, 0.12F, 0.0F, weight * 0.54F);
		PartPose head = new PartPose(-0.04F, 0.18F, 0.0F, weight * 0.58F);
		return new BodyPose(axeArm, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, head);
	}

	private static BodyPose engineerCheck(final float progress) {
		float weight = envelope(progress, 0.16F, 0.86F);
		float check = (float)Math.sin(progress * Math.PI * 2.0) * 0.10F;
		PartPose toolArm = new PartPose(-0.92F + check, -0.30F, 0.12F, weight * 0.78F);
		PartPose body = new PartPose(0.28F, -0.08F, 0.0F, weight * 0.74F);
		PartPose head = new PartPose(0.30F, -0.10F, 0.0F, weight * 0.88F);
		return new BodyPose(toolArm, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, head);
	}

	private static BodyPose confusedTilt(final float progress) {
		float weight = envelope(progress, 0.14F, 0.86F);
		float side = (float)Math.sin(progress * Math.PI) * 0.22F;
		PartPose rightArm = new PartPose(-0.34F, -0.08F, 0.06F, weight * 0.28F);
		PartPose leftArm = mirror(rightArm);
		PartPose body = new PartPose(0.02F, -side * 0.22F, side * 0.18F, weight * 0.48F);
		PartPose head = new PartPose(-0.04F, side * 0.22F, side, weight);
		return new BodyPose(rightArm, leftArm, body, PartPose.NONE, PartPose.NONE, head);
	}

	private static BodyPose successionLookAround(final float progress) {
		float weight = envelope(progress, 0.12F, 0.88F);
		float scan = (float)Math.sin(progress * Math.PI * 2.0);
		PartPose body = new PartPose(0.02F, scan * 0.18F, 0.0F, weight * 0.58F);
		PartPose head = new PartPose(-0.02F, scan * 0.72F, 0.0F, weight);
		return new BodyPose(PartPose.NONE, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, head);
	}

	private static BodyPose successionSalute(final float progress) {
		float weight = envelope(progress, 0.16F, 0.88F);
		PartPose actionArm = new PartPose(-2.34F, -0.22F, 0.24F, weight);
		PartPose otherArm = new PartPose(-1.18F, 0.22F, -0.14F, weight * 0.78F);
		PartPose body = new PartPose(-0.14F, -0.10F, 0.0F, weight * 0.82F);
		PartPose head = new PartPose(-0.22F, 0.04F, 0.0F, weight * 0.92F);
		return new BodyPose(actionArm, otherArm, body, PartPose.NONE, PartPose.NONE, head);
	}

	private static BodyPose armedSuccessionSalute(final float progress) {
		float weight = envelope(progress, 0.16F, 0.88F);
		PartPose weaponArm = new PartPose(-2.18F, -0.16F, 0.22F, weight);
		PartPose body = new PartPose(-0.12F, -0.10F, 0.0F, weight * 0.80F);
		PartPose head = new PartPose(-0.22F, 0.04F, 0.0F, weight * 0.92F);
		return new BodyPose(weaponArm, PartPose.NONE, body, PartPose.NONE, PartPose.NONE, head);
	}

	private static BodyPose preserveOccupiedHands(
		final BodyPose pose,
		final boolean rightHandOccupied,
		final boolean leftHandOccupied
	) {
		return new BodyPose(
			rightHandOccupied ? PartPose.NONE : pose.rightArm(),
			leftHandOccupied ? PartPose.NONE : pose.leftArm(),
			pose.body(),
			pose.rightLeg(),
			pose.leftLeg(),
			pose.head()
		);
	}

	private static BodyPose preserveNonPointingHand(
		final BodyPose pose,
		final boolean pointingRight,
		final boolean rightHandOccupied,
		final boolean leftHandOccupied
	) {
		return new BodyPose(
			!pointingRight && rightHandOccupied ? PartPose.NONE : pose.rightArm(),
			pointingRight && leftHandOccupied ? PartPose.NONE : pose.leftArm(),
			pose.body(),
			pose.rightLeg(),
			pose.leftLeg(),
			pose.head()
		);
	}

	/** 采样器统一以“动作臂在右侧”建模，左手实体只需做一次完整左右镜像。 */
	private static BodyPose actionHanded(final BodyPose pose, final boolean actionArmRight) {
		if (actionArmRight) {
			return pose;
		}
		return mirrorBody(pose);
	}

	private static BodyPose mirrorBody(final BodyPose pose) {
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
