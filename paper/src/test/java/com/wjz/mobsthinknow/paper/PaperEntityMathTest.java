package com.wjz.mobsthinknow.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class PaperEntityMathTest {
	@Test
	void primitiveDistanceMathPreservesThreeDimensionalAndHorizontalSemantics() {
		assertEquals(177.0, PaperEntityMath.distanceSquared(4.0, -2.0, 8.0, -4.0, 5.0, 0.0));
		assertEquals(128.0, PaperEntityMath.horizontalDistanceSquared(4.0, 8.0, -4.0, 0.0));
		assertEquals(0.0, PaperEntityMath.distanceSquared(-3.5, 64.0, 9.25, -3.5, 64.0, 9.25));
	}
}
