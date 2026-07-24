package com.wjz.mobsthinknow.ai.zombie;

public record ZombieDecisionContext(
	boolean hasLineOfSight,
	boolean hasRecentLastSeenPosition,
	boolean targetIsBlocking,
	boolean zombieIsInFrontOfTarget,
	int packSize,
	int packIndex,
	boolean prefersLeftFlank
) {
}
