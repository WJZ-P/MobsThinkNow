package com.wjz.mobsthinknow.ai.nether;

/** GameTest 与诊断层读取疣猪兽冲锋状态的只读接口。 */
public interface HoglinChargeAccess {
	HoglinChargeController.Phase mobsthinknow$getChargePhase();

	int mobsthinknow$getChargeTicksRemaining();
}
