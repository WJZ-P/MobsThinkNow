package com.wjz.mobsthinknow.ai.giant;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Giant Mixin 暴露给通用逻辑的最小状态接口。
 *
 * <p>UUID 是存档中的稳定身份；实体 ID、阶段和阶段起始 tick 由 SynchedEntityData
 * 同步给客户端。业务代码只通过 {@link GiantTacticsState} 使用这些原始字段。</p>
 */
public interface GiantTacticsAccess {
	@Nullable UUID mobsthinknow$getPayloadUuid(GiantHand hand);

	void mobsthinknow$setPayloadUuid(GiantHand hand, @Nullable UUID uuid);

	int mobsthinknow$getPayloadEntityId(GiantHand hand);

	void mobsthinknow$setPayloadEntityId(GiantHand hand, int entityId);

	GiantHandPhase mobsthinknow$getHandPhase(GiantHand hand);

	void mobsthinknow$setHandPhase(GiantHand hand, GiantHandPhase phase);

	int mobsthinknow$getHandPhaseStartTick(GiantHand hand);

	void mobsthinknow$setHandPhaseStartTick(GiantHand hand, int tick);

	@Nullable UUID mobsthinknow$getBoardingRiderUuid();

	void mobsthinknow$setBoardingRiderUuid(@Nullable UUID uuid);

	int mobsthinknow$getBoardingRiderEntityId();

	void mobsthinknow$setBoardingRiderEntityId(int entityId);

	GiantBoardingPhase mobsthinknow$getBoardingPhase();

	void mobsthinknow$setBoardingPhase(GiantBoardingPhase phase);

	int mobsthinknow$getBoardingPhaseStartTick();

	void mobsthinknow$setBoardingPhaseStartTick(int tick);
}
