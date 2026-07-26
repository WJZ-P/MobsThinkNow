package com.wjz.mobsthinknow.ai.zombie;

/**
 * 由 {@code ZombieMixin} 实现的实体数据访问接口。
 *
 * <p>智力值属于单只僵尸的长期特征，因此会随实体一起存档；小队、职位和命令则是战斗现场的
 * 临时状态，不会写入存档。</p>
 */
public interface ZombieIntelligenceAccess {
	int mobsthinknow$getIntelligence();

	/**
	 * 主要供测试、管理工具和未来的数据迁移使用；实现方必须把数值限制在 1～10。
	 */
	void mobsthinknow$setIntelligence(int intelligence);
}
