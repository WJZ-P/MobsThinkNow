package com.wjz.mobsthinknow.ai.zombie;

/**
 * 给僵尸暴露原版 {@code LivingEntity} 已有、但只在 {@code Player} 上提供公开入口的滑翔开关。
 *
 * <p>飞行动力学、鞘翅耐久和烟花助推仍全部由原版执行；这里不复制物理，只补齐怪物启动滑翔的入口。</p>
 */
public interface ZombieFlightAccess {
	void mobsthinknow$startFallFlying();

	void mobsthinknow$stopFallFlying();
}
