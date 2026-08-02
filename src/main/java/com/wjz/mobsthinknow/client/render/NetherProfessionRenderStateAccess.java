package com.wjz.mobsthinknow.client.render;

import com.wjz.mobsthinknow.ai.nether.NetherProfession;

/** 客户端每帧渲染快照携带的下界职业身份。 */
public interface NetherProfessionRenderStateAccess {
	void mobsthinknow$setNetherProfession(NetherProfession profession);

	NetherProfession mobsthinknow$getNetherProfession();
}
