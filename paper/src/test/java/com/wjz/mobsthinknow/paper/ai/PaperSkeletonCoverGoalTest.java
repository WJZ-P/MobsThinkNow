package com.wjz.mobsthinknow.paper.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PaperSkeletonCoverGoalTest {
	@Test
	void coverCellsStayOutsideTheSameEmergencyRangeUsedByTheHigherPriorityGoal() {
		assertFalse(PaperSkeletonCoverGoal.avoidsEmergencyDisengage(0, 7, 0.5, 0.0, 10.0, 10));
		assertTrue(PaperSkeletonCoverGoal.avoidsEmergencyDisengage(0, 8, 0.5, 0.0, 10.0, 10));
		assertTrue(PaperSkeletonCoverGoal.avoidsEmergencyDisengage(0, -9, 0.5, 0.0, 10.0, 10));
	}
}
