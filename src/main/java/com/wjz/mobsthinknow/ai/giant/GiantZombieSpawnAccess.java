package com.wjz.mobsthinknow.ai.giant;

/** 普通僵尸 finalizeSpawn 与实体入世事件之间的一次性巨人替换标记。 */
public interface GiantZombieSpawnAccess {
	void mobsthinknow$markGiantReplacement();

	boolean mobsthinknow$consumeGiantReplacement();
}
