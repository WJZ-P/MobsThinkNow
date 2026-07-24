package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.utility.ScoredOption;
import com.wjz.mobsthinknow.ai.utility.UtilitySelector;
import java.util.List;

public final class ZombieTacticEvaluator {
	private static final double DISABLED = Double.NEGATIVE_INFINITY;
	private static final double SWITCH_MARGIN = 4.0;

	private ZombieTacticEvaluator() {
	}

	public static ZombieTactic select(final ZombieDecisionContext context, final ZombieTactic current) {
		boolean pressureRole = context.packSize() > 1 && context.packIndex() == 0;
		boolean shieldNeedsFlank = context.hasLineOfSight()
			&& context.targetIsBlocking()
			&& context.zombieIsInFrontOfTarget()
			&& !pressureRole;
		boolean shouldSurround = context.hasLineOfSight() && context.packSize() > 1 && context.packIndex() > 0;

		double pressureScore = context.hasLineOfSight() ? 55.0 : 0.0;
		if (!context.targetIsBlocking()) {
			pressureScore += 12.0;
		}
		if (pressureRole) {
			pressureScore += 40.0;
		}

		double searchScore = !context.hasLineOfSight() && context.hasRecentLastSeenPosition() ? 120.0 : DISABLED;
		double leftScore = shieldNeedsFlank ? 92.0 + (context.prefersLeftFlank() ? 3.0 : 0.0) : DISABLED;
		double rightScore = shieldNeedsFlank ? 92.0 + (context.prefersLeftFlank() ? 0.0 : 3.0) : DISABLED;
		double surroundScore = shouldSurround ? 78.0 : DISABLED;

		return UtilitySelector.select(
			List.of(
				new ScoredOption<>(ZombieTactic.PRESSURE, pressureScore),
				new ScoredOption<>(ZombieTactic.SEARCH_LAST_SEEN, searchScore),
				new ScoredOption<>(ZombieTactic.FLANK_LEFT, leftScore),
				new ScoredOption<>(ZombieTactic.FLANK_RIGHT, rightScore),
				new ScoredOption<>(ZombieTactic.SURROUND, surroundScore)
			),
			current,
			SWITCH_MARGIN
		);
	}
}
