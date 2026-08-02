package com.wjz.mobsthinknow.client.render;

import com.wjz.mobsthinknow.ai.enderman.EndermanProfession;

/** 客户端末影人每帧快照携带的稳定职业身份。 */
public interface EndermanProfessionRenderStateAccess {
	void mobsthinknow$setEndermanProfession(EndermanProfession profession);

	EndermanProfession mobsthinknow$getEndermanProfession();
}
