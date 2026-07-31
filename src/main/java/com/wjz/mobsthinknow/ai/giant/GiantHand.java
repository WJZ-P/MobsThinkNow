package com.wjz.mobsthinknow.ai.giant;

/** 巨人的两个固定载荷槽；槽位身份不再依赖 passenger list 的可变顺序。 */
public enum GiantHand {
	RIGHT(0),
	LEFT(1);

	private final int index;

	GiantHand(final int index) {
		this.index = index;
	}

	public int index() {
		return this.index;
	}

	public GiantHand opposite() {
		return this == RIGHT ? LEFT : RIGHT;
	}

	public static GiantHand fromIndex(final int index) {
		return index == 1 ? LEFT : RIGHT;
	}
}
