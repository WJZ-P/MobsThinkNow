package com.wjz.mobsthinknow.client.render;

import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;

/** 客户端 ZombieRenderState 中的战术动作快照。 */
public interface ZombieBodyActionRenderStateAccess {
	void mobsthinknow$setBodyActionState(
		ZombieBodyAction action,
		float elapsedTicks,
		ZombieBodyAction previousAction,
		float previousElapsedTicks,
		float transitionElapsedTicks
	);

	ZombieBodyAction mobsthinknow$getBodyAction();

	float mobsthinknow$getBodyActionElapsedTicks();

	ZombieBodyAction mobsthinknow$getPreviousBodyAction();

	float mobsthinknow$getPreviousBodyActionElapsedTicks();

	float mobsthinknow$getBodyActionTransitionElapsedTicks();
}
