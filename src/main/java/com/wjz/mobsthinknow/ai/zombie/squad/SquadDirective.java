package com.wjz.mobsthinknow.ai.zombie.squad;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 协调器交给单个混编怪物的只读命令快照。
 *
 * @param term 首领任期；首领更替时递增
 * @param planEpoch 同一任期内的计划版本；重新集结或重新部署时递增
 */
public record SquadDirective(
	long squadId,
	int term,
	int planEpoch,
	SquadState state,
	SquadRole role,
	@Nullable Vec3 destination,
	@Nullable Vec3 focusPosition,
	boolean hasSharedTargetMemory
) {
	public boolean isMeetingPhase() {
		return this.state == SquadState.FORMING
			|| this.state == SquadState.RALLYING
			|| this.state == SquadState.BRIEFING
			|| this.state == SquadState.REORGANIZING;
	}

	public boolean isCombatPhase() {
		return this.state == SquadState.DEPLOYING || this.state == SquadState.ENGAGING;
	}
}
