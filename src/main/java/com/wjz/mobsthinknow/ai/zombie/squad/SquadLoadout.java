package com.wjz.mobsthinknow.ai.zombie.squad;

/** 成员的战术装备快照：主手兵种 + 是否持盾，供职位规划做匹配。 */
public record SquadLoadout(WeaponClass weapon, boolean shield) {
	public static final SquadLoadout UNARMED = new SquadLoadout(WeaponClass.NONE, false);
}
