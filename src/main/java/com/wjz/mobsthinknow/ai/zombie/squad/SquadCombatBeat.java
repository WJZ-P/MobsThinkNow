package com.wjz.mobsthinknow.ai.zombie.squad;

/**
 * 交战阶段由首领统一推进的战斗节拍。
 *
 * <p>它不是新的 Goal，而是协调器发给所有成员的共享时钟：个体仍负责寻路、瞄准和攻击细节，
 * 只在对应窗口释放动作，避免混编小队重新退化成各打各的。</p>
 */
public enum SquadCombatBeat {
	/** 回到阵位、装填武器并等待下一轮口令。 */
	PREPARE(false, false, true),
	/** 远程单位按微错峰窗口释放火力，近战继续保持阵位。 */
	SUPPRESS(false, true, true),
	/** 首领下令后近战、载具与爆破单位同步突击。 */
	COMMIT(true, true, false),
	/** 已经接敌的成员继续追击，避免一次挥击后立刻机械撤退。 */
	EXPLOIT(true, true, false),
	/** 一轮攻势结束，成员重新拉开并为下一轮创造空间。 */
	RESET(false, false, true);

	private final boolean meleeAttackAllowed;
	private final boolean rangedAttackAllowed;
	private final boolean formationHeld;

	SquadCombatBeat(
		final boolean meleeAttackAllowed,
		final boolean rangedAttackAllowed,
		final boolean formationHeld
	) {
		this.meleeAttackAllowed = meleeAttackAllowed;
		this.rangedAttackAllowed = rangedAttackAllowed;
		this.formationHeld = formationHeld;
	}

	public boolean allowsMeleeAttack() {
		return this.meleeAttackAllowed;
	}

	public boolean allowsRangedAttack() {
		return this.rangedAttackAllowed;
	}

	public boolean holdsFormation() {
		return this.formationHeld;
	}
}
