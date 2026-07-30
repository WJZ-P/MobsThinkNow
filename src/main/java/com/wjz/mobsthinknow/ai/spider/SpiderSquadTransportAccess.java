package com.wjz.mobsthinknow.ai.spider;

/** 标记由混编载具 Goal 建立的乘员关系，避免影响原版自然生成的蜘蛛骑士。 */
public interface SpiderSquadTransportAccess {
	void mobsthinknow$markSquadPassenger(int passengerEntityId);

	void mobsthinknow$clearSquadPassenger();

	boolean mobsthinknow$isSquadPassenger(int passengerEntityId);
}
