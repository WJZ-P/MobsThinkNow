package com.wjz.mobsthinknow.ai.zombie.squad;

/** 成员的战术装备快照：主手兵种、盾牌与战场工具，供职位规划做匹配。 */
public record SquadLoadout(WeaponClass weapon, boolean shield, UtilityClass utility) {
	public static final SquadLoadout UNARMED = new SquadLoadout(WeaponClass.NONE, false, UtilityClass.NONE);

	/** 保留旧调用点的二参数构造语义。 */
	public SquadLoadout(final WeaponClass weapon, final boolean shield) {
		this(weapon, shield, UtilityClass.NONE);
	}
}
