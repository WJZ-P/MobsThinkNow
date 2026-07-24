package com.wjz.mobsthinknow.ai.utility;

import java.util.List;
import java.util.Objects;

public final class UtilitySelector {
	private UtilitySelector() {
	}

	public static <T> T select(final List<ScoredOption<T>> options, final T current, final double switchMargin) {
		if (options.isEmpty()) {
			throw new IllegalArgumentException("At least one utility option is required.");
		}

		ScoredOption<T> best = options.getFirst();
		ScoredOption<T> currentOption = null;

		for (ScoredOption<T> option : options) {
			if (normalized(option.score()) > normalized(best.score())) {
				best = option;
			}

			if (Objects.equals(option.value(), current)) {
				currentOption = option;
			}
		}

		if (currentOption != null && normalized(currentOption.score()) + Math.max(0.0, switchMargin) >= normalized(best.score())) {
			return currentOption.value();
		}

		return best.value();
	}

	private static double normalized(final double score) {
		return Double.isNaN(score) ? Double.NEGATIVE_INFINITY : score;
	}
}
