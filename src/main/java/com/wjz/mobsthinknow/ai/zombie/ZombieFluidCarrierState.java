package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * 流体辅助兵的持久化事务状态。
 *
 * @param utility 原始桶种类；放出流体后手里只剩空桶，仍需靠此字段记住应回收哪种流体
 * @param source 自己放出的源方块；为空表示桶当前在手中
 * @param retrieveAt 最早回收时刻
 * @param cooldownUntil 下次允许投放的时刻
 */
public record ZombieFluidCarrierState(
	UtilityClass utility,
	@Nullable BlockPos source,
	long retrieveAt,
	long cooldownUntil
) {
	public static final ZombieFluidCarrierState NONE = new ZombieFluidCarrierState(UtilityClass.NONE, null, 0L, 0L);

	public boolean isDeployed() {
		return this.utility != UtilityClass.NONE && this.source != null;
	}
}
