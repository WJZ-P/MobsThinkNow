package com.wjz.mobsthinknow.ai.zombie.squad;

import net.minecraft.world.phys.Vec3;

/** 一轮限时伤员撤离中，协调器交给伤员或护卫的只读命令。 */
public record SquadCasualtyDirective(
	long squadId,
	int casualtyId,
	int escortId,
	Role role,
	Vec3 destination,
	Vec3 focusPosition,
	long endsAt
) {
	public enum Role {
		EVACUEE,
		ESCORT
	}
}
