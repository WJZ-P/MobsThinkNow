package com.wjz.mobsthinknow.ai.spider;

import java.util.UUID;

/** 苦力怕身上的瞬时运输预约；预约不写入存档，真实骑乘关系仍由原版负责序列化。 */
public interface CreeperTransportAccess {
	boolean mobsthinknow$tryReserveForSpider(UUID spiderId, long currentTick, long expiresAtTick);

	boolean mobsthinknow$isReservedForSpider(UUID spiderId, long currentTick);

	boolean mobsthinknow$isReservedForAnySpider(long currentTick);

	void mobsthinknow$releaseSpiderReservation(UUID spiderId);
}
