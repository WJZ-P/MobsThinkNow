package com.wjz.mobsthinknow.client.render;

import com.wjz.mobsthinknow.ai.giant.GiantBoardingPhase;
import com.wjz.mobsthinknow.ai.giant.GiantHand;
import com.wjz.mobsthinknow.ai.giant.GiantHandPhase;
import com.wjz.mobsthinknow.ai.giant.GiantMeleeAction;

/** GiantMobRenderer 写入、AbstractZombieModel 读取的挂点与全身格斗单帧快照。 */
public interface GiantCarrierRenderStateAccess {
	void mobsthinknow$setGiantHandState(
		GiantHand hand,
		GiantHandPhase phase,
		float progress,
		boolean loaded
	);

	GiantHandPhase mobsthinknow$getGiantHandPhase(GiantHand hand);

	float mobsthinknow$getGiantHandProgress(GiantHand hand);

	boolean mobsthinknow$isGiantHandLoaded(GiantHand hand);

	void mobsthinknow$setGiantBoardingState(GiantBoardingPhase phase, float progress);

	GiantBoardingPhase mobsthinknow$getGiantBoardingPhase();

	float mobsthinknow$getGiantBoardingProgress();

	void mobsthinknow$setGiantMeleeState(GiantMeleeAction action, float progress);

	GiantMeleeAction mobsthinknow$getGiantMeleeAction();

	float mobsthinknow$getGiantMeleeProgress();
}
