package com.wjz.mobsthinknow.ai.spider;

import java.util.UUID;

/** 苦力怕身上的瞬时运输预约；蜘蛛与末影人共用同一租约，真实骑乘关系仍由原版负责序列化。 */
public interface CreeperTransportAccess {
	boolean mobsthinknow$tryReserveForCarrier(UUID carrierId, long currentTick, long expiresAtTick);

	boolean mobsthinknow$isReservedForCarrier(UUID carrierId, long currentTick);

	boolean mobsthinknow$isReservedForAnyCarrier(long currentTick);

	void mobsthinknow$releaseCarrierReservation(UUID carrierId);

	/** 旧蜘蛛调用名保留为默认适配器，避免既有源码和二进制接入点立即失效。 */
	default boolean mobsthinknow$tryReserveForSpider(
		final UUID spiderId,
		final long currentTick,
		final long expiresAtTick
	) {
		return this.mobsthinknow$tryReserveForCarrier(spiderId, currentTick, expiresAtTick);
	}

	default boolean mobsthinknow$isReservedForSpider(final UUID spiderId, final long currentTick) {
		return this.mobsthinknow$isReservedForCarrier(spiderId, currentTick);
	}

	default boolean mobsthinknow$isReservedForAnySpider(final long currentTick) {
		return this.mobsthinknow$isReservedForAnyCarrier(currentTick);
	}

	default void mobsthinknow$releaseSpiderReservation(final UUID spiderId) {
		this.mobsthinknow$releaseCarrierReservation(spiderId);
	}
}
