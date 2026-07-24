package com.wjz.mobsthinknow.ai.utility;

import java.util.Objects;

public record ScoredOption<T>(T value, double score) {
	public ScoredOption {
		Objects.requireNonNull(value, "value");
	}
}
