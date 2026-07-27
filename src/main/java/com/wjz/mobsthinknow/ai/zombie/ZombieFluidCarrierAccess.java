package com.wjz.mobsthinknow.ai.zombie;

/** 由 ZombieMixin 实现，用于把流体投放事务跟随实体存档。 */
public interface ZombieFluidCarrierAccess {
	ZombieFluidCarrierState mobsthinknow$getFluidCarrierState();

	void mobsthinknow$setFluidCarrierState(ZombieFluidCarrierState state);
}
