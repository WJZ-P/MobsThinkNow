package com.wjz.mobsthinknow.ai.spider;

/** 由 SpiderMixin 实现，向纯业务代码暴露蜘蛛的稳定智力值。 */
public interface SpiderIntelligenceAccess {
	int mobsthinknow$getSpiderIntelligence();

	void mobsthinknow$setSpiderIntelligence(int intelligence);
}
