package com.wjz.mobsthinknow.ai.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class UtilitySelectorTest {
	@Test
	void selectsHighestScoringOption() {
		String result = UtilitySelector.select(
			List.of(new ScoredOption<>("pressure", 10.0), new ScoredOption<>("flank", 20.0)),
			"pressure",
			2.0
		);

		assertEquals("flank", result);
	}

	@Test
	void keepsCurrentOptionInsideSwitchMargin() {
		String result = UtilitySelector.select(
			List.of(new ScoredOption<>("pressure", 18.0), new ScoredOption<>("flank", 20.0)),
			"pressure",
			3.0
		);

		assertEquals("pressure", result);
	}

	@Test
	void rejectsEmptyOptionList() {
		assertThrows(IllegalArgumentException.class, () -> UtilitySelector.select(List.of(), "pressure", 0.0));
	}
}
