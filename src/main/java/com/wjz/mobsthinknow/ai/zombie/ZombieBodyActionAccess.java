package com.wjz.mobsthinknow.ai.zombie;

/** 由 ZombieMixin 实现，保存低频同步的动作编号和开始 tick。 */
public interface ZombieBodyActionAccess {
	ZombieBodyAction mobsthinknow$getBodyAction();

	long mobsthinknow$getBodyActionStartedAt();

	void mobsthinknow$setBodyAction(ZombieBodyAction action, long startedAt);
}
