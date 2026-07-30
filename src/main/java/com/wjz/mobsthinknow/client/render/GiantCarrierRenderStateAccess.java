package com.wjz.mobsthinknow.client.render;

/** 巨人渲染快照上左右手是否正托着实体载荷的瞬时标记。 */
public interface GiantCarrierRenderStateAccess {
	void mobsthinknow$setGiantHeldPayloads(boolean rightHand, boolean leftHand);

	boolean mobsthinknow$isGiantRightHandLoaded();

	boolean mobsthinknow$isGiantLeftHandLoaded();
}
