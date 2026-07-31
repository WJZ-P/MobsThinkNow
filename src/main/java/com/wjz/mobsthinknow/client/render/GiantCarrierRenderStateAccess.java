package com.wjz.mobsthinknow.client.render;

import com.wjz.mobsthinknow.ai.giant.GiantBoardingPhase;
import com.wjz.mobsthinknow.ai.giant.GiantHand;
import com.wjz.mobsthinknow.ai.giant.GiantHandPhase;

/** GiantMobRenderer 写入、AbstractZombieModel 读取的单帧战术动作快照。 */
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
}
