package com.wjz.mobsthinknow.client.render;

import com.wjz.mobsthinknow.ai.zombie.ZombieProfession;

/** 把实体同步职业快照带入 26.x 的无实体 RenderState 渲染阶段。 */
public interface ZombieProfessionRenderStateAccess {
	void mobsthinknow$setZombieProfession(ZombieProfession profession);

	ZombieProfession mobsthinknow$getZombieProfession();
}
