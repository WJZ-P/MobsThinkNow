package com.wjz.mobsthinknow.ai.spider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpiderTransportRouteEvaluatorTest {
	@Test
	void distinguishesPassengerHeadroomFromCarrierWidthFailure() {
		assertEquals(
			SpiderTransportRouteEvaluator.Status.CLEAR,
			SpiderTransportRouteEvaluator.classifyClearance(true, true)
		);
		assertEquals(
			SpiderTransportRouteEvaluator.Status.LOW_CEILING,
			SpiderTransportRouteEvaluator.classifyClearance(false, true)
		);
		assertEquals(
			SpiderTransportRouteEvaluator.Status.NARROW,
			SpiderTransportRouteEvaluator.classifyClearance(false, false)
		);
	}

	@Test
	void onlyClearRoutesAreUsable() {
		assertTrue(SpiderTransportRouteEvaluator.Status.CLEAR.usable());
		assertFalse(SpiderTransportRouteEvaluator.Status.UNREACHABLE.usable());
		assertFalse(SpiderTransportRouteEvaluator.Status.LOW_CEILING.usable());
		assertFalse(SpiderTransportRouteEvaluator.Status.NARROW.usable());
		assertFalse(SpiderTransportRouteEvaluator.Status.DANGEROUS_DROP.usable());
	}
}
