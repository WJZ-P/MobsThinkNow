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
 * @param purpose 投放用途；日光自救水源会等到夜晚、降雨或水源被遮蔽后再回收
 */
public record ZombieFluidCarrierState(
	UtilityClass utility,
	@Nullable BlockPos source,
	long retrieveAt,
	long cooldownUntil,
	FluidDeploymentPurpose purpose
) {
	public static final ZombieFluidCarrierState NONE = new ZombieFluidCarrierState(
		UtilityClass.NONE,
		null,
		0L,
		0L,
		FluidDeploymentPurpose.COMBAT
	);

	/** 兼容已有战斗桶调用点与旧测试；未显式声明时一律视为战术投放。 */
	public ZombieFluidCarrierState(
		final UtilityClass utility,
		final @Nullable BlockPos source,
		final long retrieveAt,
		final long cooldownUntil
	) {
		this(utility, source, retrieveAt, cooldownUntil, FluidDeploymentPurpose.COMBAT);
	}

	public ZombieFluidCarrierState {
		purpose = purpose == null ? FluidDeploymentPurpose.COMBAT : purpose;
	}

	public boolean isDeployed() {
		return this.utility != UtilityClass.NONE && this.source != null;
	}

	public boolean isSunProtection() {
		return this.purpose == FluidDeploymentPurpose.SUN_PROTECTION;
	}
}
