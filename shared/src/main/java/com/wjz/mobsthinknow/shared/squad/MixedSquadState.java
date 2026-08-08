package com.wjz.mobsthinknow.shared.squad;

/** Paper 与 Fabric 可共同理解的小队阶段。 */
public enum MixedSquadState {
	FORMING,
	BRIEFING,
	DEPLOYING,
	ENGAGING,
	REORGANIZING;

	public boolean isMeetingPhase() {
		return this == FORMING || this == BRIEFING || this == REORGANIZING;
	}

	public boolean isFormationPhase() {
		return this != ENGAGING;
	}
}
