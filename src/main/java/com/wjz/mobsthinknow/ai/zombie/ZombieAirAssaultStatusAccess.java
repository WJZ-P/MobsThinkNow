package com.wjz.mobsthinknow.ai.zombie;

/** 每只僵尸的瞬时空袭诊断状态；只服务运行观测，不写入世界存档。 */
public interface ZombieAirAssaultStatusAccess {
	ZombieSpearAirAssaultGoal.Phase mobsthinknow$getAirAssaultPhase();

	void mobsthinknow$setAirAssaultPhase(ZombieSpearAirAssaultGoal.Phase phase);

	int mobsthinknow$getRocketsLaunched();

	void mobsthinknow$recordRocketLaunch();

	int mobsthinknow$getDivesStarted();

	void mobsthinknow$recordDiveStart();
}
