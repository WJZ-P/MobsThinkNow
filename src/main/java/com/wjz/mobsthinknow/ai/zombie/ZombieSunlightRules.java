package com.wjz.mobsthinknow.ai.zombie;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.attribute.EnvironmentAttributes;

/** 与 26.1.2 原版日晒条件对齐的确定性判定，不复制原版那一层随机点火抽样。 */
final class ZombieSunlightRules {
	private static final Set<UUID> FORCED_TEST_EXPOSURE = ConcurrentHashMap.newKeySet();

	private ZombieSunlightRules() {
	}

	static boolean isExposed(final Zombie zombie, final ServerLevel level) {
		BlockPos eye = BlockPos.containing(zombie.getX(), zombie.getEyeY(), zombie.getZ());
		return zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
			&& !zombie.isInWaterOrRain()
			&& !zombie.isInPowderSnow
			&& !zombie.wasInPowderSnow
			&& (isForcedForTesting(zombie)
				? level.canSeeSky(eye)
				: isDangerousOpenSky(level, eye));
	}

	/** Goal 已启动后即使站进自己放出的水里，也继续利用这段安全时间寻找真正阴影。 */
	static boolean requiresEscape(final Zombie zombie, final ServerLevel level) {
		if (!zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			return false;
		}
		BlockPos eye = BlockPos.containing(zombie.getX(), zombie.getEyeY(), zombie.getZ());
		return isForcedForTesting(zombie) ? level.canSeeSky(eye) : isDangerousOpenSky(level, eye);
	}

	static boolean isDangerousSource(final Zombie zombie, final ServerLevel level, final BlockPos source) {
		return isForcedForTesting(zombie) ? level.canSeeSky(source.above()) : isDangerousOpenSky(level, source.above());
	}

	static boolean isDangerousOpenSky(final ServerLevel level, final BlockPos pos) {
		return level.isBrightOutside()
			&& level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, Vec3.atCenterOf(pos))
			&& !level.isRainingAt(pos)
			&& level.canSeeSky(pos);
	}

	static boolean isShaded(final Zombie zombie, final ServerLevel level) {
		BlockPos eye = BlockPos.containing(zombie.getX(), zombie.getEyeY(), zombie.getZ());
		return !level.canSeeSky(eye);
	}

	/** 仅供 GameTest 隔离单个实体，避免并行用例为了造日晒条件而修改全服世界时钟。 */
	static void forceExposureForTesting(final Zombie zombie, final boolean forced) {
		if (forced) {
			FORCED_TEST_EXPOSURE.add(zombie.getUUID());
		} else {
			FORCED_TEST_EXPOSURE.remove(zombie.getUUID());
		}
	}

	private static boolean isForcedForTesting(final Zombie zombie) {
		return FORCED_TEST_EXPOSURE.contains(zombie.getUUID());
	}
}
