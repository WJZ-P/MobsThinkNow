package com.wjz.mobsthinknow.ai.activity;

/**
 * 跨 Goal 的战术活动类别。
 *
 * <p>原版 {@code Goal.Flag} 只表达 MOVE/LOOK/JUMP 等资源冲突，无法表达“工程施工”和
 * “撤退”虽然可能临时不写导航，却仍不能同时推进。这里的优先级只负责 Mod 内部活动抢占，
 * 不替代 GoalSelector 的原版优先级。</p>
 */
public enum TacticalActivity {
	MELEE(20),
	RANGED(25),
	POUNCE(35),
	FIRING_LANE_CLEAR(40),
	ENGINEERING(45),
	CARRIER_ASSEMBLY(55),
	CARRIER_DELIVERY(60),
	SQUAD_PREPARATION(65),
	RETREAT(80),
	AIR_ASSAULT(85),
	FIRE_SURVIVAL(90),
	EMERGENCY_DISENGAGE(95);

	private final int priority;

	TacticalActivity(final int priority) {
		this.priority = priority;
	}

	public int priority() {
		return this.priority;
	}
}
