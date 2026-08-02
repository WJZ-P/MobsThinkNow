package com.wjz.mobsthinknow.ai.nether;

import net.minecraft.util.Mth;

/**
 * 职业对既有战术状态机的纯参数修正。
 *
 * <p>这里不查询世界、不扫描实体；每次决策仍保持 O(1)，并且所有倍率都可独立做单元测试。</p>
 */
public final class NetherProfessionTactics {
	private NetherProfessionTactics() {
	}

	public static double piglinMoveSpeedMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case PIGLIN_MARKSMAN -> 0.96;
			case PIGLIN_COMMANDER -> 1.04;
			case PIGLIN_VANGUARD -> 1.10;
			default -> 1.0;
		};
	}

	public static double blazeRangeMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case BLAZE_VOLLEYMASTER -> 1.12;
			case BLAZE_CINDER_GUARD -> 0.72;
			default -> 1.0;
		};
	}

	public static int blazeVolleySize(final int baseVolley, final NetherProfession profession) {
		return switch (profession) {
			case BLAZE_VOLLEYMASTER -> Math.min(5, baseVolley + 1);
			case BLAZE_CINDER_GUARD -> Math.max(2, baseVolley - 1);
			default -> baseVolley;
		};
	}

	public static int blazeChargeTicks(final int baseTicks, final NetherProfession profession) {
		return switch (profession) {
			case BLAZE_VOLLEYMASTER -> baseTicks + 5;
			case BLAZE_CINDER_GUARD -> Math.max(16, baseTicks - 6);
			default -> baseTicks;
		};
	}

	public static double blazeUncertaintyMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case BLAZE_VOLLEYMASTER -> 0.82;
			case BLAZE_CINDER_GUARD -> 1.15;
			default -> 1.0;
		};
	}

	public static double ghastPredictionMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case GHAST_SPOTTER -> 1.25;
			case GHAST_SIEGEBREAKER -> 0.88;
			default -> 1.0;
		};
	}

	public static double ghastRelocationRadiusMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case GHAST_SPOTTER -> 1.16;
			case GHAST_SIEGEBREAKER -> 0.82;
			default -> 1.0;
		};
	}

	public static int ghastExplosionPower(final int basePower, final NetherProfession profession) {
		return profession == NetherProfession.GHAST_SIEGEBREAKER
			? Mth.clamp(basePower + 1, 1, 3)
			: basePower;
	}

	public static int hoglinWindupTicks(final int baseTicks, final NetherProfession profession) {
		return switch (profession) {
			case HOGLIN_RAVAGER -> Math.max(7, baseTicks - 4);
			case HOGLIN_BULWARK -> baseTicks + 4;
			default -> baseTicks;
		};
	}

	public static double hoglinImpulseMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case HOGLIN_RAVAGER -> 1.14;
			case HOGLIN_BULWARK -> 0.88;
			default -> 1.0;
		};
	}

	public static double magmaPounceMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case MAGMA_AMBUSHER -> 1.14;
			case MAGMA_TITAN -> 0.90;
			default -> 1.0;
		};
	}
}
